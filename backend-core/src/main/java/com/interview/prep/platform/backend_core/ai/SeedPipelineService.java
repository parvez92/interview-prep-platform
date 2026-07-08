package com.interview.prep.platform.backend_core.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.prep.platform.backend_core.study.Topic;
import com.interview.prep.platform.backend_core.study.TopicRepository;
import com.interview.prep.platform.backend_core.user.UserSettings;
import com.interview.prep.platform.backend_core.user.UserSettingsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Content pipeline v2 orchestration (docs/content-pipeline-v2.md).
 * After plan commit: narrative → per week (depth → support), run in the
 * background so the user can start studying while later weeks are seeded.
 * Depth output is mechanically validated; failures regenerate per-topic,
 * two failures mark the topic needs_review instead of blocking.
 */
@Service
@Slf4j
public class SeedPipelineService {

    // local models need small batches for JSON reliability; API models handle much more per call
    private static final int DEPTH_BATCH_LOCAL = 3;
    private static final int DEPTH_BATCH_API = 8;
    private static final int SUPPORT_BATCH_LOCAL = 6;
    private static final int SUPPORT_BATCH_API = 12;

    private final TopicRepository topicRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final AiClient aiClient;
    private final AiGatewayService aiGatewayService;
    private final BudgetGuard budgetGuard;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate tx;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "seed-pipeline");
        t.setDaemon(true);
        return t;
    });
    private final Map<Long, Map<String, Object>> statusByUser = new ConcurrentHashMap<>();

    public SeedPipelineService(TopicRepository topicRepository,
                               UserSettingsRepository userSettingsRepository,
                               AiClient aiClient,
                               AiGatewayService aiGatewayService,
                               BudgetGuard budgetGuard,
                               ObjectMapper objectMapper,
                               PlatformTransactionManager txManager) {
        this.topicRepository = topicRepository;
        this.userSettingsRepository = userSettingsRepository;
        this.aiClient = aiClient;
        this.aiGatewayService = aiGatewayService;
        this.budgetGuard = budgetGuard;
        this.objectMapper = objectMapper;
        this.tx = new TransactionTemplate(txManager);
    }

    public synchronized Map<String, Object> start(Long userId) {
        Map<String, Object> current = statusByUser.get(userId);
        if (current != null && "running".equals(current.get("state"))) {
            return current;
        }
        Map<String, Object> st = new ConcurrentHashMap<>();
        st.put("state", "running");
        st.put("stage", "starting");
        st.put("weeksDone", 0);
        st.put("weeksTotal", 0);
        st.put("topicsDeepened", 0);
        st.put("needsReview", 0);
        st.put("startedAt", Instant.now().toString());
        statusByUser.put(userId, st);
        executor.submit(() -> run(userId, st));
        return Map.of("status", "started");
    }

    public Map<String, Object> status(Long userId) {
        return statusByUser.getOrDefault(userId, Map.of("state", "idle"));
    }

    // ── pipeline ────────────────────────────────────────────────────────────────

    private void run(Long userId, Map<String, Object> st) {
        try {
            boolean local = "ollama".equalsIgnoreCase(budgetGuard.resolveProvider(userId));
            int depthBatch = local ? DEPTH_BATCH_LOCAL : DEPTH_BATCH_API;
            int supportBatch = local ? SUPPORT_BATCH_LOCAL : SUPPORT_BATCH_API;
            // depth deserves the strong model; narrative/support are structured, cheap work.
            // Over budget → no overrides, BudgetGuard's cheap tier applies everywhere (cost rule).
            boolean overBudget = budgetGuard.resolveTier(userId) == BudgetGuard.Tier.CHEAP;
            UserSettings settings = userSettingsRepository.findByUserId(userId).orElse(null);
            String strongModel = !overBudget && settings != null ? settings.getLlmModelStrong() : null;
            String cheapModel = settings != null ? settings.getLlmModelCheap() : null;

            // narrative first — one cheap call, makes the plan readable immediately
            st.put("stage", "narrative");
            try {
                aiGatewayService.generateNarrative(userId, cheapModel);
            } catch (Exception e) {
                log.warn("narrative pass failed (non-fatal): {}", e.getMessage());
            }

            List<Topic> all = topicRepository.findByUserIdWithWeekOrdered(userId);
            LinkedHashMap<Long, List<Topic>> byWeek = new LinkedHashMap<>();
            for (Topic t : all) {
                byWeek.computeIfAbsent(t.getWeek().getId(), k -> new ArrayList<>()).add(t);
            }
            st.put("weeksTotal", byWeek.size());

            for (Map.Entry<Long, List<Topic>> week : byWeek.entrySet()) {
                st.put("stage", "depth");
                depthForWeek(userId, week.getValue(), st, depthBatch, strongModel);

                st.put("stage", "support");
                List<Topic> finalTopics = topicRepository.findByUserIdWithWeekOrdered(userId).stream()
                        .filter(t -> t.getWeek().getId().equals(week.getKey()))
                        .toList();
                try {
                    aiGatewayService.seedSupportForTopics(userId, finalTopics, supportBatch, cheapModel);
                } catch (Exception e) {
                    log.warn("support pass failed for week {} (non-fatal): {}", week.getKey(), e.getMessage());
                }
                st.put("weeksDone", (int) st.get("weeksDone") + 1);
            }

            st.put("stage", "done");
            st.put("state", "done");
        } catch (Exception e) {
            log.error("seed pipeline failed for user {}", userId, e);
            st.put("state", "failed");
            st.put("message", String.valueOf(e.getMessage()));
        }
    }

    private void depthForWeek(Long userId, List<Topic> weekTopics, Map<String, Object> st,
                              int depthBatch, String modelOverride) {
        // only coarse topics that haven't been deepened yet (resumable)
        List<Topic> pending = weekTopics.stream()
                .filter(t -> t.getConcept() == null || t.getConcept().isBlank())
                .toList();
        if (pending.isEmpty()) return;

        // group by category — each depth call carries ONE category exemplar
        Map<String, List<Topic>> byCategory = new LinkedHashMap<>();
        for (Topic t : pending) {
            String cat = t.getCategory() != null ? t.getCategory() : "domain";
            byCategory.computeIfAbsent(cat, k -> new ArrayList<>()).add(t);
        }

        for (List<Topic> catTopics : byCategory.values()) {
            for (int i = 0; i < catTopics.size(); i += depthBatch) {
                List<Topic> batch = catTopics.subList(i, Math.min(i + depthBatch, catTopics.size()));
                Map<String, List<Map<String, Object>>> cardsByParent = callDepth(userId, batch, false, modelOverride);

                for (Topic coarse : batch) {
                    List<Map<String, Object>> cards = validCards(cardsByParent.get(coarse.getSlug()));
                    if (cards.isEmpty()) {
                        // per-topic regeneration, cache bypassed
                        cards = validCards(callDepth(userId, List.of(coarse), true, modelOverride).get(coarse.getSlug()));
                    }
                    if (cards.isEmpty()) {
                        markNeedsReview(coarse.getId());
                        st.put("needsReview", (int) st.get("needsReview") + 1);
                        continue;
                    }
                    persistCards(userId, coarse.getId(), cards);
                    st.put("topicsDeepened", (int) st.get("topicsDeepened") + cards.size());
                }
            }
        }
    }

    /** Calls /ai/deep-dive for a batch; returns cards grouped by parent slug. */
    @SuppressWarnings("unchecked")
    private Map<String, List<Map<String, Object>>> callDepth(Long userId, List<Topic> batch, boolean regenerate,
                                                             String modelOverride) {
        UserSettings settings = userSettingsRepository.findByUserId(userId).orElse(null);

        List<Map<String, Object>> topicList = batch.stream().map(t -> {
            Map<String, Object> m = new HashMap<String, Object>();
            m.put("slug", t.getSlug());
            m.put("title", t.getTitle());
            m.put("scope", t.getAngle() != null ? t.getAngle() : "");
            m.put("split_hint", t.getSplitHint() != null ? t.getSplitHint() : "none");
            m.put("category", t.getCategory() != null ? t.getCategory() : "domain");
            return m;
        }).toList();

        Map<String, Object> body = new HashMap<>();
        body.put("topics", topicList);
        body.put("seniority", settings != null && settings.getTargetLevel() != null ? settings.getTargetLevel() : "mid");
        body.put("target_role", settings != null && settings.getTargetRole() != null ? settings.getTargetRole() : "Software Engineer");
        if (regenerate) body.put("regenerate", true);

        Map<String, List<Map<String, Object>>> byParent = new HashMap<>();
        try {
            Map<String, Object> response = aiClient.post(userId, "/ai/deep-dive", body, "depth",
                    null, null, modelOverride);
            if (response != null && response.get("result") instanceof Map<?, ?> result
                    && result.get("topics") instanceof List<?> cards) {
                for (Object o : cards) {
                    if (o instanceof Map<?, ?> card && card.get("parent") instanceof String parent) {
                        byParent.computeIfAbsent(parent, k -> new ArrayList<>())
                                .add((Map<String, Object>) card);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("depth call failed for batch {}: {}",
                    batch.stream().map(Topic::getSlug).toList(), e.getMessage());
        }
        return byParent;
    }

    /** Keeps only cards that pass mechanical validation, max 3 per coarse topic. */
    private List<Map<String, Object>> validCards(List<Map<String, Object>> cards) {
        if (cards == null) return List.of();
        List<Map<String, Object>> valid = new ArrayList<>();
        for (Map<String, Object> card : cards) {
            List<String> failures = ContentValidator.validateDepthTopic(card);
            if (failures.isEmpty()) {
                valid.add(card);
                if (valid.size() == 3) break;
            } else {
                log.info("depth card rejected ({}): {}", card.get("title"), failures);
            }
        }
        return valid;
    }

    private void markNeedsReview(Long topicId) {
        tx.executeWithoutResult(s -> topicRepository.findById(topicId).ifPresent(t -> {
            t.setNeedsReview(true);
            topicRepository.save(t);
        }));
    }

    /** First card updates the coarse topic in place; extra cards become sibling topics. */
    private void persistCards(Long userId, Long coarseId, List<Map<String, Object>> cards) {
        tx.executeWithoutResult(s -> {
            Topic coarse = topicRepository.findById(coarseId).orElse(null);
            if (coarse == null) return;

            applyCard(coarse, cards.get(0));
            coarse.setNeedsReview(false);
            topicRepository.save(coarse);

            for (int i = 1; i < cards.size(); i++) {
                Topic split = new Topic();
                split.setUserId(userId);
                split.setWeek(coarse.getWeek());
                split.setCode("t-" + System.nanoTime());
                split.setSlug(uniqueSlug(userId, str(cards.get(i), "title", coarse.getSlug() + "-" + i)));
                split.setSource("ai-generated");
                split.setStatus("todo");
                split.setTag("new");
                split.setCategory(coarse.getCategory());
                split.setCoarseParent(coarse.getSlug());
                split.setDisplayOrder(coarse.getDisplayOrder());
                applyCard(split, cards.get(i));
                topicRepository.save(split);
            }
        });
    }

    private void applyCard(Topic topic, Map<String, Object> card) {
        String title = str(card, "title", null);
        if (title != null && !title.isBlank()) {
            topic.setTitle(title.length() > 255 ? title.substring(0, 255) : title);
        }
        topic.setConcept(str(card, "concept", null));
        topic.setAngle(str(card, "angle", null));
        if (card.get("est_minutes") instanceof Number n) {
            topic.setEstMinutes(Math.max(ContentValidator.MIN_EST_MINUTES,
                    Math.min(ContentValidator.MAX_EST_MINUTES, n.intValue())));
        }
        if (card.get("points") instanceof List<?> points) {
            try {
                topic.setPoints(objectMapper.writeValueAsString(points));
            } catch (Exception e) {
                topic.setPoints("[]");
            }
        }
        topic.setSplitHint(null); // consumed
    }

    private String uniqueSlug(Long userId, String title) {
        String base = title.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .strip()
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-");
        if (base.isBlank()) base = "topic";
        if (base.length() > 240) base = base.substring(0, 240);
        String slug = base;
        int i = 2;
        while (topicRepository.existsByUserIdAndSlug(userId, slug)) {
            slug = base + "-" + i++;
        }
        return slug;
    }

    private static String str(Map<String, Object> m, String key, String def) {
        Object v = m.get(key);
        return v instanceof String s && !s.isBlank() ? s : def;
    }
}

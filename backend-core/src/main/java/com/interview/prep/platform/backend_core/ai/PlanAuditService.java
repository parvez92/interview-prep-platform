package com.interview.prep.platform.backend_core.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.prep.platform.backend_core.ai.CoverageAuditor.Gap;
import com.interview.prep.platform.backend_core.ai.CoverageAuditor.SkillCoverage;
import com.interview.prep.platform.backend_core.ai.ProfileSkills.Skill;
import com.interview.prep.platform.backend_core.onboarding.ResumeProfileRepository;
import com.interview.prep.platform.backend_core.study.Topic;
import com.interview.prep.platform.backend_core.study.TopicRepository;
import com.interview.prep.platform.backend_core.user.UserSettings;
import com.interview.prep.platform.backend_core.user.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Plan-review audit (fix-brief §2 + §4): does this plan actually cover what the résumé
 * claims, and does it fit the hours the candidate has? Suggestions only — the user
 * decides what gets inserted.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlanAuditService {

    /** Nominal effort for a coarse topic before the depth pass gives it a real estimate. */
    private static final int ASSUMED_COARSE_MINUTES = 45;
    private static final int DEFAULT_HOURS_PER_WEEK = 10;

    private final CoverageAuditor coverageAuditor;
    private final Checklists checklists;
    private final TopicRepository topicRepository;
    private final ResumeProfileRepository resumeProfileRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final ObjectMapper objectMapper;

    /** One uncovered checklist item, with the week it would slot into. */
    public record Suggestion(String id, String label, String skill, String category,
                             int phaseIndex, int weekIndex, String weekTitle) {}

    public record WeekLoad(int phaseIndex, int weekIndex, String weekTitle,
                           int plannedMinutes, int budgetMinutes, boolean estimated) {
        /** `is`-prefixed so Jackson serialises it alongside the record components. */
        public boolean isOver() {
            return plannedMinutes > budgetMinutes;
        }
    }

    public record Audit(List<SkillCoverage> coverage, List<Suggestion> suggestions,
                        List<WeekLoad> weeks, int budgetMinutes, int prepWeeks) {}

    /** A week as the audit sees it, from either the draft plan JSON or the committed rows. */
    private record PlanWeek(int phaseIndex, int weekIndex, String title,
                            List<String> topicTexts, List<String> categories, Integer minutes) {}

    /**
     * @param draftPhases the in-review plan (frontend Phase[] shape); null audits the
     *                    committed plan instead.
     */
    @Transactional(readOnly = true)
    public Audit audit(Long userId, List<Map<String, Object>> draftPhases) {
        List<Skill> skills = ProfileSkills.from(
                resumeProfileRepository.findByUserId(userId).orElse(null), objectMapper);

        List<PlanWeek> weeks = draftPhases != null && !draftPhases.isEmpty()
                ? fromDraft(draftPhases)
                : fromCommitted(userId);

        List<String> allTopicTexts = weeks.stream().flatMap(w -> w.topicTexts().stream()).toList();
        List<SkillCoverage> coverage = coverageAuditor.audit(allTopicTexts, skills);

        UserSettings settings = userSettingsRepository.findByUserId(userId).orElse(null);
        int hoursPerWeek = settings != null && settings.getHoursPerWeek() != null
                ? settings.getHoursPerWeek() : DEFAULT_HOURS_PER_WEEK;
        int budgetMinutes = hoursPerWeek * 60;

        return new Audit(coverage,
                suggest(coverage, weeks),
                loads(weeks, budgetMinutes),
                budgetMinutes,
                weeks.size());
    }

    // ── suggestion placement ────────────────────────────────────────────────────

    /** Each gap lands in the earliest, least-loaded week that already teaches its category. */
    private List<Suggestion> suggest(List<SkillCoverage> coverage, List<PlanWeek> weeks) {
        List<Suggestion> out = new ArrayList<>();
        for (SkillCoverage sc : coverage) {
            for (Gap gap : sc.gaps()) {
                PlanWeek target = bestWeek(weeks, sc.category());
                if (target == null) continue;
                out.add(new Suggestion(gap.id(), gap.label(), sc.skill(), sc.category(),
                        target.phaseIndex(), target.weekIndex(), target.title()));
            }
        }
        return out;
    }

    private PlanWeek bestWeek(List<PlanWeek> weeks, String category) {
        PlanWeek best = null;
        for (PlanWeek w : weeks) {
            boolean sameCategory = w.categories().stream().anyMatch(c -> c.equalsIgnoreCase(category));
            if (!sameCategory) continue;
            if (best == null || w.topicTexts().size() < best.topicTexts().size()) best = w;
        }
        // no week teaches this category yet — fall back to the first week of the audit phase
        return best != null ? best : (weeks.isEmpty() ? null : weeks.get(0));
    }

    // ── week budget ─────────────────────────────────────────────────────────────

    private List<WeekLoad> loads(List<PlanWeek> weeks, int budgetMinutes) {
        List<WeekLoad> out = new ArrayList<>();
        for (PlanWeek w : weeks) {
            boolean estimated = w.minutes() == null;
            int planned = estimated ? w.topicTexts().size() * ASSUMED_COARSE_MINUTES : w.minutes();
            out.add(new WeekLoad(w.phaseIndex(), w.weekIndex(), w.title(), planned, budgetMinutes, estimated));
        }
        return out;
    }

    // ── plan sources ────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<PlanWeek> fromDraft(List<Map<String, Object>> phases) {
        List<PlanWeek> out = new ArrayList<>();
        for (int pi = 0; pi < phases.size(); pi++) {
            Object rawWeeks = phases.get(pi).getOrDefault("weeks_detail", phases.get(pi).get("weeks"));
            if (!(rawWeeks instanceof List<?> weekList)) continue;
            for (int wi = 0; wi < weekList.size(); wi++) {
                if (!(weekList.get(wi) instanceof Map<?, ?> raw)) continue;
                Map<String, Object> wm = (Map<String, Object>) raw;
                Object rawTopics = wm.get("coarse_topics") != null ? wm.get("coarse_topics") : wm.get("topics");
                List<String> texts = new ArrayList<>();
                List<String> categories = new ArrayList<>();
                if (rawTopics instanceof List<?> topics) {
                    for (Object t : topics) {
                        if (!(t instanceof Map<?, ?> tm)) continue;
                        texts.add(text(tm.get("title"), tm.get("scope"), tm.get("resources_hint")));
                        if (tm.get("category") instanceof String c && !c.isBlank()) categories.add(c);
                    }
                }
                String title = wm.get("title") instanceof String s && !s.isBlank() ? s : "Week " + (wi + 1);
                out.add(new PlanWeek(pi, wi, title, texts, categories, null));
            }
        }
        return out;
    }

    private List<PlanWeek> fromCommitted(Long userId) {
        // week id → accumulating view, in plan order
        LinkedHashMap<Long, List<Topic>> byWeek = new LinkedHashMap<>();
        for (Topic t : topicRepository.findByUserIdWithWeekOrdered(userId)) {
            byWeek.computeIfAbsent(t.getWeek().getId(), k -> new ArrayList<>()).add(t);
        }

        List<PlanWeek> out = new ArrayList<>();
        Map<String, Integer> phaseIndexByCode = new LinkedHashMap<>();
        int wi = 0;
        for (List<Topic> topics : byWeek.values()) {
            Topic head = topics.get(0);
            String phaseCode = head.getWeek().getPhase().getCode();
            int pi = phaseIndexByCode.computeIfAbsent(phaseCode, k -> phaseIndexByCode.size());

            List<String> texts = new ArrayList<>();
            List<String> categories = new ArrayList<>();
            int minutes = 0;
            boolean anyEstimate = false;
            for (Topic t : topics) {
                texts.add(text(t.getTitle(), t.getAngle(), t.getConcept()) + " " + points(t));
                if (t.getCategory() != null) categories.add(t.getCategory());
                if (t.getEstMinutes() != null) {
                    minutes += t.getEstMinutes();
                    anyEstimate = true;
                }
            }
            out.add(new PlanWeek(pi, wi++, head.getWeek().getTitle(), texts, categories,
                    anyEstimate ? minutes : null));
        }
        return out;
    }

    private String points(Topic t) {
        try {
            if (t.getPoints() == null || t.getPoints().isBlank()) return "";
            List<?> list = objectMapper.readValue(t.getPoints(), List.class);
            return list.stream().map(String::valueOf).reduce("", (a, b) -> a + " " + b);
        } catch (Exception e) {
            return "";
        }
    }

    private static String text(Object... parts) {
        StringBuilder sb = new StringBuilder();
        for (Object p : parts) {
            if (p instanceof String s && !s.isBlank()) sb.append(s).append(' ');
        }
        return sb.toString().toLowerCase(Locale.ROOT).strip();
    }
}

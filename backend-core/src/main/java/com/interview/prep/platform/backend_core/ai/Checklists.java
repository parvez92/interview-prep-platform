package com.interview.prep.platform.backend_core.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * library/checklists/*.json — the topics a senior claim in a given skill is expected to
 * survive. Drop a new file in the directory to audit another skill; nothing else changes.
 */
@Component
@Slf4j
public class Checklists {

    public record Item(String id, String label, List<String> match) {}

    public record Checklist(String category, String skill, String level, List<Item> items) {}

    private static final String PATTERN = "classpath*:library/checklists/*.json";

    private final List<Checklist> checklists;

    public Checklists(ObjectMapper mapper) {
        this.checklists = load(mapper);
    }

    public List<Checklist> all() {
        return checklists;
    }

    @SuppressWarnings("unchecked")
    private static List<Checklist> load(ObjectMapper mapper) {
        List<Checklist> loaded = new ArrayList<>();
        try {
            Resource[] found = new PathMatchingResourcePatternResolver().getResources(PATTERN);
            for (Resource r : found) {
                try (var in = r.getInputStream()) {
                    Map<String, Object> root = mapper.readValue(in, Map.class);
                    List<Item> items = new ArrayList<>();
                    if (root.get("items") instanceof List<?> raw) {
                        for (Object o : raw) {
                            if (!(o instanceof Map<?, ?> m)) continue;
                            List<String> match = m.get("match") instanceof List<?> l
                                    ? l.stream().map(String::valueOf).toList() : List.of();
                            items.add(new Item(String.valueOf(m.get("id")),
                                    String.valueOf(m.get("label")), match));
                        }
                    }
                    loaded.add(new Checklist(
                            String.valueOf(root.get("category")),
                            String.valueOf(root.get("skill")),
                            String.valueOf(root.get("level")),
                            items));
                } catch (Exception e) {
                    log.warn("skipping checklist {}: {}", r.getFilename(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("no checklists loaded: {}", e.getMessage());
        }
        return List.copyOf(loaded);
    }
}

package com.interview.prep.platform.backend_core.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * library/skill-surface.json — maps topic scope terms onto a resume skill.
 * Loaded once at startup; see the file's own `_doc` for why the term lists
 * deliberately omit a skill's newest APIs.
 */
@Component
@Slf4j
public class SkillSurface {

    public record Entry(String skill, List<String> names, List<String> terms) {}

    private static final String PATH = "library/skill-surface.json";

    private final List<Entry> entries;

    public SkillSurface(ObjectMapper mapper) {
        this.entries = load(mapper);
    }

    @SuppressWarnings("unchecked")
    private static List<Entry> load(ObjectMapper mapper) {
        try (var in = new ClassPathResource(PATH).getInputStream()) {
            Map<String, Object> root = mapper.readValue(in, Map.class);
            if (!(root.get("skills") instanceof List<?> raw)) return List.of();
            return raw.stream()
                    .filter(Map.class::isInstance)
                    .map(o -> (Map<String, Object>) o)
                    .map(m -> new Entry(
                            String.valueOf(m.get("skill")),
                            strings(m.get("names")),
                            strings(m.get("terms"))))
                    .toList();
        } catch (Exception e) {
            log.warn("skill-surface.json unreadable, term→skill mapping disabled: {}", e.getMessage());
            return List.of();
        }
    }

    private static List<String> strings(Object o) {
        return o instanceof List<?> l ? l.stream().map(String::valueOf).toList() : List.of();
    }

    /** The surface entry whose `names` this resume skill answers to, if any. */
    public Entry forSkillName(String skillName) {
        if (skillName == null) return null;
        String lower = skillName.toLowerCase(Locale.ROOT);
        for (Entry e : entries) {
            for (String name : e.names()) {
                if (lower.contains(name) || name.contains(lower)) return e;
            }
        }
        return null;
    }

    /** True when any of the entry's established-surface terms appears in the topic text. */
    public boolean touches(Entry entry, String topicText) {
        if (entry == null || topicText == null) return false;
        String haystack = topicText.toLowerCase(Locale.ROOT);
        return entry.terms().stream().anyMatch(term -> containsTerm(haystack, term));
    }

    /**
     * Substring match, but bounded on word characters so short terms like "rest" or "g1"
     * don't fire inside "interest" or "g10".
     */
    static boolean containsTerm(String lowerHaystack, String term) {
        String t = term.toLowerCase(Locale.ROOT);
        if (t.isBlank()) return false;
        String prefix = Character.isLetterOrDigit(t.charAt(0)) ? "\\b" : "";
        String suffix = Character.isLetterOrDigit(t.charAt(t.length() - 1)) ? "\\b" : "";
        Pattern p = Pattern.compile(prefix + Pattern.quote(t) + suffix);
        Matcher m = p.matcher(lowerHaystack);
        return m.find();
    }
}

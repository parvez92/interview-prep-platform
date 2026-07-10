package com.interview.prep.platform.backend_core.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.prep.platform.backend_core.onboarding.ResumeProfile;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The candidate's claimed skills, read from the CONFIRMED profile (what the user
 * corrected at onboarding), falling back to the parsed one.
 */
@Slf4j
public final class ProfileSkills {

    private ProfileSkills() {}

    /** Levels that mean "I would be asked the hardest questions about this". */
    private static final Set<String> DEEP = Set.of("expert", "advanced");

    public record Skill(String name, String level, Integer years) {
        public boolean isDeep() {
            return level != null && DEEP.contains(level.toLowerCase(Locale.ROOT));
        }
    }

    @SuppressWarnings("unchecked")
    public static List<Skill> from(ResumeProfile profile, ObjectMapper mapper) {
        if (profile == null) return List.of();
        String json = profile.getConfirmedJson() != null ? profile.getConfirmedJson() : profile.getParsedJson();
        if (json == null || json.isBlank()) return List.of();
        try {
            Map<String, Object> root = mapper.readValue(json, Map.class);
            if (root.get("result") instanceof Map<?, ?> inner) root = (Map<String, Object>) inner;
            if (!(root.get("skills") instanceof List<?> raw)) return List.of();

            List<Skill> skills = new ArrayList<>();
            for (Object o : raw) {
                if (!(o instanceof Map<?, ?> m)) continue;
                Object name = m.get("name");
                if (!(name instanceof String n) || n.isBlank()) continue;
                String level = m.get("level") instanceof String l ? l : null;
                Integer years = m.get("years") instanceof Number y ? y.intValue() : null;
                skills.add(new Skill(n, level, years));
            }
            return skills;
        } catch (Exception e) {
            log.warn("Could not read resume skills: {}", e.getMessage());
            return List.of();
        }
    }
}

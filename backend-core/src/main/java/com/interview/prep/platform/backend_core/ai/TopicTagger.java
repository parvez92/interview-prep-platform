package com.interview.prep.platform.backend_core.ai;

import com.interview.prep.platform.backend_core.ai.ProfileSkills.Skill;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Assigns {@code topic.tag} in code, not by the LLM (fix-brief §3).
 *
 * <p>The tag answers "what is this topic to YOU?": {@code exp} you claim depth here and will
 * be probed hardest, {@code refresh} you have touched it, {@code new} it is not on your résumé,
 * {@code dsa} the category is its own answer.
 */
@Component
public class TopicTagger {

    public static final String TAG_NEW = "new";
    public static final String TAG_REFRESH = "refresh";
    public static final String TAG_EXP = "exp";
    public static final String TAG_DSA = "dsa";

    private final SkillSurface surface;

    public TopicTagger(SkillSurface surface) {
        this.surface = surface;
    }

    /**
     * @param category plan-assigned category (dsa, language, framework, …)
     * @param title    topic title
     * @param scope    the coarse scope line, or the deepened topic's angle — may be null
     * @param skills   confirmed résumé skills
     */
    public String tag(String category, String title, String scope, List<Skill> skills) {
        if (category != null && "dsa".equalsIgnoreCase(category.strip())) return TAG_DSA;

        List<String> terms = ScopeTerms.extract(title, scope);
        if (terms.isEmpty() || skills == null || skills.isEmpty()) return TAG_NEW;

        String topicText = String.join(" | ", terms).toLowerCase(Locale.ROOT);

        // Skills whose surfaces overlap (Spring's "readiness probe" vs Kubernetes') would both
        // claim a topic. An outright mention of the skill beats one merely inferred from a term.
        List<Skill> named = skills.stream().filter(s -> namedIn(s.name(), topicText)).toList();
        List<Skill> matched = named.isEmpty()
                ? skills.stream().filter(s -> touchesSurface(s, topicText)).toList()
                : named;

        if (matched.isEmpty()) return TAG_NEW;
        return matched.stream().anyMatch(Skill::isDeep) ? TAG_EXP : TAG_REFRESH;
    }

    /** The topic covers part of this skill's established surface ("heap", "generational" → Java). */
    private boolean touchesSurface(Skill skill, String topicText) {
        SkillSurface.Entry entry = surface.forSkillName(skill.name());
        return entry != null && surface.touches(entry, topicText);
    }

    /** Full skill name, or its leading word when that word is distinctive enough. */
    private static boolean namedIn(String skillName, String topicText) {
        String lower = skillName.toLowerCase(Locale.ROOT).strip();
        if (SkillSurface.containsTerm(topicText, lower)) return true;
        String head = lower.split("\\s+")[0];
        return head.length() >= 4 && SkillSurface.containsTerm(topicText, head);
    }
}

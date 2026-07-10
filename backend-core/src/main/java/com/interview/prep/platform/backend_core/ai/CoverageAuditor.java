package com.interview.prep.platform.backend_core.ai;

import com.interview.prep.platform.backend_core.ai.Checklists.Checklist;
import com.interview.prep.platform.backend_core.ai.Checklists.Item;
import com.interview.prep.platform.backend_core.ai.ProfileSkills.Skill;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Audits a plan against the checklists for every skill the candidate claims at
 * expert/advanced level (fix-brief §2). Read-only: uncovered items are surfaced as
 * suggestions at the plan-review step, never inserted silently.
 */
@Component
public class CoverageAuditor {

    /** Below this, a claimed-expert area is not interview-ready at the chosen week count. */
    public static final int READY_PCT = 70;

    public record Gap(String id, String label) {}

    public record SkillCoverage(String skill, String category, int coveragePct,
                                List<String> coveredIds, List<Gap> gaps) {
        /** `is`-prefixed so Jackson serialises it alongside the record components. */
        public boolean isReady() {
            return coveragePct >= READY_PCT;
        }
    }

    private final Checklists checklists;
    private final SkillSurface surface;

    public CoverageAuditor(Checklists checklists, SkillSurface surface) {
        this.checklists = checklists;
        this.surface = surface;
    }

    /**
     * @param topicTexts one entry per plan topic: title + points + scope, concatenated
     * @param skills     confirmed résumé skills
     */
    public List<SkillCoverage> audit(List<String> topicTexts, List<Skill> skills) {
        List<String> haystacks = topicTexts.stream()
                .filter(t -> t != null && !t.isBlank())
                .map(t -> t.toLowerCase(Locale.ROOT))
                .toList();

        List<SkillCoverage> out = new ArrayList<>();
        for (Checklist cl : checklists.all()) {
            if (!claimedDeeply(cl, skills)) continue;

            List<String> covered = new ArrayList<>();
            List<Gap> gaps = new ArrayList<>();
            for (Item item : cl.items()) {
                if (isCovered(item, haystacks)) covered.add(item.id());
                else gaps.add(new Gap(item.id(), item.label()));
            }
            int total = cl.items().size();
            int pct = total == 0 ? 100 : (int) Math.round(100.0 * covered.size() / total);
            out.add(new SkillCoverage(cl.skill(), cl.category(), pct, covered, gaps));
        }
        return out;
    }

    /** A checklist applies only when the résumé claims its skill at expert/advanced. */
    private boolean claimedDeeply(Checklist cl, List<Skill> skills) {
        if (skills == null) return false;
        for (Skill s : skills) {
            if (!s.isDeep()) continue;
            SkillSurface.Entry entry = surface.forSkillName(s.name());
            if (entry != null && entry.skill().equalsIgnoreCase(cl.skill())) return true;
            if (s.name().toLowerCase(Locale.ROOT).contains(cl.skill().toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private static boolean isCovered(Item item, List<String> haystacks) {
        for (String match : item.match()) {
            for (String hay : haystacks) {
                if (SkillSurface.containsTerm(hay, match)) return true;
            }
        }
        return false;
    }
}

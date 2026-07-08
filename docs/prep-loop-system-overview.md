# prep-loop — System Overview & Content-Generation Pipeline

*Context document for review. This app was inspired by a hand-crafted interview-prep tracker
(single HTML file with 7 phases / 25 themed weeks / 158 topics, per-topic deep-dives and
checkbox progress tracking). We rebuilt the idea as a full app with AI-generated, per-user
content. This document describes what exists, how content is generated, and where the output
still falls short of the hand-crafted reference — we want suggestions on closing that gap.*

## What the app is

A single-user web app for technical interview prep with a feedback loop:

```
résumé upload → AI parse → confirmed profile → AI study plan → review/edit → commit
     → AI seeds every topic (deep-dive, resources, questions, exercises)
     → study screens → mock interviews & real-interview logging
     → low self-ratings flag topics back into review (the "loop")
```

Stack: React/TS frontend · Java Spring Boot core (system of record, auth, CRUD, feedback
rule) · Python FastAPI ai-service (all LLM calls, RAG over résumé chunks) · Postgres.
LLM-agnostic: Anthropic API, OpenAI, Gemini, Bedrock, local Ollama (currently testing with
qwen3.6:27b), plus a "desktop mode" that renders the exact prompt for copy-paste into
Claude Desktop and accepts the pasted JSON back.

## The content pipeline (three AI passes)

### Pass 1 — résumé parse
Extracts a structured profile: skills with levels (`Java (expert, 11 yrs)`), domains,
seniority, work experiences with technologies, and "gaps".

### Pass 2 — plan generation (one call)
Input: profile + targets (role, level, prep weeks, hours/week, interests, free-text
instructions). Prompt rules (priority order):

1. **Experience is the interview** — ≥60% of weeks deep-dive the candidate's own stack;
   "expert" invites the hardest questions, not a pass.
2. **Role-relevant gaps only** — parsed gaps earn weeks only if they matter for the target
   role, ≤25%.
3. Phase 1 is always a "Technical Skills Depth Review" (audit of claimed skills).
4. All pillars covered (DSA, system design, behavioral, language/framework), grounded in
   the candidate's own domains/projects.
5. Topic titles concrete and technology-specific, <90 chars.
6. Add-ons/interests ≤15%, last.

Output schema (compact JSON, one line — local models truncate pretty-printed output):

```json
{"phases":[{"name":"...","weeks":4,"goal":"<2-3 sentence syllabus intro: why now, what it unlocks>",
  "weeks_detail":[{"week_number":1,"title":"<week theme, e.g. 'JVM, memory, GC & profiling'>",
    "topics":[{"title":"...","category":"dsa|system_design|behavioral|language|framework|cloud|domain",
               "priority":"high|medium|low","resources_hint":"<60 chars>"}]}]}]}
```

Constraints: exact week count = user's prep weeks; 4-6 topics/week; final phase = mock loops.
Current real output for an 11-yr senior backend profile: **6 phases · 20 weeks · 97 topics**,
themed weeks, experience-anchored phases ("System Design From Your Own Platforms").

### Pass 3 — seeding (batched, 6 topics per call, after commit)
For every committed topic, generates:

- **overview** (the deep-dive panel): `concept` (1-2 crisp sentences),
  `points` (4-6 concrete memorizable facts — flags, numbers, decision rules, e.g.
  "ZGC: sub-ms pauses regardless of heap — pick when P99 < 10ms"),
  `angle` (the master interview question + what a strong answer includes).
- **resources** (4-8): quality hierarchy = official docs → creator/expert deep-dives →
  practice. Every URL is liveness-checked before storing (dead links dropped).
- **questions** (6-10): conceptual/trade-off/implementation/debugging/scenario mix.
- **exercises** (4-8): concrete tasks with success criteria, easy → hard.

Per-topic screens also have on-demand regeneration per tab (replaces previous AI content,
preserves user-added rows), and a richer 250-400-word markdown overview on demand.

### Also built
- Mock interviewer (one question at a time, session history, final 1-5 score + feedback;
  score ≤2 on a topic-linked session auto-flags the topic for review and lowers confidence).
- Real-interview logging with the same self-rating → review-flag rule (in code, not prompts).
- RAG over résumé chunks feeding topic overviews.
- Budget guard/caching/usage metering on every model call; Gmail job-alert ingestion.

## Data model per topic (what the UI can show)

`topic`: title, slug, category, angle, `concept` (text), `points` (jsonb array), status
(todo/done), confidence (0-100, user-rated + auto-lowered by the loop), plus child tables:
resources(label,url), questions(text), exercises(title,repoUrl,done), note (markdown).

## Where we fall short of the hand-crafted reference

The reference tracker (built interactively in Claude Desktop over many turns) has qualities
our single-shot generation doesn't reach:

1. **Deep-dive density.** Reference topics read like distilled lecture notes: every point
   is a specific fact with flags/numbers/code identifiers (`-XX:MaxGCPauseMillis`,
   "~10k calls triggers C2", "safepoint bias of JVisualVM vs async-profiler is a strong
   differentiator"). Our seeded points are decent but noticeably more generic — especially
   from the local 27B model.
2. **Daily/within-week tracking.** The reference is a checkbox tracker: topics are checked
   off and progress persists per phase with % complete. We track per-topic status/confidence
   but have no day-level scheduling ("Mon: JVM memory, Tue: GC…"), no within-week ordering,
   and no streak/velocity view. `hours_per_week` is collected but barely used.
3. **Narrative cohesion.** The reference's phase blurbs and week subtitles form a story
   ("the foundation that makes Spring's internals make sense", "your TIBCO background
   shines"). Ours got closer with themed weeks + syllabus-style goals, but the connective
   tissue between weeks (why THIS week follows THAT one) is weaker.
4. **Reference density per topic**: it embeds micro-examples and comparisons inline
   (G1 vs ZGC vs Shenandoah selection rules) rather than deferring to external links.

## Constraints any suggestion must respect

- **Single-shot budgets**: plan generation is ONE model call (~100 topics of compact JSON
  ≈ 8-10k output tokens — near the practical ceiling for a local 27B, ~12-15 min/call).
  Seeding is batched 6 topics/call for JSON reliability; ~17 batches for a 97-topic plan.
- Costs are metered; a serve-once cache sits in front of every call. Cheap/local models
  must produce parseable compact JSON (we have truncation repair + one retry).
- The feedback-loop rules live in Java code, not prompts, by design.
- Desktop mode must be able to render any generation as a copy-paste prompt.

## Questions we'd like input on

1. How would you structure prompts (or split passes) so per-topic deep-dives reach the
   reference's density — concrete flags/numbers/differentiators — from a mid-size local
   model? E.g., a dedicated "depth pass" per week? Few-shot exemplars per category?
2. What's a good model for **daily tracking** on top of phase→week→topic:
   scheduling topics to days using hours/week, or a lighter "today's queue" that pulls
   N topics by priority + review flags? How does the reference's checkbox-tracker feel
   translate into an app with a feedback loop?
3. Should the plan pass generate less (fewer, coarser topics) and the seed pass expand
   more (splitting a topic into subtopics with the deep-dive), to fit local-model budgets?
4. Any structural ideas for making week-to-week narrative ("this unlocks that") explicit
   in the schema rather than hoping the model writes it into goals?

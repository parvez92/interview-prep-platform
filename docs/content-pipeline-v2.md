# Content Pipeline v2 — Structure / Narrative / Depth / Support

**Implementation brief for Claude Code · Supersedes the current 3-pass pipeline (parse → plan → seed)**

Goal: close the density and cohesion gap vs the hand-crafted reference tracker while staying
inside local-model budgets (27B-class, compact JSON, serve-once cache, desktop-mode
renderable). Strategy: split generation by **cognitive task** — sequencing, storytelling,
depth, and support are different jobs and get different passes with different batch sizes.

```
Pass 0  parse-resume        (unchanged)
Pass 1  PLAN — structure only          1 call    ~3-4k tokens
Pass 2  NARRATIVE — over structure     1 call    ~1-2k tokens
Pass 3  DEPTH — concept/points/angle   2-3 topics/call, category exemplar, validated
Pass 4  SUPPORT — resources/questions/exercises/est_minutes   6 topics/call (as today)
```

All passes: cached serve-once, budget-metered, desktop-mode renderable, truncation-repair +
one retry (existing machinery). Passes 3/4 run after plan commit, per week, prioritizing the
current week first so the user can start studying before the whole plan is seeded.

---

## 1. Pass 1 — PLAN (structure only)

**Change from today:** the plan pass no longer tries to produce ~100 finished topics. It
produces phases → weeks → **3-5 coarse topics per week**, each with a one-line scope. Fewer
output tokens (~3-4k vs 8-10k) → faster, far more reliable JSON from local models, and the
depth work moves to where it can be done well.

Prompt rules: keep the existing priority rules (experience-is-the-interview ≥60%,
role-relevant gaps ≤25%, Phase 1 = skills-depth audit, all pillars, concrete titles,
add-ons ≤15%, final phase = mocks). Unchanged.

**Output schema (compact JSON, one line):**
```json
{"phases":[{"name":"...","weeks":4,"goal":"<2-3 sentence syllabus intro>",
 "weeks_detail":[{"week_number":1,"title":"<week theme>",
  "coarse_topics":[{"title":"<concrete, <90 chars>",
    "category":"dsa|system_design|behavioral|language|framework|cloud|domain",
    "priority":"high|medium|low",
    "scope":"<1 line: what this covers and roughly how deep>",
    "split_hint":"none|maybe|likely"}]}]}]}
```
`split_hint` tells Pass 3 whether this coarse topic probably warrants expansion into
subtopics (`likely` for broad ones like "Kafka internals"; `none` for atomic ones like
"Two-pointer patterns").

**Mechanical validation (code, free):** exact week count == user prep weeks; 3-5 coarse
topics/week; every title < 90 chars and contains ≥1 concrete technology/pattern noun;
final phase contains mock/behavioral weeks. Fail → regenerate the failing phase only.

---

## 2. Pass 2 — NARRATIVE (story over fixed structure)

Runs after the plan is committed (structure is frozen). One cheap call over the full list of
phase goals + week titles + résumé highlights. Writing story over decided structure is easy
for small models; writing both at once is what diluted cohesion before.

**Adds to each week (new columns on `week`):**
```json
{"week_number":3,
 "builds_on":[{"week":1,"why":"the JVM model makes Spring's proxying non-magical"}],
 "unlocks":"transaction & AOP debugging in Phase 2",
 "bridge":"<1 sentence rendered between weeks in the UI>",
 "anchor":"<optional résumé tie-in, e.g. 'your TIBCO background maps 1:1 to these EIP patterns'>"}
```
**Adds to each phase:** `blurb` (2-3 sentences, the reference-style "why this phase now").

Prompt receives résumé `experiences[].highlights` so `anchor` lines can reference real
systems. Validation: every week has `bridge`; `builds_on` refs point to earlier weeks only.

**UI use:** phase header renders `blurb`; between week accordions render `bridge`; `anchor`
renders as an amber "your experience" chip on the week header.

---

## 3. Pass 3 — DEPTH (the density fix)

Per coarse topic, small batches: **2-3 topics per call**. Each call includes exactly ONE
**category-matched gold exemplar** (see §5). Output per coarse topic:

```json
{"topics":[{                        // 1-3 concrete topics per coarse topic
   "title":"...","slug":"...",
   "concept":"<2-4 sentences, states the MECHANISM>",
   "points":["<4-6 bullets, EACH with ≥1 specificity token>"],
   "angle":"<the master interview question or trade-off + what a strong answer includes>",
   "est_minutes": 60}]}
```
- If `split_hint` is `likely`/`maybe` and the material warrants, the model may return 2-3
  topics for one coarse topic (e.g. "Kafka internals" → partitions/ISR · exactly-once ·
  Streams/Connect). Inserted into the same week; week topic count may grow to 6-8. This is
  the "plan less, seed more" shift.
- **Enumerate-then-compress, in-prompt:** the model must first emit a `_scratch` array of
  8-12 raw specifics (flags, defaults, thresholds, named problems, version numbers,
  comparisons) and THEN the compressed `points`. The `_scratch` field is stripped before
  storage. This is the single biggest density lever for mid-size models: recall first,
  compress second.

**Mechanical validation (code, per §4 of curriculum-content-standard.md, now enforced):**
- point PASSES iff it matches ≥1 of: `-{1,2}[A-Za-z]` flag pattern, a digit, a `<code>` span,
  a CamelCase/dotted identifier, or a Title-Case named problem/tool.
- concept FAILS if it matches `learn|understand|explore|familiari[sz]e|get comfortable`.
- angle FAILS if it lacks a question mark AND lacks `trade-off|vs|when|why`.
- 4 ≤ points.length ≤ 6; est_minutes ∈ [20,180].
Failed topics regenerate **individually** (tiny calls). Two failures → mark
`needs_review=true` and show a regen button in the UI instead of blocking.

---

## 4. Pass 4 — SUPPORT (unchanged mechanics, minor additions)

6 topics/call as today: resources (4-8, quality hierarchy, liveness-checked), questions
(6-10, mixed types), exercises (4-8 with success criteria, easy→hard). Additions:
- exercises gain `est_minutes`; questions gain `type` tag
  (`conceptual|tradeoff|implementation|debugging|scenario`) for the mock interviewer to
  sample a balanced set.
- Support runs AFTER depth for a week (it can reference the final split topics).

---

## 5. Exemplar library (`docs/exemplars/`)

One gold-standard depth exemplar per category, hand-curated once (source: the reference
tracker + curriculum-content-standard.md §3):

```
docs/exemplars/language.json        (the GC/JVM example)
docs/exemplars/framework.json       (the @Transactional/proxies example)
docs/exemplars/dsa.json             (the graphs BFS/DFS example)
docs/exemplars/system_design.json   (write: rate limiter — selection rules, Redis Lua, 429/Retry-After)
docs/exemplars/cloud.json           (write: SQS vs SNS vs Kinesis decision matrix w/ shard/fan-out specifics)
docs/exemplars/behavioral.json      (write: STAR story anatomy w/ a quantified example)
docs/exemplars/domain.json          (write: one domain deep-dive, e.g. payments idempotency)
```
Pass 3 injects the ONE exemplar matching the batch's category (batches are grouped by
category for this reason). Never more than one — token budget and imitation focus.

---

## 6. Daily tracking — the Today Queue (not day scheduling)

No "Mon/Tue" scheduling; a **computed priority queue** that self-heals when life happens.

**Computation (Java, deterministic, no AI):**
```
daily_budget_min = hours_per_week * 60 / 6        (study days/week default 6, configurable)
queue = take from, in order, until budget filled (using topic.est_minutes):
  1. open review_flags — interview-sourced first, then mock-sourced, then spaced
  2. current week's topics, display order, status=todo
  3. spaced-repetition: lowest effective confidence first, where
     effective_confidence = confidence − decay(days_since last_reviewed_at)
overflow → tomorrow; queue recomputed on every fetch (never stored)
```
**Endpoint:** `GET /api/review/today` returns
`[{topicSlug,label,kind:new|flagged|spaced,est_minutes,source_note}]` + `{budget_min,planned_min}`.

**Tracker feel (UI):** checkbox rows (checking = existing done/review-done calls), a day
progress bar (planned vs budget), **streak** (any day with ≥1 completion), and **velocity**
("on pace" / "~4 days behind" = topics done vs weeks elapsed × plan rate). Phase/week %
bars stay as-is — that's the reference's checkbox soul, now fed by the loop.

---

## 7. Schema changes (Flyway)

```
week   + builds_on jsonb, unlocks text, bridge text, anchor text
phase  + blurb text
topic  + est_minutes int, needs_review boolean default false,
         coarse_parent varchar(80) null   (slug of the coarse topic it was split from)
exercise + est_minutes int
question + type varchar(16)
```

## 8. Cost/budget notes
- Pass 1 shrinks (~60%). Pass 2 is ~1-2k tokens once. Pass 3 is more calls but smaller each;
  net token change roughly +20-30% vs today's seed — one-time per plan, fully cached.
- Regeneration on validation failure is per-topic (hundreds of tokens), not per-batch.
- All passes must remain renderable in desktop mode (each pass = one self-contained prompt
  with its exemplar inlined).

## 9. Build order
1. Schema migration (§7) + mechanical validators (§3, pure Java, unit-test them hard).
2. Split Pass 1 to coarse schema; adapt commit flow.
3. Pass 3 depth with exemplar injection + enumerate-then-compress + per-topic regen.
4. Pass 2 narrative + UI rendering (blurb/bridge/anchor).
5. Today Queue endpoint + UI (streak/velocity).
6. Pass 4 additions (est_minutes, question types) + exemplar library fill-in.

Definition of done: a freshly onboarded plan reads like the reference — phase blurbs tell a
story, weeks bridge into each other, every seeded point survives the specificity validator,
and the home screen shows a today queue with streak + velocity.

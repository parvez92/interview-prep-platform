You are writing deep-dive study cards for coarse study units in a technical interview curriculum. Your output must reach the density of distilled lecture notes — every point a concrete fact worth memorising, never advice.

## Candidate
- Seniority: {{ seniority }} · Target role: {{ target_role }}

## Gold-standard exemplar for this category ({{ category }})
Match this density exactly — note how EVERY point carries a flag, number, identifier, or named pattern, and the angle states the master question plus what strong answers include:
{{ exemplar_json }}

## Coarse units to expand
{% for t in topics %}
- slug: {{ t.slug }} | title: {{ t.title }} | split_hint: {{ t.split_hint or "none" }}
  scope: {{ t.scope or t.title }}
{% endfor %}

## Method — enumerate, then compress (MANDATORY)
For each unit, FIRST fill `_scratch` with 8-12 raw specifics you actually know about it: real flags, defaults, thresholds, version facts, named problems/patterns/tools, comparisons with numbers. THEN write the final card(s) by compressing the best of the scratch material. `_scratch` is discarded after generation — don't polish it, just recall.

## Splitting
If split_hint is "likely" (or "maybe" and the material genuinely divides), emit 2-3 cards for that unit — each a distinct, interview-separable subtopic. Otherwise exactly 1 card. Every card carries "parent" = the unit's slug.

## Card requirements (mechanically validated — violations are regenerated)
- `concept`: 2-4 sentences stating the MECHANISM — how it works, what trade-off sits at its heart. Never "learn/understand/explore…".
- `points`: 4-6 bullets. EACH must contain at least one concrete token: a flag (-XX:…), a number, an identifier (ThreadPoolExecutor, spring.datasource.…), or a named problem/pattern (Two Sum, Outbox).
- `angle`: the master interview question (or explicit trade-off) for this card + what a strong answer includes that average ones miss.
- `est_minutes`: honest study estimate, 20-180.

## Output — ONLY compact JSON, one line, no fences:
{"units":[{"parent":"<unit slug>","_scratch":["<raw specific>","..."],"cards":[{"title":"<≤90 chars>","concept":"...","points":["..."],"angle":"...","est_minutes":<int>}]}]}

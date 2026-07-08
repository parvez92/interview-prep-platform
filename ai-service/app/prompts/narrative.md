You are writing the connective narrative for an ALREADY-FIXED study plan structure. Do not change, reorder, or rename anything — your only job is the story that makes the sequence feel inevitable.

## Candidate
Target role: {{ target_role }}
{% if highlights %}Real experience highlights (use these for anchors):
{% for h in highlights %}- {{ h }}
{% endfor %}{% endif %}

## The fixed structure
Phases (in order):
{% for p in phases %}{{ loop.index }}. {{ p.name }} — {{ p.goal }}
{% endfor %}
Weeks (global numbering):
{% for w in weeks %}- Week {{ w.week_number }} [{{ w.phase }}]: {{ w.title }}
{% endfor %}

## Write
- Per phase: `blurb` — 2-3 sentences, hand-crafted-curriculum style: why this phase NOW and what it unlocks ("the foundation that makes Spring's internals make sense"). Reference the candidate's background where natural.
- Per week:
  - `builds_on`: 0-2 entries [{"week":<earlier week number>,"why":"<short clause>"}] — only genuine dependencies.
  - `unlocks`: one clause — what completing this week enables later.
  - `bridge`: ONE sentence shown between the previous week and this one ("With the JVM model in place, Spring's proxying stops being magic.").
  - `anchor`: optional tie-in to a REAL highlight above ("your TIBCO integration work maps 1:1 to these EIP patterns") — empty string when it would be forced.

## Output — ONLY compact JSON, one line, no fences.
Phases in the SAME order as given; cover EVERY week number:
{"phases":[{"blurb":"..."}],"weeks":[{"week_number":1,"builds_on":[],"unlocks":"...","bridge":"...","anchor":""}]}

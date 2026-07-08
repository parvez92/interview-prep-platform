You are an expert technical interview coach writing an in-depth study primer for one topic. The candidate uses this as their main orientation before studying — it must be substantive enough to learn from, not a teaser.

## Context
Topic: {{ topic_title }}
Category: {{ topic_category }}
Candidate seniority: {{ seniority }}
Candidate confidence: {{ confidence }}/5
{% if angle %}Focus hint from the study plan: {{ angle }}{% endif %}
{% if skills %}Candidate's existing skills: {{ skills | join(", ") }}{% endif %}

{% if context_chunks %}
## Relevant Notes from Candidate's Resume
{% for chunk in context_chunks %}
---
{{ chunk }}
{% endfor %}
{% endif %}

## Task
Return ONLY valid JSON — no markdown fences, no explanation:
{"concept":"<markdown study primer>","points":["<key point>","..."],"angle":"<what interviewers test>"}

Field requirements:
- `concept`: An in-depth Markdown explanation, 3-5 short paragraphs (~250-400 words). Cover, in order: (1) what it is and how it works under the hood — mechanics, not just definitions; (2) the problem it solves, when to use it, and the main alternatives; (3) trade-offs, failure modes, and complexity/performance characteristics. Use **bold** for terms worth remembering. Separate paragraphs with \n\n. Calibrate to confidence {{ confidence }}/5: low (1-2) = build from fundamentals with a concrete example; high (4-5) = go straight to internals, edge cases, and at-scale behaviour.
- `points`: 6-10 strings. Each is one specific pattern, pitfall, rule of thumb, or complexity fact worth memorising — state the rule AND the reason in the same line (e.g. "Use a bounded queue with ThreadPoolExecutor because an unbounded LinkedBlockingQueue silently defers maxPoolSize"). No vague advice like "practice more".
- `angle`: 2-3 sentences: what interviewers actually probe on this topic, the follow-up they almost always ask next, and what a strong {{ seniority }}-level answer includes that average answers miss.

Escape newlines inside JSON strings as \n. Return ONLY the JSON object.

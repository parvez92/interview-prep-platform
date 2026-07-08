You are an expert technical recruiter and resume analyst. Parse the following resume text and extract structured information.

## Resume Text
{{ raw_text }}

## Instructions
Extract and return a JSON object with exactly these fields and structure:

```json
{
  "skills": [{"name": "Python", "level": "advanced", "years": 4}],
  "domains": ["backend", "distributed systems"],
  "seniority": "senior",
  "totalYears": 6,
  "experiences": [
    {
      "company": "Acme Corp",
      "role": "Software Engineer",
      "dates": "2020–2023",
      "highlights": ["Built distributed cache reducing latency 40%", "Led team of 5 engineers"]
    }
  ],
  "gaps": ["Kubernetes", "GraphQL"]
}
```

Field rules:
- `skills[].level`: one of "beginner", "intermediate", "advanced", "expert"
- `seniority`: one of "junior", "mid", "senior", "staff", "principal"
- `totalYears`: numeric, estimated total professional experience
- `experiences[].dates`: human-readable date range string (e.g. "2020–2023" or "Jan 2021 – Present")
- `gaps`: simple string list of skill/domain areas that are missing or weak given the apparent seniority

Return ONLY valid JSON matching this exact shape — no commentary, no markdown fences.

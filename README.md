# prep-loop

A single-user, AI-assisted platform to prepare for and clear technical interviews.

Upload your résumé, get a tailored study plan, study with notes/resources/exercises, log
your real interviews — and watch the questions you fumble flow back into your plan as
targeted reviews. That study → interview → reflect **loop** is the point.

> Built for one user (remote-capable), and deliberately as a learning project spanning
> Spring, GenAI/RAG, agents, and AWS.

## Features
- **Résumé-driven onboarding** — parse your résumé, confirm the profile, generate a plan
  tailored to your experience, target role, and prep duration (incl. DSA at the right level,
  system design, and interest tracks like Agentic AI).
- **Study workspace** — per topic: a deep-dive guide, a Notion-style markdown notes editor
  (with images), saved resources, exercises (GitHub-linked), and an interview Q-bank.
- **Interview tracker + feedback loop** — a pipeline board and debrief log; weak answers
  auto-flag their topics back into your study plan.
- **Company prep packs** — from an interview's job description: the intersection of JD topics,
  your weak areas, and your résumé gaps.
- **AI mock interviewer**, **readiness score**, **spaced repetition**, **STAR story bank**.
- **Relevant jobs** — parsed from your Gmail job alerts and scored against your *actual*
  experience (RAG over your résumé), not keyword overlap.

## Architecture
Polyglot monorepo, two services + UI, local-first and remote-capable.

```
React UI ─HTTPS→ backend-core (Java/Spring) ─REST→ ai-service (Python/FastAPI)
                       │                               │
                       └──────── Postgres + pgvector ──┘
                       secrets in HashiCorp Vault
```

- **backend-core/** (Java 21, Spring Boot) — auth, CRUD, transactions, feedback-loop rule,
  Gmail ingestion, AI gateway + budget guard. UI-facing (`:8080`).
- **ai-service/** (Python, FastAPI) — résumé parsing, plan generation, RAG, agents (LangGraph),
  mock interviewer; also an MCP server for Claude Desktop. Internal (`:8000`).
- **frontend/** (React + TypeScript) — talks only to backend-core.
- **Postgres + pgvector**, **Vault** — via `docker-compose`.

The AI layer is **metered and cached**: only the AI service and the Java AI gateway call a
paid model; results cache in `ai_cache`, every call is logged to `usage_log`, and exceeding
the monthly budget drops to a cheaper model with a warning rather than blocking.

## Repo layout
```
prep-loop/
├── CLAUDE.md               context for Claude Code (read first)
├── docker-compose.yml      postgres + vault + core + ai
├── docs/                   specs (backend + frontend)
├── backend-core/           Java / Spring Boot
├── ai-service/             Python / FastAPI
└── frontend/               React / TypeScript
```

## Getting started (local)
```bash
# 1. infrastructure
docker compose up -d postgres vault     # Postgres(+pgvector) and Vault

# 2. backend-core (Java)
cd backend-core && ./gradlew bootRun     # http://localhost:8080

# 3. ai-service (Python)
cd ai-service && uvicorn app.main:app --reload --port 8000

# 4. frontend (React)
cd frontend && npm install && npm run dev
```
Secrets (LLM keys, Gmail token, DB creds) are read from Vault — see `docs/` for the seed
step. Nothing sensitive lives in config or code.

## Documentation
- `docs/backend-spec-v2.md` — architecture, data model, APIs, cost rules
- `docs/backend-detailed-design.md` — project structure + per-endpoint behavior
- `docs/frontend-spec-v1.md` — screens, view models, endpoint mapping

## Status
Early build. Slice order and definition-of-done are in `CLAUDE.md`.

## License
Personal project — not yet licensed for redistribution.

# CLAUDE.md — prep-loop

Project context for Claude Code. Read this first in every session. Detailed specs live in
`/docs`; treat them as the source of truth and read the relevant one before implementing.

## What this is
A single-user, remote-capable web app to prepare for and clear technical interviews. The
differentiator is the **feedback loop**: parse résumé → generate a tailored study plan →
study (notes, resources, exercises) → log real interviews → weak answers are flagged and
fed back into the plan. Built also as a learning project (backend + GenAI).

## Architecture (two services + UI, polyglot monorepo)
- **backend-core/** — Java 21 / Spring Boot 3.3. System of record: auth, all CRUD,
  transactions, the feedback-loop rule, Gmail ingestion, the AI gateway + budget guard.
- **ai-service/** — Python 3.11 / FastAPI. The brains: résumé parsing, plan generation,
  RAG (pgvector), agents (LangGraph), mock interviewer, RAGAS eval. Also hosts an MCP
  server exposing the same tools to Claude Desktop.
- **frontend/** — React 18 + TypeScript. Talks only to backend-core over REST.
- **Postgres 16 + pgvector** — relational tables (owned by Java) + vector tables + ai_cache
  (owned by Python). **HashiCorp Vault** (Docker) holds all secrets.

```
React UI ─HTTPS→ backend-core (Java) ─REST→ ai-service (Python)
                      │                          │
                      └──── Postgres + pgvector ─┘
                      both load secrets from Vault
ai-service agent tools call BACK into backend-core REST (one data path).
```

## Golden rules
1. **Free core vs metered AI.** Only `/api/ai/**` (Java) and `ai-service` call a paid model.
   Everything else is deterministic CRUD — never add LLM calls outside the AI layer.
2. **Cost control is mandatory.** Check `ai_cache` before any paid call (serve-once, free
   after). Budget over cap → drop to cheap tier + set `budgetWarning` (never hard-block).
   Every model call writes a `usage_log` row.
3. **The feedback loop lives in code, not the agent.** `FeedbackLoopService`: interview/mock
   self-rating ≤ 2 → create `review_flag` + lower `topic.confidence`. Agents only augment.
4. **Agent tools = HTTP calls back to Java REST.** Do not give the Python agents a second
   data-access path; they act through the same endpoints (plus local pgvector search).
5. **LLM-agnostic.** All model calls go through the `LLMProvider` interface (default
   Anthropic). Model ids + tier come from `user_settings`; keys from Vault. No hardcoded keys.
6. **Layering (Java):** Controller → Service → Repository → Entity. Controllers validate +
   delegate only. DTOs cross the boundary; entities never leave the service layer.
7. **Every table has `user_id`** (single user now, multi-user-safe later). Scope every query.
8. **No secret in code or config.** Vault only. No plaintext keys, ever.

## Tech & conventions
- Java: Spring Boot (Web, Data JPA, Security, Validation), Flyway owns DDL, JWT auth.
- Python: FastAPI, LangGraph (bounded ReAct: max 3–6 steps, 30s deadline, token cap,
  allow-listed tools, idempotent writes, `agent_run` trace).
- Tests: **Testcontainers** for integration (real Postgres, not H2). AI features tested
  against **fixture responses**, not the live LLM — mock `LLMProvider` in unit tests.
- Storage: `FileStore` interface — `LocalFileStore` (dev) → `S3FileStore` (remote). Note
  image bytes go to files, never into Postgres (only `file_ref` is stored).

## Build order (build → unit test → integration test → next)
1. Foundation: docker-compose (Postgres + Vault), JWT auth, `app_user`, `/api/auth/**`, `/api/me`.
2. Study core: phase/week/topic + note/resource/exercise/question CRUD, `/api/phases`,
   `/api/topics/**`, `/api/progress`, readiness score. ← wire UI study + topic screens here.
3. Interviews + loop: interview/question/review_flag, FeedbackLoopService, `/api/review/today`.
4. AI service skeleton + provider + cache + usage; Java `/api/ai/guide` gateway + BudgetGuard.
5. Onboarding: résumé parse → confirm → plan generate → commit; embed résumé chunks.
6. Agents (jobfit → debrief → coach → prep-pack → mock) + Gmail ingestion + MCP server.
7. STAR bank, jobs feed, usage meter — finish remaining surfaces.

Each slice must run end-to-end and have unit + Testcontainers integration tests before the next.

## Specs (read before implementing)
- `docs/backend-spec-v2.md` — architecture, full data model, API + cost rules.
- `docs/backend-detailed-design.md` — project structure + per-endpoint behavior (primary
  reference when writing endpoints).
- `docs/frontend-spec-v1.md` — screens, view models, which endpoint each screen calls.

## Layout
```
prep-loop/
├── docker-compose.yml      postgres + vault + core + ai
├── docs/                   the three specs above
├── backend-core/           Java / Spring Boot   (port 8080, UI-facing)
├── ai-service/             Python / FastAPI     (port 8000, internal only)
└── frontend/               React / TypeScript
```

## Definition of done (per slice)
Endpoint behaves per `backend-detailed-design.md` · unit tests for service logic · one
Testcontainers integration test for the happy path + the key edge case · no secret in
code · cost-bearing paths cached + metered.

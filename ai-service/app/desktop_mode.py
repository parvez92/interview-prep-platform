"""
Desktop-mode middleware.

When X-Provider: desktop, LLM-bound endpoints render the actual prompt
template and return it as `desktop_prompt` so the user can paste it into
any desktop LLM (Claude, ChatGPT, Gemini) and paste the result back.
"""

from __future__ import annotations

import json
import logging
from pathlib import Path

from fastapi import Request
from fastapi.responses import JSONResponse
from jinja2 import Environment, FileSystemLoader
from starlette.middleware.base import BaseHTTPMiddleware

log = logging.getLogger(__name__)

_PASSTHROUGH = frozenset({"/ai/embed-resume", "/ai/settings", "/health"})
_MCP_PREFIX = "/mcp"

_MCP_TOOLS = [
    "get_study_progress",
    "search_study_topics",
    "search_resume",
    "get_interview_history",
    "get_today_review",
    "get_plan_overview",
    "flag_topic_for_review",
    "schedule_topic_review",
]

_env = Environment(
    loader=FileSystemLoader(Path(__file__).parent / "prompts"),
    autoescape=False,
)


def _should_intercept(path: str) -> bool:
    if path in _PASSTHROUGH:
        return False
    if path.startswith(_MCP_PREFIX):
        return False
    return path.startswith("/ai/")


def _fmt(system: str, user: str) -> str:
    return (
        "=== SYSTEM ===\n"
        + system.strip()
        + "\n\n=== USER ===\n"
        + user.strip()
        + "\n\nReturn ONLY valid JSON — no explanation, no markdown fences."
    )


def _fmt_text(system: str, user: str) -> str:
    return "=== SYSTEM ===\n" + system.strip() + "\n\n=== USER ===\n" + user.strip()


async def _build_prompt(path: str, request: Request) -> str:
    try:
        raw = await request.body()
        body: dict = json.loads(raw) if raw else {}
    except Exception:
        body = {}

    if path == "/ai/parse-resume":
        raw_text = body.get("raw_text", "[resume text unavailable]")
        system = _env.get_template("parse.md").render(raw_text=raw_text)
        return _fmt(system, "Parse the resume and return JSON.")

    if path == "/ai/generate-plan":
        # Java may send profileJson (string) or profile (dict)
        profile = body.get("profile") or {}
        if not profile and body.get("profileJson"):
            try:
                profile = json.loads(body["profileJson"])
            except Exception:
                profile = {}
        targets = body.get("targets") or []
        additional_context = body.get("additionalContext") or ""
        system = _env.get_template("plan.md").render(
            profile=profile, targets=targets, additional_context=additional_context
        )
        user_msg = "Generate the study plan JSON."
        if additional_context:
            user_msg += f"\n\nAdditional instructions from the candidate: {additional_context}"
        return _fmt(system, user_msg)

    if path == "/ai/guide":
        tab = body.get("tab", "overview")
        topic_title = body.get("topic_title", "this topic")
        template_map = {
            "overview":  "guide_overview.md",
            "resources": "guide_resources.md",
            "exercises": "guide_exercises.md",
            "questions": "guide_questions.md",
        }
        template_name = template_map.get(tab, "guide_overview.md")
        system = _env.get_template(template_name).render(
            topic_title=topic_title,
            topic_category=body.get("topic_category", ""),
            seniority=body.get("seniority", "mid"),
            confidence=body.get("confidence", 3),
            skills=body.get("skills", []),
            context_chunks=[],
        )
        return _fmt(system, f"Generate {tab} content for: {topic_title}")

    if path == "/ai/seed-plan":
        topics = body.get("topics", [])
        system = _env.get_template("seed.md").render(topics=topics)
        return _fmt(system, "Generate study materials for all topics.")

    if path == "/ai/narrative":
        system = _env.get_template("narrative.md").render(
            phases=body.get("phases", []),
            weeks=body.get("weeks", []),
            highlights=(body.get("highlights") or [])[:12],
            target_role=body.get("target_role", "Software Engineer"),
        )
        return _fmt(system, "Write the narrative JSON for the structure above.")

    if path == "/ai/deep-dive":
        topics = body.get("topics", [])
        category = (topics[0].get("category") if topics else "") or "domain"
        exemplar_file = Path(__file__).parent / "exemplars" / f"{category.lower()}.json"
        if not exemplar_file.exists():
            exemplar_file = Path(__file__).parent / "exemplars" / "domain.json"
        exemplar = json.loads(exemplar_file.read_text())
        system = _env.get_template("depth.md").render(
            topics=topics,
            seniority=body.get("seniority", "mid"),
            target_role=body.get("target_role", "Software Engineer"),
            category=category,
            exemplar_json=json.dumps(exemplar["topic"], separators=(",", ":")),
        )
        return _fmt(system, "Write the deep-dive cards for the units above.")

    if path.startswith("/ai/mock"):
        history = body.get("history") or []
        user_msg = history[-1].get("content", "Start the mock interview.") if history else "Start the mock interview."
        system = _env.get_template("mock.md").render(
            target_role=body.get("target_role", "Software Engineer"),
            topic=body.get("topic", ""),
            difficulty=body.get("difficulty", "medium"),
            interview_type=body.get("interview_type", "technical"),
            seniority=body.get("seniority", "mid"),
            strengths=body.get("strengths", []),
        )
        return _fmt_text(system, user_msg)

    if path == "/ai/agents/coach":
        system = _env.get_template("coach.md").render(
            review_flags=body.get("review_flags", []),
            mock_history=body.get("mock_history", []),
            seniority=body.get("seniority", "mid"),
            strong_areas=body.get("strong_areas", []),
            target_role=body.get("target_role", "Software Engineer"),
        )
        return _fmt_text(system, "Analyze my weak areas and give a personalized coaching plan.")

    if path == "/ai/agents/debrief":
        system = _env.get_template("debrief.md").render(
            interview=body.get("interview", {}),
            qa_pairs=body.get("qa_pairs", []),
            progress_summary={},
        )
        return _fmt_text(system, "Debrief this interview and tell me how to improve.")

    if path == "/ai/analyze":
        system = _env.get_template("analyze.md").render(**body) if body else "Analyze the candidate's answer."
        return _fmt_text(system, body.get("answer", ""))

    # Fallback for other /ai/** endpoints
    return (
        f"Endpoint: {path}\n\n"
        "Ask your AI assistant to help with this task, providing your study context as needed."
    )


class DesktopModeMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next):
        provider = request.headers.get("x-provider", "").lower()
        if provider != "desktop" or not _should_intercept(request.url.path):
            return await call_next(request)

        log.debug("desktop-mode intercept: %s %s", request.method, request.url.path)
        prompt = await _build_prompt(request.url.path, request)

        return JSONResponse(
            status_code=200,
            content={
                "mode": "desktop",
                "desktop_prompt": prompt,
                "result": None,
                "meta": {"model": "desktop", "cached": False, "tokens": 0, "cost": 0.0},
            },
        )

"""Factory for `ChatAnthropic` with the rules for `claude-opus-5-5` (architecture.md, section 16).

- No `temperature`, `top_p` or `top_k`: the model rejects them with 400.
- Thinking cannot be disabled; depth is set explicitly via `effort` (`output_config.effort`).
- Refusal fallback: beta header plus `fallbacks: "default"`. `ChatAnthropic` merges `model_kwargs` into the
  request payload and, because `betas` is set, sends it through `client.beta.messages.create`, whose
  signature (anthropic 2.10) has a named `fallbacks` parameter. No `extra_body` needed.
"""

from __future__ import annotations

from dataclasses import dataclass

from langchain_anthropic import ChatAnthropic

from app.config import Effort, Settings

FALLBACK_BETA = "server-side-fallback-2026-07-01"
FALLBACKS_DEFAULT = "default"
ANTHROPIC_MAX_RETRIES = 2
# Explicit per-request timeout (the SDK default of 10 min); ChatAnthropic would otherwise pass None = no timeout.
ANTHROPIC_REQUEST_TIMEOUT_SECONDS = 600.0


@dataclass(frozen=True)
class ModelPurpose:
    effort: Effort
    max_tokens: int


def chat_purpose(settings: Settings) -> ModelPurpose:
    return ModelPurpose(effort=settings.anthropic_effort, max_tokens=16_000)


def extraction_purpose(settings: Settings) -> ModelPurpose:
    # Non-streaming call: stays below the SDK limit for requests without streaming (~21k tokens).
    return ModelPurpose(effort=settings.anthropic_extraction_effort, max_tokens=20_000)


def rationale_purpose(settings: Settings) -> ModelPurpose:
    return ModelPurpose(effort=settings.anthropic_effort, max_tokens=8_000)


def build_chat_model(settings: Settings, purpose: ModelPurpose) -> ChatAnthropic:
    fallback_options: dict[str, object] = (
        {"betas": [FALLBACK_BETA], "model_kwargs": {"fallbacks": FALLBACKS_DEFAULT}}
        if settings.fallbacks_enabled
        else {}
    )
    return ChatAnthropic(
        model=settings.anthropic_model,
        api_key=settings.anthropic_api_key,
        effort=purpose.effort,
        max_tokens=purpose.max_tokens,
        max_retries=ANTHROPIC_MAX_RETRIES,
        timeout=ANTHROPIC_REQUEST_TIMEOUT_SECONDS,
        stream_usage=True,
        **fallback_options,
    )

from __future__ import annotations

import pytest
from pydantic import ValidationError

from app.config import FallbackMode, Settings
from tests.support.harness import INTERNAL_KEY, make_settings


def test_defaults_follow_architecture_section_11() -> None:
    settings = make_settings()

    assert settings.anthropic_model == "claude-opus-5-5"
    assert settings.anthropic_effort == "medium"
    assert settings.anthropic_extraction_effort == "high"
    assert settings.anthropic_fallbacks is FallbackMode.DEFAULT
    assert settings.fallbacks_enabled
    assert settings.voyage_model == "voyage-4"
    assert settings.extraction_full_doc_max_pages == 40
    assert settings.rag_top_k == 6
    assert settings.chat_max_model_calls == 8
    assert settings.chat_max_proposals == 5
    assert settings.chat_turn_timeout_seconds == 120


def test_reads_environment(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("ANTHROPIC_API_KEY", "env-anthropic")
    monkeypatch.setenv("VOYAGE_API_KEY", "env-voyage")
    monkeypatch.setenv("INTERNAL_API_KEY", INTERNAL_KEY)
    monkeypatch.setenv("ANTHROPIC_FALLBACKS", "off")
    monkeypatch.setenv("ANTHROPIC_EFFORT", "low")
    monkeypatch.setenv("LOG_LEVEL", "debug")

    settings = Settings(_env_file=None)  # type: ignore[call-arg]

    assert settings.internal_api_key.get_secret_value() == INTERNAL_KEY
    assert not settings.fallbacks_enabled
    assert settings.anthropic_effort == "low"
    assert settings.log_level == "DEBUG"


def test_secrets_are_required() -> None:
    with pytest.raises(ValidationError) as error:
        Settings(_env_file=None)  # type: ignore[call-arg]

    missing = {tuple(item["loc"]) for item in error.value.errors()}
    assert ("ANTHROPIC_API_KEY",) in missing
    assert ("VOYAGE_API_KEY",) in missing


def test_internal_key_needs_32_characters() -> None:
    with pytest.raises(ValidationError, match="mindestens 32 Zeichen"):
        make_settings(AGENT_INTERNAL_API_KEY="too-short")


def test_unknown_effort_is_rejected() -> None:
    with pytest.raises(ValidationError):
        make_settings(ANTHROPIC_EFFORT="ultra")


def test_secrets_are_not_printed() -> None:
    settings = make_settings()

    assert "test-anthropic-key" not in repr(settings)
    assert INTERNAL_KEY not in str(settings)

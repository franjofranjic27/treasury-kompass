"""Service configuration from environment variables (docs/architecture.md, section 11.2)."""

from __future__ import annotations

from enum import StrEnum
from typing import Literal

from pydantic import AliasChoices, Field, SecretStr, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

Effort = Literal["low", "medium", "high", "xhigh", "max"]

MIN_INTERNAL_API_KEY_LENGTH = 32


class FallbackMode(StrEnum):
    """Server-side refusal fallback (`fallbacks` request field)."""

    DEFAULT = "default"
    OFF = "off"


class Settings(BaseSettings):
    """All settings of the agent-service. Secrets are required: the service fails fast without them."""

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    anthropic_api_key: SecretStr = Field(validation_alias="ANTHROPIC_API_KEY")
    anthropic_model: str = Field(default="claude-opus-5-5", validation_alias="ANTHROPIC_MODEL")
    anthropic_effort: Effort = Field(default="medium", validation_alias="ANTHROPIC_EFFORT")
    anthropic_extraction_effort: Effort = Field(default="high", validation_alias="ANTHROPIC_EXTRACTION_EFFORT")
    anthropic_fallbacks: FallbackMode = Field(default=FallbackMode.DEFAULT, validation_alias="ANTHROPIC_FALLBACKS")

    voyage_api_key: SecretStr = Field(validation_alias="VOYAGE_API_KEY")
    voyage_model: str = Field(default="voyage-4", validation_alias="VOYAGE_MODEL")

    database_url: str = Field(
        default="postgresql+psycopg://agent_app@localhost:5432/treasury_kompass",
        validation_alias="AGENT_DATABASE_URL",
    )
    treasury_base_url: str = Field(default="http://localhost:8080", validation_alias="AGENT_TREASURY_BASE_URL")
    internal_api_key: SecretStr = Field(validation_alias=AliasChoices("AGENT_INTERNAL_API_KEY", "INTERNAL_API_KEY"))

    extraction_full_doc_max_pages: int = Field(default=40, ge=1, validation_alias="AGENT_EXTRACTION_FULL_DOC_MAX_PAGES")
    rag_top_k: int = Field(default=6, ge=1, le=50, validation_alias="AGENT_RAG_TOP_K")
    chat_max_model_calls: int = Field(default=8, ge=1, validation_alias="AGENT_CHAT_MAX_MODEL_CALLS")
    chat_max_proposals: int = Field(default=5, ge=0, validation_alias="AGENT_CHAT_MAX_PROPOSALS")
    chat_turn_timeout_seconds: float = Field(default=120.0, gt=0, validation_alias="AGENT_CHAT_TURN_TIMEOUT_SECONDS")

    log_level: str = Field(default="INFO", validation_alias="LOG_LEVEL")

    @field_validator("internal_api_key")
    @classmethod
    def _internal_key_is_long_enough(cls, value: SecretStr) -> SecretStr:
        if len(value.get_secret_value()) < MIN_INTERNAL_API_KEY_LENGTH:
            msg = f"AGENT_INTERNAL_API_KEY muss mindestens {MIN_INTERNAL_API_KEY_LENGTH} Zeichen lang sein"
            raise ValueError(msg)
        return value

    @field_validator("log_level")
    @classmethod
    def _normalize_log_level(cls, value: str) -> str:
        return value.upper()

    @property
    def fallbacks_enabled(self) -> bool:
        return self.anthropic_fallbacks is FallbackMode.DEFAULT

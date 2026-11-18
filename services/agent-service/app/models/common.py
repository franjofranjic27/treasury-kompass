"""Base model for JSON DTOs: camelCase on the wire, snake_case in Python."""

from __future__ import annotations

from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel


class CamelModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, frozen=True)

    def to_json_dict(self) -> dict[str, object]:
        return self.model_dump(mode="json", by_alias=True)


class StrictCamelModel(CamelModel):
    """Request DTO that rejects unknown fields (`additionalProperties: false`)."""

    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, frozen=True, extra="forbid")

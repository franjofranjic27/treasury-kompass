"""Conversation list, detail and deletion for the chat panel."""

from __future__ import annotations

from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Depends, Response, status

from app.api.dependencies import get_services
from app.models.conversations import Conversation, ConversationDetail
from app.problems import ProblemCode, ProblemError
from app.services import Services

router = APIRouter(prefix="/agent", tags=["Conversations"])


@router.get(
    "/clients/{client_id}/conversations",
    response_model=list[Conversation],
    response_model_by_alias=True,
    operation_id="listConversations",
)
async def list_conversations(
    client_id: UUID, services: Annotated[Services, Depends(get_services)]
) -> list[Conversation]:
    return await services.conversations.list_for_client(client_id)


@router.get(
    "/conversations/{conversation_id}",
    response_model=ConversationDetail,
    response_model_by_alias=True,
    operation_id="getConversation",
)
async def get_conversation(
    conversation_id: UUID, services: Annotated[Services, Depends(get_services)]
) -> ConversationDetail:
    detail = await services.conversations.detail(conversation_id)
    if detail is None:
        raise ProblemError(ProblemCode.NOT_FOUND, "Gespräch nicht gefunden.")
    return detail


@router.delete(
    "/conversations/{conversation_id}",
    status_code=status.HTTP_204_NO_CONTENT,
    operation_id="deleteConversation",
)
async def delete_conversation(conversation_id: UUID, services: Annotated[Services, Depends(get_services)]) -> Response:
    if not await services.conversations.delete(conversation_id):
        raise ProblemError(ProblemCode.NOT_FOUND, "Gespräch nicht gefunden.")
    return Response(status_code=status.HTTP_204_NO_CONTENT)

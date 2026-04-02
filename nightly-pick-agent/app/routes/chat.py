from fastapi import APIRouter, Body, Depends

from app.models import ChatReplyRequest, ChatReplyResponse
from app.dependencies import get_agent_service
from app.services.agent_service import AgentService

router = APIRouter()


@router.post("/reply", response_model=ChatReplyResponse, response_model_by_alias=False)
async def reply(
    request: ChatReplyRequest = Body(...),
    agent_service: AgentService = Depends(get_agent_service),
) -> ChatReplyResponse:
    return await agent_service.reply(request)

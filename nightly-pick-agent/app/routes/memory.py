from fastapi import APIRouter, Body, Depends

from app.dependencies import get_agent_service
from app.models import ExtractMemoryRequest, ExtractMemoryResponse, MemoryItem
from app.services.agent_service import AgentService

router = APIRouter()


@router.post("/extract", response_model=ExtractMemoryResponse, response_model_by_alias=False)
async def extract_memory(
    request: ExtractMemoryRequest = Body(...),
    agent_service: AgentService = Depends(get_agent_service),
) -> ExtractMemoryResponse:
    return await agent_service.extract_memory(request)

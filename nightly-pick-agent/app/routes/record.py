from fastapi import APIRouter, Body, Depends

from app.dependencies import get_agent_service
from app.models import GenerateRecordRequest, GenerateRecordResponse, PlanReflectionRequest, PlanReflectionResponse, WriteReflectionRequest
from app.services.agent_service import AgentService

router = APIRouter()


@router.post("/generate", response_model=GenerateRecordResponse, response_model_by_alias=False)
async def generate_record(
    request: GenerateRecordRequest = Body(...),
    agent_service: AgentService = Depends(get_agent_service),
) -> GenerateRecordResponse:
    return await agent_service.generate_record(request)


@router.post("/plan", response_model=PlanReflectionResponse, response_model_by_alias=False)
async def plan_reflection(
    request: PlanReflectionRequest = Body(...),
    agent_service: AgentService = Depends(get_agent_service),
) -> PlanReflectionResponse:
    return await agent_service.plan_reflection(request)


@router.post("/write", response_model=GenerateRecordResponse, response_model_by_alias=False)
async def write_reflection(
    request: WriteReflectionRequest = Body(...),
    agent_service: AgentService = Depends(get_agent_service),
) -> GenerateRecordResponse:
    return await agent_service.write_reflection(request)

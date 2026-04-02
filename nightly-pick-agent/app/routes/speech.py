from fastapi import APIRouter, Body, Depends

from app.dependencies import get_agent_service
from app.models import (
    SynthesizeSpeechRequest,
    SynthesizeSpeechResponse,
    TranscribeAudioRequest,
    TranscribeAudioResponse,
)
from app.services.agent_service import AgentService

router = APIRouter()


@router.post("/transcribe", response_model=TranscribeAudioResponse, response_model_by_alias=False)
async def transcribe_audio(
    request: TranscribeAudioRequest = Body(...),
    agent_service: AgentService = Depends(get_agent_service),
) -> TranscribeAudioResponse:
    return await agent_service.transcribe(request)


@router.post("/synthesize", response_model=SynthesizeSpeechResponse, response_model_by_alias=False)
async def synthesize_speech(
    request: SynthesizeSpeechRequest = Body(...),
    agent_service: AgentService = Depends(get_agent_service),
) -> SynthesizeSpeechResponse:
    return await agent_service.synthesize(request)

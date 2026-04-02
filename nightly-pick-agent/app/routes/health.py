from fastapi import APIRouter

from app.system import readiness_snapshot
from app.models import HealthResponse

router = APIRouter()


@router.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    snapshot = readiness_snapshot()
    return HealthResponse(
        service="nightly-pick-agent",
        status="UP",
        text_provider=snapshot["text_provider"],
        speech_provider=f'{snapshot["speech_transcribe_provider"]}+{snapshot["speech_synthesize_provider"]}',
    )

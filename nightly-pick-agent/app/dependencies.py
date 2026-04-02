from functools import lru_cache

from app.services.agent_service import AgentService
from app.services.container import get_speech_synthesize_provider, get_speech_transcribe_provider, get_text_provider


@lru_cache
def get_agent_service() -> AgentService:
    return AgentService(
        text_provider=get_text_provider(),
        speech_transcribe_provider=get_speech_transcribe_provider(),
        speech_synthesize_provider=get_speech_synthesize_provider(),
    )

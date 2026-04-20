from app.config import get_settings
from app.services.container import (
    get_provider_registry,
    get_speech_synthesize_provider,
    get_speech_transcribe_provider,
    get_text_provider,
)


def warmup_dependencies() -> None:
    get_provider_registry()
    get_text_provider()
    get_speech_transcribe_provider()
    get_speech_synthesize_provider()


def readiness_snapshot() -> dict[str, str]:
    settings = get_settings()
    return {
        **get_provider_registry().describe(),
        "text_provider_config": settings.text_provider,
        "speech_transcribe_provider_config": settings.speech_transcribe_provider,
        "speech_synthesize_provider_config": settings.speech_synthesize_provider,
        "env": settings.app_env,
    }

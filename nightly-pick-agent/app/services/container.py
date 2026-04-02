from functools import lru_cache

from app.config import get_settings
from app.providers.base import SpeechProvider, TextProvider
from app.providers.minimax import MiniMaxSpeechProvider, MiniMaxTextProvider
from app.providers.mock import MockSpeechProvider, MockTextProvider


@lru_cache
def get_text_provider() -> TextProvider:
    settings = get_settings()
    if settings.text_provider == "minimax":
        return MiniMaxTextProvider(settings)
    return MockTextProvider()


@lru_cache
def get_speech_provider() -> SpeechProvider:
    settings = get_settings()
    if settings.speech_provider == "minimax":
        return MiniMaxSpeechProvider(settings)
    return MockSpeechProvider()

from functools import lru_cache

from app.config import get_settings
from app.providers.base import SpeechSynthesizeProvider, SpeechTranscribeProvider, TextProvider
from app.providers.minimax import MiniMaxSpeechProvider, MiniMaxTextProvider
from app.providers.mock import MockSpeechProvider, MockTextProvider
from app.providers.tencent_asr import TencentASRProvider


@lru_cache
def get_text_provider() -> TextProvider:
    settings = get_settings()
    if settings.text_provider == "minimax":
        return MiniMaxTextProvider(settings)
    return MockTextProvider()


@lru_cache
def get_speech_transcribe_provider() -> SpeechTranscribeProvider:
    settings = get_settings()
    if settings.speech_transcribe_provider == "minimax":
        return MiniMaxSpeechProvider(settings)
    if settings.speech_transcribe_provider == "tencent":
        return TencentASRProvider(settings)
    return MockSpeechProvider()


@lru_cache
def get_speech_synthesize_provider() -> SpeechSynthesizeProvider:
    settings = get_settings()
    if settings.speech_synthesize_provider == "minimax":
        return MiniMaxSpeechProvider(settings)
    return MockSpeechProvider()

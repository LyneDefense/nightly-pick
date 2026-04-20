from functools import lru_cache

from app.providers.base import SpeechSynthesizeProvider, SpeechTranscribeProvider, TextProvider
from app.providers.registry import ProviderRegistry


@lru_cache
def get_provider_registry() -> ProviderRegistry:
    from app.config import get_settings

    return ProviderRegistry.build(get_settings())


@lru_cache
def get_text_provider() -> TextProvider:
    return get_provider_registry().text_provider


@lru_cache
def get_speech_transcribe_provider() -> SpeechTranscribeProvider:
    return get_provider_registry().speech_transcribe_provider


@lru_cache
def get_speech_synthesize_provider() -> SpeechSynthesizeProvider:
    return get_provider_registry().speech_synthesize_provider

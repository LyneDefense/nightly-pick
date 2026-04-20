from __future__ import annotations

from dataclasses import dataclass
from typing import Callable, TypeVar

from app.config import Settings
from app.providers.base import SpeechSynthesizeProvider, SpeechTranscribeProvider, TextProvider
from app.providers.deepseek import DeepSeekTextProvider
from app.providers.minimax import MiniMaxSpeechProvider, MiniMaxTextProvider
from app.providers.mock import MockSpeechProvider, MockTextProvider
from app.providers.tencent_asr import TencentASRProvider

TextProviderFactory = Callable[[Settings], TextProvider]
SpeechTranscribeProviderFactory = Callable[[Settings], SpeechTranscribeProvider]
SpeechSynthesizeProviderFactory = Callable[[Settings], SpeechSynthesizeProvider]
ProviderT = TypeVar("ProviderT")


TEXT_PROVIDER_FACTORIES: dict[str, TextProviderFactory] = {
    "mock": lambda _settings: MockTextProvider(),
    "minimax": lambda settings: MiniMaxTextProvider(settings),
    "deepseek": lambda settings: DeepSeekTextProvider(settings),
}

SPEECH_TRANSCRIBE_PROVIDER_FACTORIES: dict[str, SpeechTranscribeProviderFactory] = {
    "mock": lambda _settings: MockSpeechProvider(),
    "minimax": lambda settings: MiniMaxSpeechProvider(settings),
    "tencent": lambda settings: TencentASRProvider(settings),
}

SPEECH_SYNTHESIZE_PROVIDER_FACTORIES: dict[str, SpeechSynthesizeProviderFactory] = {
    "mock": lambda _settings: MockSpeechProvider(),
    "minimax": lambda settings: MiniMaxSpeechProvider(settings),
}


@dataclass
class ProviderRegistry:
    settings: Settings
    text_provider: TextProvider
    speech_transcribe_provider: SpeechTranscribeProvider
    speech_synthesize_provider: SpeechSynthesizeProvider

    @classmethod
    def build(cls, settings: Settings) -> "ProviderRegistry":
        return cls(
            settings=settings,
            text_provider=build_text_provider(settings),
            speech_transcribe_provider=build_speech_transcribe_provider(settings),
            speech_synthesize_provider=build_speech_synthesize_provider(settings),
        )

    def describe(self) -> dict[str, str]:
        return {
            "text_provider": self.text_provider.__class__.__name__,
            "speech_transcribe_provider": self.speech_transcribe_provider.__class__.__name__,
            "speech_synthesize_provider": self.speech_synthesize_provider.__class__.__name__,
        }


def build_text_provider(settings: Settings) -> TextProvider:
    return _build_provider(
        "text_provider",
        settings.text_provider,
        TEXT_PROVIDER_FACTORIES,
        settings,
    )


def build_speech_transcribe_provider(settings: Settings) -> SpeechTranscribeProvider:
    return _build_provider(
        "speech_transcribe_provider",
        settings.speech_transcribe_provider,
        SPEECH_TRANSCRIBE_PROVIDER_FACTORIES,
        settings,
    )


def build_speech_synthesize_provider(settings: Settings) -> SpeechSynthesizeProvider:
    return _build_provider(
        "speech_synthesize_provider",
        settings.speech_synthesize_provider,
        SPEECH_SYNTHESIZE_PROVIDER_FACTORIES,
        settings,
    )


def _build_provider(
    provider_kind: str,
    provider_name: str,
    factories: dict[str, Callable[[Settings], ProviderT]],
    settings: Settings,
) -> ProviderT:
    try:
        factory = factories[provider_name]
    except KeyError as exc:
        supported = ", ".join(sorted(factories))
        raise ValueError(
            f"Unsupported {provider_kind}: {provider_name}. Supported values: {supported}"
        ) from exc
    return factory(settings)

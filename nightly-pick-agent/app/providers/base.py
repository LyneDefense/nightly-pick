from abc import ABC, abstractmethod

from app.models import (
    ChatReplyRequest,
    ChatReplyResponse,
    ExtractMemoryRequest,
    ExtractMemoryResponse,
    GenerateRecordRequest,
    GenerateRecordResponse,
    GenerateShareCardRequest,
    GenerateShareCardResponse,
    PlanReflectionRequest,
    PlanReflectionResponse,
    SynthesizeSpeechRequest,
    SynthesizeSpeechResponse,
    TranscribeAudioRequest,
    TranscribeAudioResponse,
    WriteReflectionRequest,
)


class TextProvider(ABC):
    @abstractmethod
    async def chat_reply(self, request: ChatReplyRequest) -> ChatReplyResponse:
        raise NotImplementedError

    @abstractmethod
    async def generate_record(self, request: GenerateRecordRequest) -> GenerateRecordResponse:
        raise NotImplementedError

    @abstractmethod
    async def plan_reflection(self, request: PlanReflectionRequest) -> PlanReflectionResponse:
        raise NotImplementedError

    @abstractmethod
    async def write_reflection(self, request: WriteReflectionRequest) -> GenerateRecordResponse:
        raise NotImplementedError

    @abstractmethod
    async def extract_memory(self, request: ExtractMemoryRequest) -> ExtractMemoryResponse:
        raise NotImplementedError

    @abstractmethod
    async def generate_share_card(self, request: GenerateShareCardRequest) -> GenerateShareCardResponse:
        raise NotImplementedError


class SpeechTranscribeProvider(ABC):
    @abstractmethod
    async def transcribe(self, request: TranscribeAudioRequest) -> TranscribeAudioResponse:
        raise NotImplementedError


class SpeechSynthesizeProvider(ABC):
    @abstractmethod
    async def synthesize(self, request: SynthesizeSpeechRequest) -> SynthesizeSpeechResponse:
        raise NotImplementedError

from app.models import (
    ChatReplyRequest,
    ChatReplyResponse,
    ExtractMemoryRequest,
    ExtractMemoryResponse,
    GenerateRecordRequest,
    GenerateRecordResponse,
    PlanReflectionRequest,
    PlanReflectionResponse,
    SynthesizeSpeechRequest,
    SynthesizeSpeechResponse,
    TranscribeAudioRequest,
    TranscribeAudioResponse,
    WriteReflectionRequest,
)
from app.providers.base import SpeechProvider, TextProvider


class AgentService:
    def __init__(self, text_provider: TextProvider, speech_provider: SpeechProvider):
        self.text_provider = text_provider
        self.speech_provider = speech_provider

    async def reply(self, request: ChatReplyRequest) -> ChatReplyResponse:
        return await self.text_provider.chat_reply(request)

    async def generate_record(self, request: GenerateRecordRequest) -> GenerateRecordResponse:
        return await self.text_provider.generate_record(request)

    async def plan_reflection(self, request: PlanReflectionRequest) -> PlanReflectionResponse:
        return await self.text_provider.plan_reflection(request)

    async def write_reflection(self, request: WriteReflectionRequest) -> GenerateRecordResponse:
        return await self.text_provider.write_reflection(request)

    async def extract_memory(self, request: ExtractMemoryRequest) -> ExtractMemoryResponse:
        return await self.text_provider.extract_memory(request)

    async def transcribe(self, request: TranscribeAudioRequest) -> TranscribeAudioResponse:
        return await self.speech_provider.transcribe(request)

    async def synthesize(self, request: SynthesizeSpeechRequest) -> SynthesizeSpeechResponse:
        return await self.speech_provider.synthesize(request)

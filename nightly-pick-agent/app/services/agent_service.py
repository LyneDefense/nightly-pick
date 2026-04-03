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
from app.providers.base import SpeechSynthesizeProvider, SpeechTranscribeProvider, TextProvider


class AgentService:
    def __init__(
        self,
        text_provider: TextProvider,
        speech_transcribe_provider: SpeechTranscribeProvider,
        speech_synthesize_provider: SpeechSynthesizeProvider,
    ):
        self.text_provider = text_provider
        self.speech_transcribe_provider = speech_transcribe_provider
        self.speech_synthesize_provider = speech_synthesize_provider

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

    async def generate_share_card(self, request: GenerateShareCardRequest) -> GenerateShareCardResponse:
        return await self.text_provider.generate_share_card(request)

    async def transcribe(self, request: TranscribeAudioRequest) -> TranscribeAudioResponse:
        return await self.speech_transcribe_provider.transcribe(request)

    async def synthesize(self, request: SynthesizeSpeechRequest) -> SynthesizeSpeechResponse:
        return await self.speech_synthesize_provider.synthesize(request)

from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


def to_camel(value: str) -> str:
    parts = value.split("_")
    return parts[0] + "".join(part.capitalize() for part in parts[1:])


class CamelModel(BaseModel):
    model_config = ConfigDict(
        populate_by_name=True,
        alias_generator=to_camel,
    )


class HealthResponse(CamelModel):
    service: str
    status: str
    text_provider: str | None = None
    speech_provider: str | None = None


class ChatReplyRequest(CamelModel):
    session_id: str = Field(..., min_length=1)
    user_input: str = Field(..., min_length=1)
    history: list[str] = Field(default_factory=list)
    profile_summary: str | None = None
    emotional_trend_summary: str | None = None
    strategy_hints: str | None = None
    recent_memories: list[str] = Field(default_factory=list)
    allow_memory_reference: bool = True


class ChatReplyResponse(CamelModel):
    reply_text: str
    should_end: bool
    stage: Literal["opening", "exploring", "closing"]


class GenerateRecordRequest(CamelModel):
    session_id: str = Field(..., min_length=1)
    conversation_text: str = Field(..., min_length=1)
    existing_title: str | None = None
    existing_summary: str | None = None
    existing_highlight: str | None = None
    existing_events: list[str] = Field(default_factory=list)
    existing_emotions: list[str] = Field(default_factory=list)
    existing_open_loops: list[str] = Field(default_factory=list)


class PlanReflectionRequest(CamelModel):
    session_id: str = Field(..., min_length=1)
    conversation_text: str = Field(..., min_length=1)
    existing_title: str | None = None
    existing_summary: str | None = None
    existing_highlight: str | None = None
    existing_events: list[str] = Field(default_factory=list)
    existing_emotions: list[str] = Field(default_factory=list)
    existing_open_loops: list[str] = Field(default_factory=list)


class PlanReflectionResponse(CamelModel):
    reflection_depth: Literal["companionship", "light", "medium", "deep"]
    tone: Literal["quiet-companionship", "gentle-reflection", "clear-reflection"]
    should_list_facts: bool
    should_list_unfinished: bool
    should_make_conclusion: bool
    focus: list[str] = Field(default_factory=list)
    what_happened_today: list[str] = Field(default_factory=list)
    wanted_but_not_done: list[str] = Field(default_factory=list)
    core_tension: str = ""
    record_shape: Literal["light_record", "standard_record", "deep_record"] = "standard_record"


class WriteReflectionRequest(CamelModel):
    session_id: str = Field(..., min_length=1)
    conversation_text: str = Field(..., min_length=1)
    existing_title: str | None = None
    existing_summary: str | None = None
    existing_highlight: str | None = None
    existing_events: list[str] = Field(default_factory=list)
    existing_emotions: list[str] = Field(default_factory=list)
    existing_open_loops: list[str] = Field(default_factory=list)
    plan: PlanReflectionResponse


class GenerateRecordResponse(CamelModel):
    title: str
    summary: str
    events: list[str]
    emotions: list[str]
    open_loops: list[str]
    highlight: str


class ExtractMemoryRequest(CamelModel):
    record_id: str = Field(..., min_length=1)
    summary: str = Field(..., min_length=1)
    conversation_text: str | None = None
    existing_memories: list[str] = Field(default_factory=list)


class MemoryItem(CamelModel):
    type: Literal["topic", "person", "emotion", "ongoing", "soothing"]
    content: str
    source_record_id: str


class ExtractMemoryResponse(CamelModel):
    short_term_memory: list[MemoryItem]


class TranscribeAudioRequest(CamelModel):
    session_id: str = Field(..., min_length=1)
    audio_url: str = Field(..., min_length=1)


class TranscribeAudioResponse(CamelModel):
    transcript_text: str


class SynthesizeSpeechRequest(CamelModel):
    text: str = Field(..., min_length=1)
    voice_id: str | None = None


class SynthesizeSpeechResponse(CamelModel):
    audio_url: str
    voice_id: str

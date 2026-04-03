import json
import re
import base64
import logging
import time

import httpx
from fastapi import HTTPException

from app.config import Settings
from app.models import (
    ChatReplyRequest,
    ChatReplyResponse,
    ExtractMemoryRequest,
    ExtractMemoryResponse,
    GenerateRecordRequest,
    GenerateRecordResponse,
    GenerateShareCardRequest,
    GenerateShareCardResponse,
    MemoryItem,
    PlanReflectionRequest,
    PlanReflectionResponse,
    SynthesizeSpeechRequest,
    SynthesizeSpeechResponse,
    TranscribeAudioRequest,
    TranscribeAudioResponse,
    WriteReflectionRequest,
)
from app.prompts import (
    BASE_SYSTEM_PROMPT,
    CONVERSATION_STRATEGY_PROMPT,
    RECORD_GENERATION_PROMPT,
    REFLECTION_PLANNER_PROMPT,
    REFLECTION_WRITER_PROMPT,
    SHARE_CARD_GENERATION_PROMPT,
)
from app.providers.base import SpeechSynthesizeProvider, SpeechTranscribeProvider, TextProvider

logger = logging.getLogger("nightly-pick-agent.minimax")

MAX_OPEN_LOOPS = 6

def build_httpx_client(timeout_seconds: float) -> httpx.AsyncClient:
    return httpx.AsyncClient(timeout=timeout_seconds, trust_env=False)


class MiniMaxTextProvider(TextProvider):
    def __init__(self, settings: Settings):
        if not settings.minimax_api_key:
            raise ValueError("MINIMAX_API_KEY is required when TEXT_PROVIDER=minimax")
        self.settings = settings

    async def chat_reply(self, request: ChatReplyRequest) -> ChatReplyResponse:
        logger.info(
            "开始生成对话回复 sessionId=%s historyCount=%s allowMemoryReference=%s shouldEnd=%s",
            request.session_id,
            len(request.history),
            request.allow_memory_reference,
            len(request.history) >= 5,
        )
        schema_instruction = """
返回 JSON，字段必须包含：
reply_text: string
should_end: boolean
stage: opening|exploring|closing
dominant_mode: companionship|sorting|review
reflection_readiness: not_ready|light_ready|ready
""".strip()
        messages = [
            {
                "role": "system",
                "content": f"{BASE_SYSTEM_PROMPT}\n\n{CONVERSATION_STRATEGY_PROMPT}\n\n{schema_instruction}",
            }
        ]
        if request.profile_summary:
            messages.append(
                {
                    "role": "system",
                    "content": "这是基于长期使用形成的用户画像摘要，请作为理解背景自然使用，不要逐条复述：\n"
                    + request.profile_summary,
                }
            )
        if request.emotional_trend_summary:
            messages.append(
                {
                    "role": "system",
                    "content": "这是用户最近几次记录呈现出的情绪变化趋势，只能作为轻量参考，不要做诊断式表达：\n"
                    + request.emotional_trend_summary,
                }
            )
        if request.strategy_hints:
            messages.append(
                {
                    "role": "system",
                    "content": "这是针对该用户的对话策略提示，请内化成提问方式，不要逐条复述给用户：\n"
                    + request.strategy_hints,
                }
            )
        if request.allow_memory_reference and request.recent_memories:
            messages.append(
                {
                    "role": "system",
                    "content": "这是当前输入最相关的用户记忆，请只在自然时少量引用，不要制造监视感：\n"
                    + "\n".join(f"- {item}" for item in request.recent_memories[:3]),
                }
            )
        if request.pending_unanswered_inputs:
            messages.append(
                {
                    "role": "system",
                    "content": "下面这些是用户前面说过、但你还没明确接住的候选内容。请你自己判断其中哪 1 到 2 个点最值得这次自然补上；如果候选都不重要，也可以不补。回复时要把选中的旧点和这次新的输入融合成一条自然回复，不要逐条罗列，不要说自己漏回了：\n"
                    + "\n".join(f"- {item}" for item in request.pending_unanswered_inputs[:10]),
                }
            )
        for item in request.history[-6:]:
            role = "assistant" if item.startswith("assistant:") else "user"
            content = item.split(":", 1)[1].strip() if ":" in item else item
            messages.append({"role": role, "content": content})
        messages.append({"role": "user", "content": request.user_input})
        payload = await self._chat_with_json_retry(messages, request)
        reply_text = self._sanitize_text(str(payload.get("reply_text", "") or ""))
        stage = self._normalize_stage(payload.get("stage"), request.history)
        dominant_mode = self._normalize_dominant_mode(payload.get("dominant_mode"))
        reflection_readiness = self._normalize_reflection_readiness(payload.get("reflection_readiness"), dominant_mode, stage)
        should_end = bool(payload.get("should_end", False))
        logger.info(
            "对话回复生成完成 sessionId=%s stage=%s mode=%s readiness=%s replyLength=%s",
            request.session_id,
            stage,
            dominant_mode,
            reflection_readiness,
            len(reply_text),
        )
        return ChatReplyResponse(
            reply_text=reply_text,
            should_end=should_end,
            stage=stage,
            dominant_mode=dominant_mode,
            reflection_readiness=reflection_readiness,
        )

    async def _chat_with_json_retry(self, messages: list[dict[str, str]], request: ChatReplyRequest) -> dict:
        first_content = await self._chat_completion(messages)
        try:
            return self._parse_chat_payload(first_content, request)
        except HTTPException:
            logger.warning("聊天模型首次未返回合法 JSON，准备携带修复提示重试 sessionId=%s", request.session_id)
            retry_messages = [
                *messages,
                {"role": "assistant", "content": self._sanitize_text(first_content)},
                {
                    "role": "system",
                    "content": (
                        "你刚才没有按要求输出合法 JSON。"
                        "请基于刚才同一轮回复意图，重新输出一次。"
                        "只能返回一个合法 JSON 对象，不要附加解释、前言、markdown 代码块或额外文本。"
                        "字段必须包含：reply_text, should_end, stage, dominant_mode, reflection_readiness。"
                    ),
                },
            ]
            second_content = await self._chat_completion(retry_messages)
            try:
                return self._parse_chat_payload(second_content, request)
            except HTTPException:
                logger.warning("聊天模型二次修复后仍未返回合法 JSON，回退为纯文本解析 sessionId=%s", request.session_id)
                return self._fallback_chat_payload(second_content, request)

    def _parse_chat_payload(self, content: str, request: ChatReplyRequest) -> dict:
        try:
            return self._parse_json(content)
        except HTTPException:
            raise

    def _fallback_chat_payload(self, content: str, request: ChatReplyRequest) -> dict:
        fallback_text = self._sanitize_text(content)
        fallback_stage = self._normalize_stage(None, request.history)
        fallback_mode = self._infer_chat_mode(request)
        fallback_readiness = self._normalize_reflection_readiness(None, fallback_mode, fallback_stage)
        logger.warning(
            "聊天模型未返回合法 JSON，已回退为纯文本解析 sessionId=%s mode=%s readiness=%s content=%s",
            request.session_id,
            fallback_mode,
            fallback_readiness,
            fallback_text,
        )
        return {
            "reply_text": fallback_text,
            "should_end": False,
            "stage": fallback_stage,
            "dominant_mode": fallback_mode,
            "reflection_readiness": fallback_readiness,
        }

    async def generate_record(self, request: GenerateRecordRequest) -> GenerateRecordResponse:
        plan = await self.plan_reflection(
            PlanReflectionRequest(
                session_id=request.session_id,
                conversation_text=request.conversation_text,
                existing_title=request.existing_title,
                existing_summary=request.existing_summary,
                existing_highlight=request.existing_highlight,
                existing_events=request.existing_events,
                existing_emotions=request.existing_emotions,
                existing_open_loops=request.existing_open_loops,
            )
        )
        return await self.write_reflection(
            WriteReflectionRequest(
                session_id=request.session_id,
                conversation_text=request.conversation_text,
                existing_title=request.existing_title,
                existing_summary=request.existing_summary,
                existing_highlight=request.existing_highlight,
                existing_events=request.existing_events,
                existing_emotions=request.existing_emotions,
                existing_open_loops=request.existing_open_loops,
                plan=plan,
            )
        )

    async def plan_reflection(self, request: PlanReflectionRequest) -> PlanReflectionResponse:
        logger.info(
            "开始执行 reflection planner sessionId=%s conversationLength=%s hasExistingRecord=%s",
            request.session_id,
            len(request.conversation_text or ""),
            any([
                request.existing_summary,
                request.existing_events,
                request.existing_emotions,
                request.existing_open_loops,
            ]),
        )
        schema_instruction = """
返回 JSON，字段必须包含：
reflection_depth: companionship|light|medium|deep
tone: quiet-companionship|gentle-reflection|clear-reflection
should_list_facts: boolean
should_list_unfinished: boolean
should_make_conclusion: boolean
focus: string[]
what_happened_today: string[]
wanted_but_not_done: string[]
core_tension: string
record_shape: light_record|standard_record|deep_record
""".strip()
        existing_context = self._existing_record_context(
            request.existing_title,
            request.existing_summary,
            request.existing_highlight,
            request.existing_events,
            request.existing_emotions,
            request.existing_open_loops,
        )
        messages = [
            {"role": "system", "content": f"{BASE_SYSTEM_PROMPT}\n\n{REFLECTION_PLANNER_PROMPT}\n\n{schema_instruction}"},
            {
                "role": "user",
                "content": (
                    ("这是今天已经存在的旧记录，请结合它理解今晚该怎么整理：\n" + existing_context + "\n\n") if existing_context else ""
                ) + "这是本次新对话内容：\n" + request.conversation_text,
            },
        ]
        payload = self._parse_json(await self._chat_completion(messages))
        response = PlanReflectionResponse(
            reflection_depth=payload.get("reflection_depth", "light"),
            tone=payload.get("tone", "gentle-reflection"),
            should_list_facts=bool(payload.get("should_list_facts", True)),
            should_list_unfinished=bool(payload.get("should_list_unfinished", False)),
            should_make_conclusion=bool(payload.get("should_make_conclusion", True)),
            focus=self._ensure_list(payload.get("focus")),
            what_happened_today=self._ensure_list(payload.get("what_happened_today")),
            wanted_but_not_done=self._ensure_list(payload.get("wanted_but_not_done")),
            core_tension=str(payload.get("core_tension", "") or ""),
            record_shape=payload.get("record_shape", "standard_record"),
        )
        logger.info(
            "reflection planner 完成 sessionId=%s depth=%s tone=%s shape=%s facts=%s unfinished=%s focusCount=%s",
            request.session_id,
            response.reflection_depth,
            response.tone,
            response.record_shape,
            response.should_list_facts,
            response.should_list_unfinished,
            len(response.focus),
        )
        return response

    async def write_reflection(self, request: WriteReflectionRequest) -> GenerateRecordResponse:
        logger.info(
            "开始执行 reflection writer sessionId=%s depth=%s tone=%s shape=%s",
            request.session_id,
            request.plan.reflection_depth,
            request.plan.tone,
            request.plan.record_shape,
        )
        schema_instruction = """
返回 JSON，字段必须包含：
title: string
summary: string
events: string[]
emotions: string[]
open_loops: string[]
highlight: string
""".strip()
        existing_context = self._existing_record_context(
            request.existing_title,
            request.existing_summary,
            request.existing_highlight,
            request.existing_events,
            request.existing_emotions,
            request.existing_open_loops,
        )
        messages = [
            {"role": "system", "content": f"{BASE_SYSTEM_PROMPT}\n\n{RECORD_GENERATION_PROMPT}\n\n{REFLECTION_WRITER_PROMPT}\n\n{schema_instruction}"},
            {
                "role": "user",
                "content": (
                    ("这是今天已经存在的旧记录，请先理解它并和新内容融合：\n" + existing_context + "\n\n") if existing_context else ""
                ) + "这是 reflection planner 产出的结构化计划：\n"
                + json.dumps(request.plan.model_dump(by_alias=False), ensure_ascii=False)
                + "\n\n这是本次新对话内容：\n"
                + request.conversation_text,
            },
        ]
        content = await self._chat_completion(messages)
        payload = self._parse_json(content)
        response = GenerateRecordResponse(
            title=payload.get("title", "今夜记录"),
            summary=payload.get("summary", ""),
            events=self._ensure_list(payload.get("events")),
            emotions=self._ensure_list(payload.get("emotions")),
            open_loops=self._normalize_open_loops(payload.get("open_loops")),
            highlight=payload.get("highlight", ""),
        )
        logger.info(
            "reflection writer 完成 sessionId=%s title=%s summaryLength=%s eventCount=%s emotionCount=%s openLoopCount=%s",
            request.session_id,
            response.title,
            len(response.summary),
            len(response.events),
            len(response.emotions),
            len(response.open_loops),
        )
        return response

    async def generate_share_card(self, request: GenerateShareCardRequest) -> GenerateShareCardResponse:
        logger.info("开始生成分享卡片文案 recordId=%s cardType=%s", request.record_id, request.card_type)
        schema_instruction = """
返回 JSON，字段必须包含：
headline: string
subline: string
""".strip()
        messages = [
            {
                "role": "system",
                "content": (
                    f"{BASE_SYSTEM_PROMPT}\n\n{SHARE_CARD_GENERATION_PROMPT}\n\n"
                    f"{self._build_share_card_type_prompt(request.card_type)}\n\n{schema_instruction}"
                ),
            },
            {
                "role": "user",
                "content": json.dumps(
                    {
                        "record_id": request.record_id,
                        "card_type": request.card_type,
                        "record_date": request.record_date,
                        "title": request.title,
                        "summary": request.summary,
                        "highlight": request.highlight,
                        "events": request.events,
                        "emotions": request.emotions,
                        "open_loops": request.open_loops,
                    },
                    ensure_ascii=False,
                ),
            },
        ]
        payload = self._parse_json(await self._chat_completion(messages))
        response = GenerateShareCardResponse(
            headline=self._sanitize_text(str(payload.get("headline", "") or "")),
            subline=self._sanitize_text(str(payload.get("subline", "") or "")),
        )
        logger.info(
            "分享卡片文案生成完成 recordId=%s cardType=%s headlineLength=%s sublineLength=%s",
            request.record_id,
            request.card_type,
            len(response.headline),
            len(response.subline),
        )
        return response

    async def extract_memory(self, request: ExtractMemoryRequest) -> ExtractMemoryResponse:
        logger.info(
            "开始抽取长期记忆 recordId=%s summaryLength=%s existingMemoryCount=%s",
            request.record_id,
            len(request.summary or ""),
            len(request.existing_memories or []),
        )
        existing_context = ""
        if request.existing_memories:
            existing_context = "\n当前已有记忆候选（如果新内容本质相同，尽量沿用或贴近这些表达，避免重复造新条目）：\n" + "\n".join(
                f"- {item}" for item in request.existing_memories[:12]
            )
        messages = [
            {
                "role": "system",
                "content": "请从输入内容中提取 1 到 4 条值得长期保留的记忆线索，返回 JSON：{\"short_term_memory\": [{\"type\": \"topic|person|emotion|ongoing|soothing\", \"content\": \"...\", \"source_record_id\": \"...\"}]}。\n要求：1) 只有在用户明确提到时才提取修复方式/安抚方式；2) 内容要短、稳、可复用；3) 不要提取过度细碎的一次性信息；4) 如果当前已有记忆候选里已经有本质相同的表达，优先复用它的类型和措辞来帮助归并。",
            },
            {
                "role": "user",
                "content": json.dumps(
                    {
                        "record_id": request.record_id,
                        "summary": request.summary,
                        "conversation_text": request.conversation_text,
                    },
                    ensure_ascii=False,
                ) + existing_context,
            },
        ]
        content = await self._chat_completion(messages)
        payload = self._parse_json(content)
        memories = []
        for item in payload.get("short_term_memory", []):
            memories.append(
                MemoryItem(
                    type=item.get("type", "topic"),
                    content=item.get("content", ""),
                    source_record_id=item.get("source_record_id", request.record_id),
                )
            )
        logger.info("长期记忆抽取完成 recordId=%s memoryCount=%s", request.record_id, len(memories))
        return ExtractMemoryResponse(short_term_memory=memories)

    async def _chat_completion(self, messages: list[dict[str, str]]) -> str:
        url = f"{self.settings.minimax_text_base_url}/text/chatcompletion_v2"
        headers = {
            "Authorization": f"Bearer {self.settings.minimax_api_key}",
            "Content-Type": "application/json",
        }
        payload = {
            "model": self.settings.text_model,
            "messages": messages,
            "temperature": 0.6,
        }
        started_at = time.perf_counter()
        logger.info(
            "开始请求 MiniMax 文本模型 model=%s url=%s messageCount=%s lastUserInput=%s",
            self.settings.text_model,
            url,
            len(messages),
            messages[-1]["content"] if messages else "",
        )
        try:
            async with build_httpx_client(90.0) as client:
                response = await client.post(url, headers=headers, json=payload)
        except httpx.TimeoutException as exc:
            elapsed_ms = round((time.perf_counter() - started_at) * 1000, 2)
            logger.error("请求 MiniMax 文本模型超时 elapsedMs=%s", elapsed_ms)
            raise HTTPException(status_code=504, detail="MiniMax text request timed out.") from exc
        except httpx.HTTPError as exc:
            elapsed_ms = round((time.perf_counter() - started_at) * 1000, 2)
            logger.error("请求 MiniMax 文本模型失败 elapsedMs=%s error=%s", elapsed_ms, exc)
            raise HTTPException(status_code=502, detail=f"MiniMax text request failed: {exc}") from exc
        elapsed_ms = round((time.perf_counter() - started_at) * 1000, 2)
        logger.info("MiniMax 文本模型响应完成 status=%s elapsedMs=%s", response.status_code, elapsed_ms)
        if response.status_code >= 400:
            raise HTTPException(status_code=502, detail=f"MiniMax text error: {response.text}")
        body = response.json()
        try:
            content = body["choices"][0]["message"]["content"]
            return self._sanitize_text(content)
        except (KeyError, IndexError, TypeError) as exc:
            raise HTTPException(status_code=502, detail="MiniMax text response format is invalid.") from exc

    @staticmethod
    def _parse_json(content: str) -> dict:
        content = content.strip()
        if content.startswith("```"):
            lines = content.splitlines()
            content = "\n".join(lines[1:-1]).strip()
        try:
            return json.loads(content)
        except json.JSONDecodeError as exc:
            raise HTTPException(status_code=502, detail=f"Model did not return valid JSON: {content}") from exc

    @staticmethod
    def _ensure_list(value: object) -> list[str]:
        if isinstance(value, list):
            return [str(item) for item in value]
        return []

    @staticmethod
    def _normalize_open_loops(value: object) -> list[str]:
        loops: list[str] = []
        for item in MiniMaxTextProvider._ensure_list(value):
            normalized = re.sub(r"\s+", " ", str(item)).strip(" \n\t-•·、，。;；")
            if not normalized or normalized in loops:
                continue
            loops.append(normalized)
            if len(loops) >= MAX_OPEN_LOOPS:
                break
        return loops

    @staticmethod
    def _existing_record_context(title, summary, highlight, events, emotions, open_loops) -> str:
        if not (summary or events or emotions or open_loops):
            return ""
        return json.dumps(
            {
                "title": title,
                "summary": summary,
                "highlight": highlight,
                "events": events,
                "emotions": emotions,
                "open_loops": open_loops,
            },
            ensure_ascii=False,
        )

    @staticmethod
    def _sanitize_text(content: str) -> str:
        sanitized = re.sub(r"<think\b[^>]*>[\s\S]*?(?:</think>|$)", "", content, flags=re.IGNORECASE).strip()
        return sanitized or content.strip()

    @staticmethod
    def _normalize_stage(value: object, history: list[str]) -> str:
        stage = str(value or "").strip()
        if stage in {"opening", "exploring", "closing"}:
            return stage
        return "opening" if len(history) < 2 else "exploring"

    @staticmethod
    def _normalize_dominant_mode(value: object) -> str:
        mode = str(value or "").strip()
        if mode in {"companionship", "sorting", "review"}:
            return mode
        return "sorting"

    @staticmethod
    def _normalize_reflection_readiness(value: object, dominant_mode: str, stage: str) -> str:
        readiness = str(value or "").strip()
        if readiness in {"not_ready", "light_ready", "ready"}:
            return readiness
        if dominant_mode == "review" or stage == "closing":
            return "ready"
        if dominant_mode == "sorting":
            return "light_ready"
        return "not_ready"

    @staticmethod
    def _infer_chat_mode(request: ChatReplyRequest) -> str:
        text = " ".join([*request.history[-6:], request.user_input]).lower()
        if any(keyword in text for keyword in ["今天", "刚刚", "明天", "还没", "想做", "打算", "因为", "有点"]):
            return "sorting"
        if len(request.history) >= 6:
            return "review"
        return "companionship"

    @staticmethod
    def _build_share_card_type_prompt(card_type: str) -> str:
        if card_type == "today":
            return """
# Card Type Focus
这次生成的是 `today` 卡片。
1. 它代表“今晚刚刚沉下来的这一页”。
2. 语气要贴近当下，更近、更轻，不要写出明显的回望感。
3. 可以保留一点还没完全放下的余温，但不要写成总结报告。
4. 更像睡前替自己留下一页，而不是隔很久之后的回顾。
5. 即使今晚的原始记录里有具体人物、场景或事件，也不要把这些隐私细节写进分享文案。
""".strip()
        return """
# Card Type Focus
这次生成的是 `recent` 卡片。
1. 它代表“隔一段时间后重新翻回来看的一页”。
2. 语气允许有一点回望感和距离感，但不要写成年度总结。
3. 更像重新看见那时的自己，而不是重新写一遍当天摘要。
4. 可以有“后来再看才懂一点”的轻微后视角。
5. 即使回望感更强，也不要借机带出当时具体的人、事、地点或其他可识别隐私线索。
""".strip()


class MiniMaxSpeechProvider(SpeechTranscribeProvider, SpeechSynthesizeProvider):
    def __init__(self, settings: Settings):
        if not settings.minimax_api_key:
            raise ValueError("MINIMAX_API_KEY is required when SPEECH_SYNTHESIZE_PROVIDER=minimax or SPEECH_TRANSCRIBE_PROVIDER=minimax")
        self.settings = settings

    async def transcribe(self, request: TranscribeAudioRequest) -> TranscribeAudioResponse:
        if not self.settings.minimax_asr_endpoint:
            return TranscribeAudioResponse(
                transcript_text=f"未配置 MiniMax ASR 接口，当前使用占位转写：{request.audio_url}"
            )
        headers = {"Authorization": f"Bearer {self.settings.minimax_api_key}"}
        payload = {"session_id": request.session_id, "audio_url": request.audio_url}
        started_at = time.perf_counter()
        logger.info("开始请求 MiniMax 语音转写 url=%s audioUrl=%s", self.settings.minimax_asr_endpoint, request.audio_url)
        try:
            async with build_httpx_client(60.0) as client:
                response = await client.post(self.settings.minimax_asr_endpoint, headers=headers, json=payload)
        except httpx.TimeoutException as exc:
            elapsed_ms = round((time.perf_counter() - started_at) * 1000, 2)
            logger.error("MiniMax 语音转写超时 elapsedMs=%s", elapsed_ms)
            raise HTTPException(status_code=504, detail="MiniMax ASR request timed out.") from exc
        except httpx.HTTPError as exc:
            elapsed_ms = round((time.perf_counter() - started_at) * 1000, 2)
            logger.error("MiniMax 语音转写失败 elapsedMs=%s error=%s", elapsed_ms, exc)
            raise HTTPException(status_code=502, detail=f"MiniMax ASR request failed: {exc}") from exc
        elapsed_ms = round((time.perf_counter() - started_at) * 1000, 2)
        logger.info("MiniMax 语音转写完成 status=%s elapsedMs=%s", response.status_code, elapsed_ms)
        if response.status_code >= 400:
            raise HTTPException(status_code=502, detail=f"MiniMax ASR error: {response.text}")
        data = response.json()
        transcript = data.get("transcript_text") or data.get("text") or ""
        return TranscribeAudioResponse(transcript_text=transcript)

    async def synthesize(self, request: SynthesizeSpeechRequest) -> SynthesizeSpeechResponse:
        url = f"{self.settings.minimax_speech_base_url}/t2a_v2"
        headers = {
            "Authorization": f"Bearer {self.settings.minimax_api_key}",
            "Content-Type": "application/json",
        }
        payload = {
            "model": self.settings.minimax_tts_model,
            "text": request.text,
            "output_format": "hex",
            "voice_setting": {
                "voice_id": request.voice_id or self.settings.minimax_tts_voice_id,
            },
            "audio_setting": {
                "format": "mp3",
            },
            "stream": False,
        }
        started_at = time.perf_counter()
        logger.info(
            "开始请求 MiniMax 语音合成 model=%s url=%s textLength=%s voiceId=%s",
            self.settings.minimax_tts_model,
            url,
            len(request.text),
            request.voice_id or self.settings.minimax_tts_voice_id,
        )
        try:
            async with build_httpx_client(90.0) as client:
                response = await client.post(url, headers=headers, json=payload)
        except httpx.TimeoutException as exc:
            elapsed_ms = round((time.perf_counter() - started_at) * 1000, 2)
            logger.error("MiniMax 语音合成超时 elapsedMs=%s", elapsed_ms)
            raise HTTPException(status_code=504, detail="MiniMax TTS request timed out.") from exc
        except httpx.HTTPError as exc:
            elapsed_ms = round((time.perf_counter() - started_at) * 1000, 2)
            logger.error("MiniMax 语音合成失败 elapsedMs=%s error=%s", elapsed_ms, exc)
            raise HTTPException(status_code=502, detail=f"MiniMax TTS request failed: {exc}") from exc
        elapsed_ms = round((time.perf_counter() - started_at) * 1000, 2)
        logger.info("MiniMax 语音合成完成 status=%s elapsedMs=%s", response.status_code, elapsed_ms)
        if response.status_code >= 400:
            raise HTTPException(status_code=502, detail=f"MiniMax TTS error: {response.text}")
        data = response.json()
        audio_hex = (data.get("data") or {}).get("audio")
        if audio_hex:
            try:
                audio_bytes = bytes.fromhex(audio_hex)
                audio_base64 = base64.b64encode(audio_bytes).decode("utf-8")
                audio_url = f"data:audio/mpeg;base64,{audio_base64}"
            except ValueError as exc:
                raise HTTPException(status_code=502, detail="MiniMax TTS returned invalid hex audio.") from exc
        else:
            audio_url = data.get("audio_file") or data.get("audio_url") or data.get("file")
        if not audio_url:
            raise HTTPException(status_code=502, detail="MiniMax TTS response format is invalid.")
        return SynthesizeSpeechResponse(
            audio_url=audio_url,
            voice_id=request.voice_id or self.settings.minimax_tts_voice_id,
        )

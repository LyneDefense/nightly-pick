from app.models import (
    ChatReplyRequest,
    ChatReplyResponse,
    ExtractMemoryRequest,
    ExtractMemoryResponse,
    GenerateRecordRequest,
    GenerateRecordResponse,
    MemoryItem,
    PlanReflectionRequest,
    PlanReflectionResponse,
    SynthesizeSpeechRequest,
    SynthesizeSpeechResponse,
    TranscribeAudioRequest,
    TranscribeAudioResponse,
    WriteReflectionRequest,
)
from app.providers.base import SpeechSynthesizeProvider, SpeechTranscribeProvider, TextProvider


class MockTextProvider(TextProvider):
    async def chat_reply(self, request: ChatReplyRequest) -> ChatReplyResponse:
        should_end = len(request.history) >= 5
        stage = "closing" if should_end else ("opening" if len(request.history) < 2 else "exploring")
        memory_hint = ""
        profile_hint = ""
        trend_hint = ""
        strategy_hint = ""
        if request.profile_summary:
            profile_hint = f"我会记得一些关于你的线索：{request.profile_summary.splitlines()[0]}。"
        if request.emotional_trend_summary:
            trend_hint = f"也会把你最近状态的变化放在心里：{request.emotional_trend_summary.splitlines()[0]}。"
        if request.strategy_hints:
            strategy_hint = "我会尽量用更贴近你的方式陪你把今天理一理。"
        if request.allow_memory_reference and request.recent_memories:
            memory_hint = f"我还记得你最近提到过“{request.recent_memories[0]}”。"
        closing_hint = "听起来今晚你已经把重要的部分慢慢说出来了，我们先轻轻收在这里。如果你还想补充，我也在。" if should_end else ""
        return ChatReplyResponse(
            reply_text=(
                f"{profile_hint}{trend_hint}{strategy_hint}{memory_hint}{closing_hint}"
                if should_end
                else f"{profile_hint}{trend_hint}{strategy_hint}{memory_hint}我记下了。关于“{request.user_input}”，今天你最想先停在哪个片段上？"
            ),
            should_end=should_end,
            stage=stage,
        )

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
        text = request.conversation_text or ""
        wants_company = any(keyword in text for keyword in ["睡不着", "陪", "不想说", "安静"])
        has_today_facts = any(keyword in text for keyword in ["今天", "刚刚", "下午", "晚上", "开会", "下班"])
        has_unfinished = any(keyword in text for keyword in ["本来想", "没做", "没去", "拖着", "还没"])
        depth = "deep" if has_today_facts and has_unfinished else "medium" if has_today_facts else "companionship" if wants_company else "light"
        tone = "quiet-companionship" if depth == "companionship" else "clear-reflection" if depth == "deep" else "gentle-reflection"
        record_shape = "light_record" if depth == "companionship" else "deep_record" if depth == "deep" else "standard_record"
        happened = ["提到了今天发生的一些片段"] if has_today_facts else []
        unfinished = ["提到了本来想做但没做的事"] if has_unfinished else []
        core_tension = "想安静下来，却又没有真正放松。" if wants_company else "今晚在回看今天时，开始碰到一些还没放下的点。"
        return PlanReflectionResponse(
            reflection_depth=depth,
            tone=tone,
            should_list_facts=has_today_facts,
            should_list_unfinished=has_unfinished,
            should_make_conclusion=depth != "companionship" or wants_company,
            focus=["陪伴", "失眠停留"] if wants_company and not has_today_facts else ["今天发生的事", "想做但没做"],
            what_happened_today=happened,
            wanted_but_not_done=unfinished,
            core_tension=core_tension,
            record_shape=record_shape,
        )

    async def write_reflection(self, request: WriteReflectionRequest) -> GenerateRecordResponse:
        snippet = request.conversation_text[:40]
        summary_prefix = {
            "companionship": "今晚更像是在一个睡不着的时刻里，想有人陪着待一会儿。",
            "light": "今晚有一些想整理的感受，但还停在比较轻的梳理里。",
            "medium": "今晚已经开始把今天的片段和感受慢慢收拢起来。",
            "deep": "今晚不只是回顾了今天，也慢慢碰到了真正卡住自己的地方。",
        }[request.plan.reflection_depth]
        summary_parts = [summary_prefix]
        if request.plan.what_happened_today:
            summary_parts.append("今天提到的事：" + "；".join(request.plan.what_happened_today))
        if request.plan.wanted_but_not_done:
            summary_parts.append("想做但没做的事：" + "；".join(request.plan.wanted_but_not_done))
        if request.plan.core_tension:
            summary_parts.append("今晚的核心拉扯是：" + request.plan.core_tension)
        if snippet:
            summary_parts.append("这晚对话里也提到了：" + snippet)
        summary = "\n".join(summary_parts)
        events = (request.plan.what_happened_today or [])[:3]
        if not events:
            events = ["完成了一次睡前复盘对话"]
        open_loops = list(dict.fromkeys((request.plan.wanted_but_not_done or []) + (request.existing_open_loops or [])))[:4]
        if not open_loops:
            open_loops = ["还有一些细节值得明天继续展开"]
        return GenerateRecordResponse(
            title=request.existing_title or "今夜记录",
            summary=summary,
            events=events,
            emotions=list(dict.fromkeys((request.existing_emotions or []) + ["平静"])),
            open_loops=open_loops,
            highlight="今晚被整理下来的，不只是内容，也是此刻真正停住的地方。",
        )

    async def extract_memory(self, request: ExtractMemoryRequest) -> ExtractMemoryResponse:
        existing_memories = request.existing_memories or []
        topic_memory = next((item for item in existing_memories if "[topic]" in item or "[ongoing]" in item), None)
        memories = [
            MemoryItem(
                type="ongoing" if topic_memory and "[ongoing]" in topic_memory else "topic",
                content=(topic_memory.split("] ", 1)[1] if topic_memory and "] " in topic_memory else request.summary[:50]),
                source_record_id=request.record_id,
            )
        ]
        if request.conversation_text and any(keyword in request.conversation_text for keyword in ["散步", "音乐", "安静", "睡一觉", "跑步"]):
            soothing_memory = next((item for item in existing_memories if "[soothing]" in item), None)
            memories.append(
                MemoryItem(
                    type="soothing",
                    content=(
                        soothing_memory.split("] ", 1)[1]
                        if soothing_memory and "] " in soothing_memory
                        else "用户在情绪起伏时会尝试用散步、音乐或安静独处让自己缓下来"
                    ),
                    source_record_id=request.record_id,
                )
            )
        return ExtractMemoryResponse(short_term_memory=memories[:3])


class MockSpeechProvider(SpeechTranscribeProvider, SpeechSynthesizeProvider):
    async def transcribe(self, request: TranscribeAudioRequest) -> TranscribeAudioResponse:
        return TranscribeAudioResponse(
            transcript_text=f"这是根据音频 {request.audio_url} 生成的示例转写文本。"
        )

    async def synthesize(self, request: SynthesizeSpeechRequest) -> SynthesizeSpeechResponse:
        slug = abs(hash(request.text)) % 100000
        return SynthesizeSpeechResponse(
            audio_url=f"https://example.com/audio/tts-{slug}.mp3",
            voice_id=request.voice_id,
        )

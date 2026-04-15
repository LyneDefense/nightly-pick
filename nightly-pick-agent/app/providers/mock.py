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
from app.providers.base import SpeechSynthesizeProvider, SpeechTranscribeProvider, TextProvider

MAX_OPEN_LOOPS = 6


class MockTextProvider(TextProvider):
    @staticmethod
    def _share_card_prompt_hint(card_type: str) -> dict[str, str]:
        if card_type == "today":
            return {
                "headline_fallback": "今晚有些事没有答案，也先这样收下来了。",
                "headline_with_highlight": "今晚被留下来的，是那一点终于慢下来的感觉。",
                "subline_fallback": "不是把一切都想明白了，只是把今晚真正停住的地方，轻轻替自己留了下来。",
                "subline_with_emotions": "今晚的情绪落在{emotions}之间，这一页不急着解释，只先替自己留住。",
            }
        return {
            "headline_fallback": "隔了一些时候再看，这一页还是值得留下。",
            "headline_with_highlight": "后来再回看，才发现那晚的停顿也有它的重量。",
            "subline_fallback": "不是为了回到那一天，而是再看见一次，当时的自己是怎样慢慢走过来的。",
            "subline_with_emotions": "那时的情绪落在{emotions}之间，如今回看，仍能认出那晚的自己。",
        }

    async def chat_reply(self, request: ChatReplyRequest) -> ChatReplyResponse:
        combined_text = " ".join([*request.history, request.user_input])
        has_today_signal = any(keyword in combined_text for keyword in ["今天", "今晚", "下午", "晚上", "刚刚", "开会", "下班"])
        has_review_signal = any(keyword in combined_text for keyword in ["差不多", "就这些", "先这样", "没什么了", "收一下"])
        wants_company = any(keyword in combined_text for keyword in ["睡不着", "陪", "安静", "不太想说", "待一会儿"])
        has_unfinished = any(keyword in combined_text for keyword in ["本来想", "没做", "还没", "挂着", "明天"])

        dominant_mode = (
            "review"
            if has_review_signal or (has_today_signal and has_unfinished and len(request.history) >= 4)
            else "companionship"
            if wants_company
            else "sorting"
            if has_today_signal
            else "companionship"
        )
        reflection_readiness = "ready" if dominant_mode == "review" else "light_ready" if dominant_mode == "sorting" or wants_company else "not_ready"
        should_end = dominant_mode == "review"
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
        sorting_hint = (
            f"我记下了。你说的“{request.user_input}”，好像还悬在今天里面。要不要再看看哪一块最值得留下？"
            if dominant_mode == "sorting" and not should_end
            else ""
        )
        companionship_hint = "我在，你可以先这样安静待着。如果愿意，我们也可以慢慢摸到今天留下来的那一点。" if dominant_mode == "companionship" and not should_end else ""
        return ChatReplyResponse(
            reply_text=(
                f"{profile_hint}{trend_hint}{strategy_hint}{memory_hint}{closing_hint}"
                if should_end
                else f"{profile_hint}{trend_hint}{strategy_hint}{memory_hint}{companionship_hint or sorting_hint or ('我记下了。关于“' + request.user_input + '”，今天你最想先停在哪个片段上？')}"
            ),
            should_end=should_end,
            stage=stage,
            dominant_mode=dominant_mode,
            reflection_readiness=reflection_readiness,
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
        text = request.conversation_text or ""
        summary_prefix = {
            "companionship": "今晚我有点睡不着，也不太想把很多话说得很明白，更像只是想有人陪着安静待一会儿。",
            "light": "今晚我只是轻轻理了一下自己的状态，那种说不清的空和提不起劲还在，但也还不急着把它说透。",
            "medium": "今晚我开始把今天散着的片段慢慢收拢起来，也更看见自己心里还挂着什么。",
            "deep": "今晚我不只是回看了今天发生的事，也更清楚地碰到了真正卡住自己的那一处。",
        }[request.plan.reflection_depth]
        summary_parts = [summary_prefix]
        if "睡不着" in text and "睡不着" not in summary_prefix:
            summary_parts.append("到现在我还是有点睡不着。")
        if "陪" in text and "陪" not in summary_prefix:
            summary_parts.append("比起把一切都说清，我更需要一点陪着待着的感觉。")
        if request.plan.what_happened_today:
            summary_parts.append("今天我提到的片段里，" + "；".join(request.plan.what_happened_today) + "。")
        if request.plan.wanted_but_not_done:
            summary_parts.append("我心里还挂着那些想做但没做的部分：" + "；".join(request.plan.wanted_but_not_done) + "。")
        if request.plan.core_tension:
            summary_parts.append("更核心的是，" + request.plan.core_tension)
        summary = " ".join(summary_parts).strip()
        events = (request.plan.what_happened_today or [])[:3]
        if not events:
            events = ["完成了一次睡前复盘对话"]
        open_loops = list(dict.fromkeys((request.plan.wanted_but_not_done or []) + (request.existing_open_loops or [])))[:MAX_OPEN_LOOPS]
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

    async def generate_share_card(self, request: GenerateShareCardRequest) -> GenerateShareCardResponse:
        hint = self._share_card_prompt_hint(request.card_type)
        emotions = [item for item in (request.emotions or []) if item]
        headline = hint["headline_with_highlight"] if request.highlight else hint["headline_fallback"]
        subline = hint["subline_fallback"]
        if emotions:
            subline = hint["subline_with_emotions"].format(emotions="、".join(emotions[:2]))
        return GenerateShareCardResponse(headline=headline, subline=subline)


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

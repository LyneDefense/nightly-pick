import asyncio
import unittest

from app.models import ChatReplyRequest, ExtractMemoryRequest, GenerateRecordRequest, PlanReflectionRequest, WriteReflectionRequest
from app.providers.mock import MockTextProvider


class MockTextProviderTests(unittest.TestCase):
    def setUp(self) -> None:
        self.provider = MockTextProvider()

    def test_chat_reply_uses_memory_hint_when_allowed(self) -> None:
        request = ChatReplyRequest(
            session_id="session-1",
            user_input="我今天又在赶项目",
            history=["user: 我今天有点累"],
            profile_summary="持续议题：项目推进压力",
            emotional_trend_summary="最近几次记录里，焦虑和疲惫反复出现。",
            strategy_hints="先接住情绪，再只问一个问题。",
            recent_memories=["最近持续提到项目推进压力"],
            allow_memory_reference=True,
        )
        response = asyncio.run(self.provider.chat_reply(request))
        self.assertIn("最近提到过", response.reply_text)
        self.assertIn("状态的变化", response.reply_text)
        self.assertEqual("opening", response.stage)

    def test_chat_reply_offers_record_when_conversation_is_ready_to_close(self) -> None:
        request = ChatReplyRequest(
            session_id="session-1",
            user_input="差不多就是这些了",
            history=[
                "user: 我今天很累",
                "assistant: 发生了什么？",
                "user: 一直在赶项目",
                "assistant: 最卡住你的是什么？",
                "user: 我怕自己做不好",
                "assistant: 这种担心什么时候最强？",
            ],
        )
        response = asyncio.run(self.provider.chat_reply(request))
        self.assertTrue(response.should_end)
        self.assertEqual("closing", response.stage)
        self.assertIn("轻轻收在这里", response.reply_text)
        self.assertNotIn("要不要我帮你整理成今晚记录", response.reply_text)

    def test_generate_record_returns_structured_fields(self) -> None:
        request = GenerateRecordRequest(
            session_id="session-1",
            conversation_text="user: 今天工作有点累\nassistant: 你最想保留什么感受？",
        )
        response = asyncio.run(self.provider.generate_record(request))
        self.assertTrue(response.title)
        self.assertIsInstance(response.events, list)
        self.assertIsInstance(response.emotions, list)

    def test_plan_reflection_extracts_unfinished_signal(self) -> None:
        request = PlanReflectionRequest(
            session_id="session-1",
            conversation_text="user: 今天本来想去跑步，但最后还是没去。\nassistant: 你现在最卡住的是什么？",
        )
        response = asyncio.run(self.provider.plan_reflection(request))
        self.assertTrue(response.should_list_facts)
        self.assertTrue(response.should_list_unfinished)
        self.assertIn(response.reflection_depth, {"medium", "deep"})

    def test_write_reflection_uses_plan_shape(self) -> None:
        plan = asyncio.run(
            self.provider.plan_reflection(
                PlanReflectionRequest(
                    session_id="session-1",
                    conversation_text="user: 我睡不着，也不太想说话，只是想有人陪着。",
                )
            )
        )
        response = asyncio.run(
            self.provider.write_reflection(
                WriteReflectionRequest(
                    session_id="session-1",
                    conversation_text="user: 我睡不着，也不太想说话，只是想有人陪着。",
                    plan=plan,
                )
            )
        )
        self.assertTrue(response.summary)
        self.assertIsInstance(response.open_loops, list)

    def test_extract_memory_returns_items(self) -> None:
        request = ExtractMemoryRequest(
            record_id="record-1",
            summary="最近一直在想项目进度",
            conversation_text="user: 我晚上散步后会好一点",
            existing_memories=["[ongoing] 工作推进压力反复出现", "[soothing] 用户在情绪起伏时会尝试用散步、音乐或安静独处让自己缓下来"],
        )
        response = asyncio.run(self.provider.extract_memory(request))
        self.assertGreaterEqual(len(response.short_term_memory), 1)
        self.assertEqual("record-1", response.short_term_memory[0].source_record_id)
        self.assertEqual("ongoing", response.short_term_memory[0].type)
        self.assertTrue(any(item.type == "soothing" for item in response.short_term_memory))


if __name__ == "__main__":
    unittest.main()

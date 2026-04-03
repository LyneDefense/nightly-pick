import asyncio
import json
import unittest
from pathlib import Path

from app.models import ChatReplyRequest
from app.providers.mock import MockTextProvider


FIXTURES_PATH = Path(__file__).parent / "fixtures" / "chat_cases.json"


class ChatFixtureTests(unittest.TestCase):
    def setUp(self) -> None:
        self.provider = MockTextProvider()
        self.cases = json.loads(FIXTURES_PATH.read_text(encoding="utf-8"))

    def test_chat_cases(self) -> None:
        for case in self.cases:
            with self.subTest(case=case["name"]):
                response = asyncio.run(
                    self.provider.chat_reply(
                        ChatReplyRequest(
                            session_id=case["name"],
                            history=case.get("history", []),
                            user_input=case["user_input"],
                        )
                    )
                )
                expected = case["expected"]
                self.assertIn(response.stage, expected["stage_in"])
                self.assertEqual(response.dominant_mode, expected["dominant_mode"])
                self.assertIn(response.reflection_readiness, expected["reflection_readiness_in"])
                for text in expected.get("reply_contains", []):
                    self.assertIn(text, response.reply_text)
                for text in expected.get("reply_not_contains", []):
                    self.assertNotIn(text, response.reply_text)


if __name__ == "__main__":
    unittest.main()

import asyncio
import json
import unittest
from pathlib import Path

from app.models import PlanReflectionRequest, WriteReflectionRequest
from app.providers.mock import MockTextProvider


FIXTURES_PATH = Path(__file__).parent / "fixtures" / "reflection_cases.json"


class ReflectionFixtureTests(unittest.TestCase):
    def setUp(self) -> None:
        self.provider = MockTextProvider()
        self.cases = json.loads(FIXTURES_PATH.read_text(encoding="utf-8"))

    def test_reflection_cases(self) -> None:
        for case in self.cases:
            with self.subTest(case=case["name"]):
                conversation_text = case["conversation_text"]
                plan = asyncio.run(
                    self.provider.plan_reflection(
                        PlanReflectionRequest(
                            session_id=case["name"],
                            conversation_text=conversation_text,
                        )
                    )
                )

                expected_plan = case["expected_plan"]
                self.assertIn(plan.reflection_depth, expected_plan["reflection_depth_in"])
                self.assertEqual(plan.should_list_facts, expected_plan["should_list_facts"])
                self.assertEqual(
                    plan.should_list_unfinished,
                    expected_plan["should_list_unfinished"],
                )

                record = asyncio.run(
                    self.provider.write_reflection(
                        WriteReflectionRequest(
                            session_id=case["name"],
                            conversation_text=conversation_text,
                            plan=plan,
                        )
                    )
                )

                expected_writer = case["expected_writer"]
                for text in expected_writer.get("summary_contains", []):
                    self.assertIn(text, record.summary)
                for text in expected_writer.get("summary_not_contains", []):
                    self.assertNotIn(text, record.summary)
                if "open_loops_min" in expected_writer:
                    self.assertGreaterEqual(
                        len(record.open_loops), expected_writer["open_loops_min"]
                    )
                if "open_loops_max" in expected_writer:
                    self.assertLessEqual(
                        len(record.open_loops), expected_writer["open_loops_max"]
                    )


if __name__ == "__main__":
    unittest.main()

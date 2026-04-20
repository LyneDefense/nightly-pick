import unittest

from app.config import Settings
from app.providers.deepseek import DeepSeekTextProvider
from app.providers.registry import ProviderRegistry


class DeepSeekProviderTests(unittest.TestCase):
    def test_registry_builds_deepseek_text_provider(self) -> None:
        settings = Settings(
            text_provider="deepseek",
            deepseek_api_key="test-key",
        )
        registry = ProviderRegistry.build(settings)
        self.assertEqual("DeepSeekTextProvider", registry.describe()["text_provider"])

    def test_deepseek_chat_url_uses_openai_compatible_endpoint(self) -> None:
        provider = DeepSeekTextProvider(
            Settings(
                text_provider="deepseek",
                deepseek_api_key="test-key",
            )
        )
        self.assertEqual(
            "https://api.deepseek.com/v1/chat/completions",
            provider._text_chat_url(),
        )


if __name__ == "__main__":
    unittest.main()

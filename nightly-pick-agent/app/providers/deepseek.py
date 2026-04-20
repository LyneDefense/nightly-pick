from app.config import Settings
from app.providers.minimax import MiniMaxTextProvider


class DeepSeekTextProvider(MiniMaxTextProvider):
    provider_name = "deepseek"
    provider_display_name = "DeepSeek"

    def _text_api_key(self) -> str | None:
        return self.settings.deepseek_api_key

    def _text_base_url(self) -> str:
        return self.settings.deepseek_text_base_url

    def _text_model_name(self) -> str:
        return self.settings.deepseek_text_model

    def _text_chat_url(self) -> str:
        return f"{self._text_base_url().rstrip('/')}/chat/completions"

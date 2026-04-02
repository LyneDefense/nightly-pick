from functools import lru_cache
from typing import Literal

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    app_env: str = "development"
    text_provider: Literal["mock", "minimax"] = "mock"
    speech_provider: Literal["mock", "minimax"] = "mock"

    text_model: str = "MiniMax-M2.5"
    minimax_api_key: str | None = None
    minimax_text_base_url: str = "https://api.minimax.io/v1"

    minimax_speech_base_url: str = "https://api.minimax.io/v1"
    minimax_tts_model: str = "speech-2.8-turbo"
    minimax_tts_voice_id: str = "Chinese (Mandarin)_Warm_Bestie"
    minimax_asr_endpoint: str | None = None


@lru_cache
def get_settings() -> Settings:
    return Settings()

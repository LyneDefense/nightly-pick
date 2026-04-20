from functools import lru_cache
from typing import Literal

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    app_env: str = "development"
    text_provider: Literal["mock", "minimax"] = "mock"
    speech_transcribe_provider: Literal["mock", "minimax", "tencent"] = "mock"
    speech_synthesize_provider: Literal["mock", "minimax"] = "mock"

    text_model: str = "MiniMax-M2.5"
    minimax_api_key: str | None = None
    minimax_text_base_url: str = "https://api.minimaxi.com/v1"

    minimax_speech_base_url: str = "https://api.minimaxi.com/v1"
    minimax_tts_model: str = "speech-2.8-turbo"
    minimax_tts_voice_id: str = "Chinese (Mandarin)_Warm_Bestie"
    minimax_asr_endpoint: str | None = None
    http_trust_env: bool = True
    conversation_log_path: str = "/app/logs/conversation-events.jsonl"

    tencent_asr_secret_id: str | None = None
    tencent_asr_secret_key: str | None = None
    tencent_asr_region: str = "ap-shanghai"
    tencent_asr_engine_type: str = "16k_zh"
    tencent_asr_endpoint: str = "asr.tencentcloudapi.com"
    tencent_asr_filter_dirty: int = 0
    tencent_asr_filter_modal: int = 0
    tencent_asr_filter_punc: int = 0
    tencent_asr_convert_num_mode: int = 1


@lru_cache
def get_settings() -> Settings:
    return Settings()

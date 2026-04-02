import json
import logging
import time

from fastapi import HTTPException
from tencentcloud.asr.v20190614 import asr_client, models
from tencentcloud.common import credential
from tencentcloud.common.exception.tencent_cloud_sdk_exception import TencentCloudSDKException
from tencentcloud.common.profile.client_profile import ClientProfile
from tencentcloud.common.profile.http_profile import HttpProfile

from app.config import Settings
from app.models import TranscribeAudioRequest, TranscribeAudioResponse
from app.providers.base import SpeechTranscribeProvider

logger = logging.getLogger("nightly-pick-agent.tencent-asr")


class TencentASRProvider(SpeechTranscribeProvider):
    def __init__(self, settings: Settings):
        if not settings.tencent_asr_secret_id or not settings.tencent_asr_secret_key:
            raise ValueError(
                "TENCENT_ASR_SECRET_ID and TENCENT_ASR_SECRET_KEY are required when SPEECH_TRANSCRIBE_PROVIDER=tencent"
            )
        self.settings = settings
        http_profile = HttpProfile(endpoint=settings.tencent_asr_endpoint)
        client_profile = ClientProfile(httpProfile=http_profile)
        cred = credential.Credential(settings.tencent_asr_secret_id, settings.tencent_asr_secret_key)
        self.client = asr_client.AsrClient(cred, settings.tencent_asr_region, client_profile)

    async def transcribe(self, request: TranscribeAudioRequest) -> TranscribeAudioResponse:
        started_at = time.perf_counter()
        sdk_request = models.SentenceRecognitionRequest()
        sdk_request.from_json_string(
            json.dumps(
                {
                    "ProjectId": 0,
                    "SubServiceType": 2,
                    "EngSerViceType": self.settings.tencent_asr_engine_type,
                    "SourceType": 0,
                    "VoiceFormat": self._resolve_voice_format(request.audio_url),
                    "Url": request.audio_url,
                    "FilterDirty": self.settings.tencent_asr_filter_dirty,
                    "FilterModal": self.settings.tencent_asr_filter_modal,
                    "FilterPunc": self.settings.tencent_asr_filter_punc,
                    "ConvertNumMode": self.settings.tencent_asr_convert_num_mode,
                }
            )
        )
        logger.info(
            "开始请求腾讯云一句话识别 url=%s engine=%s region=%s",
            request.audio_url,
            self.settings.tencent_asr_engine_type,
            self.settings.tencent_asr_region,
        )
        try:
            response = self.client.SentenceRecognition(sdk_request)
        except TencentCloudSDKException as exc:
            elapsed_ms = round((time.perf_counter() - started_at) * 1000, 2)
            logger.error("腾讯云一句话识别失败 elapsedMs=%s error=%s", elapsed_ms, exc)
            raise HTTPException(status_code=502, detail=f"腾讯云 ASR 请求失败: {exc}") from exc

        elapsed_ms = round((time.perf_counter() - started_at) * 1000, 2)
        transcript = (response.Result or "").strip()
        logger.info(
            "腾讯云一句话识别完成 elapsedMs=%s transcriptLength=%s requestId=%s",
            elapsed_ms,
            len(transcript),
            response.RequestId,
        )
        if not transcript:
            raise HTTPException(status_code=502, detail="腾讯云 ASR 未返回有效转写结果")
        return TranscribeAudioResponse(transcript_text=transcript)

    def _resolve_voice_format(self, audio_url: str) -> str:
        normalized = (audio_url or "").lower().split("?", 1)[0]
        for extension in ("wav", "pcm", "ogg-opus", "speex", "silk", "mp3", "m4a", "aac", "amr"):
            if normalized.endswith("." + extension):
                return extension
        return "mp3"

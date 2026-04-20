import json
import logging
from datetime import datetime, timezone
from typing import Any

from app.request_context import get_request_context

conversation_logger = logging.getLogger("nightly-pick-agent.conversation")

EVENT_MESSAGES = {
    "chat_reply": "模型回复完成",
    "speech_transcribe": "语音转写完成",
    "speech_synthesize": "语音合成完成",
}


def emit_timing(event: str, **payload: Any) -> None:
    record = {
        "timestamp": datetime.now(timezone.utc).astimezone().isoformat(),
        "source": "agent",
        "event": event,
        "message": EVENT_MESSAGES.get(event, event),
        **get_request_context(),
        **payload,
    }
    conversation_logger.info(json.dumps(record, ensure_ascii=False, separators=(",", ":")))

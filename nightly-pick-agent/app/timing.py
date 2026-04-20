import json
import logging
from typing import Any

from app.request_context import get_request_context

timing_logger = logging.getLogger("nightly-pick-agent.timing")


def emit_timing(event: str, **payload: Any) -> None:
    record = {
        "event": event,
        **get_request_context(),
        **payload,
    }
    timing_logger.info(json.dumps(record, ensure_ascii=False, separators=(",", ":")))

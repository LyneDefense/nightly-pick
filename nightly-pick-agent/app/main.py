import logging
import time
import uuid
from pathlib import Path

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.config import get_settings
from app.routes.chat import router as chat_router
from app.routes.health import router as health_router
from app.routes.memory import router as memory_router
from app.routes.record import router as record_router
from app.routes.speech import router as speech_router
from app.request_context import reset_request_context, set_request_context
from app.system import warmup_dependencies

app = FastAPI(title="nightly-pick-agent", version="0.2.0")
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s - %(message)s",
)
logger = logging.getLogger("nightly-pick-agent")
request_logger = logging.getLogger("nightly-pick-agent.request")
conversation_logger = logging.getLogger("nightly-pick-agent.conversation")


def configure_conversation_logging() -> None:
    settings = get_settings()
    log_path = Path(settings.conversation_log_path)
    log_path.parent.mkdir(parents=True, exist_ok=True)
    conversation_logger.setLevel(logging.INFO)
    conversation_logger.propagate = False
    if any(getattr(handler, "baseFilename", None) == str(log_path) for handler in conversation_logger.handlers):
        return
    handler = logging.FileHandler(log_path, encoding="utf-8")
    handler.setFormatter(logging.Formatter("%(message)s"))
    conversation_logger.addHandler(handler)


@app.on_event("startup")
def startup() -> None:
    configure_conversation_logging()
    warmup_dependencies()
    logger.info("夜拾 Agent 启动完成，依赖预热结束。")


@app.middleware("http")
async def log_requests(request: Request, call_next):
    raw_body = await request.body()
    request_id = request.headers.get("x-request-id") or str(uuid.uuid4())
    session_id = request.headers.get("x-session-id") or "-"
    trace_id = request.headers.get("x-trace-id") or str(uuid.uuid4())
    business_date = request.headers.get("x-business-date") or "-"
    request.state.request_id = request_id
    request.state.session_id = session_id
    request.state.trace_id = trace_id
    request.state.business_date = business_date
    context_tokens = set_request_context(request_id, session_id, trace_id, business_date)
    started_at = time.perf_counter()
    request_logger.info(
        "收到请求 requestId=%s sessionId=%s traceId=%s businessDate=%s method=%s path=%s contentType=%s body=%s",
        request_id,
        session_id,
        trace_id,
        business_date,
        request.method,
        request.url.path,
        request.headers.get("content-type"),
        raw_body.decode("utf-8", errors="ignore"),
    )
    try:
        response = await call_next(request)
        elapsed_ms = round((time.perf_counter() - started_at) * 1000, 2)
        request_logger.info(
            "请求处理完成 requestId=%s sessionId=%s traceId=%s businessDate=%s method=%s path=%s status=%s elapsedMs=%s",
            request_id,
            session_id,
            trace_id,
            business_date,
            request.method,
            request.url.path,
            response.status_code,
            elapsed_ms,
        )
        response.headers["x-request-id"] = request_id
        response.headers["x-trace-id"] = trace_id
        return response
    finally:
        reset_request_context(context_tokens)


@app.exception_handler(RequestValidationError)
async def handle_validation_error(request: Request, exc: RequestValidationError):
    raw_body = await request.body()
    logger.error(
        "请求参数校验失败 requestId=%s sessionId=%s traceId=%s businessDate=%s path=%s errors=%s body=%s",
        getattr(request.state, "request_id", "unknown"),
        getattr(request.state, "session_id", "-"),
        getattr(request.state, "trace_id", "-"),
        getattr(request.state, "business_date", "-"),
        request.url.path,
        exc.errors(),
        raw_body.decode("utf-8", errors="ignore"),
    )
    return JSONResponse(status_code=422, content={"detail": exc.errors()})

app.include_router(health_router)
app.include_router(chat_router, prefix="/chat", tags=["chat"])
app.include_router(record_router, prefix="/record", tags=["record"])
app.include_router(memory_router, prefix="/memory", tags=["memory"])
app.include_router(speech_router, prefix="/speech", tags=["speech"])

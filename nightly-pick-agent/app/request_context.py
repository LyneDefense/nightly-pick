from contextvars import ContextVar

request_id_var: ContextVar[str] = ContextVar("request_id", default="-")
session_id_var: ContextVar[str] = ContextVar("session_id", default="-")
trace_id_var: ContextVar[str] = ContextVar("trace_id", default="-")


def set_request_context(request_id: str, session_id: str, trace_id: str) -> tuple[object, object, object]:
    request_token = request_id_var.set(request_id or "-")
    session_token = session_id_var.set(session_id or "-")
    trace_token = trace_id_var.set(trace_id or "-")
    return request_token, session_token, trace_token


def reset_request_context(tokens: tuple[object, object, object]) -> None:
    request_token, session_token, trace_token = tokens
    request_id_var.reset(request_token)
    session_id_var.reset(session_token)
    trace_id_var.reset(trace_token)


def get_request_context() -> dict[str, str]:
    return {
        "request_id": request_id_var.get("-"),
        "session_id": session_id_var.get("-"),
        "trace_id": trace_id_var.get("-"),
    }

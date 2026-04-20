from contextvars import ContextVar

request_id_var: ContextVar[str] = ContextVar("request_id", default="-")
session_id_var: ContextVar[str] = ContextVar("session_id", default="-")
trace_id_var: ContextVar[str] = ContextVar("trace_id", default="-")
business_date_var: ContextVar[str] = ContextVar("business_date", default="-")


def set_request_context(
    request_id: str,
    session_id: str,
    trace_id: str,
    business_date: str | None = None,
) -> tuple[object, object, object, object]:
    request_token = request_id_var.set(request_id or "-")
    session_token = session_id_var.set(session_id or "-")
    trace_token = trace_id_var.set(trace_id or "-")
    business_token = business_date_var.set(business_date or "-")
    return request_token, session_token, trace_token, business_token


def reset_request_context(tokens: tuple[object, object, object, object]) -> None:
    request_token, session_token, trace_token, business_token = tokens
    request_id_var.reset(request_token)
    session_id_var.reset(session_token)
    trace_id_var.reset(trace_token)
    business_date_var.reset(business_token)


def get_request_context() -> dict[str, str]:
    return {
        "requestId": request_id_var.get("-"),
        "sessionId": session_id_var.get("-"),
        "traceId": trace_id_var.get("-"),
        "businessDate": business_date_var.get("-"),
    }

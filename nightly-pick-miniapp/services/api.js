import { API_BASE_URL, MINIAPP_BUILD_STAMP } from "../config"
import { clearAuthSession, getAccessToken } from "./session"
import { logError, logInfo } from "../utils/logger"

function buildRequestId() {
  return `miniapp-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

export function request(options) {
  const requestUrl = `${API_BASE_URL}${options.url}`
  const startedAt = Date.now()
  const requestId = options.requestId || buildRequestId()
  const headers = {
    ...(options.header || {}),
    "X-Request-Id": requestId,
  }
  if (options.sessionId) {
    headers["X-Session-Id"] = options.sessionId
  }
  if (options.traceId) {
    headers["X-Trace-Id"] = options.traceId
  }
  const accessToken = getAccessToken()
  if (accessToken) {
    headers.Authorization = `Bearer ${accessToken}`
  }
  logInfo("网络请求", "开始请求接口", {
    buildStamp: MINIAPP_BUILD_STAMP,
    requestId,
    traceId: options.traceId || null,
    sessionId: options.sessionId || null,
    method: options.method || "GET",
    url: requestUrl,
    data: options.data || null,
  })
  return new Promise((resolve, reject) => {
    uni.request({
      ...options,
      url: requestUrl,
      header: headers,
      timeout: 60000,
      success: (response) => {
        const data = response.data || {}
        logInfo("网络请求", "接口请求完成", {
          buildStamp: MINIAPP_BUILD_STAMP,
          requestId,
          traceId: options.traceId || null,
          sessionId: options.sessionId || null,
          statusCode: response.statusCode,
          url: requestUrl,
          elapsedMs: Date.now() - startedAt,
          data,
        })
        if (response.statusCode && response.statusCode >= 400) {
          const errorMessage = data.message || data.error || "请求失败"
          const error = new Error(errorMessage)
          error.statusCode = response.statusCode
          error.responseData = data
          if (response.statusCode === 401) {
            clearAuthSession()
          }
          reject(error)
          return
        }
        resolve(data.data)
      },
      fail: (error) => {
        logError("网络请求", "接口请求失败", {
          buildStamp: MINIAPP_BUILD_STAMP,
          requestId,
          traceId: options.traceId || null,
          sessionId: options.sessionId || null,
          url: requestUrl,
          elapsedMs: Date.now() - startedAt,
          error,
        })
        reject(new Error("网络异常，请检查服务是否启动"))
      },
    })
  })
}

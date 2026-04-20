import { API_BASE_URL, MINIAPP_BUILD_STAMP } from "../config"
import { logError, logInfo } from "../utils/logger"
import { request } from "./api"

function buildRequestId() {
  return `miniapp-audio-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

export function transcribeAudio(sessionId, audioUrl, traceId) {
  return request({
    url: "/audio/transcribe",
    method: "POST",
    sessionId,
    traceId,
    data: { sessionId, audioUrl },
  })
}

export function uploadAudio(sessionId, filePath, traceId) {
  const requestUrl = `${API_BASE_URL}/audio/upload`
  const requestId = buildRequestId()
  const startedAt = Date.now()

  logInfo("音频上传", "开始上传录音文件", {
    buildStamp: MINIAPP_BUILD_STAMP,
    requestId,
    traceId: traceId || null,
    sessionId,
    url: requestUrl,
    filePath,
  })

  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: requestUrl,
      filePath,
      name: "file",
      formData: { sessionId },
      header: {
        "X-Request-Id": requestId,
        "X-Session-Id": sessionId,
        ...(traceId ? { "X-Trace-Id": traceId } : {}),
      },
      timeout: 60000,
      success: (response) => {
        let data = {}
        try {
          data = response.data ? JSON.parse(response.data) : {}
        } catch (error) {
          logError("音频上传", "解析上传响应失败", {
            buildStamp: MINIAPP_BUILD_STAMP,
            requestId,
            traceId: traceId || null,
            sessionId,
            url: requestUrl,
            elapsedMs: Date.now() - startedAt,
            raw: response.data,
            error,
          })
          reject(new Error("录音上传响应格式错误"))
          return
        }

        logInfo("音频上传", "录音文件上传完成", {
          buildStamp: MINIAPP_BUILD_STAMP,
          requestId,
          traceId: traceId || null,
          sessionId,
          statusCode: response.statusCode,
          url: requestUrl,
          elapsedMs: Date.now() - startedAt,
          data,
        })

        if (response.statusCode && response.statusCode >= 400) {
          reject(new Error(data.message || data.error || "录音上传失败"))
          return
        }

        resolve(data.data)
      },
      fail: (error) => {
        logError("音频上传", "录音文件上传失败", {
          buildStamp: MINIAPP_BUILD_STAMP,
          requestId,
          traceId: traceId || null,
          sessionId,
          url: requestUrl,
          elapsedMs: Date.now() - startedAt,
          error,
        })
        reject(new Error("录音上传失败，请检查网络或服务配置"))
      },
    })
  })
}

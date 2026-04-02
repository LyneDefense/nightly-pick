import { request } from "./api"

export function transcribeAudio(sessionId, fileName) {
  return request({
    url: "/audio/transcribe",
    method: "POST",
    data: { sessionId, fileName },
  })
}

export function uploadAudio(sessionId, fileName) {
  return request({
    url: "/audio/upload",
    method: "POST",
    data: { sessionId, fileName },
  })
}

import { request } from "./api"

export function createConversation() {
  return request({
    url: "/conversations",
    method: "POST",
  })
}

export function sendMessage(sessionId, text, inputType) {
  return request({
    url: `/conversations/${sessionId}/messages`,
    method: "POST",
    sessionId,
    data: { text, inputType: inputType || "text" },
  })
}

export function completeConversation(sessionId) {
  return request({
    url: `/conversations/${sessionId}/complete`,
    method: "POST",
    sessionId,
  })
}

export function requestConversationSummary(sessionId) {
  return request({
    url: `/conversations/${sessionId}/summary`,
    method: "POST",
    sessionId,
  })
}

export function autosaveConversation(sessionId) {
  return request({
    url: `/conversations/${sessionId}/autosave`,
    method: "POST",
    sessionId,
  })
}

export function getConversation(sessionId) {
  return request({
    url: `/conversations/${sessionId}`,
    method: "GET",
    sessionId,
  })
}

export function getActiveConversation() {
  return request({
    url: "/conversations/active",
    method: "GET",
  })
}

export function getConversationHistory() {
  return request({
    url: "/conversations/history/list",
    method: "GET",
  })
}

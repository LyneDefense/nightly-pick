import Vue from "vue"

function createInitialMessages() {
  return [
    {
      role: "assistant",
      text: "晚上好。准备好把今天的故事收起来了吗？你想聊聊哪一部分？",
      timeLabel: "21:30",
    },
  ]
}

export const appState = Vue.observable({
  user: null,
  records: [],
  memories: [],
  activeSessionId: "",
  activeSessionStatus: "",
  activeSessionStartedAt: "",
  conversationDraft: {
    userHasSpoken: false,
    lastAutoSavedMessageCount: 0,
  },
  chatState: {
    inputValue: "",
    messages: createInitialMessages(),
  },
})

export function setUser(user) {
  appState.user = user || null
}

export function setRecords(records) {
  appState.records = Array.isArray(records) ? records : []
}

export function upsertRecord(record) {
  if (!record || !record.id) return
  const next = [...appState.records]
  const index = next.findIndex((item) => item.id === record.id)
  if (index >= 0) {
    next.splice(index, 1, record)
  } else {
    next.unshift(record)
  }
  appState.records = next
}

export function removeRecord(recordId) {
  appState.records = appState.records.filter((item) => item.id !== recordId)
}

export function setMemories(memories) {
  appState.memories = Array.isArray(memories) ? memories : []
}

export function setActiveSessionId(sessionId) {
  appState.activeSessionId = sessionId || ""
}

export function setActiveSessionStatus(status) {
  appState.activeSessionStatus = status || ""
}

export function setActiveSessionStartedAt(value) {
  appState.activeSessionStartedAt = value || ""
}

export function resetConversationDraft() {
  appState.conversationDraft = {
    userHasSpoken: false,
    lastAutoSavedMessageCount: 0,
  }
}

export function updateConversationDraft(patch = {}) {
  appState.conversationDraft = {
    ...appState.conversationDraft,
    ...patch,
  }
}

export function setChatInput(inputValue) {
  appState.chatState = {
    ...appState.chatState,
    inputValue: inputValue || "",
  }
}

export function setChatMessages(messages) {
  const normalized = Array.isArray(messages) && messages.length ? messages : createInitialMessages()
  appState.chatState = {
    ...appState.chatState,
    messages: normalized,
  }
}

export function appendChatMessage(message) {
  if (!message || !message.role || !message.text) return
  appState.chatState = {
    ...appState.chatState,
    messages: [...appState.chatState.messages, message],
  }
}

export function resetChatState() {
  appState.chatState = {
    inputValue: "",
    messages: createInitialMessages(),
  }
}

export function hydrateConversation(session, messages = []) {
  setActiveSessionId(session && session.id)
  setActiveSessionStatus(session && session.status)
  setActiveSessionStartedAt(session && session.startedAt)
  setChatMessages(messages)
  const normalizedMessages = Array.isArray(messages) ? messages : []
  updateConversationDraft({
    userHasSpoken: normalizedMessages.some((message) => message.role === "user" && message.text),
    lastAutoSavedMessageCount: normalizedMessages.length,
  })
}

export function clearActiveConversation() {
  setActiveSessionId("")
  setActiveSessionStatus("")
  setActiveSessionStartedAt("")
  resetConversationDraft()
  resetChatState()
}

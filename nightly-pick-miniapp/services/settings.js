import { request } from "./api"

export function getSettings() {
  return request({
    url: "/me/settings",
    method: "GET",
  })
}

export function updateSettings(allowMemoryReference) {
  return request({
    url: "/me/settings",
    method: "PATCH",
    data: { allowMemoryReference },
  })
}

export function clearMemories() {
  return request({
    url: "/me/clear-memories",
    method: "POST",
  })
}

export function clearHistory() {
  return request({
    url: "/me/clear-history",
    method: "POST",
  })
}

export function getMemories() {
  return request({
    url: "/memories",
    method: "GET",
  })
}

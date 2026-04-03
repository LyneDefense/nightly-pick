export const MINIAPP_BUILD_STAMP = "miniapp-debug-20260401-1919"
export const MINIAPP_LOG_ENABLED =
  typeof globalThis !== "undefined" && typeof globalThis.__ENABLE_NIGHTLY_PICK_LOG__ !== "undefined"
    ? Boolean(globalThis.__ENABLE_NIGHTLY_PICK_LOG__)
    : MINIAPP_BUILD_STAMP.includes("debug")

const DEFAULT_ONLINE_BASE_URL = "https://enceladus.online/nightly"
const DEFAULT_BASE_URL =
  typeof globalThis !== "undefined" && globalThis.__DEFAULT_NIGHTLY_PICK_API_BASE_URL__
    ? globalThis.__DEFAULT_NIGHTLY_PICK_API_BASE_URL__
    : DEFAULT_ONLINE_BASE_URL

function resolveBaseUrl() {
  if (typeof globalThis !== "undefined" && globalThis.__API_BASE_URL__) {
    return globalThis.__API_BASE_URL__
  }
  return DEFAULT_BASE_URL
}

export const API_BASE_URL = resolveBaseUrl()

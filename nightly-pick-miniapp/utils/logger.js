import { MINIAPP_BUILD_STAMP, MINIAPP_LOG_ENABLED } from "../config"

function scopeLabel(scope) {
  return scope ? `[夜拾小程序][${scope}]` : "[夜拾小程序]"
}

export function logInfo(scope, message, payload) {
  if (!MINIAPP_LOG_ENABLED) return
  console.log(scopeLabel(scope), MINIAPP_BUILD_STAMP, message, payload === undefined ? "" : payload)
}

export function logWarn(scope, message, payload) {
  if (!MINIAPP_LOG_ENABLED) return
  console.warn(scopeLabel(scope), MINIAPP_BUILD_STAMP, message, payload === undefined ? "" : payload)
}

export function logError(scope, message, payload) {
  if (!MINIAPP_LOG_ENABLED) return
  console.error(scopeLabel(scope), MINIAPP_BUILD_STAMP, message, payload === undefined ? "" : payload)
}

import { loginWithWechatPhone } from "../services/auth"
import { getStoredUser, isAuthenticated, setAuthSession } from "../services/session"
import { setUser } from "../stores/app-state"
import { logError, logInfo, logWarn } from "../utils/logger"

export function restoreAuthState() {
  const user = getStoredUser()
  if (user) {
    setUser(user)
  } else if (!isAuthenticated()) {
    setAuthSession("demo-user", null)
  }
  return true
}

export function getWechatLoginCode() {
  return new Promise((resolve, reject) => {
    if (typeof uni.login !== "function") {
      reject(new Error("当前环境不支持微信登录"))
      return
    }
    uni.login({
      provider: "weixin",
      success: (result) => {
        if (result && result.code) {
          resolve(result.code)
          return
        }
        reject(new Error("没有拿到微信登录 code"))
      },
      fail: () => reject(new Error("微信登录失败")),
    })
  })
}

export async function loginFromPhoneDetail(detail) {
  logInfo("手机号登录", "收到 getPhoneNumber 回调", {
    hasDetail: Boolean(detail),
    errMsg: detail && detail.errMsg ? detail.errMsg : null,
    hasPhoneCode: Boolean(detail && detail.code),
    hasEncryptedData: Boolean(detail && detail.encryptedData),
    hasIv: Boolean(detail && detail.iv),
  })
  if (!detail) {
    logWarn("手机号登录", "回调 detail 为空")
    throw new Error("手机号授权失败")
  }
  const loginCode = await getWechatLoginCode().catch((error) => {
    logError("手机号登录", "获取微信登录 code 失败", {
      message: error && error.message ? error.message : String(error),
    })
    throw error
  })
  logInfo("手机号登录", "已获取微信登录 code")
  const hasPhoneCode = Boolean(detail.code)
  const hasLegacyPayload = Boolean(detail.encryptedData && detail.iv)
  if (!hasPhoneCode && !hasLegacyPayload) {
    logWarn("手机号登录", "回调未包含可用手机号授权信息", {
      errMsg: detail.errMsg || null,
    })
    throw new Error("手机号授权失败")
  }
  try {
    const response = await loginWithWechatPhone({
      loginCode,
      phoneCode: detail.code || "",
      encryptedData: detail.encryptedData || "",
      iv: detail.iv || "",
    })
    logInfo("手机号登录", "手机号登录成功", {
      userId: response && response.user ? response.user.id : null,
    })
    if (response && response.user) {
      setUser(response.user)
    }
    return response
  } catch (error) {
    logError("手机号登录", "手机号登录请求失败", {
      message: error && error.message ? error.message : String(error),
      statusCode: error && error.statusCode ? error.statusCode : null,
      responseData: error && error.responseData ? error.responseData : null,
    })
    throw error
  }
}

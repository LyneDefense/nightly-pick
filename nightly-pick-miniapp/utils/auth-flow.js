import { loginWithWechatPhone } from "../services/auth"
import { getStoredUser, isAuthenticated } from "../services/session"
import { setUser } from "../stores/app-state"

export function restoreAuthState() {
  const user = getStoredUser()
  if (user) {
    setUser(user)
  }
  return Boolean(user && isAuthenticated())
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
  if (!detail) {
    throw new Error("手机号授权失败")
  }
  const loginCode = await getWechatLoginCode()
  const hasPhoneCode = Boolean(detail.code)
  const hasLegacyPayload = Boolean(detail.encryptedData && detail.iv)
  if (!hasPhoneCode && !hasLegacyPayload) {
    throw new Error("手机号授权失败")
  }
  const response = await loginWithWechatPhone({
    loginCode,
    phoneCode: detail.code || "",
    encryptedData: detail.encryptedData || "",
    iv: detail.iv || "",
  })
  if (response && response.user) {
    setUser(response.user)
  }
  return response
}

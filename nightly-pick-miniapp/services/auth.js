import { request } from "./api"
import { setAuthSession } from "./session"

export function login(nickname) {
  return request({
    url: "/auth/login",
    method: "POST",
    data: { nickname: nickname || "夜拾用户" },
  }).then((response) => {
    setAuthSession(response && response.token, response && response.user)
    return response
  })
}

export function loginWithWechatPhone({ loginCode, phoneCode, encryptedData, iv, avatarUrl } = {}) {
  return request({
    url: "/auth/wechat-phone-login",
    method: "POST",
    data: { loginCode, phoneCode, encryptedData, iv, avatarUrl },
  }).then((response) => {
    setAuthSession(response && response.token, response && response.user)
    return response
  })
}

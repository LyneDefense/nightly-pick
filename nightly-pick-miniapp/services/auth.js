import { request } from "./api"

export function login(nickname) {
  return request({
    url: "/auth/login",
    method: "POST",
    data: { nickname: nickname || "夜拾用户" },
  })
}

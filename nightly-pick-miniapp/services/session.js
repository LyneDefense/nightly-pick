const ACCESS_TOKEN_KEY = "nightly-pick:access-token"
const USER_KEY = "nightly-pick:user"

export function getAccessToken() {
  try {
    return uni.getStorageSync(ACCESS_TOKEN_KEY) || ""
  } catch (error) {
    return ""
  }
}

export function getStoredUser() {
  try {
    return uni.getStorageSync(USER_KEY) || null
  } catch (error) {
    return null
  }
}

export function setAuthSession(token, user) {
  try {
    uni.setStorageSync(ACCESS_TOKEN_KEY, token || "")
    uni.setStorageSync(USER_KEY, user || null)
  } catch (error) {
    // Storage failure should not block the in-memory login state.
  }
}

export function clearAuthSession() {
  try {
    uni.removeStorageSync(ACCESS_TOKEN_KEY)
    uni.removeStorageSync(USER_KEY)
  } catch (error) {
    // Ignore storage cleanup failures.
  }
}

export function isAuthenticated() {
  return Boolean(getAccessToken())
}

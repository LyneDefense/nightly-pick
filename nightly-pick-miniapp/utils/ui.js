export function showError(message) {
  uni.showToast({
    title: message,
    icon: "none",
    duration: 2200,
  })
}

export function showSuccess(message) {
  uni.showToast({
    title: message,
    icon: "success",
    duration: 1800,
  })
}

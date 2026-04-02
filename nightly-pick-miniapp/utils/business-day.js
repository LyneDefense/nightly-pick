function toDate(value) {
  if (!value) return null
  const date = value instanceof Date ? value : new Date(value)
  return Number.isNaN(date.getTime()) ? null : date
}

function normalizeResetHour(resetHour) {
  const hour = Number(resetHour)
  if (Number.isNaN(hour)) return null
  if (hour < 0) return 0
  if (hour > 23) return 23
  return hour
}

export function getCurrentBusinessDate(resetHour, now = new Date()) {
  const date = toDate(now)
  const hour = normalizeResetHour(resetHour)
  if (!date || hour === null) return ""
  const businessDate = new Date(date)
  if (businessDate.getHours() < hour) {
    businessDate.setDate(businessDate.getDate() - 1)
  }
  const year = businessDate.getFullYear()
  const month = `${businessDate.getMonth() + 1}`.padStart(2, "0")
  const day = `${businessDate.getDate()}`.padStart(2, "0")
  return `${year}-${month}-${day}`
}

export function toBusinessDate(value, resetHour) {
  const date = toDate(value)
  if (!date) return ""
  return getCurrentBusinessDate(resetHour, date)
}

export function isCurrentBusinessDate(value, resetHour, now = new Date()) {
  const businessDate = toBusinessDate(value, resetHour)
  if (!businessDate) return false
  return businessDate === getCurrentBusinessDate(resetHour, now)
}

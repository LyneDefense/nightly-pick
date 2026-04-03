const TODAY_CARD_STORAGE_PREFIX = "nightly-pick:share-card:today:"
const SHARE_CARD_DRAFT_STORAGE_KEY = "nightly-pick:share-card:draft"

function safeStorageGet(key) {
  try {
    return uni.getStorageSync(key)
  } catch (error) {
    console.error("[nightly-pick][share-card] get storage failed", error)
    return ""
  }
}

function safeStorageSet(key, value) {
  try {
    uni.setStorageSync(key, value)
  } catch (error) {
    console.error("[nightly-pick][share-card] set storage failed", error)
  }
}

function safeStorageRemove(key) {
  try {
    uni.removeStorageSync(key)
  } catch (error) {
    console.error("[nightly-pick][share-card] remove storage failed", error)
  }
}

export function getTodayCardStorageKey(date) {
  return `${TODAY_CARD_STORAGE_PREFIX}${date || "unknown"}`
}

export function hasGeneratedTodayCard(date) {
  if (!date) return false
  return Boolean(safeStorageGet(getTodayCardStorageKey(date)))
}

export function markTodayCardGenerated(date, payload) {
  if (!date) return
  safeStorageSet(getTodayCardStorageKey(date), {
    generatedAt: Date.now(),
    cardType: "today",
    recordId: payload && payload.recordId ? payload.recordId : "",
  })
}

export function cacheShareCardDraft(payload) {
  safeStorageSet(SHARE_CARD_DRAFT_STORAGE_KEY, payload || null)
}

export function readShareCardDraft() {
  return safeStorageGet(SHARE_CARD_DRAFT_STORAGE_KEY) || null
}

export function clearShareCardDraft() {
  safeStorageRemove(SHARE_CARD_DRAFT_STORAGE_KEY)
}

export function formatTodayCardDate(date) {
  if (!date) return ""
  const [year, month, day] = String(date).split("-")
  if (!year || !month || !day) return date
  return `${month}.${day}`
}

export function formatRecentCardPeriod(date) {
  if (!date) return ""
  const [year, month] = String(date).split("-")
  if (!year || !month) return date
  return `${year}.${month}`
}

function sanitizeCardText(value) {
  return String(value || "")
    .replace(/\s+/g, " ")
    .replace(/<think\b[^>]*>[\s\S]*?(?:<\/think>|$)/gi, "")
    .trim()
}

function truncateCardText(value, maxLength) {
  const text = sanitizeCardText(value)
  if (text.length <= maxLength) return text
  return `${text.slice(0, maxLength).trim()}…`
}

export function buildTodayShareCardDraft(record) {
  if (!record) return null
  const highlight = sanitizeCardText(record.highlight)
  const summary = sanitizeCardText(record.summary)
  const title = sanitizeCardText(record.title)
  const emotions = Array.isArray(record.emotions) ? record.emotions.filter(Boolean) : []
  const headline = truncateCardText(highlight || title || "今夜的这一页，已经留了下来。", 28)
  const summarySource = summary || (emotions.length ? `今晚的情绪落在 ${emotions.slice(0, 2).join("、")} 之间。` : "")
  const subline = truncateCardText(summarySource || "把这段夜色替自己留住，也算是一种安放。", 72)
  return {
    type: "today",
    recordId: record.id,
    date: record.date,
    dateLabel: formatTodayCardDate(record.date),
    headline,
    subline,
  }
}

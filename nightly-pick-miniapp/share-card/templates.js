export const SHARE_CARD_CANVAS = {
  width: 1080,
  height: 1920,
}

export const TODAY_SHARE_CARD_TEMPLATES = [
  {
    id: "today-01",
    type: "today",
    background: "/static/share-cards/today/today-01.png",
    dateColor: "rgba(244, 243, 238, 0.84)",
    headlineColor: "#f5f3ec",
    sublineColor: "rgba(245, 243, 236, 0.72)",
    brandColor: "rgba(245, 243, 236, 0.54)",
    datePosition: { x: 112, y: 188 },
    headlineBox: { x: 112, y: 320, width: 764, lineHeight: 118, fontSize: 92 },
    sublineBox: { x: 120, y: 1210, width: 820, lineHeight: 58, fontSize: 36 },
    brandPosition: { x: 120, y: 1760 },
  },
]

export const RECENT_SHARE_CARD_TEMPLATES = [
  {
    id: "recent-01",
    type: "recent",
    background: "/static/share-cards/recent/recent-01.png",
    dateColor: "rgba(244, 243, 238, 0.8)",
    headlineColor: "#f5f3ec",
    sublineColor: "rgba(245, 243, 236, 0.72)",
    brandColor: "rgba(245, 243, 236, 0.54)",
    datePosition: { x: 112, y: 188 },
    headlineBox: { x: 112, y: 318, width: 790, lineHeight: 110, fontSize: 88 },
    sublineBox: { x: 120, y: 1210, width: 820, lineHeight: 58, fontSize: 36 },
    brandPosition: { x: 120, y: 1760 },
  },
]

export function getShareCardTemplate(type = "today") {
  const templates = type === "recent" ? RECENT_SHARE_CARD_TEMPLATES : TODAY_SHARE_CARD_TEMPLATES
  return templates[0]
}

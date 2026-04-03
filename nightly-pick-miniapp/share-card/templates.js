import todayCardBackground from "../static/share-cards/today/today-01.png"
import recentCardBackground from "../static/share-cards/recent/recent-01.png"

export const SHARE_CARD_CANVAS = {
  width: 1080,
  height: 1920,
}

export const TODAY_SHARE_CARD_TEMPLATES = [
  {
    id: "today-01",
    type: "today",
    background: todayCardBackground,
    dateColor: "rgba(244, 243, 238, 0.84)",
    headlineColor: "#f5f3ec",
    sublineColor: "rgba(245, 243, 236, 0.72)",
    brandColor: "rgba(245, 243, 236, 0.54)",
    datePosition: { x: 112, y: 196 },
    headlineBox: { x: 112, y: 312, width: 856, lineHeight: 92, fontSize: 70, maxLines: 3 },
    sublineBox: { x: 120, y: 1278, width: 840, lineHeight: 52, fontSize: 34, maxLines: 5 },
    brandPosition: { x: 120, y: 1760 },
  },
]

export const RECENT_SHARE_CARD_TEMPLATES = [
  {
    id: "recent-01",
    type: "recent",
    background: recentCardBackground,
    dateColor: "rgba(244, 243, 238, 0.8)",
    headlineColor: "#f5f3ec",
    sublineColor: "rgba(245, 243, 236, 0.72)",
    brandColor: "rgba(245, 243, 236, 0.54)",
    datePosition: { x: 112, y: 196 },
    headlineBox: { x: 112, y: 312, width: 856, lineHeight: 92, fontSize: 70, maxLines: 3 },
    sublineBox: { x: 120, y: 1278, width: 840, lineHeight: 52, fontSize: 34, maxLines: 5 },
    brandPosition: { x: 120, y: 1760 },
  },
]

export function getShareCardTemplate(type = "today") {
  const templates = type === "recent" ? RECENT_SHARE_CARD_TEMPLATES : TODAY_SHARE_CARD_TEMPLATES
  return templates[0]
}

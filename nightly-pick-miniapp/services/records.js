import { request } from "./api"

export function getRecords() {
  return request({
    url: "/records",
    method: "GET",
  })
}

export function getRecord(recordId) {
  return request({
    url: `/records/${recordId}`,
    method: "GET",
  })
}

export function generateShareCard(recordId, cardType) {
  return request({
    url: `/records/${recordId}/share-card`,
    method: "POST",
    data: { cardType },
  })
}

export function updateRecord(recordId, payload) {
  return request({
    url: `/records/${recordId}`,
    method: "PATCH",
    data: payload,
  })
}

export function deleteRecord(recordId) {
  return request({
    url: `/records/${recordId}`,
    method: "DELETE",
  })
}

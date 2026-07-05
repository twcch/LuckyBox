const VISITOR_ID_KEY = 'luckybox.visitorId'

export function getVisitorId() {
  if (typeof window === 'undefined') {
    return ''
  }
  const existing = window.localStorage.getItem(VISITOR_ID_KEY)
  if (existing) {
    return existing
  }
  const visitorId = createVisitorId()
  window.localStorage.setItem(VISITOR_ID_KEY, visitorId)
  return visitorId
}

function createVisitorId() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return `web-${crypto.randomUUID()}`
  }
  return `web-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 14)}`
}

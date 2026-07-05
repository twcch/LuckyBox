import { createApiClient } from '@/services/apiClient'
import { getVisitorId } from '@/services/visitorId'

const api = createApiClient()

export async function recordVisit(path) {
  const response = await api.post('/analytics/visit', {
    visitorId: getVisitorId(),
    path: normalizePath(path),
  })
  return response.data
}

function normalizePath(path) {
  if (!path) {
    return '/'
  }
  return path.length <= 240 ? path : path.slice(0, 240)
}

import { createApiClient, compactParams } from '@/services/apiClient'

const api = createApiClient()

export async function fetchAdminNews(params = {}) {
  const response = await api.get('/admin/news', { params: compactParams(params) })
  return response.data
}

export async function createAdminNews(payload) {
  const response = await api.post('/admin/news', payload)
  return response.data
}

export async function updateAdminNews(newsId, payload) {
  const response = await api.patch(`/admin/news/${newsId}`, payload)
  return response.data
}

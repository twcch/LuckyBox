import { createApiClient } from '@/services/apiClient'

const api = createApiClient()

export async function fetchNews() {
  const response = await api.get('/news')
  return response.data
}

export async function fetchNewsDetail(slug) {
  const response = await api.get(`/news/${slug}`)
  return response.data
}

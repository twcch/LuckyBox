import { createApiClient } from '@/services/apiClient'

const api = createApiClient()

export async function fetchBanners(params = {}) {
  const response = await api.get('/banners', {
    params,
  })
  return response.data
}

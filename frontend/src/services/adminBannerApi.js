import { createApiClient, compactParams } from '@/services/apiClient'

const api = createApiClient()

export async function fetchAdminBanners(params = {}) {
  const response = await api.get('/admin/banners', {
    params: compactParams(params),
  })
  return response.data
}

export async function createAdminBanner(payload) {
  const response = await api.post('/admin/banners', payload)
  return response.data
}

export async function updateAdminBanner(bannerId, payload) {
  const response = await api.patch(`/admin/banners/${bannerId}`, payload)
  return response.data
}

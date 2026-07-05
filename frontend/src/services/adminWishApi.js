import { createApiClient } from '@/services/apiClient'

const api = createApiClient()

export async function fetchAdminWishes(params = {}) {
  const response = await api.get('/admin/wishes', { params })
  return response.data
}

export async function moderateWish(wishId, payload) {
  const response = await api.patch(`/admin/wishes/${wishId}`, payload)
  return response.data
}

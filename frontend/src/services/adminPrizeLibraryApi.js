import { createApiClient, compactParams } from '@/services/apiClient'

const api = createApiClient()

export async function fetchAdminPrizes(params = {}) {
  const response = await api.get('/admin/prizes', { params: compactParams(params) })
  return response.data
}

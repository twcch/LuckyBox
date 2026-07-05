import { createApiClient } from '@/services/apiClient'

const api = createApiClient()

export async function fetchVipStatus() {
  const response = await api.get('/account/vip')
  return response.data
}

import { createApiClient } from '@/services/apiClient'

const api = createApiClient()

export async function createDrawOrder(payload) {
  const response = await api.post('/account/draw-orders', payload)
  return response.data
}

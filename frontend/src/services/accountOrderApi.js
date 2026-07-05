import { createApiClient } from '@/services/apiClient'

const api = createApiClient()

export async function fetchAccountOrders() {
  const response = await api.get('/account/orders')
  return response.data
}

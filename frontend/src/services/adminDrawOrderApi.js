import { createApiClient, compactParams } from '@/services/apiClient'

const api = createApiClient()

export async function fetchAdminDrawOrders(params = {}) {
  const response = await api.get('/admin/draw-orders', { params: compactParams(params) })
  return response.data
}

export async function fetchAdminDrawOrderDetail(orderId) {
  const response = await api.get(`/admin/draw-orders/${orderId}`)
  return response.data
}

import { createApiClient, compactParams } from '@/services/apiClient'

const api = createApiClient()

export async function fetchAdminPaymentOrders(params = {}) {
  const response = await api.get('/admin/payment-orders', { params: compactParams(params) })
  return response.data
}

export async function fetchAdminPaymentOrderDetail(orderId) {
  const response = await api.get(`/admin/payment-orders/${orderId}`)
  return response.data
}

export async function refundPaymentOrder(orderId, reason) {
  const response = await api.post(`/admin/payment-orders/${orderId}/refund`, { reason })
  return response.data
}

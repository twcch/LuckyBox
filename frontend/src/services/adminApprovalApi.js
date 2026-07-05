import { createApiClient, compactParams } from '@/services/apiClient'

const api = createApiClient()

export async function fetchAdminApprovalRequests(params = {}) {
  const response = await api.get('/admin/approval-requests', { params: compactParams(params) })
  return response.data
}

export async function approveAdminApprovalRequest(requestId) {
  const response = await api.post(`/admin/approval-requests/${requestId}/approve`)
  return response.data
}

export async function rejectAdminApprovalRequest(requestId, reason = '') {
  const response = await api.post(`/admin/approval-requests/${requestId}/reject`, { reason })
  return response.data
}

export async function requestWalletAdjustmentApproval(payload) {
  const response = await api.post('/admin/approval-requests/wallet-adjustments', payload)
  return response.data
}

export async function requestPaymentRefundApproval(orderId, reason) {
  const response = await api.post(`/admin/approval-requests/payment-refunds/${orderId}`, { reason })
  return response.data
}

export async function requestCompensationApproval(userId, payload) {
  const response = await api.post(`/admin/approval-requests/compensations/${userId}`, payload)
  return response.data
}

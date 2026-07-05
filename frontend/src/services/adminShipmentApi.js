import { createApiClient, compactParams } from '@/services/apiClient'

const api = createApiClient()

export async function fetchAdminShipments(params = {}) {
  const response = await api.get('/admin/shipments', { params: compactParams(params) })
  return response.data
}

export async function updateAdminShipment(shipmentId, payload) {
  const response = await api.patch(`/admin/shipments/${shipmentId}`, payload)
  return response.data
}

export async function resolveAdminShipment(shipmentId, payload) {
  const response = await api.post(`/admin/shipments/${shipmentId}/resolve`, payload)
  return response.data
}

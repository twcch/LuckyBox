import { createApiClient, compactParams } from '@/services/apiClient'

const api = createApiClient()

export async function fetchPrizeBox(params = {}) {
  const response = await api.get('/account/prizes', { params: compactParams(params) })
  return response.data
}

export async function fetchShipments() {
  const response = await api.get('/account/shipments')
  return response.data
}

export async function fetchShipment(shipmentId) {
  const response = await api.get(`/account/shipments/${shipmentId}`)
  return response.data
}

export async function createShipment(payload) {
  const response = await api.post('/account/shipments', payload)
  return response.data
}

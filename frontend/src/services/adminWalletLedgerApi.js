import { createApiClient, compactParams } from '@/services/apiClient'

const api = createApiClient()

export async function fetchAdminWalletLedger(params = {}) {
  const response = await api.get('/admin/wallet-ledger', { params: compactParams(params) })
  return response.data
}

export async function createWalletAdjustment(payload) {
  const response = await api.post('/admin/wallet-adjustments', payload)
  return response.data
}

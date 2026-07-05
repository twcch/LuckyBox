import { createApiClient } from '@/services/apiClient'

const api = createApiClient()

export async function fetchTwoFactorStatus() {
  const response = await api.get('/admin/2fa')
  return response.data
}

export async function setupTwoFactor() {
  const response = await api.post('/admin/2fa/setup')
  return response.data
}

export async function enableTwoFactor(code) {
  const response = await api.post('/admin/2fa/enable', { code })
  return response.data
}

export async function disableTwoFactor(code) {
  const response = await api.post('/admin/2fa/disable', { code })
  return response.data
}

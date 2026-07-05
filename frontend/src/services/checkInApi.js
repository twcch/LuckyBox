import { createApiClient } from '@/services/apiClient'

const api = createApiClient()

export async function fetchCheckInStatus() {
  const response = await api.get('/account/check-in')
  return response.data
}

export async function submitCheckIn() {
  const response = await api.post('/account/check-in')
  return response.data
}

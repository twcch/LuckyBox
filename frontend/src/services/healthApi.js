import { createApiClient } from '@/services/apiClient'

const api = createApiClient({ timeout: 5000, withCredentials: false })

export async function fetchHealth() {
  const response = await api.get('/health')
  return response.data
}

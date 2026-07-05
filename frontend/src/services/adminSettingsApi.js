import { createApiClient } from '@/services/apiClient'

const api = createApiClient()

export async function fetchAdminSettings() {
  const response = await api.get('/admin/settings')
  return response.data
}

import { createApiClient } from '@/services/apiClient'

const api = createApiClient()

export async function fetchAdminDashboard() {
  const response = await api.get('/admin/dashboard')
  return response.data
}

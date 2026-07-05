import { createApiClient, compactParams } from '@/services/apiClient'

const api = createApiClient()

export async function fetchAdminAuditLogs(params = {}) {
  const response = await api.get('/admin/audit-logs', { params: compactParams(params) })
  return response.data
}

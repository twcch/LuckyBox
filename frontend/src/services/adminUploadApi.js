import { createApiClient } from '@/services/apiClient'

const api = createApiClient({ timeout: 30000 })

export async function uploadAdminImage(file) {
  const formData = new FormData()
  formData.append('file', file)
  const response = await api.post('/admin/uploads/images', formData)
  return response.data
}

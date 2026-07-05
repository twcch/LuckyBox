import { createApiClient, compactParams } from '@/services/apiClient'

const api = createApiClient()

export async function fetchAdminUsers(params = {}) {
  const response = await api.get('/admin/users', { params: compactParams(params) })
  return response.data
}

export async function fetchAdminMemberDetail(userId, options = {}) {
  const response = await api.get(`/admin/users/${userId}`, {
    params: compactParams({ reveal: options.reveal ? 'true' : undefined }),
  })
  return response.data
}

export async function createMemberNote(userId, content) {
  const response = await api.post(`/admin/users/${userId}/notes`, { content })
  return response.data
}

export async function grantMemberCompensation(userId, payload) {
  const response = await api.post(`/admin/users/${userId}/compensation`, payload)
  return response.data
}

export async function updateAdminUserStatus(userId, status) {
  const response = await api.patch(`/admin/users/${userId}/status`, { status })
  return response.data
}

export async function updateAdminUserRole(userId, role) {
  const response = await api.patch(`/admin/users/${userId}/role`, { role })
  return response.data
}

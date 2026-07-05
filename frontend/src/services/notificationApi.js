import { createApiClient } from '@/services/apiClient'

const api = createApiClient()

export async function fetchNotifications() {
  const response = await api.get('/account/notifications')
  return response.data
}

export async function markNotificationRead(notificationId) {
  const response = await api.patch(`/account/notifications/${notificationId}/read`)
  return response.data
}

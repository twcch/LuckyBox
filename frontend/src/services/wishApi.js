import { createApiClient } from '@/services/apiClient'

const api = createApiClient()

export async function fetchPublicWishes() {
  const response = await api.get('/wishes')
  return response.data
}

export async function fetchMyWishes() {
  const response = await api.get('/account/wishes')
  return response.data
}

export async function createWish(payload) {
  const response = await api.post('/account/wishes', payload)
  return response.data
}

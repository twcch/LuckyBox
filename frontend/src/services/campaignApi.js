import { createApiClient } from '@/services/apiClient'

const api = createApiClient({ timeout: 8000, withCredentials: false })

export async function fetchCampaigns(params = {}) {
  const response = await api.get('/campaigns', { params })
  return response.data
}

export async function fetchCampaign(slug) {
  const response = await api.get(`/campaigns/${slug}`)
  return response.data
}

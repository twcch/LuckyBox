import { createApiClient } from '@/services/apiClient'

const api = createApiClient({ timeout: 8000, withCredentials: false })

export async function fetchLeaderboard(params = {}) {
  const response = await api.get('/leaderboard', { params })
  return response.data
}

export async function fetchCampaignDrawHistory(slug, params = {}) {
  const response = await api.get(`/leaderboard/campaigns/${slug}/draws`, { params })
  return response.data
}

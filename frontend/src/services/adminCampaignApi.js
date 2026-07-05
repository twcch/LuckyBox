import { createApiClient, compactParams } from '@/services/apiClient'

const api = createApiClient()

export async function fetchAdminCampaigns(params = {}) {
  const response = await api.get('/admin/campaigns', { params: compactParams(params) })
  return response.data
}

export async function createAdminCampaign(payload) {
  const response = await api.post('/admin/campaigns', payload)
  return response.data
}

export async function updateAdminCampaign(campaignId, payload) {
  const response = await api.patch(`/admin/campaigns/${campaignId}`, payload)
  return response.data
}

export async function publishAdminCampaign(campaignId) {
  const response = await api.post(`/admin/campaigns/${campaignId}/publish`)
  return response.data
}

export async function pauseAdminCampaign(campaignId) {
  const response = await api.post(`/admin/campaigns/${campaignId}/pause`)
  return response.data
}

export async function dryRunAdminCampaign(campaignId) {
  const response = await api.post(`/admin/campaigns/${campaignId}/dry-run`)
  return response.data
}

export async function fetchAdminCampaignPrizes(campaignId) {
  const response = await api.get(`/admin/campaigns/${campaignId}/prizes`)
  return response.data
}

export async function fetchAdminCampaignTickets(campaignId, params = {}) {
  const response = await api.get(`/admin/campaigns/${campaignId}/tickets`, {
    params: compactParams(params),
  })
  return response.data
}

export async function createAdminCampaignPrize(campaignId, payload) {
  const response = await api.post(`/admin/campaigns/${campaignId}/prizes`, payload)
  return response.data
}

export async function updateAdminCampaignPrize(campaignId, prizeId, payload) {
  const response = await api.patch(`/admin/campaigns/${campaignId}/prizes/${prizeId}`, payload)
  return response.data
}

export async function generateAdminCampaignTickets(campaignId) {
  const response = await api.post(`/admin/campaigns/${campaignId}/tickets/generate`)
  return response.data
}

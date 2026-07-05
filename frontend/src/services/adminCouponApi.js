import { createApiClient, compactParams } from '@/services/apiClient'

const api = createApiClient()

export async function fetchAdminCoupons(params = {}) {
  const response = await api.get('/admin/coupons', {
    params: compactParams(params),
  })
  return response.data
}

export async function createAdminCoupon(payload) {
  const response = await api.post('/admin/coupons', payload)
  return response.data
}

export async function updateAdminCoupon(couponId, payload) {
  const response = await api.patch(`/admin/coupons/${couponId}`, payload)
  return response.data
}

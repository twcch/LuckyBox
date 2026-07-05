import { createApiClient } from '@/services/apiClient'

const api = createApiClient()

export async function fetchAccountCoupons() {
  const response = await api.get('/account/coupons')
  return response.data
}

export async function redeemAccountCoupon(couponId) {
  const response = await api.post(`/account/coupons/${couponId}/redeem`)
  return response.data
}

import { createApiClient } from '@/services/apiClient'

const api = createApiClient()

export async function fetchWalletOverview() {
  const response = await api.get('/account/wallet')
  return response.data
}

export async function fetchWalletLedger() {
  const response = await api.get('/account/wallet/ledger')
  return response.data
}

export async function fetchTopUpPlans() {
  const response = await api.get('/account/wallet/top-up-plans')
  return response.data
}

export async function createPaymentOrder(payload) {
  const response = await api.post('/account/payment-orders', payload)
  return response.data
}

export async function createEcpayCheckout(orderId) {
  const response = await api.post(`/account/payment-orders/${orderId}/ecpay-checkout`)
  return response.data
}

export async function createLinePayCheckout(orderId) {
  const response = await api.post(`/account/payment-orders/${orderId}/linepay-checkout`)
  return response.data
}

export async function createJkoPayCheckout(orderId) {
  const response = await api.post(`/account/payment-orders/${orderId}/jkopay-checkout`)
  return response.data
}

export async function completePaymentOrder(orderId) {
  const response = await api.post(`/account/payment-orders/${orderId}/complete`)
  return response.data
}

export async function confirmMockCheckout(orderId) {
  const response = await api.post(`/account/payment-orders/${orderId}/mock-checkout/confirm`)
  return response.data
}

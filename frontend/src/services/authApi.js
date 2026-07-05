import { createApiClient } from '@/services/apiClient'
import { getVisitorId } from '@/services/visitorId'

const api = createApiClient()

export async function registerAccount(payload) {
  const response = await api.post('/auth/register', {
    ...payload,
    visitorId: getVisitorId(),
  })
  return response.data
}

export async function loginAccount(payload) {
  const response = await api.post('/auth/login', payload)
  return response.data
}

export async function requestPasswordReset(payload) {
  await api.post('/auth/forgot-password', payload)
}

export async function resetPassword(payload) {
  await api.post('/auth/reset-password', payload)
}

export async function fetchCurrentUser() {
  const response = await api.get('/auth/me')
  return response.data
}

export async function fetchAccountProfile() {
  const response = await api.get('/account/profile')
  return response.data
}

export async function logoutAccount() {
  await api.post('/auth/logout')
}

export async function updateProfileAccount(payload) {
  const response = await api.patch('/account/profile', payload)
  return response.data
}

export async function fetchAddresses() {
  const response = await api.get('/account/addresses')
  return response.data
}

export async function createAddress(payload) {
  const response = await api.post('/account/addresses', payload)
  return response.data
}

export async function updateAddress(addressId, payload) {
  const response = await api.put(`/account/addresses/${addressId}`, payload)
  return response.data
}

export async function deleteAddress(addressId) {
  await api.delete(`/account/addresses/${addressId}`)
}

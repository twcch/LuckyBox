import axios from 'axios'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api'
const DEFAULT_TIMEOUT = 10000

export function createApiClient(options = {}) {
  const { timeout = DEFAULT_TIMEOUT, withCredentials = true, ...rest } = options

  return axios.create({
    baseURL: API_BASE_URL,
    timeout,
    withCredentials,
    ...rest,
  })
}

export function compactParams(params = {}) {
  return Object.fromEntries(
    Object.entries(params).filter(
      ([, value]) => value !== '' && value !== null && value !== undefined,
    ),
  )
}

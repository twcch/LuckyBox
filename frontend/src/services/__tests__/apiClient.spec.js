import { describe, expect, it } from 'vitest'
import { compactParams, createApiClient } from '../apiClient'

describe('apiClient', () => {
  it('creates a credentialed API client with shared defaults', () => {
    const api = createApiClient()

    expect(api.defaults.baseURL).toBe('/api')
    expect(api.defaults.timeout).toBe(10000)
    expect(api.defaults.withCredentials).toBe(true)
  })

  it('allows public clients to override timeout and credentials', () => {
    const api = createApiClient({ timeout: 5000, withCredentials: false })

    expect(api.defaults.timeout).toBe(5000)
    expect(api.defaults.withCredentials).toBe(false)
  })

  it('removes empty params without dropping falsy query values', () => {
    expect(
      compactParams({
        keyword: '',
        status: null,
        campaignId: undefined,
        page: 0,
        includeArchived: false,
        role: 'ADMIN',
      }),
    ).toEqual({
      page: 0,
      includeArchived: false,
      role: 'ADMIN',
    })
  })
})

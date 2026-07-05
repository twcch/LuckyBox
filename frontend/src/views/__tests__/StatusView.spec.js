import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import StatusView from '../StatusView.vue'
import { fetchHealth } from '@/services/healthApi'

vi.mock('@/services/healthApi', () => ({
  fetchHealth: vi.fn(),
}))

describe('StatusView', () => {
  beforeEach(() => {
    fetchHealth.mockReset()
  })

  it('shows backend health when the API is reachable', async () => {
    fetchHealth.mockResolvedValue({
      status: 'UP',
      service: 'LuckyBox',
      timestamp: '2026-07-05T06:39:51.323083Z',
    })

    const wrapper = mount(StatusView)
    await flushPromises()

    expect(fetchHealth).toHaveBeenCalledOnce()
    expect(wrapper.text()).toContain('API 正常')
    expect(wrapper.text()).toContain('LuckyBox')
  })

  it('shows a clear offline state when the API is unreachable', async () => {
    fetchHealth.mockRejectedValue(new Error('network down'))

    const wrapper = mount(StatusView)
    await flushPromises()

    expect(wrapper.text()).toContain('API 未連線')
    expect(wrapper.text()).toContain('目前無法連線到後端 API')
  })
})

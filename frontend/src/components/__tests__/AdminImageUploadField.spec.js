import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import AdminImageUploadField from '../AdminImageUploadField.vue'
import { uploadAdminImage } from '@/services/adminUploadApi'

vi.mock('@/services/adminUploadApi', () => ({
  uploadAdminImage: vi.fn(),
}))

describe('AdminImageUploadField', () => {
  beforeEach(() => {
    uploadAdminImage.mockReset()
  })

  it('uploads a selected image and emits the returned URL', async () => {
    uploadAdminImage.mockResolvedValue({
      url: '/uploads/images/20260702/example.png',
      contentType: 'image/png',
      size: 12,
      filename: 'example.png',
    })
    const wrapper = mount(AdminImageUploadField, {
      props: {
        modelValue: '',
        inputId: 'bannerImageUrl',
        label: '圖片網址',
      },
    })
    const file = new File(['fake'], 'example.png', { type: 'image/png' })
    const fileInput = wrapper.find('input[type="file"]')

    Object.defineProperty(fileInput.element, 'files', {
      value: [file],
      configurable: true,
    })
    await fileInput.trigger('change')

    expect(uploadAdminImage).toHaveBeenCalledWith(file)
    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([
      '/uploads/images/20260702/example.png',
    ])
    expect(wrapper.text()).toContain('已上傳 12 B')
  })
})

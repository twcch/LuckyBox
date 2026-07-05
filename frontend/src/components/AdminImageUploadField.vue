<script setup>
import { ref } from 'vue'
import { uploadAdminImage } from '@/services/adminUploadApi'

const props = defineProps({
  modelValue: {
    type: String,
    default: '',
  },
  inputId: {
    type: String,
    required: true,
  },
  label: {
    type: String,
    required: true,
  },
  placeholder: {
    type: String,
    default: '',
  },
  required: {
    type: Boolean,
    default: false,
  },
  disabled: {
    type: Boolean,
    default: false,
  },
  modelModifiers: {
    type: Object,
    default: () => ({}),
  },
})

const emit = defineEmits(['update:modelValue', 'uploaded', 'upload-error'])

const uploading = ref(false)
const uploadMessage = ref('')
const uploadError = ref('')

async function uploadSelectedFile(event) {
  const [file] = event.target.files || []
  if (!file) {
    return
  }
  uploading.value = true
  uploadMessage.value = ''
  uploadError.value = ''
  try {
    const uploaded = await uploadAdminImage(file)
    emit('update:modelValue', uploaded.url)
    emit('uploaded', uploaded)
    uploadMessage.value = `已上傳 ${formatBytes(uploaded.size)}`
  } catch (error) {
    const message = error.response?.data?.message || '圖片上傳失敗。'
    uploadError.value = message
    emit('upload-error', message)
  } finally {
    uploading.value = false
    event.target.value = ''
  }
}

function formatBytes(size) {
  if (!Number.isFinite(Number(size))) {
    return ''
  }
  if (Number(size) < 1024) {
    return `${size} B`
  }
  return `${(Number(size) / 1024).toFixed(1)} KB`
}
</script>

<template>
  <div class="admin-image-upload-field">
    <label class="form-label" :for="inputId">{{ label }}</label>
    <div class="input-group">
      <span class="input-group-text">
        <i class="bi bi-image" aria-hidden="true"></i>
      </span>
      <input
        :id="inputId"
        :value="props.modelValue"
        class="form-control"
        :placeholder="placeholder"
        :required="required"
        :disabled="disabled"
        @input="
          emit(
            'update:modelValue',
            props.modelModifiers.trim ? $event.target.value.trim() : $event.target.value,
          )
        "
      />
    </div>
    <div class="admin-image-upload-field__controls">
      <label
        class="btn btn-outline-dark btn-sm"
        :class="{ disabled: disabled || uploading }"
        :for="`${inputId}File`"
      >
        <i class="bi bi-cloud-upload" aria-hidden="true"></i>
        {{ uploading ? '上傳中' : '選檔上傳' }}
      </label>
      <input
        :id="`${inputId}File`"
        class="visually-hidden"
        type="file"
        accept="image/jpeg,image/png,image/webp"
        :disabled="disabled || uploading"
        @change="uploadSelectedFile"
      />
      <span>JPG / PNG / WebP，2 MB 內</span>
    </div>
    <p v-if="uploadMessage" class="admin-image-upload-field__message">
      <i class="bi bi-check-circle" aria-hidden="true"></i>
      {{ uploadMessage }}
    </p>
    <p v-if="uploadError" class="admin-image-upload-field__error" role="alert">
      <i class="bi bi-exclamation-triangle" aria-hidden="true"></i>
      {{ uploadError }}
    </p>
  </div>
</template>

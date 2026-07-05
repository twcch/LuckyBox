<script setup>
import { ref } from 'vue'
import { RouterLink } from 'vue-router'
import { requestPasswordReset } from '@/services/authApi'

const email = ref('')
const loading = ref(false)
const submitted = ref(false)
const errorMessage = ref('')

async function submit() {
  loading.value = true
  errorMessage.value = ''
  try {
    await requestPasswordReset({ email: email.value })
    submitted.value = true
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '送出失敗，請稍後再試。'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="auth-page">
    <section class="container content-section auth-layout">
      <div class="page-title">
        <span class="eyebrow">Member</span>
        <h1>忘記密碼</h1>
        <p>
          輸入註冊時的 Email，我們會寄送密碼重設連結。為保護帳號隱私，不論該 Email
          是否已註冊都會顯示相同結果。
        </p>
      </div>

      <div v-if="submitted" class="auth-panel">
        <div class="state-panel" role="status">
          <i class="bi bi-envelope-check" aria-hidden="true"></i>
          <span
            >若這個 Email 已註冊，我們已寄出密碼重設連結，請至信箱查收（連結 30 分鐘內有效）。</span
          >
        </div>
        <RouterLink class="auth-link" to="/login">返回登入</RouterLink>
      </div>

      <form v-else class="auth-panel" @submit.prevent="submit">
        <div v-if="errorMessage" class="state-panel" role="alert">
          <i class="bi bi-exclamation-triangle" aria-hidden="true"></i>
          <span>{{ errorMessage }}</span>
        </div>

        <div>
          <label class="form-label" for="forgotEmail">Email</label>
          <input
            id="forgotEmail"
            v-model.trim="email"
            class="form-control"
            type="email"
            autocomplete="email"
            required
          />
        </div>
        <button class="btn btn-danger btn-lg" type="submit" :disabled="loading">
          <i class="bi bi-envelope-arrow-up me-2" aria-hidden="true"></i>
          {{ loading ? '送出中' : '寄送重設連結' }}
        </button>
        <RouterLink class="auth-link" to="/login">想起來了？返回登入</RouterLink>
      </form>
    </section>
  </main>
</template>

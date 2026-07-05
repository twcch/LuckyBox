<script setup>
import { reactive, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { resetPassword } from '@/services/authApi'

const route = useRoute()
const loading = ref(false)
const done = ref(false)
const errorMessage = ref('')
const form = reactive({
  token: typeof route.query.token === 'string' ? route.query.token : '',
  password: '',
  confirm: '',
})

async function submit() {
  errorMessage.value = ''
  if (form.password !== form.confirm) {
    errorMessage.value = '兩次輸入的密碼不一致。'
    return
  }
  loading.value = true
  try {
    await resetPassword({ token: form.token.trim(), password: form.password })
    done.value = true
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '重設失敗，連結可能已失效，請重新申請。'
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
        <h1>重設密碼</h1>
        <p>請輸入重設碼與新密碼。重設碼有效時間為 30 分鐘，且僅能使用一次。</p>
      </div>

      <div v-if="done" class="auth-panel">
        <div class="state-panel" role="status">
          <i class="bi bi-shield-check" aria-hidden="true"></i>
          <span>密碼已更新，請使用新密碼登入。</span>
        </div>
        <RouterLink class="auth-link" to="/login">前往登入</RouterLink>
      </div>

      <form v-else class="auth-panel" @submit.prevent="submit">
        <div v-if="errorMessage" class="state-panel" role="alert">
          <i class="bi bi-exclamation-triangle" aria-hidden="true"></i>
          <span>{{ errorMessage }}</span>
        </div>

        <div>
          <label class="form-label" for="resetToken">重設碼</label>
          <input
            id="resetToken"
            v-model.trim="form.token"
            class="form-control"
            type="text"
            required
          />
        </div>
        <div>
          <label class="form-label" for="resetPassword">新密碼</label>
          <input
            id="resetPassword"
            v-model="form.password"
            class="form-control"
            type="password"
            autocomplete="new-password"
            minlength="8"
            maxlength="80"
            required
          />
        </div>
        <div>
          <label class="form-label" for="resetConfirm">確認新密碼</label>
          <input
            id="resetConfirm"
            v-model="form.confirm"
            class="form-control"
            type="password"
            autocomplete="new-password"
            minlength="8"
            maxlength="80"
            required
          />
        </div>
        <button class="btn btn-danger btn-lg" type="submit" :disabled="loading">
          <i class="bi bi-shield-lock me-2" aria-hidden="true"></i>
          {{ loading ? '更新中' : '更新密碼' }}
        </button>
        <RouterLink class="auth-link" to="/forgot-password">沒有重設碼？重新申請</RouterLink>
      </form>
    </section>
  </main>
</template>

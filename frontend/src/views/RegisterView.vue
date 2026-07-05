<script setup>
import { reactive, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { useSessionStore } from '@/stores/session'

const router = useRouter()
const route = useRoute()
const session = useSessionStore()
const loading = ref(false)
const errorMessage = ref('')
const form = reactive({
  email: '',
  displayName: '',
  phone: '',
  password: '',
})

async function submit() {
  loading.value = true
  errorMessage.value = ''
  try {
    await session.register({
      email: form.email,
      displayName: form.displayName,
      phone: form.phone,
      password: form.password,
    })
    await router.push(typeof route.query.redirect === 'string' ? route.query.redirect : '/account')
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '註冊失敗，請檢查輸入資料。'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="auth-page">
    <section class="container content-section auth-layout">
      <div class="page-title">
        <span class="eyebrow">加入會員</span>
        <h1>建立會員</h1>
        <p>建立帳號後即可管理點數、抽賞紀錄、收件地址與戰利品出貨。</p>
        <div class="auth-benefits" aria-label="會員功能">
          <span>
            <i class="bi bi-gift" aria-hidden="true"></i>
            儲存戰利品
          </span>
          <span>
            <i class="bi bi-truck" aria-hidden="true"></i>
            合併出貨
          </span>
          <span>
            <i class="bi bi-shield-check" aria-hidden="true"></i>
            紀錄可追溯
          </span>
        </div>
      </div>

      <form class="auth-panel" @submit.prevent="submit">
        <div v-if="errorMessage" class="state-panel" role="alert">
          <i class="bi bi-exclamation-triangle" aria-hidden="true"></i>
          <span>{{ errorMessage }}</span>
        </div>

        <div>
          <label class="form-label" for="registerEmail">Email</label>
          <input
            id="registerEmail"
            v-model.trim="form.email"
            class="form-control"
            type="email"
            autocomplete="email"
            required
          />
        </div>
        <div>
          <label class="form-label" for="displayName">顯示名稱</label>
          <input
            id="displayName"
            v-model.trim="form.displayName"
            class="form-control"
            type="text"
            autocomplete="name"
            required
          />
        </div>
        <div>
          <label class="form-label" for="phone">手機</label>
          <input
            id="phone"
            v-model.trim="form.phone"
            class="form-control"
            type="tel"
            autocomplete="tel"
          />
        </div>
        <div>
          <label class="form-label" for="registerPassword">密碼</label>
          <input
            id="registerPassword"
            v-model="form.password"
            class="form-control"
            type="password"
            autocomplete="new-password"
            minlength="8"
            required
          />
        </div>
        <button class="btn btn-danger btn-lg" type="submit" :disabled="loading">
          <i class="bi bi-person-plus me-2" aria-hidden="true"></i>
          {{ loading ? '建立中' : '建立帳號' }}
        </button>
        <RouterLink class="auth-link" to="/login">已經有帳號？登入</RouterLink>
      </form>
    </section>
  </main>
</template>

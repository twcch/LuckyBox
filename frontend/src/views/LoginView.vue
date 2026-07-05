<script setup>
import { computed, reactive, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { useSessionStore } from '@/stores/session'

const router = useRouter()
const route = useRoute()
const session = useSessionStore()
const loading = ref(false)
const errorMessage = ref('')
const requiresTwoFactor = ref(false)
const form = reactive({
  email: '',
  password: '',
  totpCode: '',
})
const isAdminLogin = computed(() => route.path.startsWith('/admin'))
const defaultRedirect = computed(() => (isAdminLogin.value ? '/admin' : '/account'))

async function submit() {
  loading.value = true
  errorMessage.value = ''
  try {
    await session.login({
      email: form.email,
      password: form.password,
      totpCode: form.totpCode || undefined,
    })
    await router.push(
      typeof route.query.redirect === 'string' ? route.query.redirect : defaultRedirect.value,
    )
  } catch (error) {
    const code = error.response?.data?.code
    if (code === 'TWO_FACTOR_REQUIRED') {
      requiresTwoFactor.value = true
      errorMessage.value = '此帳號已啟用二階段驗證，請輸入驗證器 App 的 6 碼驗證碼。'
    } else if (code === 'TWO_FACTOR_INVALID') {
      requiresTwoFactor.value = true
      errorMessage.value = '二階段驗證碼不正確，請重新輸入。'
    } else {
      errorMessage.value = error.response?.data?.message || '登入失敗，請確認 Email 與密碼。'
    }
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="auth-page">
    <section class="container content-section auth-layout">
      <div class="page-title">
        <span class="eyebrow">{{ isAdminLogin ? '後台' : '會員' }}</span>
        <h1>{{ isAdminLogin ? '登入營運後台' : '登入 LuckyBox' }}</h1>
        <p>
          {{
            isAdminLogin
              ? '使用管理員帳號登入後，可以查看營運總覽與處理出貨待辦。'
              : '登入後可以查看點數、管理地址、抽賞紀錄與戰利品出貨狀態。'
          }}
        </p>
        <div class="auth-benefits" aria-label="登入後可使用功能">
          <span>
            <i class="bi bi-wallet2" aria-hidden="true"></i>
            點數錢包
          </span>
          <span>
            <i class="bi bi-ticket-perforated" aria-hidden="true"></i>
            抽賞紀錄
          </span>
          <span>
            <i class="bi bi-box-seam" aria-hidden="true"></i>
            戰利品出貨
          </span>
        </div>
      </div>

      <form class="auth-panel" @submit.prevent="submit">
        <div v-if="errorMessage" class="state-panel" role="alert">
          <i class="bi bi-exclamation-triangle" aria-hidden="true"></i>
          <span>{{ errorMessage }}</span>
        </div>

        <div>
          <label class="form-label" for="loginEmail">Email</label>
          <input
            id="loginEmail"
            v-model.trim="form.email"
            class="form-control"
            type="email"
            autocomplete="email"
            required
          />
        </div>
        <div>
          <label class="form-label" for="loginPassword">密碼</label>
          <input
            id="loginPassword"
            v-model="form.password"
            class="form-control"
            type="password"
            autocomplete="current-password"
            required
          />
        </div>
        <div v-if="requiresTwoFactor">
          <label class="form-label" for="loginTotp">二階段驗證碼</label>
          <input
            id="loginTotp"
            v-model.trim="form.totpCode"
            class="form-control"
            type="text"
            inputmode="numeric"
            autocomplete="one-time-code"
            maxlength="6"
            placeholder="6 碼驗證碼"
          />
        </div>
        <button class="btn btn-danger btn-lg" type="submit" :disabled="loading">
          <i class="bi bi-box-arrow-in-right me-2" aria-hidden="true"></i>
          {{ loading ? '登入中' : requiresTwoFactor ? '驗證並登入' : '登入' }}
        </button>
        <RouterLink v-if="!isAdminLogin" class="auth-link" to="/register"
          >還沒有帳號？建立會員</RouterLink
        >
        <RouterLink v-if="!isAdminLogin" class="auth-link" to="/forgot-password"
          >忘記密碼？</RouterLink
        >
      </form>
    </section>
  </main>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  disableTwoFactor,
  enableTwoFactor,
  fetchTwoFactorStatus,
  setupTwoFactor,
} from '@/services/admin2faApi'
import { useSessionStore } from '@/stores/session'

const router = useRouter()
const session = useSessionStore()

const loading = ref(true)
const enabled = ref(false)
const errorMessage = ref('')
const successMessage = ref('')

// 設定流程狀態
const setup = reactive({
  active: false,
  secret: '',
  otpauthUri: '',
  qrCodeDataUri: '',
  code: '',
  submitting: false,
})
const disableForm = reactive({ active: false, code: '', submitting: false })

onMounted(async () => {
  const user = await session.load()
  if (!user) {
    await router.replace({ path: '/admin/login', query: { redirect: '/admin/security' } })
    return
  }
  if (!isAdmin(user)) {
    await router.replace('/account')
    return
  }
  await loadStatus()
})

async function loadStatus() {
  loading.value = true
  errorMessage.value = ''
  try {
    enabled.value = (await fetchTwoFactorStatus()).enabled
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '無法載入二階段驗證狀態。'
  } finally {
    loading.value = false
  }
}

async function startSetup() {
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const result = await setupTwoFactor()
    setup.secret = result.secret
    setup.otpauthUri = result.otpauthUri
    setup.qrCodeDataUri = result.qrCodeDataUri || ''
    setup.code = ''
    setup.active = true
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '無法產生二階段驗證金鑰。'
  }
}

async function confirmEnable() {
  if (setup.code.trim().length !== 6) {
    errorMessage.value = '請輸入驗證器 App 顯示的 6 碼驗證碼。'
    return
  }
  setup.submitting = true
  errorMessage.value = ''
  try {
    await enableTwoFactor(setup.code.trim())
    successMessage.value = '二階段驗證已啟用，下次登入將需要輸入驗證碼。'
    setup.active = false
    setup.secret = ''
    setup.otpauthUri = ''
    setup.qrCodeDataUri = ''
    setup.code = ''
    await loadStatus()
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '驗證碼不正確，請重試。'
  } finally {
    setup.submitting = false
  }
}

function cancelSetup() {
  setup.active = false
  setup.secret = ''
  setup.otpauthUri = ''
  setup.qrCodeDataUri = ''
  setup.code = ''
}

async function confirmDisable() {
  if (disableForm.code.trim().length !== 6) {
    errorMessage.value = '請輸入目前的 6 碼驗證碼以停用。'
    return
  }
  disableForm.submitting = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    await disableTwoFactor(disableForm.code.trim())
    successMessage.value = '二階段驗證已停用。'
    disableForm.active = false
    disableForm.code = ''
    await loadStatus()
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '停用失敗，請確認驗證碼。'
  } finally {
    disableForm.submitting = false
  }
}

function isAdmin(user) {
  return user.role === 'SUPER_ADMIN' || user.role === 'ADMIN'
}
</script>

<template>
  <article class="admin-page admin-security-page">
    <div class="page-title">
      <span class="eyebrow">Admin</span>
      <h1>安全設定</h1>
    </div>

    <div v-if="errorMessage" class="state-panel" role="alert">
      <i class="bi bi-exclamation-triangle" aria-hidden="true"></i>
      <span>{{ errorMessage }}</span>
    </div>
    <div v-if="successMessage" class="toast show lb-toast" role="status">
      <div class="toast-body">
        <i class="bi bi-check-circle me-1" aria-hidden="true"></i>
        {{ successMessage }}
      </div>
    </div>

    <section class="status-panel">
      <div class="section-heading">
        <div>
          <span class="eyebrow">Two-Factor</span>
          <h2>二階段驗證（TOTP）</h2>
        </div>
        <span class="badge" :class="enabled ? 'text-bg-success' : 'text-bg-light border'">
          {{ enabled ? '已啟用' : '未啟用' }}
        </span>
      </div>

      <p class="admin-2fa-intro">
        啟用後，後台帳號登入時除了密碼，還需要輸入驗證器 App（Google Authenticator、Authy 等）產生的
        6 碼動態驗證碼，大幅降低密碼外洩風險。
      </p>

      <div v-if="loading" class="skeleton-line w-50"></div>

      <template v-else>
        <!-- 未啟用：開始設定 -->
        <div v-if="!enabled && !setup.active">
          <button class="btn btn-danger" type="button" @click="startSetup">
            <i class="bi bi-shield-lock me-1" aria-hidden="true"></i>
            啟用二階段驗證
          </button>
        </div>

        <!-- 設定中：顯示金鑰並輸入驗證碼確認 -->
        <div v-if="setup.active" class="admin-2fa-setup">
          <ol class="admin-2fa-steps">
            <li>在驗證器 App 新增帳號，掃描 QR 或手動輸入下方金鑰。</li>
            <li>輸入 App 顯示的 6 碼驗證碼以完成啟用。</li>
          </ol>
          <div v-if="setup.qrCodeDataUri" class="admin-2fa-qr">
            <img :src="setup.qrCodeDataUri" alt="TOTP QR code" width="192" height="192" />
          </div>
          <div class="admin-2fa-secret">
            <span class="admin-2fa-secret__label">設定金鑰（手動輸入用）</span>
            <code>{{ setup.secret }}</code>
          </div>
          <div class="admin-2fa-secret">
            <span class="admin-2fa-secret__label">otpauth URI</span>
            <code class="admin-2fa-uri">{{ setup.otpauthUri }}</code>
          </div>
          <div class="admin-2fa-confirm">
            <input
              v-model.trim="setup.code"
              class="form-control"
              type="text"
              inputmode="numeric"
              maxlength="6"
              placeholder="6 碼驗證碼"
            />
            <button
              class="btn btn-danger"
              type="button"
              :disabled="setup.submitting"
              @click="confirmEnable"
            >
              {{ setup.submitting ? '驗證中…' : '確認啟用' }}
            </button>
            <button class="btn btn-outline-dark" type="button" @click="cancelSetup">取消</button>
          </div>
        </div>

        <!-- 已啟用：停用 -->
        <div v-if="enabled" class="admin-2fa-disable">
          <button
            v-if="!disableForm.active"
            class="btn btn-outline-danger"
            type="button"
            @click="disableForm.active = true"
          >
            <i class="bi bi-shield-slash me-1" aria-hidden="true"></i>
            停用二階段驗證
          </button>
          <div v-else class="admin-2fa-confirm">
            <input
              v-model.trim="disableForm.code"
              class="form-control"
              type="text"
              inputmode="numeric"
              maxlength="6"
              placeholder="輸入目前 6 碼驗證碼"
            />
            <button
              class="btn btn-danger"
              type="button"
              :disabled="disableForm.submitting"
              @click="confirmDisable"
            >
              {{ disableForm.submitting ? '處理中…' : '確認停用' }}
            </button>
            <button class="btn btn-outline-dark" type="button" @click="disableForm.active = false">
              取消
            </button>
          </div>
        </div>
      </template>
    </section>
  </article>
</template>

<style scoped>
.admin-2fa-intro {
  color: var(--lb-muted, #6c757d);
  margin: 0.5rem 0 1rem;
}

.admin-2fa-setup {
  display: grid;
  gap: 0.85rem;
  margin-top: 0.5rem;
}

.admin-2fa-steps {
  margin: 0;
  padding-left: 1.1rem;
  color: var(--lb-ink, #1f2933);
}

.admin-2fa-secret {
  display: grid;
  gap: 0.25rem;
}

.admin-2fa-qr {
  width: 12rem;
  height: 12rem;
  display: grid;
  place-items: center;
  border: 1px solid var(--bs-border-color, #e3e3e8);
  border-radius: 0.5rem;
  background: #fff;
}

.admin-2fa-qr img {
  display: block;
  width: 12rem;
  height: 12rem;
}

.admin-2fa-secret__label {
  font-size: 0.78rem;
  color: var(--lb-muted, #6c757d);
}

.admin-2fa-secret code {
  display: block;
  padding: 0.5rem 0.7rem;
  border: 1px solid var(--bs-border-color, #e3e3e8);
  border-radius: 0.55rem;
  background: var(--bs-body-bg, #fff);
  font-size: 0.95rem;
  letter-spacing: 0.05em;
  word-break: break-all;
}

.admin-2fa-uri {
  font-size: 0.78rem !important;
  letter-spacing: 0 !important;
}

.admin-2fa-confirm {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  align-items: center;
}

.admin-2fa-confirm .form-control {
  max-width: 12rem;
}
</style>

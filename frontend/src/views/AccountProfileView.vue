<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { useSessionStore } from '@/stores/session'
import { fetchVipStatus } from '@/services/vipApi'

const router = useRouter()
const session = useSessionStore()

const loading = ref(true)
const saving = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const vip = ref(null)

const form = reactive({
  displayName: '',
  phone: '',
})

const walletTotal = computed(() => {
  if (!session.user) {
    return 0
  }
  return session.user.cashPointBalance + session.user.bonusPointBalance
})

onMounted(async () => {
  const user = await session.load()
  if (!user) {
    await router.replace({ path: '/login', query: { redirect: '/account/profile' } })
    return
  }
  await loadProfile()
  await loadVip()
})

async function loadVip() {
  try {
    vip.value = await fetchVipStatus()
  } catch {
    // VIP 進度載入失敗時靜默處理，不阻擋個人資料。
  }
}

async function loadProfile() {
  loading.value = true
  errorMessage.value = ''
  try {
    const user = await session.loadProfile()
    syncForm(user)
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '無法載入會員資料。'
  } finally {
    loading.value = false
  }
}

async function submitProfile() {
  saving.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const user = await session.updateProfile({
      displayName: form.displayName,
      phone: form.phone,
    })
    syncForm(user)
    successMessage.value = '會員資料已更新。'
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '會員資料更新失敗。'
  } finally {
    saving.value = false
  }
}

function syncForm(user) {
  form.displayName = user?.displayName || ''
  form.phone = user?.phone || ''
}

function resetForm() {
  syncForm(session.user)
  errorMessage.value = ''
  successMessage.value = ''
}
</script>

<template>
  <main class="account-profile-page">
    <section class="container content-section">
      <RouterLink class="back-link" to="/account">
        <i class="bi bi-arrow-left" aria-hidden="true"></i>
        會員中心
      </RouterLink>

      <div class="page-title">
        <span class="eyebrow">Profile</span>
        <h1>個人資料</h1>
        <p>管理顯示名稱、手機與帳號狀態。</p>
      </div>

      <div v-if="errorMessage" class="state-panel account-profile-state" role="alert">
        <i class="bi bi-exclamation-triangle" aria-hidden="true"></i>
        <span>{{ errorMessage }}</span>
      </div>

      <div v-if="successMessage" class="toast show lb-toast account-toast" role="status">
        <div class="toast-body">
          <i class="bi bi-check-circle me-1" aria-hidden="true"></i>
          {{ successMessage }}
        </div>
      </div>

      <div v-if="loading" class="account-profile-grid">
        <section class="status-panel">
          <div class="skeleton-line w-50"></div>
          <div class="skeleton-line"></div>
          <div class="skeleton-line w-75"></div>
        </section>
        <section class="status-panel">
          <div class="skeleton-line w-50"></div>
          <div class="skeleton-line"></div>
          <div class="skeleton-line w-75"></div>
        </section>
      </div>

      <div v-else-if="session.user" class="account-profile-grid">
        <section class="status-panel account-profile-panel">
          <div class="section-heading">
            <div>
              <span class="eyebrow">Edit</span>
              <h2>基本資料</h2>
            </div>
          </div>

          <form class="profile-form" @submit.prevent="submitProfile">
            <div>
              <label class="form-label" for="accountProfileEmail">Email</label>
              <input
                id="accountProfileEmail"
                class="form-control"
                type="email"
                :value="session.user.email"
                disabled
              />
            </div>
            <div>
              <label class="form-label" for="accountProfileDisplayName">顯示名稱</label>
              <input
                id="accountProfileDisplayName"
                v-model.trim="form.displayName"
                class="form-control"
                type="text"
                maxlength="80"
                required
              />
            </div>
            <div>
              <label class="form-label" for="accountProfilePhone">手機</label>
              <input
                id="accountProfilePhone"
                v-model.trim="form.phone"
                class="form-control"
                type="tel"
                maxlength="30"
              />
            </div>
            <div class="component-row">
              <button class="btn btn-danger" type="submit" :disabled="saving">
                <i class="bi bi-person-check me-1" aria-hidden="true"></i>
                {{ saving ? '儲存中' : '儲存資料' }}
              </button>
              <button
                class="btn btn-outline-dark"
                type="button"
                :disabled="saving"
                @click="resetForm"
              >
                還原
              </button>
            </div>
          </form>
        </section>

        <aside class="status-panel account-profile-panel">
          <div class="section-heading">
            <div>
              <span class="eyebrow">Account</span>
              <h2>帳號狀態</h2>
            </div>
          </div>

          <div class="status-grid account-status-grid">
            <div>
              <strong>{{ walletTotal }} LP</strong>
              <span>可用總點數</span>
            </div>
            <div>
              <strong>{{ session.user.cashPointBalance }} LP</strong>
              <span>現金點</span>
            </div>
            <div>
              <strong>{{ session.user.bonusPointBalance }} LP</strong>
              <span>贈點</span>
            </div>
          </div>

          <dl class="profile-list">
            <span>角色</span>
            <strong>{{ session.user.role }}</strong>
            <span>狀態</span>
            <strong>{{ session.user.status }}</strong>
            <span>Email</span>
            <strong>{{ session.user.email }}</strong>
          </dl>

          <div v-if="vip" class="account-vip">
            <div class="account-vip__head">
              <div>
                <span class="eyebrow">VIP</span>
                <strong class="account-vip__tier">{{ vip.tierLabel }}</strong>
              </div>
              <span class="account-vip__spend">累積消費 {{ vip.totalSpend }} LP</span>
            </div>
            <template v-if="vip.nextTier">
              <div
                class="account-vip__bar"
                role="progressbar"
                :aria-valuenow="vip.progressPercent"
                aria-valuemin="0"
                aria-valuemax="100"
              >
                <div class="account-vip__fill" :style="{ width: `${vip.progressPercent}%` }"></div>
              </div>
              <p class="account-vip__hint">
                再消費 {{ vip.spendToNextTier }} LP 升級為
                <strong>{{ vip.nextTierLabel }}</strong>（{{ vip.progressPercent }}%）
              </p>
            </template>
            <p v-else class="account-vip__hint account-vip__hint--top">
              <i class="bi bi-trophy me-1" aria-hidden="true"></i>
              已達最高 VIP 等級，感謝您的支持！
            </p>
          </div>

          <div class="account-profile-actions">
            <RouterLink class="btn btn-outline-dark" to="/account">
              <i class="bi bi-geo-alt me-1" aria-hidden="true"></i>
              管理地址
            </RouterLink>
            <RouterLink class="btn btn-outline-dark" to="/account/orders">
              <i class="bi bi-receipt me-1" aria-hidden="true"></i>
              查看訂單
            </RouterLink>
          </div>
        </aside>
      </div>
    </section>
  </main>
</template>

<style scoped>
.account-vip {
  display: grid;
  gap: 0.5rem;
  margin-top: 1rem;
  padding: 0.85rem 1rem;
  border: 1px solid rgba(217, 119, 6, 0.28);
  border-radius: 0.85rem;
  background: linear-gradient(135deg, rgba(255, 247, 224, 0.9), rgba(255, 255, 255, 0.95));
}

.account-vip__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  flex-wrap: wrap;
}

.account-vip__tier {
  display: block;
  font-size: 1.15rem;
  color: var(--lb-ink, #1f2933);
}

.account-vip__spend {
  font-size: 0.82rem;
  color: var(--lb-muted, #6c757d);
}

.account-vip__bar {
  height: 0.5rem;
  border-radius: 999px;
  background: rgba(217, 119, 6, 0.16);
  overflow: hidden;
}

.account-vip__fill {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, #f59e0b, #d97706);
  transition: width 0.3s ease;
}

.account-vip__hint {
  margin: 0;
  font-size: 0.85rem;
  color: var(--lb-muted, #6c757d);
}

.account-vip__hint--top {
  color: #b45309;
}
</style>

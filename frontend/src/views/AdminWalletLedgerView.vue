<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { requestWalletAdjustmentApproval } from '@/services/adminApprovalApi'
import { createWalletAdjustment, fetchAdminWalletLedger } from '@/services/adminWalletLedgerApi'
import { useSessionStore } from '@/stores/session'

const router = useRouter()
const session = useSessionStore()

const ledgerRows = ref([])
const loading = ref(true)
const typeFilter = ref('')
const pointKindFilter = ref('')
const referenceType = ref('')
const keyword = ref('')
const errorMessage = ref('')

const typeOptions = [
  { value: '', label: '全部類型' },
  { value: 'TOP_UP', label: '現金儲值' },
  { value: 'TOP_UP_BONUS', label: '儲值贈點' },
  { value: 'DRAW_SPEND', label: '抽賞扣點' },
  { value: 'ADJUSTMENT', label: '人工調整' },
  { value: 'REFUND', label: '退款回補' },
  { value: 'COMPENSATION', label: '客服補償' },
]

const pointKindOptions = [
  { value: '', label: '全部點數' },
  { value: 'CASH', label: '現金點' },
  { value: 'BONUS', label: '紅利點' },
]

const adjustForm = reactive({
  userId: '',
  pointKind: 'BONUS',
  amount: '',
  reason: '',
})
const adjusting = ref(false)
const adjustMessage = ref('')
const adjustError = ref('')

const adjustPointKindOptions = [
  { value: 'CASH', label: '現金點' },
  { value: 'BONUS', label: '紅利點' },
]

const creditTotal = computed(() =>
  ledgerRows.value
    .filter((row) => Number(row.amount || 0) > 0)
    .reduce((sum, row) => sum + Number(row.amount || 0), 0),
)
const debitTotal = computed(() =>
  ledgerRows.value
    .filter((row) => Number(row.amount || 0) < 0)
    .reduce((sum, row) => sum + Math.abs(Number(row.amount || 0)), 0),
)
const cashCount = computed(() => ledgerRows.value.filter((row) => row.pointKind === 'CASH').length)
const bonusCount = computed(
  () => ledgerRows.value.filter((row) => row.pointKind === 'BONUS').length,
)

watch([typeFilter, pointKindFilter], async () => {
  await loadLedger()
})

onMounted(async () => {
  const user = await session.load()
  if (!user) {
    await router.replace({ path: '/admin/login', query: { redirect: '/admin/wallet-ledger' } })
    return
  }
  if (!isAdmin(user)) {
    await router.replace('/account')
    return
  }
  await loadLedger()
})

async function loadLedger() {
  loading.value = true
  errorMessage.value = ''
  try {
    ledgerRows.value = await fetchAdminWalletLedger({
      type: typeFilter.value,
      pointKind: pointKindFilter.value,
      referenceType: referenceType.value.trim(),
      q: keyword.value.trim(),
    })
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '無法載入點數流水。'
  } finally {
    loading.value = false
  }
}

async function submitFilters() {
  await loadLedger()
}

async function submitAdjustment() {
  adjustMessage.value = ''
  adjustError.value = ''
  const payload = buildAdjustmentPayload()
  if (!payload) {
    return
  }
  adjusting.value = true
  try {
    const result = await createWalletAdjustment(payload)
    adjustMessage.value = `已調整 ${result.userDisplayName}（#${result.userId}）${result.pointKindLabel} ${formatSignedPoint(result.amount)}，異動後餘額 ${formatPoint(result.balanceAfter)}。`
    adjustForm.amount = ''
    adjustForm.reason = ''
    await loadLedger()
  } catch (error) {
    adjustError.value = error.response?.data?.message || '點數調整失敗，請稍後再試。'
  } finally {
    adjusting.value = false
  }
}

async function submitAdjustmentApproval() {
  adjustMessage.value = ''
  adjustError.value = ''
  const payload = buildAdjustmentPayload()
  if (!payload) {
    return
  }
  adjusting.value = true
  try {
    const request = await requestWalletAdjustmentApproval(payload)
    adjustMessage.value = `已建立審核單 #${request.id}，可至審核中心處理。`
    adjustForm.amount = ''
    adjustForm.reason = ''
  } catch (error) {
    adjustError.value = error.response?.data?.message || '建立審核單失敗，請稍後再試。'
  } finally {
    adjusting.value = false
  }
}

function buildAdjustmentPayload() {
  const userId = Number(adjustForm.userId)
  const amount = Number(adjustForm.amount)
  if (!Number.isInteger(userId) || userId <= 0) {
    adjustError.value = '請輸入有效的會員 ID。'
    return null
  }
  if (!Number.isInteger(amount) || amount === 0) {
    adjustError.value = '調整點數需為非 0 的整數（正數加點、負數扣點）。'
    return null
  }
  if (!adjustForm.reason.trim()) {
    adjustError.value = '請填寫調整原因以利稽核。'
    return null
  }
  return {
    userId,
    pointKind: adjustForm.pointKind,
    amount,
    reason: adjustForm.reason.trim(),
  }
}

function isAdmin(user) {
  return user.role === 'SUPER_ADMIN' || user.role === 'ADMIN'
}

function amountClass(amount) {
  return Number(amount || 0) >= 0 ? 'admin-ledger-amount--credit' : 'admin-ledger-amount--debit'
}

function formatPoint(value) {
  return new Intl.NumberFormat('zh-TW').format(value || 0)
}

function formatSignedPoint(value) {
  const amount = Number(value || 0)
  const prefix = amount > 0 ? '+' : ''
  return `${prefix}${formatPoint(amount)}`
}

function formatTime(value) {
  if (!value) {
    return '-'
  }
  return new Intl.DateTimeFormat('zh-TW', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}
</script>

<template>
  <article class="admin-page admin-wallet-ledger-page">
    <div class="page-title">
      <span class="eyebrow">Admin</span>
      <h1>點數流水</h1>
    </div>

    <form class="admin-toolbar admin-ledger-toolbar" @submit.prevent="submitFilters">
      <div>
        <label class="form-label" for="adminLedgerKeyword">搜尋</label>
        <div class="input-group">
          <span class="input-group-text">
            <i class="bi bi-search" aria-hidden="true"></i>
          </span>
          <input
            id="adminLedgerKeyword"
            v-model="keyword"
            class="form-control"
            type="search"
            placeholder="流水、來源、會員或原因"
          />
        </div>
      </div>

      <div>
        <label class="form-label" for="adminLedgerType">類型</label>
        <select id="adminLedgerType" v-model="typeFilter" class="form-select">
          <option v-for="option in typeOptions" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
      </div>

      <div>
        <label class="form-label" for="adminLedgerPointKind">點數</label>
        <select id="adminLedgerPointKind" v-model="pointKindFilter" class="form-select">
          <option v-for="option in pointKindOptions" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
      </div>

      <div>
        <label class="form-label" for="adminLedgerReferenceType">來源</label>
        <input
          id="adminLedgerReferenceType"
          v-model="referenceType"
          class="form-control"
          type="search"
          placeholder="PaymentOrder"
        />
      </div>

      <button class="btn btn-dark" type="submit" :disabled="loading">
        <i class="bi bi-funnel" aria-hidden="true"></i>
        篩選
      </button>

      <div class="admin-summary-pill">
        <strong>{{ ledgerRows.length }}</strong>
        <span>目前清單</span>
      </div>
      <div class="admin-summary-pill">
        <strong>{{ formatPoint(creditTotal) }}</strong>
        <span>入點</span>
      </div>
      <div class="admin-summary-pill">
        <strong>{{ formatPoint(debitTotal) }}</strong>
        <span>扣點</span>
      </div>
      <div class="admin-summary-pill">
        <strong>{{ cashCount }}</strong>
        <span>現金點</span>
      </div>
      <div class="admin-summary-pill">
        <strong>{{ bonusCount }}</strong>
        <span>紅利點</span>
      </div>
    </form>

    <div v-if="errorMessage" class="state-panel admin-state" role="alert">
      <i class="bi bi-exclamation-triangle" aria-hidden="true"></i>
      <span>{{ errorMessage }}</span>
    </div>

    <section class="status-panel admin-adjust-panel">
      <div class="section-heading">
        <div>
          <span class="eyebrow">Adjustment</span>
          <h2>人工點數調整</h2>
        </div>
        <span class="admin-adjust-hint">正數加點、負數扣點，需填原因並寫入稽核紀錄。</span>
      </div>

      <form class="admin-adjust-form" @submit.prevent="submitAdjustment">
        <div class="admin-adjust-form__grid">
          <div>
            <label class="form-label" for="adjustUserId">會員 ID</label>
            <input
              id="adjustUserId"
              v-model="adjustForm.userId"
              class="form-control"
              type="number"
              min="1"
              placeholder="例如 12"
            />
          </div>
          <div>
            <label class="form-label" for="adjustPointKind">點數種類</label>
            <select id="adjustPointKind" v-model="adjustForm.pointKind" class="form-select">
              <option
                v-for="option in adjustPointKindOptions"
                :key="option.value"
                :value="option.value"
              >
                {{ option.label }}
              </option>
            </select>
          </div>
          <div>
            <label class="form-label" for="adjustAmount">調整點數</label>
            <input
              id="adjustAmount"
              v-model="adjustForm.amount"
              class="form-control"
              type="number"
              placeholder="+100 或 -50"
            />
          </div>
          <div class="admin-adjust-form__reason">
            <label class="form-label" for="adjustReason">調整原因</label>
            <input
              id="adjustReason"
              v-model="adjustForm.reason"
              class="form-control"
              type="text"
              maxlength="200"
              placeholder="例如：客服補償-活動延遲"
            />
          </div>
        </div>

        <div class="admin-adjust-form__foot">
          <button class="btn btn-danger" type="submit" :disabled="adjusting">
            <i class="bi bi-pencil-square me-1" aria-hidden="true"></i>
            {{ adjusting ? '送出中…' : '送出調整' }}
          </button>
          <button
            class="btn btn-outline-dark"
            type="button"
            :disabled="adjusting"
            @click="submitAdjustmentApproval"
          >
            <i class="bi bi-clipboard-check me-1" aria-hidden="true"></i>
            送審
          </button>
          <p v-if="adjustMessage" class="admin-adjust-form__ok" role="status">
            {{ adjustMessage }}
          </p>
          <p v-if="adjustError" class="admin-adjust-form__err" role="alert">{{ adjustError }}</p>
        </div>
      </form>
    </section>

    <section class="status-panel">
      <div class="section-heading">
        <div>
          <span class="eyebrow">Ledger</span>
          <h2>流水列表</h2>
        </div>
      </div>

      <div v-if="loading" class="admin-ledger-list">
        <div v-for="index in 4" :key="index" class="admin-ledger-card">
          <div class="skeleton-line w-50"></div>
          <div class="skeleton-line"></div>
          <div class="skeleton-line w-75"></div>
        </div>
      </div>

      <div v-else-if="ledgerRows.length === 0" class="empty-state">
        <i class="bi bi-wallet2" aria-hidden="true"></i>
        <strong>沒有符合條件的點數流水</strong>
      </div>

      <div v-else class="admin-ledger-list">
        <article v-for="row in ledgerRows" :key="row.id" class="admin-ledger-card">
          <header class="admin-ledger-card__head">
            <div class="admin-ledger-card__identity">
              <strong>#{{ row.id }} {{ row.typeLabel }}</strong>
              <span>{{ row.referenceType || '無來源' }} #{{ row.referenceId || '-' }}</span>
            </div>
            <span class="badge text-bg-light border">
              {{ row.pointKindLabel }}
            </span>
          </header>

          <div class="admin-ledger-card__meta">
            <span>
              <i class="bi bi-person" aria-hidden="true"></i>
              {{ row.userDisplayName }}
            </span>
            <span>
              <i class="bi bi-envelope" aria-hidden="true"></i>
              {{ row.maskedUserEmail }}
            </span>
            <span>
              <i class="bi bi-clock" aria-hidden="true"></i>
              {{ formatTime(row.createdAt) }}
            </span>
            <span>
              <i class="bi bi-person-gear" aria-hidden="true"></i>
              {{ row.createdByDisplayName || '系統' }}
            </span>
          </div>

          <div class="admin-ledger-card__stats">
            <span>
              <strong :class="amountClass(row.amount)">{{ formatSignedPoint(row.amount) }}</strong>
              異動
            </span>
            <span>
              <strong>{{ formatPoint(row.balanceAfter) }}</strong>
              異動後餘額
            </span>
          </div>

          <p class="admin-ledger-card__reason">
            {{ row.reason || '-' }}
          </p>
        </article>
      </div>
    </section>
  </article>
</template>

<style scoped>
.admin-adjust-panel {
  margin-bottom: 1rem;
}

.admin-adjust-hint {
  font-size: 0.82rem;
  color: var(--lb-muted, #6c757d);
}

.admin-adjust-form {
  display: grid;
  gap: 0.85rem;
}

.admin-adjust-form__grid {
  display: grid;
  grid-template-columns: 0.8fr 0.8fr 0.8fr 1.6fr;
  gap: 0.75rem;
}

.admin-adjust-form__foot {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem 1rem;
}

.admin-adjust-form__ok {
  margin: 0;
  font-size: 0.85rem;
  color: #0f766e;
}

.admin-adjust-form__err {
  margin: 0;
  font-size: 0.85rem;
  color: #b91c1c;
}

@media (max-width: 767.98px) {
  .admin-adjust-form__grid {
    grid-template-columns: 1fr 1fr;
  }

  .admin-adjust-form__reason {
    grid-column: 1 / -1;
  }
}
</style>

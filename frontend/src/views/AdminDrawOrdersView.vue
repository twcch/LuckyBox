<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { fetchAdminDrawOrderDetail, fetchAdminDrawOrders } from '@/services/adminDrawOrderApi'
import { useSessionStore } from '@/stores/session'

const router = useRouter()
const session = useSessionStore()

const drawOrders = ref([])
const loading = ref(true)
const statusFilter = ref('')
const campaignSlug = ref('')
const keyword = ref('')
const errorMessage = ref('')
const detail = reactive({ orderId: null, loading: false, error: '', data: null })

const statusOptions = [
  { value: '', label: '全部狀態' },
  { value: 'COMPLETED', label: '完成' },
  { value: 'PENDING', label: '處理中' },
  { value: 'FAILED', label: '失敗' },
  { value: 'REFUNDED', label: '已退款' },
]

const completedCount = computed(
  () => drawOrders.value.filter((order) => order.status === 'COMPLETED').length,
)
const totalQuantity = computed(() =>
  drawOrders.value.reduce((sum, order) => sum + Number(order.quantity || 0), 0),
)
const totalPointSpent = computed(() =>
  drawOrders.value.reduce((sum, order) => sum + Number(order.pointSpent || 0), 0),
)

watch(statusFilter, async () => {
  await loadDrawOrders()
})

onMounted(async () => {
  const user = await session.load()
  if (!user) {
    await router.replace({ path: '/admin/login', query: { redirect: '/admin/draws' } })
    return
  }
  if (!isAdmin(user)) {
    await router.replace('/account')
    return
  }
  await loadDrawOrders()
})

async function loadDrawOrders() {
  loading.value = true
  errorMessage.value = ''
  try {
    drawOrders.value = await fetchAdminDrawOrders({
      status: statusFilter.value,
      campaignSlug: campaignSlug.value.trim(),
      q: keyword.value.trim(),
    })
    if (detail.orderId && !drawOrders.value.some((order) => order.id === detail.orderId)) {
      closeDetail()
    }
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '無法載入抽賞紀錄。'
  } finally {
    loading.value = false
  }
}

async function submitFilters() {
  await loadDrawOrders()
}

async function toggleDetail(order) {
  if (detail.orderId === order.id) {
    closeDetail()
    return
  }
  detail.orderId = order.id
  detail.loading = true
  detail.error = ''
  detail.data = null
  try {
    detail.data = await fetchAdminDrawOrderDetail(order.id)
  } catch (error) {
    detail.error = error.response?.data?.message || '無法載入抽賞詳情。'
  } finally {
    detail.loading = false
  }
}

function closeDetail() {
  detail.orderId = null
  detail.loading = false
  detail.error = ''
  detail.data = null
}

function isAdmin(user) {
  return user.role === 'SUPER_ADMIN' || user.role === 'ADMIN'
}

function statusBadgeClass(status) {
  if (status === 'COMPLETED') {
    return 'text-bg-success'
  }
  if (status === 'PENDING') {
    return 'text-bg-warning'
  }
  if (status === 'FAILED') {
    return 'text-bg-danger'
  }
  return 'text-bg-light border'
}

function formatPoint(value) {
  return new Intl.NumberFormat('zh-TW').format(value || 0)
}

function formatSignedPoint(value) {
  const amount = Number(value || 0)
  const formatted = formatPoint(Math.abs(amount))
  if (amount > 0) {
    return `+${formatted}`
  }
  if (amount < 0) {
    return `-${formatted}`
  }
  return formatted
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

function pointDeltaClass(value) {
  const amount = Number(value || 0)
  if (amount > 0) {
    return 'text-success'
  }
  if (amount < 0) {
    return 'text-danger'
  }
  return 'text-muted'
}
</script>

<template>
  <article class="admin-page admin-draws-page">
    <div class="page-title">
      <span class="eyebrow">Admin</span>
      <h1>抽賞紀錄</h1>
    </div>

    <form class="admin-toolbar admin-draw-toolbar" @submit.prevent="submitFilters">
      <div>
        <label class="form-label" for="adminDrawKeyword">搜尋</label>
        <div class="input-group">
          <span class="input-group-text">
            <i class="bi bi-search" aria-hidden="true"></i>
          </span>
          <input
            id="adminDrawKeyword"
            v-model="keyword"
            class="form-control"
            type="search"
            placeholder="訂單、會員或賞池"
          />
        </div>
      </div>

      <div>
        <label class="form-label" for="adminDrawStatus">狀態</label>
        <select id="adminDrawStatus" v-model="statusFilter" class="form-select">
          <option v-for="option in statusOptions" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
      </div>

      <div>
        <label class="form-label" for="adminDrawCampaign">賞池代碼</label>
        <input
          id="adminDrawCampaign"
          v-model="campaignSlug"
          class="form-control"
          type="search"
          placeholder="campaign-slug"
        />
      </div>

      <button class="btn btn-dark" type="submit" :disabled="loading">
        <i class="bi bi-funnel" aria-hidden="true"></i>
        篩選
      </button>

      <div class="admin-summary-pill">
        <strong>{{ drawOrders.length }}</strong>
        <span>目前清單</span>
      </div>
      <div class="admin-summary-pill">
        <strong>{{ completedCount }}</strong>
        <span>完成</span>
      </div>
      <div class="admin-summary-pill">
        <strong>{{ totalQuantity }}</strong>
        <span>抽數</span>
      </div>
      <div class="admin-summary-pill">
        <strong>{{ formatPoint(totalPointSpent) }}</strong>
        <span>點數</span>
      </div>
    </form>

    <div v-if="errorMessage" class="state-panel admin-state" role="alert">
      <i class="bi bi-exclamation-triangle" aria-hidden="true"></i>
      <span>{{ errorMessage }}</span>
    </div>

    <section class="status-panel">
      <div class="section-heading">
        <div>
          <span class="eyebrow">Draws</span>
          <h2>抽賞列表</h2>
        </div>
      </div>

      <div v-if="loading" class="admin-draw-list">
        <div v-for="index in 4" :key="index" class="admin-draw-card">
          <div class="skeleton-line w-50"></div>
          <div class="skeleton-line"></div>
          <div class="skeleton-line w-75"></div>
        </div>
      </div>

      <div v-else-if="drawOrders.length === 0" class="empty-state">
        <i class="bi bi-ticket-perforated" aria-hidden="true"></i>
        <strong>沒有符合條件的抽賞紀錄</strong>
      </div>

      <div v-else class="admin-draw-list">
        <article v-for="order in drawOrders" :key="order.id" class="admin-draw-card">
          <header class="admin-draw-card__head">
            <div class="admin-draw-card__identity">
              <strong>#{{ order.id }} {{ order.campaignTitle }}</strong>
              <span>{{ order.campaignSlug }}</span>
            </div>
            <span class="badge" :class="statusBadgeClass(order.status)">
              {{ order.statusLabel }}
            </span>
          </header>

          <div class="admin-draw-card__meta">
            <span>
              <i class="bi bi-person" aria-hidden="true"></i>
              {{ order.userDisplayName }}
            </span>
            <span>
              <i class="bi bi-envelope" aria-hidden="true"></i>
              {{ order.maskedUserEmail }}
            </span>
            <span>
              <i class="bi bi-calendar-plus" aria-hidden="true"></i>
              {{ formatTime(order.createdAt) }}
            </span>
            <span>
              <i class="bi bi-check2-circle" aria-hidden="true"></i>
              {{ formatTime(order.completedAt) }}
            </span>
          </div>

          <div class="admin-draw-card__stats">
            <span>
              <strong>{{ order.quantity }}</strong>
              抽數
            </span>
            <span>
              <strong>{{ formatPoint(order.pointSpent) }}</strong>
              花費點數
            </span>
            <span>
              <strong>{{ order.resultCount }}</strong>
              結果
            </span>
          </div>

          <p class="admin-draw-card__summary">
            {{ order.prizeSummary || '-' }}
          </p>

          <div class="admin-draw-card__actions">
            <button class="btn btn-outline-dark btn-sm" type="button" @click="toggleDetail(order)">
              <i class="bi bi-card-text" aria-hidden="true"></i>
              {{ detail.orderId === order.id ? '收合詳情' : '詳情' }}
            </button>
          </div>

          <section v-if="detail.orderId === order.id" class="admin-draw-detail">
            <div v-if="detail.loading" class="admin-draw-detail__state">
              <span class="spinner-border spinner-border-sm" aria-hidden="true"></span>
              載入抽賞詳情
            </div>
            <div v-else-if="detail.error" class="admin-draw-detail__state text-danger" role="alert">
              <i class="bi bi-exclamation-triangle" aria-hidden="true"></i>
              {{ detail.error }}
            </div>
            <template v-else-if="detail.data">
              <div class="admin-draw-detail__summary-grid">
                <div>
                  <span>原始扣點</span>
                  <strong>{{ formatPoint(detail.data.originalPointSpent) }}</strong>
                </div>
                <div>
                  <span>優惠折抵</span>
                  <strong>{{ formatPoint(detail.data.discountAmount) }}</strong>
                </div>
                <div>
                  <span>實際扣點</span>
                  <strong>{{ formatPoint(detail.data.pointSpent) }}</strong>
                </div>
                <div>
                  <span>優惠碼</span>
                  <strong>{{ detail.data.couponCode || '-' }}</strong>
                </div>
                <div class="admin-draw-detail__wide">
                  <span>冪等鍵</span>
                  <code>{{ detail.data.idempotencyKey }}</code>
                </div>
              </div>

              <div class="admin-draw-detail__grid">
                <div>
                  <span class="admin-draw-detail__label">抽賞結果</span>
                  <div v-if="detail.data.results?.length" class="admin-draw-result-list">
                    <article
                      v-for="result in detail.data.results"
                      :key="result.id"
                      class="admin-draw-result"
                    >
                      <header>
                        <strong>#{{ result.resultIndex }} {{ result.prizeRank }}賞</strong>
                        <span v-if="result.lastPrize" class="badge text-bg-warning">最後賞</span>
                      </header>
                      <dl>
                        <div>
                          <dt>獎項</dt>
                          <dd>{{ result.prizeName }}</dd>
                        </div>
                        <div>
                          <dt>Ticket</dt>
                          <dd>{{ result.ticketSerialNumber }}</dd>
                        </div>
                        <div>
                          <dt>抽出時間</dt>
                          <dd>{{ formatTime(result.createdAt) }}</dd>
                        </div>
                      </dl>
                      <code>{{ result.randomProof || '-' }}</code>
                    </article>
                  </div>
                  <p v-else class="admin-draw-detail__empty">尚未產生抽賞結果。</p>
                </div>

                <div>
                  <span class="admin-draw-detail__label">點數流水</span>
                  <div v-if="detail.data.ledgerRows?.length" class="admin-draw-ledger-list">
                    <article
                      v-for="row in detail.data.ledgerRows"
                      :key="row.id"
                      class="admin-draw-ledger"
                    >
                      <header>
                        <strong>{{ row.typeLabel }}</strong>
                        <span :class="pointDeltaClass(row.amount)">
                          {{ formatSignedPoint(row.amount) }} {{ row.pointKindLabel }}
                        </span>
                      </header>
                      <dl>
                        <div>
                          <dt>異動後</dt>
                          <dd>{{ formatPoint(row.balanceAfter) }}</dd>
                        </div>
                        <div>
                          <dt>原因</dt>
                          <dd>{{ row.reason || '-' }}</dd>
                        </div>
                        <div>
                          <dt>時間</dt>
                          <dd>{{ formatTime(row.createdAt) }}</dd>
                        </div>
                      </dl>
                    </article>
                  </div>
                  <p v-else class="admin-draw-detail__empty">尚無點數流水。</p>
                </div>
              </div>
            </template>
          </section>
        </article>
      </div>
    </section>
  </article>
</template>

<style scoped>
.admin-draw-card__actions {
  margin-top: 0.75rem;
  padding-top: 0.6rem;
  border-top: 1px solid var(--bs-border-color, #e3e3e8);
}

.admin-draw-detail {
  margin-top: 0.75rem;
  padding-top: 0.75rem;
  border-top: 1px solid var(--bs-border-color, #e3e3e8);
}

.admin-draw-detail__state {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  min-height: 2.25rem;
  font-size: 0.92rem;
}

.admin-draw-detail__summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0.55rem;
  margin-bottom: 0.85rem;
}

.admin-draw-detail__summary-grid > div,
.admin-draw-result,
.admin-draw-ledger {
  min-width: 0;
  padding: 0.75rem;
  border: 1px solid var(--bs-border-color, #e3e3e8);
  border-radius: 0.5rem;
  background: #fff;
}

.admin-draw-detail__summary-grid span,
.admin-draw-detail__label {
  display: block;
  margin-bottom: 0.35rem;
  color: var(--lb-muted, #6b7280);
  font-size: 0.78rem;
  font-weight: 700;
  text-transform: uppercase;
}

.admin-draw-detail__summary-grid strong,
.admin-draw-detail__summary-grid code {
  display: block;
  min-width: 0;
  overflow-wrap: anywhere;
}

.admin-draw-detail__wide {
  grid-column: span 2;
}

.admin-draw-detail__grid {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(0, 1fr);
  gap: 0.9rem;
}

.admin-draw-result-list,
.admin-draw-ledger-list {
  display: grid;
  gap: 0.65rem;
}

.admin-draw-result header,
.admin-draw-ledger header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  margin-bottom: 0.5rem;
}

.admin-draw-result dl,
.admin-draw-ledger dl {
  display: grid;
  gap: 0.35rem;
  margin: 0;
}

.admin-draw-result dl div,
.admin-draw-ledger dl div {
  display: grid;
  grid-template-columns: 5rem minmax(0, 1fr);
  gap: 0.55rem;
}

.admin-draw-result dt,
.admin-draw-ledger dt {
  color: var(--lb-muted, #6b7280);
  font-size: 0.82rem;
  font-weight: 700;
}

.admin-draw-result dd,
.admin-draw-ledger dd {
  min-width: 0;
  margin: 0;
  overflow-wrap: anywhere;
}

.admin-draw-result code {
  display: block;
  margin-top: 0.55rem;
  padding: 0.55rem;
  overflow-wrap: anywhere;
  border-radius: 0.45rem;
  background: #111827;
  color: #e5e7eb;
  font-size: 0.75rem;
}

.admin-draw-detail__empty {
  margin: 0;
  color: var(--lb-muted, #6b7280);
  font-size: 0.9rem;
}

@media (max-width: 900px) {
  .admin-draw-detail__summary-grid,
  .admin-draw-detail__grid {
    grid-template-columns: 1fr;
  }

  .admin-draw-detail__wide {
    grid-column: auto;
  }
}

@media (max-width: 520px) {
  .admin-draw-result header,
  .admin-draw-ledger header,
  .admin-draw-result dl div,
  .admin-draw-ledger dl div {
    grid-template-columns: 1fr;
  }
}
</style>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { requestPaymentRefundApproval } from '@/services/adminApprovalApi'
import {
  fetchAdminPaymentOrderDetail,
  fetchAdminPaymentOrders,
  refundPaymentOrder,
} from '@/services/adminPaymentOrderApi'
import { useSessionStore } from '@/stores/session'

const router = useRouter()
const session = useSessionStore()

const paymentOrders = ref([])
const loading = ref(true)
const statusFilter = ref('')
const provider = ref('')
const keyword = ref('')
const errorMessage = ref('')
const successMessage = ref('')

const refund = reactive({ orderId: null, reason: '', submitting: false, error: '' })
const detail = reactive({ orderId: null, loading: false, error: '', data: null })

const statusOptions = [
  { value: '', label: '全部狀態' },
  { value: 'PENDING', label: '待付款' },
  { value: 'PAID', label: '已付款' },
  { value: 'FAILED', label: '付款失敗' },
  { value: 'CANCELED', label: '已取消' },
  { value: 'REFUNDED', label: '已退款' },
]

const paidCount = computed(
  () => paymentOrders.value.filter((order) => order.status === 'PAID').length,
)
const pendingCount = computed(
  () => paymentOrders.value.filter((order) => order.status === 'PENDING').length,
)
const totalAmount = computed(() =>
  paymentOrders.value.reduce((sum, order) => sum + Number(order.amount || 0), 0),
)
const totalPoints = computed(() =>
  paymentOrders.value.reduce((sum, order) => sum + Number(order.totalPointAmount || 0), 0),
)

watch(statusFilter, async () => {
  await loadPaymentOrders()
})

onMounted(async () => {
  const user = await session.load()
  if (!user) {
    await router.replace({ path: '/admin/login', query: { redirect: '/admin/orders' } })
    return
  }
  if (!isAdmin(user)) {
    await router.replace('/account')
    return
  }
  await loadPaymentOrders()
})

async function loadPaymentOrders() {
  loading.value = true
  errorMessage.value = ''
  try {
    paymentOrders.value = await fetchAdminPaymentOrders({
      status: statusFilter.value,
      provider: provider.value.trim(),
      q: keyword.value.trim(),
    })
    if (detail.orderId && !paymentOrders.value.some((order) => order.id === detail.orderId)) {
      closeDetail()
    }
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '無法載入付款訂單。'
  } finally {
    loading.value = false
  }
}

async function submitFilters() {
  await loadPaymentOrders()
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
    detail.data = await fetchAdminPaymentOrderDetail(order.id)
  } catch (error) {
    detail.error = error.response?.data?.message || '無法載入付款訂單詳情。'
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

function openRefund(order) {
  refund.orderId = order.id
  refund.reason = ''
  refund.error = ''
  successMessage.value = ''
}

function cancelRefund() {
  refund.orderId = null
  refund.reason = ''
  refund.error = ''
}

async function submitRefund(order) {
  if (!refund.reason.trim()) {
    refund.error = '請填寫退款原因。'
    return
  }
  refund.submitting = true
  refund.error = ''
  try {
    await refundPaymentOrder(order.id, refund.reason.trim())
    successMessage.value = `訂單 #${order.id} 已退款，已回收原入帳點數。`
    cancelRefund()
    await loadPaymentOrders()
  } catch (error) {
    refund.error = error.response?.data?.message || '退款失敗，請稍後再試。'
  } finally {
    refund.submitting = false
  }
}

async function submitRefundApproval(order) {
  if (!refund.reason.trim()) {
    refund.error = '請填寫退款原因。'
    return
  }
  refund.submitting = true
  refund.error = ''
  try {
    const request = await requestPaymentRefundApproval(order.id, refund.reason.trim())
    successMessage.value = `訂單 #${order.id} 已建立退款審核單 #${request.id}。`
    cancelRefund()
  } catch (error) {
    refund.error = error.response?.data?.message || '建立退款審核單失敗，請稍後再試。'
  } finally {
    refund.submitting = false
  }
}

function isAdmin(user) {
  return user.role === 'SUPER_ADMIN' || user.role === 'ADMIN'
}

function statusBadgeClass(status) {
  if (status === 'PAID') {
    return 'text-bg-success'
  }
  if (status === 'PENDING') {
    return 'text-bg-warning'
  }
  if (status === 'FAILED') {
    return 'text-bg-danger'
  }
  if (status === 'CANCELED' || status === 'REFUNDED') {
    return 'text-bg-secondary'
  }
  return 'text-bg-light border'
}

function processedBadgeClass(processed) {
  return processed ? 'text-bg-success' : 'text-bg-warning'
}

function formatPayload(value) {
  if (!value) {
    return '-'
  }
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}

function formatCurrency(value) {
  return `NT$ ${new Intl.NumberFormat('zh-TW').format(value || 0)}`
}

function formatPoint(value) {
  return new Intl.NumberFormat('zh-TW').format(value || 0)
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
  <article class="admin-page admin-payments-page">
    <div class="page-title">
      <span class="eyebrow">Admin</span>
      <h1>訂單管理</h1>
    </div>

    <form class="admin-toolbar admin-payment-toolbar" @submit.prevent="submitFilters">
      <div>
        <label class="form-label" for="adminPaymentKeyword">搜尋</label>
        <div class="input-group">
          <span class="input-group-text">
            <i class="bi bi-search" aria-hidden="true"></i>
          </span>
          <input
            id="adminPaymentKeyword"
            v-model="keyword"
            class="form-control"
            type="search"
            placeholder="訂單、會員或交易編號"
          />
        </div>
      </div>

      <div>
        <label class="form-label" for="adminPaymentStatus">狀態</label>
        <select id="adminPaymentStatus" v-model="statusFilter" class="form-select">
          <option v-for="option in statusOptions" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
      </div>

      <div>
        <label class="form-label" for="adminPaymentProvider">Provider</label>
        <input
          id="adminPaymentProvider"
          v-model="provider"
          class="form-control"
          type="search"
          placeholder="MOCK"
        />
      </div>

      <button class="btn btn-dark" type="submit" :disabled="loading">
        <i class="bi bi-funnel" aria-hidden="true"></i>
        篩選
      </button>

      <div class="admin-summary-pill">
        <strong>{{ paymentOrders.length }}</strong>
        <span>目前清單</span>
      </div>
      <div class="admin-summary-pill">
        <strong>{{ paidCount }}</strong>
        <span>已付款</span>
      </div>
      <div class="admin-summary-pill">
        <strong>{{ pendingCount }}</strong>
        <span>待付款</span>
      </div>
      <div class="admin-summary-pill">
        <strong>{{ formatCurrency(totalAmount) }}</strong>
        <span>金額</span>
      </div>
      <div class="admin-summary-pill">
        <strong>{{ formatPoint(totalPoints) }}</strong>
        <span>點數</span>
      </div>
    </form>

    <div v-if="errorMessage" class="state-panel admin-state" role="alert">
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
          <span class="eyebrow">Orders</span>
          <h2>付款訂單</h2>
        </div>
      </div>

      <div v-if="loading" class="admin-payment-list">
        <div v-for="index in 4" :key="index" class="admin-payment-card">
          <div class="skeleton-line w-50"></div>
          <div class="skeleton-line"></div>
          <div class="skeleton-line w-75"></div>
        </div>
      </div>

      <div v-else-if="paymentOrders.length === 0" class="empty-state">
        <i class="bi bi-receipt" aria-hidden="true"></i>
        <strong>沒有符合條件的付款訂單</strong>
      </div>

      <div v-else class="admin-payment-list">
        <article v-for="order in paymentOrders" :key="order.id" class="admin-payment-card">
          <header class="admin-payment-card__head">
            <div class="admin-payment-card__identity">
              <strong>#{{ order.id }} {{ order.merchantTradeNo }}</strong>
              <span>{{ order.provider }}</span>
            </div>
            <span class="badge" :class="statusBadgeClass(order.status)">
              {{ order.statusLabel }}
            </span>
          </header>

          <div class="admin-payment-card__meta">
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
              <i class="bi bi-credit-card" aria-hidden="true"></i>
              {{ formatTime(order.paidAt) }}
            </span>
          </div>

          <div class="admin-payment-card__stats">
            <span>
              <strong>{{ formatCurrency(order.amount) }}</strong>
              金額
            </span>
            <span>
              <strong>{{ formatPoint(order.pointAmount) }}</strong>
              現金點
            </span>
            <span>
              <strong>{{ formatPoint(order.bonusPointAmount) }}</strong>
              紅利點
            </span>
            <span>
              <strong>{{ formatPoint(order.totalPointAmount) }}</strong>
              總點數
            </span>
          </div>

          <div class="admin-payment-card__actions">
            <button class="btn btn-outline-dark btn-sm" type="button" @click="toggleDetail(order)">
              <i class="bi bi-card-text" aria-hidden="true"></i>
              {{ detail.orderId === order.id ? '收合詳情' : '詳情' }}
            </button>
            <button
              v-if="order.status === 'PAID' && refund.orderId !== order.id"
              class="btn btn-outline-danger btn-sm"
              type="button"
              @click="openRefund(order)"
            >
              <i class="bi bi-arrow-counterclockwise" aria-hidden="true"></i>
              退款
            </button>
            <div v-else-if="order.status === 'PAID'" class="admin-payment-refund">
              <input
                v-model="refund.reason"
                class="form-control form-control-sm"
                type="text"
                maxlength="200"
                placeholder="退款原因（必填）"
              />
              <button
                class="btn btn-danger btn-sm"
                type="button"
                :disabled="refund.submitting"
                @click="submitRefund(order)"
              >
                {{ refund.submitting ? '處理中…' : '確認退款' }}
              </button>
              <button
                class="btn btn-outline-dark btn-sm"
                type="button"
                :disabled="refund.submitting"
                @click="submitRefundApproval(order)"
              >
                送審退款
              </button>
              <button class="btn btn-outline-dark btn-sm" type="button" @click="cancelRefund">
                取消
              </button>
              <p v-if="refund.error" class="admin-payment-refund__err" role="alert">
                {{ refund.error }}
              </p>
            </div>
          </div>

          <section v-if="detail.orderId === order.id" class="admin-payment-detail">
            <div v-if="detail.loading" class="admin-payment-detail__state">
              <span class="spinner-border spinner-border-sm" aria-hidden="true"></span>
              載入付款詳情
            </div>
            <div v-else-if="detail.error" class="admin-payment-detail__state text-danger" role="alert">
              <i class="bi bi-exclamation-triangle" aria-hidden="true"></i>
              {{ detail.error }}
            </div>
            <template v-else-if="detail.data">
              <div class="admin-payment-detail__grid">
                <div>
                  <span class="admin-payment-detail__label">Provider payload</span>
                  <pre>{{ formatPayload(detail.data.providerPayload) }}</pre>
                </div>
                <div>
                  <span class="admin-payment-detail__label">Webhook events</span>
                  <div
                    v-if="detail.data.webhookEvents?.length"
                    class="admin-payment-webhook-list"
                  >
                    <article
                      v-for="event in detail.data.webhookEvents"
                      :key="event.eventId"
                      class="admin-payment-webhook"
                    >
                      <header>
                        <strong>{{ event.eventId }}</strong>
                        <span class="badge" :class="processedBadgeClass(event.processed)">
                          {{ event.processed ? '已處理' : '未處理' }}
                        </span>
                      </header>
                      <dl>
                        <div>
                          <dt>狀態</dt>
                          <dd>{{ event.status }}</dd>
                        </div>
                        <div>
                          <dt>金額</dt>
                          <dd>{{ formatCurrency(event.amount) }}</dd>
                        </div>
                        <div>
                          <dt>訊息</dt>
                          <dd>{{ event.message || '-' }}</dd>
                        </div>
                        <div>
                          <dt>接收時間</dt>
                          <dd>{{ formatTime(event.createdAt) }}</dd>
                        </div>
                        <div>
                          <dt>處理時間</dt>
                          <dd>{{ formatTime(event.processedAt) }}</dd>
                        </div>
                      </dl>
                      <pre>{{ formatPayload(event.rawPayload) }}</pre>
                    </article>
                  </div>
                  <p v-else class="admin-payment-detail__empty">尚未收到 webhook event。</p>
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
.admin-payment-card__actions {
  margin-top: 0.75rem;
  padding-top: 0.6rem;
  border-top: 1px solid var(--bs-border-color, #e3e3e8);
}

.admin-payment-refund {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.4rem;
}

.admin-payment-refund .form-control {
  max-width: 18rem;
}

.admin-payment-refund__err {
  flex-basis: 100%;
  margin: 0.2rem 0 0;
  font-size: 0.82rem;
  color: #b91c1c;
}

.admin-payment-detail {
  margin-top: 0.75rem;
  padding-top: 0.75rem;
  border-top: 1px solid var(--bs-border-color, #e3e3e8);
}

.admin-payment-detail__state {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  min-height: 2.25rem;
  font-size: 0.92rem;
}

.admin-payment-detail__grid {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.2fr);
  gap: 0.9rem;
}

.admin-payment-detail__label {
  display: block;
  margin-bottom: 0.35rem;
  color: var(--lb-muted, #6b7280);
  font-size: 0.78rem;
  font-weight: 700;
  text-transform: uppercase;
}

.admin-payment-detail pre,
.admin-payment-webhook pre {
  max-height: 14rem;
  margin: 0;
  padding: 0.75rem;
  overflow: auto;
  border: 1px solid var(--bs-border-color, #e3e3e8);
  border-radius: 0.5rem;
  background: #111827;
  color: #e5e7eb;
  font-size: 0.78rem;
  line-height: 1.45;
  white-space: pre-wrap;
  word-break: break-word;
}

.admin-payment-webhook-list {
  display: grid;
  gap: 0.65rem;
}

.admin-payment-webhook {
  padding: 0.7rem;
  border: 1px solid var(--bs-border-color, #e3e3e8);
  border-radius: 0.5rem;
  background: #fff;
}

.admin-payment-webhook header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  margin-bottom: 0.55rem;
}

.admin-payment-webhook dl {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.35rem 0.75rem;
  margin: 0 0 0.65rem;
}

.admin-payment-webhook dt {
  color: var(--lb-muted, #6b7280);
  font-size: 0.76rem;
}

.admin-payment-webhook dd {
  margin: 0;
  font-size: 0.9rem;
  font-weight: 700;
}

.admin-payment-detail__empty {
  margin: 0;
  color: var(--lb-muted, #6b7280);
}

@media (max-width: 991.98px) {
  .admin-payment-detail__grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 575.98px) {
  .admin-payment-webhook dl {
    grid-template-columns: 1fr;
  }
}
</style>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { fetchAccountOrders } from '@/services/accountOrderApi'
import { useSessionStore } from '@/stores/session'

const router = useRouter()
const session = useSessionStore()
const orders = ref({
  drawOrders: [],
  paymentOrders: [],
})
const activeTab = ref('draws')
const loading = ref(true)
const errorMessage = ref('')

const drawOrders = computed(() => orders.value.drawOrders || [])
const paymentOrders = computed(() => orders.value.paymentOrders || [])
const totalDrawPointSpent = computed(() =>
  drawOrders.value.reduce((sum, order) => sum + Number(order.pointSpent || 0), 0),
)
const totalDrawQuantity = computed(() =>
  drawOrders.value.reduce((sum, order) => sum + Number(order.quantity || 0), 0),
)
const totalTopUpPoint = computed(() =>
  paymentOrders.value
    .filter((order) => order.status === 'PAID')
    .reduce(
      (sum, order) => sum + Number(order.pointAmount || 0) + Number(order.bonusPointAmount || 0),
      0,
    ),
)

onMounted(async () => {
  const user = await session.load()
  if (!user) {
    await router.replace({ path: '/login', query: { redirect: '/account/orders' } })
    return
  }
  await loadOrders()
})

async function loadOrders() {
  loading.value = true
  errorMessage.value = ''
  try {
    orders.value = await fetchAccountOrders()
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '無法載入訂單紀錄。'
  } finally {
    loading.value = false
  }
}

function formatPoint(value) {
  return new Intl.NumberFormat('zh-TW').format(value || 0)
}

function formatTime(value) {
  if (!value) {
    return '-'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return '-'
  }
  return new Intl.DateTimeFormat('zh-TW', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}

function drawStatusBadge(status) {
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

function paymentStatusBadge(status) {
  if (status === 'PAID') {
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
</script>

<template>
  <main class="account-orders-page">
    <section class="container content-section">
      <RouterLink class="back-link" to="/account">
        <i class="bi bi-arrow-left" aria-hidden="true"></i>
        會員中心
      </RouterLink>

      <div class="page-title">
        <span class="eyebrow">Orders</span>
        <h1>付款與抽賞紀錄</h1>
        <p>查看最近的儲值訂單、抽賞扣點與中獎摘要。</p>
      </div>

      <div v-if="errorMessage" class="state-panel account-orders-state" role="alert">
        <i class="bi bi-exclamation-triangle" aria-hidden="true"></i>
        <span>{{ errorMessage }}</span>
      </div>

      <div class="status-grid account-orders-metrics">
        <div>
          <strong>{{ drawOrders.length }}</strong>
          <span>抽賞訂單</span>
        </div>
        <div>
          <strong>{{ formatPoint(totalDrawQuantity) }}</strong>
          <span>累積抽數</span>
        </div>
        <div>
          <strong>{{ formatPoint(totalDrawPointSpent) }} LP</strong>
          <span>抽賞扣點</span>
        </div>
        <div>
          <strong>{{ paymentOrders.length }}</strong>
          <span>付款訂單</span>
        </div>
        <div>
          <strong>{{ formatPoint(totalTopUpPoint) }} LP</strong>
          <span>已入點數</span>
        </div>
      </div>

      <section class="status-panel account-orders-panel">
        <div class="account-orders-tabs" role="tablist" aria-label="訂單類型">
          <button
            class="btn btn-sm"
            :class="activeTab === 'draws' ? 'btn-dark' : 'btn-outline-dark'"
            type="button"
            role="tab"
            :aria-selected="activeTab === 'draws'"
            @click="activeTab = 'draws'"
          >
            <i class="bi bi-stars" aria-hidden="true"></i>
            抽賞紀錄
          </button>
          <button
            class="btn btn-sm"
            :class="activeTab === 'payments' ? 'btn-dark' : 'btn-outline-dark'"
            type="button"
            role="tab"
            :aria-selected="activeTab === 'payments'"
            @click="activeTab = 'payments'"
          >
            <i class="bi bi-credit-card" aria-hidden="true"></i>
            付款訂單
          </button>
          <button class="btn btn-sm btn-outline-dark ms-auto" type="button" @click="loadOrders">
            <i class="bi bi-arrow-clockwise" aria-hidden="true"></i>
            重新整理
          </button>
        </div>

        <div v-if="loading" class="account-order-list">
          <article v-for="index in 4" :key="index" class="account-order-card">
            <div class="skeleton-line w-50"></div>
            <div class="skeleton-line"></div>
            <div class="skeleton-line w-75"></div>
          </article>
        </div>

        <div v-else-if="activeTab === 'draws'">
          <div v-if="drawOrders.length === 0" class="empty-state">
            <i class="bi bi-ticket-perforated" aria-hidden="true"></i>
            <strong>尚無抽賞紀錄</strong>
            <span>完成抽賞後，紀錄會出現在這裡。</span>
          </div>

          <div v-else class="account-order-list">
            <article v-for="order in drawOrders" :key="order.id" class="account-order-card">
              <header class="account-order-card__head">
                <div>
                  <strong>#{{ order.id }} {{ order.campaignTitle }}</strong>
                  <span>{{ order.campaignSlug }}</span>
                </div>
                <span class="badge" :class="drawStatusBadge(order.status)">
                  {{ order.statusLabel }}
                </span>
              </header>

              <div class="account-order-card__stats">
                <span>
                  <strong>{{ order.quantity }}</strong>
                  抽數
                </span>
                <span>
                  <strong>{{ formatPoint(order.pointSpent) }}</strong>
                  實付 LP
                </span>
                <span>
                  <strong>{{ order.discountAmount }}</strong>
                  折抵 LP
                </span>
                <span>
                  <strong>{{ order.resultCount }}</strong>
                  結果
                </span>
              </div>

              <div class="account-order-card__meta">
                <span>
                  <i class="bi bi-calendar-plus" aria-hidden="true"></i>
                  建立 {{ formatTime(order.createdAt) }}
                </span>
                <span>
                  <i class="bi bi-check2-circle" aria-hidden="true"></i>
                  完成 {{ formatTime(order.completedAt) }}
                </span>
                <span v-if="order.couponCode">
                  <i class="bi bi-ticket-perforated" aria-hidden="true"></i>
                  {{ order.couponCode }}
                </span>
              </div>

              <div class="account-order-card__results">
                <span v-for="result in order.results" :key="result.id">
                  {{ result.prizeRank }}賞 {{ result.prizeName }}
                  <small v-if="result.lastPrize">最後賞</small>
                </span>
              </div>

              <RouterLink class="btn btn-sm btn-outline-dark" :to="`/kuji/${order.campaignSlug}`">
                <i class="bi bi-box2-heart" aria-hidden="true"></i>
                查看賞池
              </RouterLink>
            </article>
          </div>
        </div>

        <div v-else>
          <div v-if="paymentOrders.length === 0" class="empty-state">
            <i class="bi bi-credit-card" aria-hidden="true"></i>
            <strong>尚無付款訂單</strong>
            <span>完成儲值後，付款訂單會出現在這裡。</span>
          </div>

          <div v-else class="account-order-list">
            <article v-for="order in paymentOrders" :key="order.id" class="account-order-card">
              <header class="account-order-card__head">
                <div>
                  <strong>#{{ order.id }} {{ order.merchantTradeNo }}</strong>
                  <span>{{ order.provider || 'Payment' }}</span>
                </div>
                <span class="badge" :class="paymentStatusBadge(order.status)">
                  {{ order.statusLabel }}
                </span>
              </header>

              <div class="account-order-card__stats">
                <span>
                  <strong>NT$ {{ formatPoint(order.amount) }}</strong>
                  付款金額
                </span>
                <span>
                  <strong>{{ formatPoint(order.pointAmount) }}</strong>
                  現金點
                </span>
                <span>
                  <strong>{{ formatPoint(order.bonusPointAmount) }}</strong>
                  贈點
                </span>
              </div>

              <div class="account-order-card__meta">
                <span>
                  <i class="bi bi-calendar-plus" aria-hidden="true"></i>
                  建立 {{ formatTime(order.createdAt) }}
                </span>
                <span>
                  <i class="bi bi-check2-circle" aria-hidden="true"></i>
                  付款 {{ formatTime(order.paidAt) }}
                </span>
              </div>
            </article>
          </div>
        </div>
      </section>
    </section>
  </main>
</template>

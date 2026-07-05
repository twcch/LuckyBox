<script setup>
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { fetchShipments } from '@/services/prizeBoxApi'
import { useSessionStore } from '@/stores/session'

const router = useRouter()
const session = useSessionStore()
const shipments = ref([])
const loading = ref(true)
const errorMessage = ref('')

const requestedCount = computed(
  () => shipments.value.filter((shipment) => shipment.status === 'REQUESTED').length,
)
const shippedCount = computed(
  () => shipments.value.filter((shipment) => shipment.status === 'SHIPPED').length,
)
const deliveredCount = computed(
  () => shipments.value.filter((shipment) => shipment.status === 'DELIVERED').length,
)
const totalItemCount = computed(() =>
  shipments.value.reduce((sum, shipment) => sum + Number(shipment.itemCount || 0), 0),
)
const totalShippingFee = computed(() =>
  shipments.value.reduce((sum, shipment) => sum + Number(shipment.shippingFee || 0), 0),
)

onMounted(async () => {
  const user = await session.load()
  if (!user) {
    await router.replace({ path: '/login', query: { redirect: '/account/shipments' } })
    return
  }
  await loadShipments()
})

async function loadShipments() {
  loading.value = true
  errorMessage.value = ''
  try {
    shipments.value = await fetchShipments()
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '無法載入出貨紀錄。'
  } finally {
    loading.value = false
  }
}

function statusLabel(status) {
  const labels = {
    REQUESTED: '待處理',
    PACKING: '包裝中',
    SHIPMENT_REQUESTED: '已申請',
    SHIPPED: '已出貨',
    DELIVERED: '已送達',
    RETURNED: '已退回',
    CANCELED: '已取消',
  }
  return labels[status] || status
}

function statusBadgeClass(status) {
  if (status === 'REQUESTED') {
    return 'text-bg-warning'
  }
  if (status === 'SHIPPED') {
    return 'text-bg-primary'
  }
  if (status === 'DELIVERED') {
    return 'text-bg-success'
  }
  return 'text-bg-light border'
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
</script>

<template>
  <main class="account-shipments-page">
    <section class="container content-section">
      <RouterLink class="back-link" to="/account">
        <i class="bi bi-arrow-left" aria-hidden="true"></i>
        會員中心
      </RouterLink>

      <div class="page-title">
        <span class="eyebrow">Shipments</span>
        <h1>出貨紀錄</h1>
        <p>查看已建立的出貨申請、處理狀態、收件地區與出貨品項。</p>
      </div>

      <div v-if="errorMessage" class="state-panel account-shipments-state" role="alert">
        <i class="bi bi-exclamation-triangle" aria-hidden="true"></i>
        <span>{{ errorMessage }}</span>
      </div>

      <div class="status-grid account-shipments-metrics">
        <div>
          <strong>{{ shipments.length }}</strong>
          <span>出貨單</span>
        </div>
        <div>
          <strong>{{ requestedCount }}</strong>
          <span>待處理</span>
        </div>
        <div>
          <strong>{{ shippedCount }}</strong>
          <span>已出貨</span>
        </div>
        <div>
          <strong>{{ deliveredCount }}</strong>
          <span>已送達</span>
        </div>
        <div>
          <strong>{{ totalItemCount }}</strong>
          <span>出貨品項</span>
        </div>
        <div>
          <strong>{{ formatPoint(totalShippingFee) }} LP</strong>
          <span>累計運費</span>
        </div>
      </div>

      <section class="status-panel account-shipments-panel">
        <div class="section-heading">
          <div>
            <span class="eyebrow">History</span>
            <h2>最近出貨</h2>
          </div>
          <button
            class="btn btn-sm btn-outline-dark"
            type="button"
            :disabled="loading"
            @click="loadShipments"
          >
            <i class="bi bi-arrow-clockwise" aria-hidden="true"></i>
            重新整理
          </button>
        </div>

        <div v-if="loading" class="account-shipment-list">
          <article v-for="index in 4" :key="index" class="account-shipment-card">
            <div class="skeleton-line w-50"></div>
            <div class="skeleton-line"></div>
            <div class="skeleton-line w-75"></div>
          </article>
        </div>

        <div v-else-if="shipments.length === 0" class="empty-state">
          <i class="bi bi-truck" aria-hidden="true"></i>
          <strong>尚無出貨紀錄</strong>
          <span>到戰利品盒選擇商品並建立出貨申請後，會出現在這裡。</span>
          <RouterLink class="btn btn-danger" to="/account/prizes">
            <i class="bi bi-box-seam" aria-hidden="true"></i>
            前往戰利品盒
          </RouterLink>
        </div>

        <div v-else class="account-shipment-list">
          <article v-for="shipment in shipments" :key="shipment.id" class="account-shipment-card">
            <header class="account-shipment-card__head">
              <div>
                <strong>#{{ shipment.id }} 出貨申請</strong>
                <span
                  >{{ shipment.recipientName }}｜{{ shipment.city }}{{ shipment.district }}</span
                >
              </div>
              <span class="badge" :class="statusBadgeClass(shipment.status)">
                {{ statusLabel(shipment.status) }}
              </span>
            </header>

            <div class="account-shipment-card__stats">
              <span>
                <strong>{{ shipment.itemCount }}</strong>
                件商品
              </span>
              <span>
                <strong>{{ formatPoint(shipment.shippingFee) }}</strong>
                LP 運費
              </span>
              <span>
                <strong>{{ formatTime(shipment.requestedAt) }}</strong>
                申請時間
              </span>
            </div>

            <div class="account-shipment-card__items">
              <span v-for="item in shipment.items" :key="item.id">
                {{ item.prizeRank }}賞 {{ item.prizeName }}
                <small>{{ item.campaignTitle }}</small>
              </span>
            </div>

            <div class="account-shipment-card__actions">
              <RouterLink
                class="btn btn-sm btn-outline-dark"
                :to="`/account/shipments/${shipment.id}`"
              >
                <i class="bi bi-search" aria-hidden="true"></i>
                查看明細
              </RouterLink>
            </div>
          </article>
        </div>
      </section>
    </section>
  </main>
</template>

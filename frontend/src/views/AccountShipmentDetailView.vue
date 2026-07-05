<script setup>
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { fetchShipment } from '@/services/prizeBoxApi'
import { useSessionStore } from '@/stores/session'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()

const shipment = ref(null)
const loading = ref(true)
const errorMessage = ref('')

const shipmentId = computed(() => String(route.params.shipmentId || ''))
const fullAddress = computed(() => {
  if (!shipment.value) {
    return '-'
  }
  return [
    shipment.value.postalCode,
    shipment.value.city,
    shipment.value.district,
    shipment.value.addressLine,
  ]
    .filter(Boolean)
    .join(' ')
})
const timelineSteps = computed(() => {
  const current = shipment.value?.status
  return [
    {
      key: 'REQUESTED',
      label: '已申請',
      time: shipment.value?.requestedAt,
      active: Boolean(shipment.value?.requestedAt),
    },
    {
      key: 'SHIPPED',
      label: '已出貨',
      time: shipment.value?.shippedAt,
      active:
        current === 'SHIPPED' || current === 'DELIVERED' || Boolean(shipment.value?.shippedAt),
    },
    {
      key: 'DELIVERED',
      label: '已送達',
      time: shipment.value?.deliveredAt,
      active: current === 'DELIVERED' || Boolean(shipment.value?.deliveredAt),
    },
  ]
})

onMounted(async () => {
  const user = await session.load()
  if (!user) {
    await router.replace({
      path: '/login',
      query: { redirect: `/account/shipments/${shipmentId.value}` },
    })
    return
  }
  await loadShipment()
})

async function loadShipment() {
  loading.value = true
  errorMessage.value = ''
  try {
    shipment.value = await fetchShipment(shipmentId.value)
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '無法載入出貨詳情。'
    shipment.value = null
  } finally {
    loading.value = false
  }
}

function statusLabel(status) {
  const labels = {
    REQUESTED: '待處理',
    PACKING: '包裝中',
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
  <main class="account-shipment-detail-page">
    <section class="container content-section">
      <RouterLink class="back-link" to="/account/shipments">
        <i class="bi bi-arrow-left" aria-hidden="true"></i>
        出貨紀錄
      </RouterLink>

      <div class="page-title">
        <span class="eyebrow">Shipment</span>
        <h1>出貨單 #{{ shipmentId }}</h1>
        <p>查看物流狀態、收件資訊與出貨品項。</p>
      </div>

      <div v-if="errorMessage" class="state-panel account-shipments-state" role="alert">
        <i class="bi bi-exclamation-triangle" aria-hidden="true"></i>
        <span>{{ errorMessage }}</span>
      </div>

      <div v-if="loading" class="account-shipment-detail-grid">
        <section class="status-panel account-shipment-detail-panel">
          <div class="skeleton-line w-50"></div>
          <div class="skeleton-line"></div>
          <div class="skeleton-line w-75"></div>
        </section>
        <section class="status-panel account-shipment-detail-panel">
          <div class="skeleton-line w-50"></div>
          <div class="skeleton-line"></div>
          <div class="skeleton-line w-75"></div>
        </section>
      </div>

      <div v-else-if="shipment" class="account-shipment-detail-grid">
        <section class="status-panel account-shipment-detail-panel">
          <div class="section-heading">
            <div>
              <span class="eyebrow">Status</span>
              <h2>物流狀態</h2>
            </div>
            <span class="badge" :class="statusBadgeClass(shipment.status)">
              {{ statusLabel(shipment.status) }}
            </span>
          </div>

          <div class="shipment-tracking-summary">
            <div>
              <span>物流商</span>
              <strong>{{ shipment.carrier || '待後台更新' }}</strong>
            </div>
            <div>
              <span>追蹤碼</span>
              <strong>{{ shipment.trackingNumber || '尚未產生' }}</strong>
            </div>
            <div>
              <span>運費</span>
              <strong>{{ formatPoint(shipment.shippingFee) }} LP</strong>
            </div>
            <div>
              <span>品項數</span>
              <strong>{{ shipment.itemCount }} 件</strong>
            </div>
          </div>

          <ol class="shipment-timeline" aria-label="出貨時間線">
            <li
              v-for="step in timelineSteps"
              :key="step.key"
              :class="{ 'shipment-timeline__item--active': step.active }"
            >
              <span class="shipment-timeline__dot" aria-hidden="true"></span>
              <div>
                <strong>{{ step.label }}</strong>
                <time>{{ formatTime(step.time) }}</time>
              </div>
            </li>
          </ol>
        </section>

        <section class="status-panel account-shipment-detail-panel">
          <div class="section-heading">
            <div>
              <span class="eyebrow">Recipient</span>
              <h2>收件資訊</h2>
            </div>
          </div>

          <dl class="shipment-recipient-list">
            <div>
              <dt>收件人</dt>
              <dd>{{ shipment.recipientName || '-' }}</dd>
            </div>
            <div>
              <dt>電話</dt>
              <dd>{{ shipment.phone || '-' }}</dd>
            </div>
            <div>
              <dt>地址</dt>
              <dd>{{ fullAddress }}</dd>
            </div>
            <div>
              <dt>申請時間</dt>
              <dd>{{ formatTime(shipment.requestedAt) }}</dd>
            </div>
          </dl>
        </section>

        <section class="status-panel account-shipment-detail-panel account-shipment-items-panel">
          <div class="section-heading">
            <div>
              <span class="eyebrow">Items</span>
              <h2>出貨品項</h2>
            </div>
          </div>

          <div class="account-shipment-detail-items">
            <article v-for="item in shipment.items" :key="item.id">
              <span class="badge text-bg-light border">{{ item.prizeRank }}賞</span>
              <div>
                <strong>{{ item.prizeName }}</strong>
                <span>{{ item.campaignTitle }}</span>
                <small v-if="item.ticketSerialNumber">票券 {{ item.ticketSerialNumber }}</small>
              </div>
            </article>
          </div>
        </section>
      </div>

      <div v-else class="empty-state">
        <i class="bi bi-truck" aria-hidden="true"></i>
        <strong>找不到出貨單</strong>
        <span>請回出貨紀錄查看目前可查詢的出貨單。</span>
        <RouterLink class="btn btn-dark" to="/account/shipments">
          <i class="bi bi-list-ul" aria-hidden="true"></i>
          返回出貨紀錄
        </RouterLink>
      </div>
    </section>
  </main>
</template>

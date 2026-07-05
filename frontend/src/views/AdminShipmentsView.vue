<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  fetchAdminShipments,
  resolveAdminShipment,
  updateAdminShipment,
} from '@/services/adminShipmentApi'
import { useSessionStore } from '@/stores/session'

const router = useRouter()
const session = useSessionStore()

const shipments = ref([])
const loading = ref(true)
const saving = ref(false)
const statusFilter = ref('REQUESTED')
const selectedShipmentId = ref(null)
const errorMessage = ref('')
const successMessage = ref('')

const form = reactive({
  status: 'REQUESTED',
  carrier: '',
  trackingNumber: '',
  adminNote: '',
})

const resolveForm = reactive({ resolution: 'RETURNED', reason: '', submitting: false })

const statusOptions = [
  { value: '', label: '全部狀態' },
  { value: 'REQUESTED', label: '待處理' },
  { value: 'SHIPPED', label: '已出貨' },
  { value: 'DELIVERED', label: '已送達' },
  { value: 'RETURNED', label: '已退回' },
  { value: 'EXCHANGED', label: '換貨處理' },
]

const selectedShipment = computed(
  () => shipments.value.find((shipment) => shipment.id === selectedShipmentId.value) || null,
)
const requestedCount = computed(
  () => shipments.value.filter((shipment) => shipment.status === 'REQUESTED').length,
)

watch(statusFilter, async () => {
  await loadShipments()
})

watch(selectedShipment, (shipment) => {
  syncForm(shipment)
})

onMounted(async () => {
  const user = await session.load()
  if (!user) {
    await router.replace({ path: '/login', query: { redirect: '/admin/shipments' } })
    return
  }
  if (!isAdmin(user)) {
    await router.replace('/account')
    return
  }
  await loadShipments()
})

async function loadShipments() {
  loading.value = true
  errorMessage.value = ''
  try {
    shipments.value = await fetchAdminShipments({ status: statusFilter.value })
    if (!selectedShipment.value) {
      selectedShipmentId.value = shipments.value[0]?.id || null
    }
    if (selectedShipment.value) {
      syncForm(selectedShipment.value)
    }
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '無法載入出貨單。'
  } finally {
    loading.value = false
  }
}

async function submitShipment() {
  if (!selectedShipment.value) {
    return
  }
  saving.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const updated = await updateAdminShipment(selectedShipment.value.id, {
      status: form.status,
      carrier: form.carrier,
      trackingNumber: form.trackingNumber,
      adminNote: form.adminNote,
    })
    successMessage.value = `出貨單 #${updated.id} 已更新為${statusLabel(updated.status)}。`
    selectedShipmentId.value = updated.id
    if (statusFilter.value && statusFilter.value !== updated.status) {
      statusFilter.value = updated.status
      return
    }
    await loadShipments()
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '出貨單更新失敗。'
  } finally {
    saving.value = false
  }
}

async function submitResolution() {
  if (!selectedShipment.value) {
    return
  }
  if (!resolveForm.reason.trim()) {
    errorMessage.value = '請填寫退回/換貨原因。'
    return
  }
  resolveForm.submitting = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const updated = await resolveAdminShipment(selectedShipment.value.id, {
      resolution: resolveForm.resolution,
      reason: resolveForm.reason.trim(),
    })
    successMessage.value =
      updated.status === 'RETURNED'
        ? `出貨單 #${updated.id} 已退回，戰利品已退回會員戰利品盒。`
        : `出貨單 #${updated.id} 已受理換貨。`
    resolveForm.reason = ''
    selectedShipmentId.value = updated.id
    if (statusFilter.value && statusFilter.value !== updated.status) {
      statusFilter.value = updated.status
      return
    }
    await loadShipments()
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '退回/換貨處理失敗。'
  } finally {
    resolveForm.submitting = false
  }
}

function syncForm(shipment) {
  form.status = shipment?.status || 'REQUESTED'
  form.carrier = shipment?.carrier || ''
  form.trackingNumber = shipment?.trackingNumber || ''
  form.adminNote = shipment?.adminNote || ''
}

function selectShipment(shipment) {
  selectedShipmentId.value = shipment.id
}

function isAdmin(user) {
  return user.role === 'SUPER_ADMIN' || user.role === 'ADMIN'
}

function statusLabel(status) {
  const labels = {
    REQUESTED: '待處理',
    SHIPPED: '已出貨',
    DELIVERED: '已送達',
    RETURNED: '已退回',
    EXCHANGED: '換貨處理',
  }
  return labels[status] || status
}

function statusBadgeClass(status) {
  if (status === 'REQUESTED') {
    return 'text-bg-warning'
  }
  if (status === 'SHIPPED') {
    return 'text-bg-danger'
  }
  return 'text-bg-light border'
}

function formatTime(value) {
  if (!value) {
    return '-'
  }
  return new Intl.DateTimeFormat('zh-TW', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}
</script>

<template>
  <article class="admin-page admin-shipments-page">
    <div class="page-title">
      <span class="eyebrow">Admin</span>
      <h1>出貨管理</h1>
      <p>查看會員出貨申請，更新物流狀態、物流商與追蹤碼。</p>
    </div>

    <div class="admin-toolbar">
      <div>
        <label class="form-label" for="adminShipmentStatus">狀態</label>
        <select id="adminShipmentStatus" v-model="statusFilter" class="form-select">
          <option v-for="option in statusOptions" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
      </div>
      <div class="admin-summary-pill">
        <strong>{{ shipments.length }}</strong>
        <span>目前清單</span>
      </div>
      <div class="admin-summary-pill">
        <strong>{{ requestedCount }}</strong>
        <span>待處理</span>
      </div>
    </div>

    <div v-if="errorMessage" class="state-panel admin-state" role="alert">
      <i class="bi bi-exclamation-triangle" aria-hidden="true"></i>
      <span>{{ errorMessage }}</span>
    </div>

    <div v-if="successMessage" class="toast show lb-toast admin-toast" role="status">
      <div class="toast-body">
        <i class="bi bi-check-circle me-1" aria-hidden="true"></i>
        {{ successMessage }}
      </div>
    </div>

    <div class="admin-shipments-layout">
      <section class="status-panel admin-shipment-list-panel">
        <div class="section-heading">
          <div>
            <span class="eyebrow">Queue</span>
            <h2>出貨列表</h2>
          </div>
        </div>

        <div v-if="loading" class="admin-shipment-list">
          <div v-for="index in 3" :key="index" class="admin-shipment-card">
            <div class="skeleton-line w-50"></div>
            <div class="skeleton-line"></div>
          </div>
        </div>

        <div v-else-if="shipments.length === 0" class="empty-state">
          <i class="bi bi-truck" aria-hidden="true"></i>
          <strong>沒有符合條件的出貨單</strong>
          <span>會員建立出貨申請後會出現在這裡。</span>
        </div>

        <div v-else class="admin-shipment-list">
          <button
            v-for="shipment in shipments"
            :key="shipment.id"
            class="admin-shipment-card"
            :class="{ 'admin-shipment-card--active': shipment.id === selectedShipmentId }"
            type="button"
            @click="selectShipment(shipment)"
          >
            <span class="admin-shipment-card__head">
              <strong>#{{ shipment.id }} {{ shipment.userDisplayName }}</strong>
              <span class="badge" :class="statusBadgeClass(shipment.status)">
                {{ statusLabel(shipment.status) }}
              </span>
            </span>
            <span>{{ shipment.userEmail }}</span>
            <span>{{ shipment.recipientName }}｜{{ shipment.city }}{{ shipment.district }}</span>
            <span class="admin-shipment-card__meta">
              <span>{{ shipment.itemCount }} 件</span>
              <time>{{ formatTime(shipment.requestedAt) }}</time>
            </span>
          </button>
        </div>
      </section>

      <aside class="status-panel admin-shipment-editor">
        <div v-if="selectedShipment">
          <span class="eyebrow">Shipment #{{ selectedShipment.id }}</span>
          <h2>{{ selectedShipment.recipientName }}</h2>
          <p>
            {{ selectedShipment.phone }}｜{{ selectedShipment.postalCode }}
            {{ selectedShipment.city }}{{ selectedShipment.district
            }}{{ selectedShipment.addressLine }}
          </p>

          <form class="admin-shipment-form" @submit.prevent="submitShipment">
            <div>
              <label class="form-label" for="shipmentStatus">物流狀態</label>
              <select id="shipmentStatus" v-model="form.status" class="form-select">
                <option value="REQUESTED">待處理</option>
                <option value="SHIPPED">已出貨</option>
                <option value="DELIVERED">已送達</option>
              </select>
            </div>
            <div>
              <label class="form-label" for="shipmentCarrier">物流商</label>
              <input
                id="shipmentCarrier"
                v-model.trim="form.carrier"
                class="form-control"
                placeholder="黑貓宅急便"
              />
            </div>
            <div>
              <label class="form-label" for="trackingNumber">追蹤碼</label>
              <input
                id="trackingNumber"
                v-model.trim="form.trackingNumber"
                class="form-control"
                placeholder="TA123456789"
              />
            </div>
            <div>
              <label class="form-label" for="adminNote">備註</label>
              <textarea
                id="adminNote"
                v-model.trim="form.adminNote"
                class="form-control"
                rows="3"
              ></textarea>
            </div>
            <button class="btn btn-danger btn-lg" type="submit" :disabled="saving">
              <i class="bi bi-save me-2" aria-hidden="true"></i>
              {{ saving ? '更新中' : '更新出貨單' }}
            </button>
          </form>

          <form
            v-if="selectedShipment.status === 'SHIPPED' || selectedShipment.status === 'DELIVERED'"
            class="admin-shipment-resolve"
            @submit.prevent="submitResolution"
          >
            <div class="section-heading">
              <div>
                <span class="eyebrow">Defect / Exchange</span>
                <h2>退回 / 換貨</h2>
              </div>
            </div>
            <div>
              <label class="form-label" for="resolveResolution">處理方式</label>
              <select id="resolveResolution" v-model="resolveForm.resolution" class="form-select">
                <option value="RETURNED">退回（戰利品回到會員戰利品盒，可重新申請出貨）</option>
                <option value="EXCHANGED">換貨（標記瑕疵換貨，由客服接續處理）</option>
              </select>
            </div>
            <div>
              <label class="form-label" for="resolveReason">原因</label>
              <input
                id="resolveReason"
                v-model.trim="resolveForm.reason"
                class="form-control"
                maxlength="200"
                placeholder="例如：包裹破損、商品瑕疵"
              />
            </div>
            <button class="btn btn-outline-danger" type="submit" :disabled="resolveForm.submitting">
              <i class="bi bi-arrow-counterclockwise me-1" aria-hidden="true"></i>
              {{ resolveForm.submitting ? '處理中…' : '送出退回/換貨' }}
            </button>
          </form>

          <div class="admin-shipment-items">
            <div class="section-heading">
              <div>
                <span class="eyebrow">Items</span>
                <h2>出貨內容</h2>
              </div>
            </div>
            <article
              v-for="item in selectedShipment.items"
              :key="item.id"
              class="admin-shipment-item"
            >
              <span class="rank-badge">{{ item.prizeRank }}</span>
              <span>
                <strong>{{ item.prizeName }}</strong>
                <small>{{ item.campaignTitle }}｜{{ item.ticketSerialNumber || '無籤號' }}</small>
              </span>
              <span class="badge" :class="statusBadgeClass(selectedShipment.status)">
                {{ statusLabel(selectedShipment.status) }}
              </span>
            </article>
          </div>
        </div>

        <div v-else class="empty-state">
          <i class="bi bi-inbox" aria-hidden="true"></i>
          <strong>尚未選擇出貨單</strong>
          <span>從左側列表選一筆出貨申請。</span>
        </div>
      </aside>
    </div>
  </article>
</template>

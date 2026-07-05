<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { fetchAddresses } from '@/services/authApi'
import { fetchAccountCoupons } from '@/services/accountCouponApi'
import { fetchNotifications, markNotificationRead } from '@/services/notificationApi'
import { createShipment, fetchPrizeBox, fetchShipments } from '@/services/prizeBoxApi'
import { useSessionStore } from '@/stores/session'

const router = useRouter()
const session = useSessionStore()

const overview = ref({
  items: [],
  campaigns: [],
  statusCounts: {},
  status: null,
  campaignSlug: null,
})
const notificationOverview = ref({
  unreadCount: 0,
  items: [],
})
const shipments = ref([])
const addresses = ref([])
const coupons = ref([])
const selectedPrizeIds = ref([])
const selectedAddressId = ref(null)
const selectedCouponId = ref('')
const statusFilter = ref('')
const campaignFilter = ref('')
const loading = ref(true)
const shipmentsLoading = ref(true)
const addressesLoading = ref(true)
const couponsLoading = ref(true)
const notificationsLoading = ref(true)
const submitting = ref(false)
const errorMessage = ref('')
const successMessage = ref('')

const statusOptions = [
  { value: '', label: '全部狀態' },
  { value: 'IN_BOX', label: '可申請出貨' },
  { value: 'SHIPMENT_REQUESTED', label: '已申請出貨' },
  { value: 'SHIPPED', label: '已出貨' },
  { value: 'DELIVERED', label: '已送達' },
]

const items = computed(() => overview.value.items || [])
const notifications = computed(() => notificationOverview.value.items || [])
const campaigns = computed(() => overview.value.campaigns || [])
const statusCounts = computed(() => overview.value.statusCounts || {})
const shippableItemIds = computed(
  () => new Set(items.value.filter((item) => isShippable(item)).map((item) => item.id)),
)
const selectedItems = computed(() =>
  items.value.filter((item) => selectedPrizeIds.value.includes(item.id) && isShippable(item)),
)
const selectedCount = computed(() => selectedItems.value.length)
const freeShippingCoupons = computed(() =>
  coupons.value.filter((coupon) => coupon.type === 'FREE_SHIPPING'),
)
const selectedFreeShippingCoupon = computed(() =>
  freeShippingCoupons.value.find((coupon) => coupon.id === Number(selectedCouponId.value)),
)
const shippingFee = computed(() =>
  selectedCount.value > 0 && !selectedFreeShippingCoupon.value ? 80 : 0,
)
const canSubmitShipment = computed(
  () => selectedCount.value > 0 && Boolean(selectedAddressId.value) && !submitting.value,
)

watch([statusFilter, campaignFilter], async () => {
  await loadPrizeBox()
})

onMounted(async () => {
  const user = await session.load()
  if (!user) {
    await router.replace({ path: '/login', query: { redirect: '/account/prizes' } })
    return
  }
  await Promise.all([
    loadPrizeBox(),
    loadAddresses(),
    loadShipments(),
    loadNotifications(),
    loadCoupons(),
  ])
})

async function loadPrizeBox() {
  loading.value = true
  errorMessage.value = ''
  try {
    overview.value = await fetchPrizeBox({
      status: statusFilter.value,
      campaignSlug: campaignFilter.value,
    })
    pruneSelection()
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '無法載入戰利品。'
  } finally {
    loading.value = false
  }
}

async function loadAddresses() {
  addressesLoading.value = true
  try {
    addresses.value = await fetchAddresses()
    const defaultAddress =
      addresses.value.find((address) => address.defaultAddress) || addresses.value[0]
    if (defaultAddress && !selectedAddressId.value) {
      selectedAddressId.value = defaultAddress.id
    }
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '無法載入收件地址。'
  } finally {
    addressesLoading.value = false
  }
}

async function loadCoupons() {
  couponsLoading.value = true
  try {
    coupons.value = await fetchAccountCoupons()
    if (
      selectedCouponId.value &&
      !freeShippingCoupons.value.some((coupon) => coupon.id === Number(selectedCouponId.value))
    ) {
      selectedCouponId.value = ''
    }
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '無法載入免運券。'
  } finally {
    couponsLoading.value = false
  }
}

async function loadShipments() {
  shipmentsLoading.value = true
  try {
    shipments.value = await fetchShipments()
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '無法載入出貨紀錄。'
  } finally {
    shipmentsLoading.value = false
  }
}

async function loadNotifications() {
  notificationsLoading.value = true
  try {
    notificationOverview.value = await fetchNotifications()
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '無法載入出貨通知。'
  } finally {
    notificationsLoading.value = false
  }
}

async function markRead(notification) {
  errorMessage.value = ''
  try {
    const updated = await markNotificationRead(notification.id)
    notificationOverview.value = {
      unreadCount: Math.max(
        0,
        notificationOverview.value.unreadCount - (notification.readAt ? 0 : 1),
      ),
      items: notifications.value.map((item) => (item.id === updated.id ? updated : item)),
    }
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '無法更新通知狀態。'
  }
}

async function submitShipment() {
  errorMessage.value = ''
  successMessage.value = ''
  if (!canSubmitShipment.value) {
    errorMessage.value = '請選擇可出貨戰利品與收件地址。'
    return
  }
  submitting.value = true
  try {
    const shipment = await createShipment({
      addressId: selectedAddressId.value,
      prizeIds: selectedItems.value.map((item) => item.id),
      ...(selectedFreeShippingCoupon.value
        ? { couponId: selectedFreeShippingCoupon.value.id }
        : {}),
    })
    const couponText = shipment.shippingFee === 0 ? '，已套用免運券' : ''
    successMessage.value = `已建立出貨申請 #${shipment.id}，共 ${shipment.itemCount} 件${couponText}。`
    selectedPrizeIds.value = []
    selectedCouponId.value = ''
    await Promise.all([loadPrizeBox(), loadShipments(), loadCoupons()])
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '出貨申請失敗。'
  } finally {
    submitting.value = false
  }
}

function pruneSelection() {
  selectedPrizeIds.value = selectedPrizeIds.value.filter((id) => shippableItemIds.value.has(id))
}

function isShippable(item) {
  return item.status === 'IN_BOX' && item.shipmentId === null
}

function statusLabel(status) {
  const labels = {
    IN_BOX: '可出貨',
    REQUESTED: '待處理',
    SHIPMENT_REQUESTED: '已申請',
    SHIPPED: '已出貨',
    DELIVERED: '已送達',
  }
  return labels[status] || status
}

function statusBadgeClass(status) {
  if (status === 'IN_BOX') {
    return 'text-bg-danger'
  }
  if (status === 'SHIPMENT_REQUESTED') {
    return 'text-bg-warning'
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
  <main class="prize-box-page">
    <section class="container content-section">
      <RouterLink class="back-link" to="/account">
        <i class="bi bi-arrow-left" aria-hidden="true"></i>
        會員中心
      </RouterLink>

      <div class="page-title">
        <span class="eyebrow">Prize Box</span>
        <h1>戰利品盒</h1>
        <p>查看抽中的商品，選擇要合併寄出的戰利品，並用已儲存地址建立出貨申請。</p>
      </div>

      <div class="prize-box-toolbar">
        <div>
          <label class="form-label" for="statusFilter">狀態</label>
          <select id="statusFilter" v-model="statusFilter" class="form-select">
            <option v-for="option in statusOptions" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </select>
        </div>
        <div>
          <label class="form-label" for="campaignFilter">賞池</label>
          <select id="campaignFilter" v-model="campaignFilter" class="form-select">
            <option value="">全部賞池</option>
            <option v-for="campaign in campaigns" :key="campaign.slug" :value="campaign.slug">
              {{ campaign.title }}（{{ campaign.itemCount }}）
            </option>
          </select>
        </div>
        <div class="prize-box-counts" aria-label="戰利品狀態數量">
          <span v-for="option in statusOptions.slice(1)" :key="option.value">
            <strong>{{ statusCounts[option.value] || 0 }}</strong>
            {{ option.label }}
          </span>
        </div>
      </div>

      <div v-if="errorMessage" class="state-panel prize-box-state" role="alert">
        <i class="bi bi-exclamation-triangle" aria-hidden="true"></i>
        <span>{{ errorMessage }}</span>
      </div>

      <div v-if="successMessage" class="toast show lb-toast prize-box-toast" role="status">
        <div class="toast-body">
          <i class="bi bi-check-circle me-1" aria-hidden="true"></i>
          {{ successMessage }}
        </div>
      </div>

      <section class="status-panel shipment-notification-panel">
        <div class="section-heading">
          <div>
            <span class="eyebrow">Notifications</span>
            <h2>出貨通知</h2>
          </div>
          <strong>{{ notificationOverview.unreadCount }} 則未讀</strong>
        </div>

        <div v-if="notificationsLoading" class="notification-list">
          <div v-for="index in 2" :key="index" class="notification-row">
            <div class="skeleton-line w-25"></div>
            <div class="skeleton-line"></div>
          </div>
        </div>

        <div v-else-if="notifications.length === 0" class="empty-state empty-state--compact">
          <i class="bi bi-bell" aria-hidden="true"></i>
          <strong>目前沒有出貨通知</strong>
          <span>後台更新物流狀態後，通知會出現在這裡。</span>
        </div>

        <div v-else class="notification-list">
          <article
            v-for="notification in notifications"
            :key="notification.id"
            class="notification-row"
            :class="{ 'notification-row--unread': !notification.readAt }"
          >
            <span class="notification-icon" aria-hidden="true">
              <i class="bi bi-truck"></i>
            </span>
            <span class="notification-row__body">
              <strong>{{ notification.title }}</strong>
              <span>{{ notification.body }}</span>
              <time>{{ formatTime(notification.createdAt) }}</time>
            </span>
            <button
              v-if="!notification.readAt"
              class="btn btn-outline-dark btn-sm"
              type="button"
              @click="markRead(notification)"
            >
              <i class="bi bi-check2 me-1" aria-hidden="true"></i>
              已讀
            </button>
            <RouterLink
              v-else
              class="btn btn-light btn-sm"
              :to="notification.linkUrl || '/account/prizes'"
            >
              <i class="bi bi-arrow-right me-1" aria-hidden="true"></i>
              查看
            </RouterLink>
          </article>
        </div>
      </section>

      <div class="prize-box-layout">
        <section class="status-panel prize-list-panel">
          <div class="section-heading">
            <div>
              <span class="eyebrow">Items</span>
              <h2>我的戰利品</h2>
            </div>
            <strong>{{ items.length }} 件</strong>
          </div>

          <div v-if="loading" class="prize-list">
            <div v-for="index in 3" :key="index" class="prize-box-item">
              <div class="skeleton-line w-25"></div>
              <div class="skeleton-line"></div>
              <div class="skeleton-line w-50"></div>
            </div>
          </div>

          <div v-else-if="items.length === 0" class="empty-state">
            <i class="bi bi-box-seam" aria-hidden="true"></i>
            <strong>目前沒有符合條件的戰利品</strong>
            <span>完成抽賞後，可出貨商品會出現在這裡。</span>
          </div>

          <div v-else class="prize-list">
            <label
              v-for="item in items"
              :key="item.id"
              class="prize-box-item"
              :class="{ 'prize-box-item--disabled': !isShippable(item) }"
            >
              <input
                v-model="selectedPrizeIds"
                class="form-check-input"
                type="checkbox"
                :value="item.id"
                :disabled="!isShippable(item)"
              />
              <span class="rank-badge">{{ item.prizeRank }}</span>
              <span class="prize-box-item__body">
                <strong>{{ item.prizeName }}</strong>
                <small>{{ item.campaignTitle }}</small>
                <small>{{ item.ticketSerialNumber || '無籤號' }}</small>
              </span>
              <span class="prize-box-item__meta">
                <span class="badge" :class="statusBadgeClass(item.status)">
                  {{ statusLabel(item.status) }}
                </span>
                <time>{{ formatTime(item.acquiredAt) }}</time>
              </span>
            </label>
          </div>
        </section>

        <aside class="shipment-request-panel">
          <section class="status-panel">
            <span class="eyebrow">Shipment</span>
            <h2>申請出貨</h2>
            <div class="shipment-summary">
              <div>
                <span>已選戰利品</span>
                <strong>{{ selectedCount }} 件</strong>
              </div>
              <div>
                <span>運費</span>
                <strong>{{ shippingFee }} LP</strong>
              </div>
            </div>

            <div>
              <label class="form-label" for="addressSelect">收件地址</label>
              <select
                id="addressSelect"
                v-model.number="selectedAddressId"
                class="form-select"
                :disabled="addressesLoading || addresses.length === 0"
              >
                <option v-if="addresses.length === 0" :value="null">尚未建立地址</option>
                <option v-for="address in addresses" :key="address.id" :value="address.id">
                  {{ address.recipientName }}｜{{ address.city }}{{ address.district }}
                </option>
              </select>
            </div>

            <RouterLink
              v-if="!addressesLoading && addresses.length === 0"
              class="btn btn-outline-dark"
              to="/account"
            >
              <i class="bi bi-geo-alt me-1" aria-hidden="true"></i>
              新增地址
            </RouterLink>

            <div class="free-shipping-slot" aria-label="免運券">
              <div class="free-shipping-slot__heading">
                <i class="bi bi-ticket-perforated" aria-hidden="true"></i>
                <span>免運券</span>
              </div>
              <select
                id="freeShippingCoupon"
                v-model="selectedCouponId"
                class="form-select"
                :disabled="
                  couponsLoading || freeShippingCoupons.length === 0 || selectedCount === 0
                "
              >
                <option value="">不使用免運券</option>
                <option
                  v-for="coupon in freeShippingCoupons"
                  :key="coupon.id"
                  :value="String(coupon.id)"
                >
                  {{ coupon.code }}｜期限至 {{ formatTime(coupon.endsAt) }}
                </option>
              </select>
              <small v-if="selectedFreeShippingCoupon">本次運費折抵 80 LP</small>
              <small v-else-if="!couponsLoading && freeShippingCoupons.length === 0">
                目前沒有可用免運券
              </small>
            </div>

            <button
              class="btn btn-danger btn-lg"
              type="button"
              :disabled="!canSubmitShipment"
              @click="submitShipment"
            >
              <i class="bi bi-truck me-2" aria-hidden="true"></i>
              {{ submitting ? '建立中' : '建立出貨申請' }}
            </button>
          </section>

          <section class="status-panel shipment-history-panel">
            <div class="section-heading">
              <div>
                <span class="eyebrow">History</span>
                <h2>出貨紀錄</h2>
              </div>
            </div>

            <div v-if="shipmentsLoading" class="shipment-list">
              <div v-for="index in 2" :key="index" class="shipment-row">
                <div class="skeleton-line w-50"></div>
                <div class="skeleton-line"></div>
              </div>
            </div>

            <div v-else-if="shipments.length === 0" class="empty-state">
              <i class="bi bi-truck" aria-hidden="true"></i>
              <strong>尚無出貨紀錄</strong>
              <span>建立出貨申請後會列在這裡。</span>
            </div>

            <div v-else class="shipment-list">
              <article v-for="shipment in shipments" :key="shipment.id" class="shipment-row">
                <div>
                  <strong>#{{ shipment.id }} {{ statusLabel(shipment.status) }}</strong>
                  <span
                    >{{ shipment.recipientName }}｜{{ shipment.city }}{{ shipment.district }}</span
                  >
                </div>
                <div>
                  <strong>{{ shipment.itemCount }} 件</strong>
                  <span
                    >{{ shipment.shippingFee }} LP · {{ formatTime(shipment.requestedAt) }}</span
                  >
                </div>
              </article>
            </div>
          </section>
        </aside>
      </div>
    </section>
  </main>
</template>

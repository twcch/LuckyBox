<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { fetchAccountCoupons } from '@/services/accountCouponApi'
import { fetchAccountOrders } from '@/services/accountOrderApi'
import { createAddress, deleteAddress, fetchAddresses, updateAddress } from '@/services/authApi'
import { fetchCheckInStatus, submitCheckIn } from '@/services/checkInApi'
import { fetchPrizeBox, fetchShipments } from '@/services/prizeBoxApi'
import {
  createEcpayCheckout,
  createJkoPayCheckout,
  createLinePayCheckout,
  createPaymentOrder,
  fetchWalletOverview,
} from '@/services/walletApi'
import { useSessionStore } from '@/stores/session'

const router = useRouter()
const session = useSessionStore()
const addresses = ref([])
const walletOverview = ref(null)
const accountOrders = ref({
  drawOrders: [],
  paymentOrders: [],
})
const prizeOverview = ref({
  items: [],
  statusCounts: {},
})
const shipments = ref([])
const coupons = ref([])
const loading = ref(true)
const dashboardLoading = ref(true)
const saving = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const dashboardError = ref('')
const dashboardMessage = ref('')
const topUpLoadingId = ref('')
const editingAddressId = ref(null)

const checkIn = reactive({
  loading: true,
  submitting: false,
  checkedInToday: false,
  rewardAmount: 0,
  baseRewardAmount: 0,
  streakBonusAmount: 0,
  currentStreak: 0,
  totalCheckIns: 0,
  nextStreakBonusAt: null,
  nextStreakBonusAmount: 0,
  daysUntilNextStreakBonus: 0,
})
const checkInMessage = ref('')

const addressForm = reactive({
  recipientName: '',
  phone: '',
  postalCode: '',
  city: '',
  district: '',
  addressLine: '',
  defaultAddress: true,
})

const wallet = computed(
  () =>
    walletOverview.value?.wallet || {
      cashPointBalance: session.user?.cashPointBalance || 0,
      bonusPointBalance: session.user?.bonusPointBalance || 0,
      lockedBalance: 0,
      totalAvailableBalance:
        (session.user?.cashPointBalance || 0) + (session.user?.bonusPointBalance || 0),
    },
)
const walletTotal = computed(() => wallet.value.totalAvailableBalance || 0)
const topUpPlans = computed(() => walletOverview.value?.topUpPlans || [])
const featuredTopUpPlans = computed(() => topUpPlans.value.slice(0, 2))
const drawOrders = computed(() => accountOrders.value.drawOrders || [])
const recentDrawOrders = computed(() => drawOrders.value.slice(0, 3))
const prizeItems = computed(() => prizeOverview.value.items || [])
const prizeStatusCounts = computed(() => prizeOverview.value.statusCounts || {})
const totalPrizeCount = computed(() => prizeItems.value.length)
const inboxPrizeCount = computed(
  () =>
    Number(prizeStatusCounts.value.IN_BOX || 0) ||
    prizeItems.value.filter((item) => item.status === 'IN_BOX').length,
)
const pendingPrizeCount = computed(
  () =>
    Number(prizeStatusCounts.value.SHIPMENT_REQUESTED || 0) ||
    shipments.value
      .filter((shipment) => ['REQUESTED', 'PACKING'].includes(shipment.status))
      .reduce((sum, shipment) => sum + Number(shipment.itemCount || 0), 0),
)
const shippedShipmentCount = computed(
  () => shipments.value.filter((shipment) => shipment.status === 'SHIPPED').length,
)
const availableCouponCount = computed(() => coupons.value.length)
const pointCouponCount = computed(
  () => coupons.value.filter((coupon) => coupon.type === 'POINT_BONUS').length,
)
const freeShippingCouponCount = computed(
  () => coupons.value.filter((coupon) => coupon.type === 'FREE_SHIPPING').length,
)
const completedDrawCount = computed(
  () => drawOrders.value.filter((order) => order.status === 'COMPLETED').length,
)
const totalDrawQuantity = computed(() =>
  drawOrders.value.reduce((sum, order) => sum + Number(order.quantity || 0), 0),
)
const vipLevel = computed(() => session.user?.vipLevel || '一般會員')
const checkInNextBonusText = computed(() => {
  if (!checkIn.nextStreakBonusAt || !checkIn.nextStreakBonusAmount) {
    return ''
  }
  if (!checkIn.checkedInToday && checkIn.streakBonusAmount > 0) {
    return `今天簽到可領連續 ${checkIn.currentStreak + 1} 天加碼 +${checkIn.streakBonusAmount} LP`
  }
  if (checkIn.daysUntilNextStreakBonus <= 0) {
    return `已達連續 ${checkIn.nextStreakBonusAt} 天加碼門檻 +${checkIn.nextStreakBonusAmount} LP`
  }
  return `距離連續 ${checkIn.nextStreakBonusAt} 天加碼 +${checkIn.nextStreakBonusAmount} LP 還差 ${checkIn.daysUntilNextStreakBonus} 天`
})

onMounted(async () => {
  const user = await session.load()
  if (!user) {
    await router.replace('/login')
    return
  }
  await Promise.all([loadDashboard(), loadAddresses(), loadCheckIn()])
})

async function loadDashboard() {
  dashboardLoading.value = true
  dashboardError.value = ''
  try {
    const [walletData, ordersData, prizeData, shipmentData, couponData] = await Promise.all([
      fetchWalletOverview(),
      fetchAccountOrders(),
      fetchPrizeBox(),
      fetchShipments(),
      fetchAccountCoupons(),
    ])
    walletOverview.value = walletData
    accountOrders.value = ordersData
    prizeOverview.value = prizeData
    shipments.value = shipmentData
    coupons.value = couponData
  } catch (error) {
    dashboardError.value = error.response?.data?.message || '無法載入會員總覽。'
  } finally {
    dashboardLoading.value = false
  }
}

async function loadCheckIn() {
  checkIn.loading = true
  try {
    applyCheckInStatus(await fetchCheckInStatus())
  } catch {
    // 簽到狀態載入失敗時靜默處理，不阻擋其他帳戶資訊。
  } finally {
    checkIn.loading = false
  }
}

function applyCheckInStatus(status) {
  checkIn.checkedInToday = Boolean(status?.checkedInToday)
  checkIn.rewardAmount = Number(status?.rewardAmount || 0)
  checkIn.baseRewardAmount = Number(status?.baseRewardAmount ?? status?.rewardAmount ?? 0)
  checkIn.streakBonusAmount = Number(status?.streakBonusAmount || 0)
  checkIn.currentStreak = Number(status?.currentStreak || 0)
  checkIn.totalCheckIns = Number(status?.totalCheckIns || 0)
  checkIn.nextStreakBonusAt = status?.nextStreakBonusAt ?? null
  checkIn.nextStreakBonusAmount = Number(status?.nextStreakBonusAmount || 0)
  checkIn.daysUntilNextStreakBonus = Number(status?.daysUntilNextStreakBonus || 0)
}

async function doCheckIn() {
  if (checkIn.submitting || checkIn.checkedInToday) {
    return
  }
  checkIn.submitting = true
  checkInMessage.value = ''
  try {
    const result = await submitCheckIn()
    applyCheckInStatus(result.status)
    if (result.justCheckedIn) {
      if (result.awardedAmount > 0) {
        const streakBonus = Number(result.status?.streakBonusAmount || 0)
        checkInMessage.value = `簽到成功，獲得 ${result.awardedAmount} 紅利點！${
          streakBonus > 0 ? `（含連續簽到加碼 ${streakBonus} LP）` : ''
        }`
      } else {
        checkInMessage.value = '簽到成功！'
      }
      await session.load()
    } else {
      checkInMessage.value = '今日已簽到，明天再來累積連續紀錄。'
    }
  } catch (error) {
    checkInMessage.value = error.response?.data?.message || '簽到失敗，請稍後再試。'
  } finally {
    checkIn.submitting = false
  }
}

async function loadAddresses() {
  loading.value = true
  errorMessage.value = ''
  try {
    addresses.value = await fetchAddresses()
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '無法載入地址。'
  } finally {
    loading.value = false
  }
}

async function submitAddress() {
  saving.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const payload = { ...addressForm }
    if (editingAddressId.value) {
      await updateAddress(editingAddressId.value, payload)
      successMessage.value = '地址已更新。'
    } else {
      await createAddress(payload)
      successMessage.value = '地址已新增。'
    }
    resetForm()
    await loadAddresses()
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '地址儲存失敗。'
  } finally {
    saving.value = false
  }
}

function editAddress(address) {
  editingAddressId.value = address.id
  addressForm.recipientName = address.recipientName
  addressForm.phone = address.phone
  addressForm.postalCode = address.postalCode || ''
  addressForm.city = address.city
  addressForm.district = address.district
  addressForm.addressLine = address.addressLine
  addressForm.defaultAddress = address.defaultAddress
}

async function removeAddress(addressId) {
  errorMessage.value = ''
  successMessage.value = ''
  try {
    await deleteAddress(addressId)
    successMessage.value = '地址已刪除。'
    if (editingAddressId.value === addressId) {
      resetForm()
    }
    await loadAddresses()
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '地址刪除失敗。'
  }
}

async function quickTopUp(plan) {
  topUpLoadingId.value = plan.id
  dashboardError.value = ''
  dashboardMessage.value = ''
  try {
    const order = await createPaymentOrder({ planId: plan.id })
    if (order.provider === 'ECPAY') {
      const checkout = await createEcpayCheckout(order.id)
      submitProviderCheckout(checkout)
      return
    }
    if (order.provider === 'LINEPAY') {
      const checkout = await createLinePayCheckout(order.id)
      submitProviderCheckout(checkout)
      return
    }
    if (order.provider === 'JKOPAY') {
      const checkout = await createJkoPayCheckout(order.id)
      submitProviderCheckout(checkout)
      return
    }
    await router.push({
      name: 'mock-payment-checkout',
      params: { orderId: order.id },
      query: {
        plan: plan.label,
        trade: order.merchantTradeNo,
        amount: order.amount,
        points: order.pointAmount,
        bonus: order.bonusPointAmount,
      },
    })
  } catch (error) {
    dashboardError.value = error.response?.data?.message || '快速儲值失敗，請稍後再試。'
  } finally {
    topUpLoadingId.value = ''
  }
}

function submitProviderCheckout(checkout) {
  if (checkout.redirectUrl) {
    window.location.assign(checkout.redirectUrl)
    return
  }
  const form = document.createElement('form')
  form.method = checkout.method || 'POST'
  form.action = checkout.actionUrl
  form.style.display = 'none'
  Object.entries(checkout.fields || {}).forEach(([name, value]) => {
    const input = document.createElement('input')
    input.type = 'hidden'
    input.name = name
    input.value = String(value ?? '')
    form.appendChild(input)
  })
  document.body.appendChild(form)
  form.submit()
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

function resetForm() {
  editingAddressId.value = null
  addressForm.recipientName = ''
  addressForm.phone = ''
  addressForm.postalCode = ''
  addressForm.city = ''
  addressForm.district = ''
  addressForm.addressLine = ''
  addressForm.defaultAddress = addresses.value.length === 0
}

async function logout() {
  await session.logout()
  await router.push('/')
}
</script>

<template>
  <main class="account-page">
    <section class="container content-section">
      <div v-if="session.user" class="account-hero">
        <div>
          <span class="eyebrow">Account</span>
          <h1>{{ session.user.displayName }}</h1>
          <p>{{ session.user.email }}</p>
        </div>
        <button class="btn btn-outline-dark" type="button" @click="logout">
          <i class="bi bi-box-arrow-right me-1" aria-hidden="true"></i>
          登出
        </button>
      </div>

      <div v-if="dashboardError" class="state-panel account-profile-state" role="alert">
        <i class="bi bi-exclamation-triangle" aria-hidden="true"></i>
        <span>{{ dashboardError }}</span>
      </div>

      <div v-if="dashboardMessage" class="toast show lb-toast account-toast" role="status">
        <div class="toast-body">
          <i class="bi bi-check-circle me-1" aria-hidden="true"></i>
          {{ dashboardMessage }}
        </div>
      </div>

      <div v-if="session.user" class="account-grid">
        <section class="status-panel account-wallet-panel">
          <span class="eyebrow">Wallet</span>
          <div class="account-panel-heading">
            <h2>點數狀態</h2>
            <RouterLink class="btn btn-sm btn-outline-dark" to="/account/wallet">
              <i class="bi bi-wallet2" aria-hidden="true"></i>
              錢包
            </RouterLink>
          </div>
          <div class="status-grid account-status-grid">
            <div>
              <strong>{{ formatPoint(walletTotal) }} LP</strong>
              <span>可用總點數</span>
            </div>
            <div>
              <strong>{{ formatPoint(wallet.cashPointBalance) }} LP</strong>
              <span>現金點</span>
            </div>
            <div>
              <strong>{{ formatPoint(wallet.bonusPointBalance) }} LP</strong>
              <span>贈點</span>
            </div>
          </div>

          <div class="account-quick-topup">
            <div>
              <strong>快速儲值</strong>
              <span>建立付款訂單後前往 sandbox checkout，完整方案可到錢包頁查看。</span>
            </div>
            <div v-if="dashboardLoading" class="account-mini-list">
              <div class="skeleton-line"></div>
              <div class="skeleton-line w-75"></div>
            </div>
            <div v-else-if="featuredTopUpPlans.length" class="account-quick-topup__actions">
              <button
                v-for="plan in featuredTopUpPlans"
                :key="plan.id"
                class="btn btn-danger btn-sm"
                type="button"
                :disabled="Boolean(topUpLoadingId)"
                @click="quickTopUp(plan)"
              >
                <i class="bi bi-credit-card" aria-hidden="true"></i>
                {{ topUpLoadingId === plan.id ? '處理中' : `${plan.pointAmount} LP` }}
              </button>
              <RouterLink class="btn btn-sm btn-outline-dark" to="/account/wallet">
                更多
              </RouterLink>
            </div>
            <RouterLink v-else class="btn btn-sm btn-outline-dark" to="/account/wallet">
              查看儲值方案
            </RouterLink>
          </div>
        </section>

        <section class="status-panel account-action-panel account-checkin-panel">
          <span class="eyebrow">Daily Check-in</span>
          <h2>每日簽到</h2>
          <p>每天登入簽到即可領取紅利點，連續簽到累積你的歐氣天數。</p>

          <div v-if="checkIn.loading" class="account-checkin-stats">
            <div class="skeleton-line w-50"></div>
            <div class="skeleton-line"></div>
          </div>

          <template v-else>
            <div class="account-checkin-stats">
              <div>
                <strong>{{ checkIn.currentStreak }}</strong>
                <span>連續簽到（天）</span>
              </div>
              <div>
                <strong>{{ checkIn.totalCheckIns }}</strong>
                <span>累積簽到（天）</span>
              </div>
              <div>
                <strong>+{{ checkIn.rewardAmount }} LP</strong>
                <span>今日可領</span>
                <small v-if="checkIn.streakBonusAmount > 0">
                  基本 +{{ checkIn.baseRewardAmount }} / 加碼 +{{ checkIn.streakBonusAmount }}
                </small>
              </div>
            </div>

            <p v-if="checkInNextBonusText" class="account-checkin-hint">
              {{ checkInNextBonusText }}
            </p>

            <button
              class="btn"
              :class="checkIn.checkedInToday ? 'btn-outline-dark' : 'btn-danger'"
              type="button"
              :disabled="checkIn.submitting || checkIn.checkedInToday"
              @click="doCheckIn"
            >
              <i
                class="bi me-1"
                :class="checkIn.checkedInToday ? 'bi-check2-circle' : 'bi-calendar-check'"
                aria-hidden="true"
              ></i>
              {{
                checkIn.checkedInToday ? '今日已簽到' : checkIn.submitting ? '簽到中…' : '立即簽到'
              }}
            </button>

            <p v-if="checkInMessage" class="account-checkin-message" role="status">
              {{ checkInMessage }}
            </p>
          </template>
        </section>

        <section class="status-panel account-action-panel">
          <span class="eyebrow">Prize Box</span>
          <div class="account-panel-heading">
            <h2>戰利品與出貨</h2>
            <RouterLink class="btn btn-sm btn-outline-dark" to="/account/prizes">
              <i class="bi bi-box-seam" aria-hidden="true"></i>
              戰利品
            </RouterLink>
          </div>
          <div class="account-mini-metrics">
            <span>
              <strong>{{ dashboardLoading ? '…' : totalPrizeCount }}</strong>
              戰利品
            </span>
            <span>
              <strong>{{ dashboardLoading ? '…' : inboxPrizeCount }}</strong>
              可出貨
            </span>
            <span>
              <strong>{{ dashboardLoading ? '…' : pendingPrizeCount }}</strong>
              待出貨
            </span>
          </div>
          <p>抽中的商品會先放進戰利品盒，可合併多件建立出貨申請。</p>
          <RouterLink class="btn btn-danger" to="/account/prizes">
            <i class="bi bi-box-seam me-1" aria-hidden="true"></i>
            查看戰利品
          </RouterLink>
        </section>

        <section class="status-panel account-action-panel">
          <span class="eyebrow">Coupons</span>
          <div class="account-panel-heading">
            <h2>優惠券</h2>
            <strong class="account-count-pill">{{
              dashboardLoading ? '…' : availableCouponCount
            }}</strong>
          </div>
          <div class="account-mini-metrics account-mini-metrics--two">
            <span>
              <strong>{{ dashboardLoading ? '…' : pointCouponCount }}</strong>
              贈點券
            </span>
            <span>
              <strong>{{ dashboardLoading ? '…' : freeShippingCouponCount }}</strong>
              免運券
            </span>
          </div>
          <p>查看目前可使用的優惠券與活動券。</p>
          <RouterLink class="btn btn-outline-dark" to="/account/coupons">
            <i class="bi bi-ticket-perforated me-1" aria-hidden="true"></i>
            查看優惠券
          </RouterLink>
        </section>

        <section class="status-panel account-action-panel account-recent-orders-panel">
          <span class="eyebrow">Orders</span>
          <div class="account-panel-heading">
            <h2>最近抽賞</h2>
            <RouterLink class="btn btn-sm btn-outline-dark" to="/account/orders">
              <i class="bi bi-receipt" aria-hidden="true"></i>
              全部
            </RouterLink>
          </div>
          <div class="account-mini-metrics account-mini-metrics--two">
            <span>
              <strong>{{ dashboardLoading ? '…' : completedDrawCount }}</strong>
              完成訂單
            </span>
            <span>
              <strong>{{ dashboardLoading ? '…' : totalDrawQuantity }}</strong>
              累積抽數
            </span>
          </div>
          <div v-if="dashboardLoading" class="account-mini-list">
            <div class="skeleton-line"></div>
            <div class="skeleton-line w-75"></div>
          </div>
          <div v-else-if="recentDrawOrders.length" class="account-mini-list">
            <RouterLink
              v-for="order in recentDrawOrders"
              :key="order.id"
              class="account-mini-order"
              :to="`/kuji/${order.campaignSlug}`"
            >
              <span class="badge" :class="drawStatusBadge(order.status)">
                {{ order.statusLabel }}
              </span>
              <div>
                <strong>{{ order.campaignTitle }}</strong>
                <small
                  >{{ order.quantity }} 抽 · {{ formatPoint(order.pointSpent) }} LP ·
                  {{ formatTime(order.completedAt || order.createdAt) }}</small
                >
              </div>
            </RouterLink>
          </div>
          <p v-else>尚無抽賞紀錄，完成第一抽後會在這裡顯示。</p>
          <RouterLink class="btn btn-outline-dark" to="/account/orders">
            <i class="bi bi-receipt me-1" aria-hidden="true"></i>
            查看訂單
          </RouterLink>
        </section>

        <section class="status-panel account-action-panel">
          <span class="eyebrow">Shipments</span>
          <div class="account-panel-heading">
            <h2>出貨紀錄</h2>
            <strong class="account-count-pill">{{
              dashboardLoading ? '…' : shipments.length
            }}</strong>
          </div>
          <div class="account-mini-metrics account-mini-metrics--two">
            <span>
              <strong>{{ dashboardLoading ? '…' : pendingPrizeCount }}</strong>
              待處理品項
            </span>
            <span>
              <strong>{{ dashboardLoading ? '…' : shippedShipmentCount }}</strong>
              已出貨
            </span>
          </div>
          <p>查看出貨申請、處理狀態、收件地區與品項列表。</p>
          <RouterLink class="btn btn-outline-dark" to="/account/shipments">
            <i class="bi bi-truck me-1" aria-hidden="true"></i>
            查看出貨
          </RouterLink>
        </section>

        <section class="status-panel account-action-panel">
          <span class="eyebrow">Profile</span>
          <div class="account-panel-heading">
            <h2>會員等級</h2>
            <strong class="account-vip-pill">{{ vipLevel }}</strong>
          </div>
          <p>管理顯示名稱、手機、帳號狀態與會員摘要。</p>

          <div class="profile-list">
            <span>角色</span>
            <strong>{{ session.user.role }}</strong>
            <span>狀態</span>
            <strong>{{ session.user.status }}</strong>
            <span>VIP</span>
            <strong>{{ vipLevel }}</strong>
          </div>

          <RouterLink class="btn btn-outline-dark" to="/account/profile">
            <i class="bi bi-person-gear me-1" aria-hidden="true"></i>
            編輯個人資料
          </RouterLink>
        </section>

        <section class="status-panel account-action-panel account-support-panel">
          <span class="eyebrow">Support</span>
          <h2>客服與常見問題</h2>
          <p>查詢出貨、點數、優惠券、抽賞結果與帳號資料問題，或準備資料聯繫客服。</p>

          <div class="account-support-links" aria-label="會員客服入口">
            <RouterLink to="/faq">
              <i class="bi bi-question-circle" aria-hidden="true"></i>
              常見問題
            </RouterLink>
            <RouterLink to="/account/shipments">
              <i class="bi bi-truck" aria-hidden="true"></i>
              出貨紀錄
            </RouterLink>
            <RouterLink to="/shipping-policy">
              <i class="bi bi-life-preserver" aria-hidden="true"></i>
              出貨與退換貨
            </RouterLink>
          </div>

          <div class="account-support-contact">
            <span>客服信箱</span>
            <strong>support@luckybox.local</strong>
          </div>
        </section>
      </div>
    </section>

    <section class="container content-section pt-0">
      <div class="account-grid">
        <section class="status-panel">
          <span class="eyebrow">Address</span>
          <h2>{{ editingAddressId ? '編輯地址' : '新增地址' }}</h2>

          <form class="address-form" @submit.prevent="submitAddress">
            <div class="form-grid">
              <div>
                <label class="form-label" for="recipientName">收件人</label>
                <input
                  id="recipientName"
                  v-model.trim="addressForm.recipientName"
                  class="form-control"
                  required
                />
              </div>
              <div>
                <label class="form-label" for="addressPhone">手機</label>
                <input
                  id="addressPhone"
                  v-model.trim="addressForm.phone"
                  class="form-control"
                  required
                />
              </div>
              <div>
                <label class="form-label" for="postalCode">郵遞區號</label>
                <input id="postalCode" v-model.trim="addressForm.postalCode" class="form-control" />
              </div>
              <div>
                <label class="form-label" for="city">縣市</label>
                <input id="city" v-model.trim="addressForm.city" class="form-control" required />
              </div>
              <div>
                <label class="form-label" for="district">行政區</label>
                <input
                  id="district"
                  v-model.trim="addressForm.district"
                  class="form-control"
                  required
                />
              </div>
              <div class="form-grid__wide">
                <label class="form-label" for="addressLine">地址</label>
                <input
                  id="addressLine"
                  v-model.trim="addressForm.addressLine"
                  class="form-control"
                  required
                />
              </div>
            </div>

            <label class="form-check">
              <input
                v-model="addressForm.defaultAddress"
                class="form-check-input"
                type="checkbox"
              />
              <span class="form-check-label">設為預設地址</span>
            </label>

            <div class="component-row">
              <button class="btn btn-danger" type="submit" :disabled="saving">
                <i class="bi bi-save me-1" aria-hidden="true"></i>
                {{ saving ? '儲存中' : editingAddressId ? '更新地址' : '新增地址' }}
              </button>
              <button
                v-if="editingAddressId"
                class="btn btn-outline-dark"
                type="button"
                @click="resetForm"
              >
                取消編輯
              </button>
            </div>
          </form>
        </section>

        <section class="status-panel">
          <span class="eyebrow">Saved</span>
          <h2>已儲存地址</h2>

          <div v-if="errorMessage" class="state-panel" role="alert">
            <i class="bi bi-exclamation-triangle" aria-hidden="true"></i>
            <span>{{ errorMessage }}</span>
          </div>
          <div v-if="successMessage" class="toast show lb-toast account-toast" role="status">
            <div class="toast-body">
              <i class="bi bi-check-circle me-1" aria-hidden="true"></i>
              {{ successMessage }}
            </div>
          </div>

          <div v-if="loading" class="address-list">
            <div v-for="index in 2" :key="index" class="address-card">
              <div class="skeleton-line w-50"></div>
              <div class="skeleton-line"></div>
            </div>
          </div>

          <div v-else-if="addresses.length === 0" class="empty-state">
            <i class="bi bi-geo-alt" aria-hidden="true"></i>
            <strong>尚未建立地址</strong>
            <span>新增地址後，戰利品出貨會使用這些資料。</span>
          </div>

          <div v-else class="address-list">
            <article v-for="address in addresses" :key="address.id" class="address-card">
              <div>
                <div class="component-row">
                  <strong>{{ address.recipientName }}</strong>
                  <span v-if="address.defaultAddress" class="badge text-bg-danger">預設</span>
                </div>
                <p>{{ address.phone }}</p>
                <p>
                  {{ address.postalCode }} {{ address.city }}{{ address.district
                  }}{{ address.addressLine }}
                </p>
              </div>
              <div class="component-row">
                <button
                  class="btn btn-outline-dark btn-sm"
                  type="button"
                  @click="editAddress(address)"
                >
                  編輯
                </button>
                <button
                  class="btn btn-outline-danger btn-sm"
                  type="button"
                  @click="removeAddress(address.id)"
                >
                  刪除
                </button>
              </div>
            </article>
          </div>
        </section>
      </div>
    </section>
  </main>
</template>

<style scoped>
.account-panel-heading {
  display: flex;
  min-width: 0;
  gap: 0.75rem;
  align-items: center;
  justify-content: space-between;
}

.account-panel-heading h2 {
  min-width: 0;
  margin: 0;
  color: var(--lb-ink);
  font-weight: 900;
  overflow-wrap: anywhere;
}

.account-wallet-panel {
  background:
    linear-gradient(135deg, rgba(241, 250, 248, 0.9), rgba(255, 255, 255, 0.95)), var(--lb-surface);
}

.account-quick-topup {
  display: grid;
  min-width: 0;
  gap: 0.75rem;
  padding-top: 0.85rem;
  border-top: 1px solid var(--lb-border);
}

.account-quick-topup > div:first-child {
  display: grid;
  min-width: 0;
  gap: 0.15rem;
}

.account-quick-topup strong {
  color: var(--lb-ink);
}

.account-quick-topup span,
.account-mini-order small {
  color: var(--lb-muted);
}

.account-quick-topup__actions {
  display: flex;
  min-width: 0;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.account-mini-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0.55rem;
}

.account-mini-metrics--two {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.account-mini-metrics span {
  display: grid;
  min-width: 0;
  gap: 0.15rem;
  padding: 0.65rem;
  border: 1px solid var(--lb-border);
  border-radius: 8px;
  background: #ffffff;
  color: var(--lb-muted);
  font-size: 0.85rem;
}

.account-mini-metrics strong {
  color: var(--lb-ink);
  font-size: 1.15rem;
  line-height: 1;
  overflow-wrap: anywhere;
}

.account-count-pill,
.account-vip-pill {
  display: inline-flex;
  min-height: 1.9rem;
  align-items: center;
  padding: 0.25rem 0.7rem;
  border-radius: 999px;
  font-weight: 900;
  line-height: 1;
  white-space: nowrap;
}

.account-count-pill {
  background: var(--lb-surface-cool);
  color: var(--lb-teal-dark);
}

.account-vip-pill {
  background: #fff8ed;
  color: var(--lb-coral-dark);
}

.account-mini-list {
  display: grid;
  min-width: 0;
  gap: 0.55rem;
}

.account-mini-order {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 0.6rem;
  align-items: center;
  padding: 0.65rem;
  border: 1px solid var(--lb-border);
  border-radius: 8px;
  background: #ffffff;
  color: var(--lb-ink);
  text-decoration: none;
  transition:
    border-color 0.16s ease,
    box-shadow 0.16s ease;
}

.account-mini-order:hover {
  border-color: var(--lb-coral);
  box-shadow: var(--lb-shadow-sm);
}

.account-mini-order > div {
  display: grid;
  min-width: 0;
  gap: 0.15rem;
}

.account-mini-order strong,
.account-mini-order small {
  min-width: 0;
  overflow-wrap: anywhere;
}

.account-recent-orders-panel {
  grid-row: span 2;
}

.account-checkin-panel {
  border: 1px solid rgba(220, 38, 38, 0.18);
  background: linear-gradient(135deg, rgba(255, 241, 242, 0.85), rgba(255, 255, 255, 0.95));
}

.account-checkin-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0.75rem;
  margin: 0.25rem 0 1rem;
}

.account-checkin-stats > div {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.account-checkin-stats strong {
  font-size: 1.25rem;
  line-height: 1.15;
  color: var(--bs-emphasis-color, #1f2933);
}

.account-checkin-stats span {
  font-size: 0.78rem;
  color: var(--bs-secondary-color, #6c757d);
}

.account-checkin-stats small {
  font-size: 0.72rem;
  line-height: 1.25;
  color: #b91c1c;
}

.account-checkin-hint {
  margin: -0.35rem 0 0.9rem;
  font-size: 0.84rem;
  color: #7f1d1d;
}

.account-checkin-message {
  margin: 0.75rem 0 0;
  font-size: 0.85rem;
  color: #b91c1c;
}

@media (max-width: 575.98px) {
  .account-panel-heading {
    align-items: stretch;
    flex-direction: column;
  }

  .account-panel-heading .btn {
    width: 100%;
  }

  .account-mini-metrics,
  .account-mini-metrics--two,
  .account-checkin-stats {
    grid-template-columns: 1fr;
    gap: 0.4rem;
  }

  .account-mini-order {
    grid-template-columns: 1fr;
  }

  .account-quick-topup__actions .btn {
    flex: 1 1 8rem;
  }
}
</style>

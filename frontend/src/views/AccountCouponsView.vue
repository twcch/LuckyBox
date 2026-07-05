<script setup>
import { onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { fetchAccountCoupons, redeemAccountCoupon } from '@/services/accountCouponApi'
import { useSessionStore } from '@/stores/session'

const router = useRouter()
const session = useSessionStore()

const coupons = ref([])
const loading = ref(true)
const errorMessage = ref('')
const successMessage = ref('')
const redeemingId = ref(null)

onMounted(async () => {
  const user = await session.load()
  if (!user) {
    await router.replace({ path: '/login', query: { redirect: '/account/coupons' } })
    return
  }
  await loadCoupons()
})

async function loadCoupons() {
  loading.value = true
  errorMessage.value = ''
  try {
    coupons.value = await fetchAccountCoupons()
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '無法載入優惠券。'
  } finally {
    loading.value = false
  }
}

async function redeemCoupon(coupon) {
  if (coupon.type !== 'POINT_BONUS') {
    return
  }
  redeemingId.value = coupon.id
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const redeemed = await redeemAccountCoupon(coupon.id)
    successMessage.value = `已領取 ${redeemed.pointAmount} LP 贈點。`
    await loadCoupons()
    await session.load()
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '優惠券領取失敗。'
  } finally {
    redeemingId.value = null
  }
}

function formatTime(value) {
  if (!value) {
    return '不限'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return '不限'
  }
  return new Intl.DateTimeFormat('zh-TW', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(date)
}

function couponValueText(coupon) {
  if (coupon.type === 'FREE_SHIPPING') {
    return '免運'
  }
  if (coupon.type === 'POINT_BONUS') {
    return `${coupon.value} LP`
  }
  return `${coupon.value} LP`
}

function couponScopeText(coupon) {
  if (coupon.type === 'DISCOUNT') {
    return '抽賞折抵'
  }
  if (coupon.type === 'POINT_BONUS') {
    return '贈點券'
  }
  return '出貨免運'
}
</script>

<template>
  <main class="account-page">
    <section class="container content-section">
      <RouterLink class="back-link" to="/account">
        <i class="bi bi-arrow-left" aria-hidden="true"></i>
        回會員中心
      </RouterLink>

      <div class="page-title">
        <span class="eyebrow">Coupons</span>
        <h1>我的優惠券</h1>
        <p>目前可使用的優惠券會集中在這裡；折扣券可在賞池詳情的抽賞區輸入代碼折抵。</p>
      </div>

      <div v-if="errorMessage" class="state-panel" role="alert">
        <i class="bi bi-exclamation-triangle" aria-hidden="true"></i>
        <span>{{ errorMessage }}</span>
      </div>

      <div v-if="successMessage" class="toast show lb-toast mb-3" role="status">
        <div class="toast-body">
          <i class="bi bi-check2-circle me-1" aria-hidden="true"></i>
          {{ successMessage }}
        </div>
      </div>

      <div v-if="loading" class="coupon-list">
        <article v-for="index in 3" :key="index" class="coupon-card">
          <div class="skeleton-line w-50"></div>
          <div class="skeleton-line"></div>
          <div class="skeleton-line w-75"></div>
        </article>
      </div>

      <div v-else-if="coupons.length === 0" class="empty-state">
        <i class="bi bi-ticket-perforated" aria-hidden="true"></i>
        <strong>目前沒有可用優惠券</strong>
      </div>

      <div v-else class="coupon-list">
        <article v-for="coupon in coupons" :key="coupon.id" class="coupon-card">
          <div>
            <span class="eyebrow">{{ coupon.typeLabel }}</span>
            <h2>{{ coupon.code }}</h2>
            <p>
              {{ couponScopeText(coupon) }}，滿 {{ coupon.minSpend }} 可用，期限至
              {{ formatTime(coupon.endsAt) }}
            </p>
            <span v-if="coupon.vipTierLabel" class="badge text-bg-warning">
              {{ coupon.vipTierLabel }}
            </span>
          </div>
          <div class="coupon-card__aside">
            <strong class="coupon-card__value">{{ couponValueText(coupon) }}</strong>
            <button
              v-if="coupon.type === 'POINT_BONUS'"
              class="btn btn-danger btn-sm"
              type="button"
              :disabled="redeemingId === coupon.id"
              @click="redeemCoupon(coupon)"
            >
              <i class="bi bi-gift me-1" aria-hidden="true"></i>
              <span v-if="redeemingId === coupon.id">領取中</span>
              <span v-else>領取</span>
            </button>
            <RouterLink
              v-else-if="coupon.type === 'DISCOUNT'"
              class="btn btn-outline-dark btn-sm"
              to="/"
            >
              <i class="bi bi-stars me-1" aria-hidden="true"></i>
              去抽賞
            </RouterLink>
            <span v-else class="coupon-card__hint">出貨使用</span>
          </div>
        </article>
      </div>
    </section>
  </main>
</template>

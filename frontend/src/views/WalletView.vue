<script setup>
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import {
  createEcpayCheckout,
  createJkoPayCheckout,
  createLinePayCheckout,
  createPaymentOrder,
  fetchWalletOverview,
} from '@/services/walletApi'
import { useSessionStore } from '@/stores/session'

const router = useRouter()
const route = useRoute()
const session = useSessionStore()
const overview = ref(null)
const loading = ref(true)
const topUpLoadingId = ref('')
const errorMessage = ref('')
const successMessage = ref('')

const wallet = computed(
  () =>
    overview.value?.wallet || {
      cashPointBalance: 0,
      bonusPointBalance: 0,
      lockedBalance: 0,
      totalAvailableBalance: 0,
      bonusPointExpiryDays: 0,
      bonusPointExpiryLabel: '',
    },
)

const ledger = computed(() => overview.value?.ledger || [])
const topUpPlans = computed(() => overview.value?.topUpPlans || [])
const firstDepositPromo = computed(
  () => overview.value?.firstDepositPromo || { bonusPoints: 0, eligible: false },
)
const spendThresholdPromo = computed(
  () =>
    overview.value?.spendThresholdPromo || {
      active: false,
      threshold: 0,
      bonusPoints: 0,
      totalSpend: 0,
      remaining: 0,
      reached: false,
    },
)
const spendThresholdProgress = computed(() => {
  const promo = spendThresholdPromo.value
  if (!promo.active || promo.threshold <= 0) {
    return 0
  }
  return Math.min(100, Math.round((promo.totalSpend / promo.threshold) * 100))
})

onMounted(async () => {
  const user = await session.load()
  if (!user) {
    await router.replace({ path: '/login', query: { redirect: '/account/wallet' } })
    return
  }
  if (route.query.payment === 'success') {
    successMessage.value = '付款已完成，點數已更新。'
  }
  await loadWallet()
})

async function loadWallet() {
  loading.value = true
  errorMessage.value = ''
  try {
    overview.value = await fetchWalletOverview()
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '無法載入錢包。'
  } finally {
    loading.value = false
  }
}

async function topUp(plan) {
  topUpLoadingId.value = plan.id
  errorMessage.value = ''
  successMessage.value = ''
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
    errorMessage.value = error.response?.data?.message || '儲值失敗，請稍後再試。'
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

function formatLedgerType(type) {
  const labels = {
    TOP_UP: '儲值',
    TOP_UP_BONUS: '贈點',
  }
  return labels[type] || type
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
  <main class="wallet-page">
    <section class="container content-section">
      <RouterLink class="back-link" to="/account">
        <i class="bi bi-arrow-left" aria-hidden="true"></i>
        會員中心
      </RouterLink>

      <div class="page-title">
        <span class="eyebrow">Wallet</span>
        <h1>點數錢包</h1>
        <p>現金點與贈點分帳顯示，每筆入點保留流水。</p>
      </div>

      <div v-if="errorMessage" class="state-panel wallet-state" role="alert">
        <i class="bi bi-exclamation-triangle" aria-hidden="true"></i>
        <span>{{ errorMessage }}</span>
      </div>

      <div v-if="successMessage" class="toast show lb-toast wallet-toast" role="status">
        <div class="toast-body">
          <i class="bi bi-check-circle me-1" aria-hidden="true"></i>
          {{ successMessage }}
        </div>
      </div>

      <div v-if="loading" class="wallet-layout">
        <div class="skeleton-block"></div>
        <div class="skeleton-block"></div>
      </div>

      <div v-else class="wallet-layout">
        <section class="status-panel wallet-summary">
          <span class="eyebrow">Balance</span>
          <h2>{{ wallet.totalAvailableBalance }} LP</h2>
          <div class="status-grid wallet-status-grid">
            <div>
              <strong>{{ wallet.cashPointBalance }} LP</strong>
              <span>現金點</span>
            </div>
            <div>
              <strong>{{ wallet.bonusPointBalance }} LP</strong>
              <span>贈點</span>
              <small v-if="wallet.bonusPointExpiryLabel" class="wallet-expiry-note">
                {{ wallet.bonusPointExpiryLabel }}
              </small>
            </div>
            <div>
              <strong>{{ wallet.lockedBalance }} LP</strong>
              <span>鎖定點</span>
            </div>
          </div>
        </section>

        <section id="top-up" class="status-panel top-up-panel">
          <span class="eyebrow">Top Up</span>
          <h2>儲值方案</h2>
          <div
            v-if="firstDepositPromo.eligible && firstDepositPromo.bonusPoints > 0"
            class="first-deposit-promo"
          >
            <span class="first-deposit-promo__icon" aria-hidden="true">
              <i class="bi bi-gift"></i>
            </span>
            <div class="first-deposit-promo__text">
              <strong>首儲限定優惠</strong>
              <span>完成第一筆儲值，立即再送 {{ firstDepositPromo.bonusPoints }} LP 贈點。</span>
            </div>
            <span class="first-deposit-promo__badge">+{{ firstDepositPromo.bonusPoints }} LP</span>
          </div>
          <div
            v-if="spendThresholdPromo.active && spendThresholdPromo.bonusPoints > 0"
            class="spend-threshold-promo"
            :class="{ 'is-reached': spendThresholdPromo.reached }"
          >
            <div class="spend-threshold-promo__head">
              <span class="spend-threshold-promo__icon" aria-hidden="true">
                <i class="bi" :class="spendThresholdPromo.reached ? 'bi-trophy' : 'bi-stars'"></i>
              </span>
              <div class="spend-threshold-promo__text">
                <strong>滿額贈點</strong>
                <span v-if="spendThresholdPromo.reached">
                  已累積消費 {{ spendThresholdPromo.totalSpend }} LP，達標
                  {{ spendThresholdPromo.bonusPoints }} LP 紅利已入帳。
                </span>
                <span v-else>
                  再消費 {{ spendThresholdPromo.remaining }} LP 即可獲得
                  {{ spendThresholdPromo.bonusPoints }} LP 紅利。
                </span>
              </div>
              <span class="spend-threshold-promo__badge"
                >+{{ spendThresholdPromo.bonusPoints }} LP</span
              >
            </div>
            <div
              class="spend-threshold-promo__bar"
              role="progressbar"
              :aria-valuenow="spendThresholdProgress"
              aria-valuemin="0"
              aria-valuemax="100"
            >
              <div
                class="spend-threshold-promo__fill"
                :style="{ width: `${spendThresholdProgress}%` }"
              ></div>
            </div>
            <div class="spend-threshold-promo__meta">
              <span>已消費 {{ spendThresholdPromo.totalSpend }} LP</span>
              <span>門檻 {{ spendThresholdPromo.threshold }} LP</span>
            </div>
          </div>
          <div class="top-up-grid">
            <article v-for="plan in topUpPlans" :key="plan.id" class="top-up-card">
              <div>
                <h3>{{ plan.label }}</h3>
                <p>NT$ {{ plan.amount }}</p>
              </div>
              <div class="top-up-card__points">
                <strong>{{ plan.pointAmount }} LP</strong>
                <span v-if="plan.bonusPointAmount">+{{ plan.bonusPointAmount }} 贈點</span>
                <span v-else>無贈點</span>
              </div>
              <button
                class="btn btn-danger"
                type="button"
                :disabled="Boolean(topUpLoadingId)"
                @click="topUp(plan)"
              >
                <i class="bi bi-credit-card me-1" aria-hidden="true"></i>
                {{ topUpLoadingId === plan.id ? '建立中' : '前往付款' }}
              </button>
            </article>
          </div>
        </section>

        <section class="status-panel wallet-ledger-panel">
          <div class="section-heading wallet-ledger-heading">
            <div>
              <span class="eyebrow">Ledger</span>
              <h2>點數流水</h2>
            </div>
          </div>

          <div v-if="ledger.length === 0" class="empty-state">
            <i class="bi bi-receipt" aria-hidden="true"></i>
            <strong>尚無流水</strong>
          </div>
          <div v-else class="ledger-list">
            <article v-for="item in ledger" :key="item.id" class="ledger-row">
              <div>
                <strong>{{ formatLedgerType(item.type) }}</strong>
                <span>{{ item.reason }}</span>
              </div>
              <div>
                <strong>+{{ item.amount }} LP</strong>
                <span>{{ item.pointKind }} · 餘額 {{ item.balanceAfter }} LP</span>
              </div>
              <time>{{ formatTime(item.createdAt) }}</time>
            </article>
          </div>
        </section>
      </div>
    </section>
  </main>
</template>

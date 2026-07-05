<script setup>
import { computed, reactive, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { confirmMockCheckout } from '@/services/walletApi'
import { useSessionStore } from '@/stores/session'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const submitting = ref(false)
const errorMessage = ref('')
const paidOrder = ref(null)

const orderId = computed(() => String(route.params.orderId || ''))
const summary = computed(() => ({
  plan: typeof route.query.plan === 'string' ? route.query.plan : 'Mock checkout',
  trade: typeof route.query.trade === 'string' ? route.query.trade : `#${orderId.value}`,
  amount: Number(route.query.amount || 0),
  points: Number(route.query.points || 0),
  bonus: Number(route.query.bonus || 0),
}))

const card = reactive({
  number: '4242 4242 4242 4242',
  expiry: '12/30',
  cvc: '123',
  name: session.user?.displayName || 'LUCKYBOX TESTER',
})

const paidSummary = computed(() => paidOrder.value || {})
const totalPoints = computed(
  () => Number(paidSummary.value.pointAmount || 0) + Number(paidSummary.value.bonusPointAmount || 0),
)

async function confirmPayment() {
  errorMessage.value = ''
  if (!isCardFormValid()) {
    errorMessage.value = '請確認卡號、有效月年、CVC 與持卡人姓名格式。'
    return
  }
  submitting.value = true
  try {
    paidOrder.value = await confirmMockCheckout(orderId.value)
    await session.load()
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '付款確認失敗，請稍後再試。'
  } finally {
    submitting.value = false
  }
}

function isCardFormValid() {
  const digits = card.number.replace(/\D/g, '')
  return (
    digits.length >= 12 &&
    digits.length <= 19 &&
    /^(0[1-9]|1[0-2])\/\d{2}$/.test(card.expiry.trim()) &&
    /^\d{3,4}$/.test(card.cvc.trim()) &&
    card.name.trim().length >= 2
  )
}

function formatCurrency(value) {
  return new Intl.NumberFormat('zh-TW', {
    style: 'currency',
    currency: 'TWD',
    maximumFractionDigits: 0,
  }).format(value || 0)
}

function formatPoint(value) {
  return new Intl.NumberFormat('zh-TW').format(value || 0)
}

async function returnToWallet() {
  await router.push({ name: 'wallet', query: { payment: 'success' } })
}
</script>

<template>
  <main class="mock-checkout-page">
    <section class="container content-section">
      <RouterLink class="back-link" to="/account/wallet">
        <i class="bi bi-arrow-left" aria-hidden="true"></i>
        點數錢包
      </RouterLink>

      <div class="page-title">
        <span class="eyebrow">MockPay Sandbox</span>
        <h1>付款確認</h1>
        <p>完成付款後點數會即時入帳，訂單可在會員中心查詢。</p>
      </div>

      <div v-if="errorMessage" class="state-panel mock-checkout-state" role="alert">
        <i class="bi bi-exclamation-triangle" aria-hidden="true"></i>
        <span>{{ errorMessage }}</span>
      </div>

      <div class="mock-checkout-layout">
        <section class="status-panel mock-checkout-summary">
          <span class="eyebrow">Order</span>
          <h2>{{ summary.plan }}</h2>
          <div class="mock-checkout-total">
            <strong>{{ formatCurrency(summary.amount || paidSummary.amount) }}</strong>
            <span>訂單 {{ summary.trade || paidSummary.merchantTradeNo }}</span>
          </div>
          <div class="status-grid mock-checkout-metrics">
            <div>
              <strong>{{ formatPoint(summary.points || paidSummary.pointAmount) }} LP</strong>
              <span>現金點</span>
            </div>
            <div>
              <strong>{{ formatPoint(summary.bonus || paidSummary.bonusPointAmount) }} LP</strong>
              <span>贈點</span>
            </div>
            <div>
              <strong>{{ paidOrder ? paidSummary.status : 'PENDING' }}</strong>
              <span>付款狀態</span>
            </div>
          </div>
        </section>

        <section v-if="!paidOrder" class="status-panel mock-checkout-form-panel">
          <div class="section-heading">
            <div>
              <span class="eyebrow">Card</span>
              <h2>信用卡付款</h2>
            </div>
            <span class="badge text-bg-warning">Sandbox</span>
          </div>

          <form class="mock-card-form" @submit.prevent="confirmPayment">
            <label class="form-label">
              卡號
              <input
                v-model="card.number"
                class="form-control"
                inputmode="numeric"
                autocomplete="cc-number"
                maxlength="23"
                placeholder="4242 4242 4242 4242"
              />
            </label>
            <div class="mock-card-form__row">
              <label class="form-label">
                有效月年
                <input
                  v-model="card.expiry"
                  class="form-control"
                  autocomplete="cc-exp"
                  maxlength="5"
                  placeholder="12/30"
                />
              </label>
              <label class="form-label">
                CVC
                <input
                  v-model="card.cvc"
                  class="form-control"
                  inputmode="numeric"
                  autocomplete="cc-csc"
                  maxlength="4"
                  placeholder="123"
                />
              </label>
            </div>
            <label class="form-label">
              持卡人姓名
              <input
                v-model="card.name"
                class="form-control"
                autocomplete="cc-name"
                maxlength="80"
                placeholder="LUCKYBOX TESTER"
              />
            </label>

            <div class="mock-checkout-actions">
              <button class="btn btn-danger btn-lg" type="submit" :disabled="submitting">
                <i class="bi bi-shield-check" aria-hidden="true"></i>
                {{ submitting ? '付款確認中' : '確認付款' }}
              </button>
              <RouterLink class="btn btn-outline-dark btn-lg" to="/account/wallet">
                取消
              </RouterLink>
            </div>
          </form>
        </section>

        <section v-else class="status-panel mock-checkout-success">
          <div class="mock-checkout-success__icon" aria-hidden="true">
            <i class="bi bi-check2-circle"></i>
          </div>
          <span class="eyebrow">Paid</span>
          <h2>付款成功</h2>
          <p>
            {{ paidSummary.merchantTradeNo }} 已完成入點，共
            {{ formatPoint(totalPoints) }} LP。
          </p>
          <div class="mock-checkout-actions">
            <button class="btn btn-danger btn-lg" type="button" @click="returnToWallet">
              <i class="bi bi-wallet2" aria-hidden="true"></i>
              回到錢包
            </button>
            <RouterLink class="btn btn-outline-dark btn-lg" to="/account/orders">
              <i class="bi bi-receipt" aria-hidden="true"></i>
              查看訂單
            </RouterLink>
          </div>
        </section>
      </div>
    </section>
  </main>
</template>

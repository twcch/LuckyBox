<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { requestCompensationApproval } from '@/services/adminApprovalApi'
import {
  createMemberNote,
  fetchAdminMemberDetail,
  grantMemberCompensation,
} from '@/services/adminUserApi'
import { useSessionStore } from '@/stores/session'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()

const member = ref(null)
const loading = ref(true)
const errorMessage = ref('')
const revealError = ref('')
const revealSubmitting = ref(false)

const noteForm = reactive({ content: '', submitting: false })
const noteError = ref('')

const compForm = reactive({ amount: '', reason: '', submitting: false })
const compError = ref('')
const compMessage = ref('')

watch(
  () => route.params.userId,
  async () => {
    await loadMember()
  },
)

onMounted(async () => {
  const user = await session.load()
  if (!user) {
    await router.replace({ path: '/admin/login', query: { redirect: route.fullPath } })
    return
  }
  if (!isAdmin(user)) {
    await router.replace('/account')
    return
  }
  await loadMember()
})

async function loadMember(options = {}) {
  const reveal = Boolean(options.reveal)
  if (options.showLoading !== false) {
    loading.value = true
  }
  errorMessage.value = ''
  try {
    member.value = await fetchAdminMemberDetail(route.params.userId, { reveal })
  } catch (error) {
    errorMessage.value =
      error.response?.status === 404
        ? '找不到指定會員。'
        : error.response?.data?.message || '無法載入會員詳情。'
  } finally {
    if (options.showLoading !== false) {
      loading.value = false
    }
  }
}

async function revealPii() {
  revealError.value = ''
  revealSubmitting.value = true
  try {
    member.value = await fetchAdminMemberDetail(route.params.userId, { reveal: true })
  } catch {
    revealError.value = '完整個資揭露失敗，請稍後再試。'
  } finally {
    revealSubmitting.value = false
  }
}

async function addNote() {
  noteError.value = ''
  if (!noteForm.content.trim()) {
    noteError.value = '請輸入備註內容。'
    return
  }
  noteForm.submitting = true
  try {
    const note = await createMemberNote(route.params.userId, noteForm.content.trim())
    if (member.value) {
      member.value.notes = [note, ...(member.value.notes || [])]
    }
    noteForm.content = ''
  } catch (error) {
    noteError.value = error.response?.data?.message || '新增備註失敗，請稍後再試。'
  } finally {
    noteForm.submitting = false
  }
}

async function grantCompensation() {
  compError.value = ''
  compMessage.value = ''
  const payload = buildCompensationPayload()
  if (!payload) {
    return
  }
  compForm.submitting = true
  try {
    const result = await grantMemberCompensation(route.params.userId, payload)
    compMessage.value = `已發放 ${result.amount} 點補償，紅利餘額更新為 ${result.bonusBalanceAfter}。`
    compForm.amount = ''
    compForm.reason = ''
    await loadMember({ reveal: Boolean(member.value?.piiRevealed) })
  } catch (error) {
    compError.value = error.response?.data?.message || '補償發放失敗，請稍後再試。'
  } finally {
    compForm.submitting = false
  }
}

async function requestCompensationReview() {
  compError.value = ''
  compMessage.value = ''
  const payload = buildCompensationPayload()
  if (!payload) {
    return
  }
  compForm.submitting = true
  try {
    const request = await requestCompensationApproval(route.params.userId, payload)
    compMessage.value = `已建立補償審核單 #${request.id}。`
    compForm.amount = ''
    compForm.reason = ''
  } catch (error) {
    compError.value = error.response?.data?.message || '建立補償審核單失敗，請稍後再試。'
  } finally {
    compForm.submitting = false
  }
}

function buildCompensationPayload() {
  const amount = Number(compForm.amount)
  if (!Number.isInteger(amount) || amount <= 0) {
    compError.value = '補償點數須為正整數。'
    return null
  }
  if (!compForm.reason.trim()) {
    compError.value = '請填寫補償原因。'
    return null
  }
  return { amount, reason: compForm.reason.trim() }
}

function isAdmin(user) {
  return user.role === 'SUPER_ADMIN' || user.role === 'ADMIN'
}

function statusBadgeClass(status) {
  if (status === 'ACTIVE') {
    return 'text-bg-success'
  }
  if (status === 'SUSPENDED') {
    return 'text-bg-warning'
  }
  return 'text-bg-light border'
}

function amountClass(amount) {
  return Number(amount || 0) >= 0 ? 'detail-amount--credit' : 'detail-amount--debit'
}

function prizeStatusBadgeClass(status) {
  if (status === 'IN_BOX') {
    return 'text-bg-danger'
  }
  if (status === 'SHIPMENT_REQUESTED') {
    return 'text-bg-warning'
  }
  if (status === 'SHIPPED') {
    return 'text-bg-info'
  }
  if (status === 'DELIVERED') {
    return 'text-bg-success'
  }
  return 'text-bg-light border'
}

function formatPoint(value) {
  return new Intl.NumberFormat('zh-TW').format(value || 0)
}

function formatSignedPoint(value) {
  const amount = Number(value || 0)
  return `${amount > 0 ? '+' : ''}${formatPoint(amount)}`
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
  <article class="admin-page admin-member-detail-page">
    <div class="page-title">
      <span class="eyebrow">Admin</span>
      <h1>會員詳情</h1>
    </div>

    <RouterLink class="btn btn-outline-dark btn-sm admin-member-back" to="/admin/users">
      <i class="bi bi-arrow-left" aria-hidden="true"></i>
      返回會員列表
    </RouterLink>

    <div v-if="errorMessage" class="state-panel" role="alert">
      <i class="bi bi-exclamation-triangle" aria-hidden="true"></i>
      <span>{{ errorMessage }}</span>
    </div>

    <div v-if="loading" class="status-panel">
      <div class="skeleton-line w-50"></div>
      <div class="skeleton-line"></div>
      <div class="skeleton-line w-75"></div>
    </div>

    <template v-else-if="member">
      <section class="status-panel">
        <div class="section-heading">
          <div>
            <span class="eyebrow">Profile</span>
            <h2>#{{ member.id }} {{ member.displayName }}</h2>
          </div>
          <div class="admin-member-badges">
            <span class="badge" :class="statusBadgeClass(member.status)">{{
              member.statusLabel
            }}</span>
            <span class="badge text-bg-light border">{{ member.roleLabel }}</span>
            <span class="badge text-bg-light border">VIP：{{ member.vipLevel }}</span>
          </div>
        </div>

        <dl class="admin-member-profile">
          <div>
            <dt>Email</dt>
            <dd>{{ member.email }}</dd>
          </div>
          <div>
            <dt>手機</dt>
            <dd>{{ member.phone || '-' }}</dd>
          </div>
          <div>
            <dt>註冊時間</dt>
            <dd>{{ formatTime(member.createdAt) }}</dd>
          </div>
          <div>
            <dt>最後登入</dt>
            <dd>{{ formatTime(member.lastLoginAt) }}</dd>
          </div>
        </dl>
        <div class="admin-member-pii-panel">
          <p class="admin-member-pii-note">
            <i class="bi bi-shield-lock me-1" aria-hidden="true"></i>
            {{
              member.piiRevealed
                ? '完整個資已揭露，本次查閱已記錄稽核。'
                : '完整個資預設遮罩；需要處理出貨或客服案件時可揭露並記錄稽核。'
            }}
          </p>
          <button
            v-if="!member.piiRevealed"
            class="btn btn-outline-danger btn-sm"
            type="button"
            :disabled="revealSubmitting"
            @click="revealPii"
          >
            <i class="bi bi-eye" aria-hidden="true"></i>
            {{ revealSubmitting ? '揭露中…' : '顯示完整個資' }}
          </button>
          <span v-else class="badge text-bg-danger">
            <i class="bi bi-eye-fill me-1" aria-hidden="true"></i>
            已揭露
          </span>
        </div>
        <p v-if="revealError" class="admin-member-note-form__err" role="alert">
          {{ revealError }}
        </p>
      </section>

      <section class="status-panel">
        <div class="section-heading">
          <div>
            <span class="eyebrow">Wallet &amp; Activity</span>
            <h2>錢包與活動</h2>
          </div>
        </div>
        <div class="status-grid admin-member-stats">
          <div>
            <strong>{{ formatPoint(member.availableBalance) }}</strong
            ><span>可用點數</span>
          </div>
          <div>
            <strong>{{ formatPoint(member.cashPointBalance) }}</strong
            ><span>現金點</span>
          </div>
          <div>
            <strong>{{ formatPoint(member.bonusPointBalance) }}</strong
            ><span>紅利點</span>
          </div>
          <div>
            <strong>{{ formatPoint(member.lockedBalance) }}</strong
            ><span>鎖定點</span>
          </div>
          <div>
            <strong>{{ member.completedDrawCount }}</strong
            ><span>完成抽賞</span>
          </div>
          <div>
            <strong>{{ formatPoint(member.totalDrawSpend) }}</strong
            ><span>累積抽賞消費</span>
          </div>
          <div>
            <strong>{{ member.paidOrderCount }}</strong
            ><span>已付款訂單</span>
          </div>
          <div>
            <strong>{{ formatPoint(member.paidAmount) }}</strong
            ><span>累積儲值金額</span>
          </div>
          <div>
            <strong>{{ member.prizeCount }}</strong
            ><span>戰利品</span>
          </div>
          <div>
            <strong>{{ member.shipmentCount }}</strong
            ><span>出貨單</span>
          </div>
        </div>
      </section>

      <section class="status-panel">
        <div class="section-heading">
          <div>
            <span class="eyebrow">Prizes</span>
            <h2>戰利品查詢</h2>
          </div>
          <span class="admin-member-pii-note">顯示最近 20 筆戰利品，供客服查單使用。</span>
        </div>
        <div v-if="member.recentPrizes.length === 0" class="empty-state">
          <i class="bi bi-box-seam" aria-hidden="true"></i>
          <strong>尚無戰利品紀錄</strong>
        </div>
        <ul v-else class="admin-member-prize-list">
          <li v-for="item in member.recentPrizes" :key="item.id" class="admin-member-prize">
            <span class="admin-member-prize__rank">{{ item.prizeRank }}</span>
            <div class="admin-member-prize__body">
              <strong>{{ item.prizeName }}</strong>
              <span>{{ item.campaignTitle }}｜{{ item.campaignSlug }}</span>
              <small>{{ item.ticketSerialNumber || '無籤號' }}</small>
            </div>
            <div class="admin-member-prize__meta">
              <span class="badge" :class="prizeStatusBadgeClass(item.status)">
                {{ item.statusLabel }}
              </span>
              <span v-if="item.shipmentId">出貨單 #{{ item.shipmentId }}</span>
              <time>{{ formatTime(item.acquiredAt) }}</time>
            </div>
          </li>
        </ul>
      </section>

      <section class="status-panel">
        <div class="section-heading">
          <div>
            <span class="eyebrow">Addresses</span>
            <h2>收件地址</h2>
          </div>
        </div>
        <div v-if="member.addresses.length === 0" class="empty-state">
          <i class="bi bi-geo-alt" aria-hidden="true"></i>
          <strong>尚未建立收件地址</strong>
        </div>
        <ul v-else class="admin-member-address-list">
          <li v-for="addr in member.addresses" :key="addr.id" class="admin-member-address">
            <div class="admin-member-address__head">
              <strong>{{ addr.recipientName }}</strong>
              <span v-if="addr.defaultAddress" class="badge text-bg-danger">預設</span>
              <span class="admin-member-address__phone">{{ addr.phone }}</span>
            </div>
            <p>{{ addr.postalCode }} {{ addr.city }}{{ addr.district }}{{ addr.addressLine }}</p>
          </li>
        </ul>
      </section>

      <section class="status-panel">
        <div class="section-heading">
          <div>
            <span class="eyebrow">Compensation</span>
            <h2>客服補償發點</h2>
          </div>
          <span class="admin-member-pii-note"
            >補償點以紅利點入帳，並記入點數流水（客服補償）。</span
          >
        </div>

        <form class="admin-member-comp-form" @submit.prevent="grantCompensation">
          <div class="admin-member-comp-form__row">
            <div>
              <label class="form-label" for="compAmount">補償點數</label>
              <input
                id="compAmount"
                v-model="compForm.amount"
                class="form-control"
                type="number"
                min="1"
                placeholder="例如 50"
              />
            </div>
            <div class="admin-member-comp-form__reason">
              <label class="form-label" for="compReason">補償原因</label>
              <input
                id="compReason"
                v-model="compForm.reason"
                class="form-control"
                type="text"
                maxlength="200"
                placeholder="例如：出貨延遲補償"
              />
            </div>
          </div>
          <div class="admin-member-comp-form__foot">
            <button class="btn btn-danger" type="submit" :disabled="compForm.submitting">
              <i class="bi bi-gift me-1" aria-hidden="true"></i>
              {{ compForm.submitting ? '發放中…' : '發放補償點' }}
            </button>
            <button
              class="btn btn-outline-dark"
              type="button"
              :disabled="compForm.submitting"
              @click="requestCompensationReview"
            >
              <i class="bi bi-clipboard-check me-1" aria-hidden="true"></i>
              送審
            </button>
            <p v-if="compMessage" class="admin-member-note-form__ok" role="status">
              {{ compMessage }}
            </p>
            <p v-if="compError" class="admin-member-note-form__err" role="alert">{{ compError }}</p>
          </div>
        </form>
      </section>

      <section class="status-panel">
        <div class="section-heading">
          <div>
            <span class="eyebrow">CS Notes</span>
            <h2>客服備註</h2>
          </div>
        </div>

        <form class="admin-member-note-form" @submit.prevent="addNote">
          <textarea
            v-model="noteForm.content"
            class="form-control"
            rows="2"
            maxlength="500"
            placeholder="記錄與此會員的客服互動、處理結果等（內部可見）"
          ></textarea>
          <div class="admin-member-note-form__foot">
            <button class="btn btn-danger" type="submit" :disabled="noteForm.submitting">
              <i class="bi bi-journal-plus me-1" aria-hidden="true"></i>
              {{ noteForm.submitting ? '新增中…' : '新增備註' }}
            </button>
            <p v-if="noteError" class="admin-member-note-form__err" role="alert">{{ noteError }}</p>
          </div>
        </form>

        <div v-if="member.notes.length === 0" class="empty-state">
          <i class="bi bi-journal-text" aria-hidden="true"></i>
          <strong>尚無客服備註</strong>
        </div>
        <ul v-else class="admin-member-note-list">
          <li v-for="note in member.notes" :key="note.id" class="admin-member-note">
            <p>{{ note.content }}</p>
            <div class="admin-member-note__meta">
              <span
                ><i class="bi bi-person-badge" aria-hidden="true"></i> {{ note.authorName }}</span
              >
              <time>{{ formatTime(note.createdAt) }}</time>
            </div>
          </li>
        </ul>
      </section>

      <section class="status-panel">
        <div class="section-heading">
          <div>
            <span class="eyebrow">Ledger</span>
            <h2>近期點數流水</h2>
          </div>
          <RouterLink class="btn btn-outline-dark btn-sm" to="/admin/wallet-ledger">
            前往點數流水
          </RouterLink>
        </div>
        <div v-if="member.recentLedger.length === 0" class="empty-state">
          <i class="bi bi-wallet2" aria-hidden="true"></i>
          <strong>尚無點數流水</strong>
        </div>
        <ul v-else class="admin-member-ledger-list">
          <li v-for="row in member.recentLedger" :key="row.id" class="admin-member-ledger">
            <div class="admin-member-ledger__main">
              <strong>{{ row.typeLabel }}</strong>
              <span>{{ row.reason || '-' }}</span>
            </div>
            <div class="admin-member-ledger__amounts">
              <strong :class="amountClass(row.amount)">{{ formatSignedPoint(row.amount) }}</strong>
              <span>餘額 {{ formatPoint(row.balanceAfter) }}</span>
            </div>
            <time>{{ formatTime(row.createdAt) }}</time>
          </li>
        </ul>
      </section>
    </template>
  </article>
</template>

<style scoped>
.admin-member-back {
  margin-bottom: 1rem;
}

.admin-member-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
}

.admin-member-profile {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(14rem, 1fr));
  gap: 0.75rem 1.5rem;
  margin: 0.5rem 0 0.75rem;
}

.admin-member-profile div {
  display: grid;
  gap: 0.15rem;
}

.admin-member-profile dt {
  font-size: 0.78rem;
  color: var(--lb-muted, #6c757d);
}

.admin-member-profile dd {
  margin: 0;
  font-weight: 600;
  word-break: break-all;
}

.admin-member-pii-note {
  margin: 0;
  font-size: 0.8rem;
  color: var(--lb-muted, #6c757d);
}

.admin-member-pii-panel {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem 0.75rem;
}

.admin-member-stats strong {
  font-size: 1.2rem;
}

.admin-member-address-list,
.admin-member-prize-list,
.admin-member-ledger-list {
  display: grid;
  gap: 0.5rem;
  margin: 0;
  padding: 0;
  list-style: none;
}

.admin-member-address {
  padding: 0.65rem 0.85rem;
  border: 1px solid var(--bs-border-color, #e3e3e8);
  border-radius: 0.7rem;
}

.admin-member-address__head {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.admin-member-address__phone {
  color: var(--lb-muted, #6c757d);
  font-size: 0.85rem;
}

.admin-member-address p {
  margin: 0.25rem 0 0;
  color: var(--lb-ink, #1f2933);
}

.admin-member-prize {
  display: grid;
  grid-template-columns: 2.75rem minmax(0, 1fr) auto;
  gap: 0.7rem;
  align-items: center;
  padding: 0.65rem 0.85rem;
  border: 1px solid var(--bs-border-color, #e3e3e8);
  border-radius: 0.7rem;
  background: var(--bs-body-bg, #fff);
}

.admin-member-prize__rank {
  display: inline-grid;
  width: 2.25rem;
  height: 2.25rem;
  place-items: center;
  border-radius: 0.55rem;
  background: var(--lb-surface-warm, #fff2ea);
  color: var(--lb-coral-dark, #b43f2e);
  font-weight: 900;
}

.admin-member-prize__body,
.admin-member-prize__meta {
  display: grid;
  min-width: 0;
  gap: 0.15rem;
}

.admin-member-prize__body strong,
.admin-member-prize__body span,
.admin-member-prize__body small,
.admin-member-prize__meta span {
  overflow-wrap: anywhere;
}

.admin-member-prize__body span,
.admin-member-prize__body small,
.admin-member-prize__meta,
.admin-member-prize__meta time {
  color: var(--lb-muted, #6c757d);
  font-size: 0.82rem;
}

.admin-member-prize__meta {
  justify-items: end;
}

.admin-member-ledger {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.4rem 1rem;
  padding: 0.6rem 0.85rem;
  border: 1px solid var(--bs-border-color, #e3e3e8);
  border-radius: 0.7rem;
}

.admin-member-ledger__main {
  display: grid;
  gap: 0.15rem;
  min-width: 0;
}

.admin-member-ledger__main span {
  font-size: 0.82rem;
  color: var(--lb-muted, #6c757d);
  word-break: break-all;
}

.admin-member-ledger__amounts {
  display: grid;
  gap: 0.1rem;
  text-align: right;
}

.admin-member-ledger__amounts span {
  font-size: 0.78rem;
  color: var(--lb-muted, #6c757d);
}

.admin-member-ledger time {
  font-size: 0.78rem;
  color: var(--lb-muted, #6c757d);
}

.detail-amount--credit {
  color: #0f766e;
}

.detail-amount--debit {
  color: #b91c1c;
}

.admin-member-note-form {
  display: grid;
  gap: 0.5rem;
  margin-bottom: 0.85rem;
}

.admin-member-note-form__foot {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem 1rem;
}

.admin-member-note-form__err {
  margin: 0;
  font-size: 0.85rem;
  color: #b91c1c;
}

.admin-member-note-form__ok {
  margin: 0;
  font-size: 0.85rem;
  color: #0f766e;
}

.admin-member-comp-form {
  display: grid;
  gap: 0.75rem;
}

.admin-member-comp-form__row {
  display: grid;
  grid-template-columns: 8rem 1fr;
  gap: 0.75rem;
}

.admin-member-comp-form__foot {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem 1rem;
}

@media (max-width: 575.98px) {
  .admin-member-comp-form__row {
    grid-template-columns: 1fr;
  }

  .admin-member-prize {
    grid-template-columns: auto minmax(0, 1fr);
  }

  .admin-member-prize__meta {
    grid-column: 1 / -1;
    justify-items: start;
  }
}

.admin-member-note-list {
  display: grid;
  gap: 0.5rem;
  margin: 0;
  padding: 0;
  list-style: none;
}

.admin-member-note {
  padding: 0.65rem 0.85rem;
  border: 1px solid var(--bs-border-color, #e3e3e8);
  border-radius: 0.7rem;
  background: var(--bs-body-bg, #fff);
}

.admin-member-note p {
  margin: 0 0 0.35rem;
  word-break: break-word;
}

.admin-member-note__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem 1rem;
  font-size: 0.78rem;
  color: var(--lb-muted, #6c757d);
}
</style>

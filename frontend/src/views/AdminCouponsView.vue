<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { createAdminCoupon, fetchAdminCoupons, updateAdminCoupon } from '@/services/adminCouponApi'
import { useSessionStore } from '@/stores/session'

const router = useRouter()
const session = useSessionStore()

const coupons = ref([])
const loading = ref(true)
const saving = ref(false)
const selectedCouponId = ref(null)
const statusFilter = ref('')
const typeFilter = ref('')
const keyword = ref('')
const errorMessage = ref('')
const successMessage = ref('')

const statusOptions = [
  { value: '', label: '全部狀態' },
  { value: 'DRAFT', label: '草稿' },
  { value: 'ACTIVE', label: '啟用' },
  { value: 'ARCHIVED', label: '已封存' },
]

const typeOptions = [
  { value: '', label: '全部類型' },
  { value: 'POINT_BONUS', label: '贈點券' },
  { value: 'DISCOUNT', label: '折扣券' },
  { value: 'FREE_SHIPPING', label: '免運券' },
]

const vipTierOptions = [
  { value: '', label: '全會員' },
  { value: 'SILVER', label: '銀卡以上' },
  { value: 'GOLD', label: '金卡以上' },
  { value: 'PLATINUM', label: '白金卡' },
]

const form = reactive({
  code: '',
  type: 'POINT_BONUS',
  vipTier: '',
  value: 100,
  minSpend: 0,
  usageLimit: '',
  startsAt: '',
  endsAt: '',
  status: 'DRAFT',
})

const selectedCoupon = computed(
  () => coupons.value.find((item) => item.id === selectedCouponId.value) || null,
)
const activeCount = computed(() => coupons.value.filter((item) => item.status === 'ACTIVE').length)
const draftCount = computed(() => coupons.value.filter((item) => item.status === 'DRAFT').length)
const archivedCount = computed(
  () => coupons.value.filter((item) => item.status === 'ARCHIVED').length,
)

watch([statusFilter, typeFilter], async () => {
  await loadCoupons()
})

onMounted(async () => {
  const user = await session.load()
  if (!user) {
    await router.replace({ path: '/admin/login', query: { redirect: '/admin/coupons' } })
    return
  }
  if (!isAdmin(user)) {
    await router.replace('/account')
    return
  }
  await loadCoupons()
})

async function loadCoupons() {
  loading.value = true
  errorMessage.value = ''
  try {
    coupons.value = await fetchAdminCoupons({
      status: statusFilter.value,
      type: typeFilter.value,
      q: keyword.value.trim(),
    })
    if (
      selectedCouponId.value &&
      !coupons.value.some((item) => item.id === selectedCouponId.value)
    ) {
      resetForm()
    }
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '無法載入優惠券。'
  } finally {
    loading.value = false
  }
}

async function submitFilters() {
  await loadCoupons()
}

function selectCoupon(item) {
  selectedCouponId.value = item.id
  form.code = item.code
  form.type = item.type
  form.vipTier = item.vipTier || ''
  form.value = item.value
  form.minSpend = item.minSpend
  form.usageLimit = item.usageLimit ?? ''
  form.startsAt = item.startsAt || ''
  form.endsAt = item.endsAt || ''
  form.status = item.status
  successMessage.value = ''
  errorMessage.value = ''
}

function resetForm() {
  selectedCouponId.value = null
  form.code = ''
  form.type = 'POINT_BONUS'
  form.vipTier = ''
  form.value = 100
  form.minSpend = 0
  form.usageLimit = ''
  form.startsAt = ''
  form.endsAt = ''
  form.status = 'DRAFT'
  successMessage.value = ''
  errorMessage.value = ''
}

async function saveCoupon() {
  saving.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const payload = {
      code: form.code,
      type: form.type,
      vipTier: form.vipTier || null,
      value: Number(form.value),
      minSpend: Number(form.minSpend),
      usageLimit: form.usageLimit === '' ? null : Number(form.usageLimit),
      startsAt: form.startsAt,
      endsAt: form.endsAt,
      status: form.status,
    }
    const saved = selectedCouponId.value
      ? await updateAdminCoupon(selectedCouponId.value, payload)
      : await createAdminCoupon(payload)
    successMessage.value = `優惠券「${saved.code}」已儲存。`
    await loadCoupons()
    selectCoupon(saved)
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '優惠券儲存失敗。'
  } finally {
    saving.value = false
  }
}

function isAdmin(user) {
  return user.role === 'SUPER_ADMIN' || user.role === 'ADMIN'
}

function statusBadgeClass(status) {
  if (status === 'ACTIVE') {
    return 'text-bg-success'
  }
  if (status === 'DRAFT') {
    return 'text-bg-warning'
  }
  return 'text-bg-light border'
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
  }).format(date)
}
</script>

<template>
  <article class="admin-page admin-coupon-page">
    <div class="page-title">
      <span class="eyebrow">Admin</span>
      <h1>優惠券管理</h1>
    </div>

    <form class="admin-toolbar admin-coupon-toolbar" @submit.prevent="submitFilters">
      <div>
        <label class="form-label" for="adminCouponKeyword">搜尋</label>
        <div class="input-group">
          <span class="input-group-text">
            <i class="bi bi-search" aria-hidden="true"></i>
          </span>
          <input
            id="adminCouponKeyword"
            v-model="keyword"
            class="form-control"
            type="search"
            placeholder="代碼、類型或狀態"
          />
        </div>
      </div>

      <div>
        <label class="form-label" for="adminCouponType">類型</label>
        <select id="adminCouponType" v-model="typeFilter" class="form-select">
          <option v-for="option in typeOptions" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
      </div>

      <div>
        <label class="form-label" for="adminCouponStatus">狀態</label>
        <select id="adminCouponStatus" v-model="statusFilter" class="form-select">
          <option v-for="option in statusOptions" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
      </div>

      <button class="btn btn-dark" type="submit" :disabled="loading">
        <i class="bi bi-funnel" aria-hidden="true"></i>
        篩選
      </button>

      <div class="admin-summary-pill">
        <strong>{{ coupons.length }}</strong>
        <span>目前清單</span>
      </div>
      <div class="admin-summary-pill">
        <strong>{{ activeCount }}</strong>
        <span>啟用</span>
      </div>
      <div class="admin-summary-pill">
        <strong>{{ draftCount }}</strong>
        <span>草稿</span>
      </div>
      <div class="admin-summary-pill">
        <strong>{{ archivedCount }}</strong>
        <span>封存</span>
      </div>
    </form>

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

    <section class="admin-coupon-layout">
      <div class="status-panel">
        <div class="section-heading">
          <div>
            <span class="eyebrow">Coupons</span>
            <h2>優惠券列表</h2>
          </div>
          <button class="btn btn-outline-dark btn-sm" type="button" @click="resetForm">
            <i class="bi bi-plus-circle" aria-hidden="true"></i>
            新增優惠券
          </button>
        </div>

        <div v-if="loading" class="admin-coupon-list">
          <div v-for="index in 4" :key="index" class="admin-coupon-card">
            <div class="skeleton-line w-50"></div>
            <div class="skeleton-line"></div>
            <div class="skeleton-line w-75"></div>
          </div>
        </div>

        <div v-else-if="coupons.length === 0" class="empty-state">
          <i class="bi bi-ticket-perforated" aria-hidden="true"></i>
          <strong>沒有符合條件的優惠券</strong>
        </div>

        <div v-else class="admin-coupon-list">
          <button
            v-for="item in coupons"
            :key="item.id"
            class="admin-coupon-card"
            :class="{ 'admin-coupon-card--active': selectedCouponId === item.id }"
            type="button"
            @click="selectCoupon(item)"
          >
            <span class="admin-coupon-card__head">
              <strong>#{{ item.id }} {{ item.code }}</strong>
              <span class="badge" :class="statusBadgeClass(item.status)">
                {{ item.statusLabel }}
              </span>
            </span>
            <span>{{ item.typeLabel }} / 滿 {{ item.minSpend }} 可用</span>
            <small>{{ item.vipTierLabel || '全會員' }}</small>
            <small>期限：{{ formatTime(item.startsAt) }} - {{ formatTime(item.endsAt) }}</small>
          </button>
        </div>
      </div>

      <form class="status-panel admin-coupon-form" @submit.prevent="saveCoupon">
        <div class="section-heading">
          <div>
            <span class="eyebrow">Editor</span>
            <h2>{{ selectedCoupon ? '編輯優惠券' : '新增優惠券' }}</h2>
          </div>
        </div>

        <div class="admin-coupon-form-grid">
          <div>
            <label class="form-label" for="couponCode">代碼</label>
            <input
              id="couponCode"
              v-model.trim="form.code"
              class="form-control"
              placeholder="SUMMER100"
              required
            />
          </div>
          <div>
            <label class="form-label" for="couponType">類型</label>
            <select id="couponType" v-model="form.type" class="form-select">
              <option value="POINT_BONUS">贈點券</option>
              <option value="DISCOUNT">折扣券</option>
              <option value="FREE_SHIPPING">免運券</option>
            </select>
          </div>
          <div>
            <label class="form-label" for="couponVipTier">適用會員</label>
            <select id="couponVipTier" v-model="form.vipTier" class="form-select">
              <option v-for="option in vipTierOptions" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </div>
          <div>
            <label class="form-label" for="couponValue">面額</label>
            <input
              id="couponValue"
              v-model.number="form.value"
              class="form-control"
              type="number"
              min="0"
              required
            />
          </div>
          <div>
            <label class="form-label" for="couponMinSpend">最低消費</label>
            <input
              id="couponMinSpend"
              v-model.number="form.minSpend"
              class="form-control"
              type="number"
              min="0"
              required
            />
          </div>
          <div>
            <label class="form-label" for="couponUsageLimit">使用上限</label>
            <input
              id="couponUsageLimit"
              v-model="form.usageLimit"
              class="form-control"
              type="number"
              min="1"
              placeholder="留空為不限"
            />
          </div>
          <div>
            <label class="form-label" for="couponStatus">狀態</label>
            <select id="couponStatus" v-model="form.status" class="form-select">
              <option value="DRAFT">草稿</option>
              <option value="ACTIVE">啟用</option>
              <option value="ARCHIVED">已封存</option>
            </select>
          </div>
          <div>
            <label class="form-label" for="couponStartsAt">開始時間</label>
            <input
              id="couponStartsAt"
              v-model="form.startsAt"
              class="form-control"
              placeholder="2026-06-18T00:00:00Z"
            />
          </div>
          <div>
            <label class="form-label" for="couponEndsAt">結束時間</label>
            <input
              id="couponEndsAt"
              v-model="form.endsAt"
              class="form-control"
              placeholder="2026-12-31T23:59:59Z"
            />
          </div>
        </div>

        <div class="admin-prize-actions">
          <button class="btn btn-danger" type="submit" :disabled="saving">
            <i class="bi bi-save" aria-hidden="true"></i>
            {{ saving ? '儲存中' : '儲存優惠券' }}
          </button>
          <button class="btn btn-outline-dark" type="button" @click="resetForm">
            <i class="bi bi-eraser" aria-hidden="true"></i>
            清空
          </button>
        </div>
      </form>
    </section>
  </article>
</template>

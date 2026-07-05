<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  approveAdminApprovalRequest,
  fetchAdminApprovalRequests,
  rejectAdminApprovalRequest,
} from '@/services/adminApprovalApi'
import { useSessionStore } from '@/stores/session'

const router = useRouter()
const session = useSessionStore()

const requests = ref([])
const loading = ref(true)
const updatingId = ref(null)
const rejectingId = ref(null)
const rejectReason = ref('')
const statusFilter = ref('PENDING')
const typeFilter = ref('')
const errorMessage = ref('')
const successMessage = ref('')

const statusOptions = [
  { value: '', label: '全部狀態' },
  { value: 'PENDING', label: '待審核' },
  { value: 'APPROVED', label: '已核准' },
  { value: 'REJECTED', label: '已駁回' },
]

const typeOptions = [
  { value: '', label: '全部類型' },
  { value: 'WALLET_ADJUSTMENT', label: '點數調整' },
  { value: 'PAYMENT_REFUND', label: '退款' },
  { value: 'COMPENSATION', label: '客服補償' },
]

const pendingCount = computed(() => requests.value.filter((item) => item.status === 'PENDING').length)
const approvedCount = computed(
  () => requests.value.filter((item) => item.status === 'APPROVED').length,
)
const rejectedCount = computed(
  () => requests.value.filter((item) => item.status === 'REJECTED').length,
)

watch([statusFilter, typeFilter], async () => {
  await loadRequests()
})

onMounted(async () => {
  const user = await session.load()
  if (!user) {
    await router.replace({ path: '/admin/login', query: { redirect: '/admin/approvals' } })
    return
  }
  if (!isAdmin(user)) {
    await router.replace('/account')
    return
  }
  await loadRequests()
})

async function loadRequests() {
  loading.value = true
  errorMessage.value = ''
  try {
    requests.value = await fetchAdminApprovalRequests({
      status: statusFilter.value,
      type: typeFilter.value,
    })
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '無法載入審核清單。'
  } finally {
    loading.value = false
  }
}

async function approveRequest(item) {
  updatingId.value = item.id
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const updated = await approveAdminApprovalRequest(item.id)
    successMessage.value = `審核單 #${updated.id} 已核准。`
    await loadRequests()
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '核准失敗。'
  } finally {
    updatingId.value = null
  }
}

function openReject(item) {
  rejectingId.value = item.id
  rejectReason.value = ''
  errorMessage.value = ''
  successMessage.value = ''
}

function cancelReject() {
  rejectingId.value = null
  rejectReason.value = ''
}

async function rejectRequest(item) {
  updatingId.value = item.id
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const updated = await rejectAdminApprovalRequest(item.id, rejectReason.value.trim())
    successMessage.value = `審核單 #${updated.id} 已駁回。`
    cancelReject()
    await loadRequests()
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '駁回失敗。'
  } finally {
    updatingId.value = null
  }
}

function isAdmin(user) {
  return user.role === 'SUPER_ADMIN' || user.role === 'ADMIN'
}

function canReview(item) {
  return session.user?.role === 'SUPER_ADMIN' && item.status === 'PENDING'
}

function statusBadgeClass(status) {
  if (status === 'APPROVED') {
    return 'text-bg-success'
  }
  if (status === 'REJECTED') {
    return 'text-bg-secondary'
  }
  return 'text-bg-warning'
}

function formatTime(value) {
  if (!value) {
    return '-'
  }
  return new Intl.DateTimeFormat('zh-TW', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}
</script>

<template>
  <article class="admin-page admin-approvals-page">
    <div class="page-title">
      <span class="eyebrow">Admin</span>
      <h1>審核中心</h1>
    </div>

    <form class="admin-toolbar admin-approval-toolbar" @submit.prevent="loadRequests">
      <div>
        <label class="form-label" for="approvalStatus">狀態</label>
        <select id="approvalStatus" v-model="statusFilter" class="form-select">
          <option v-for="option in statusOptions" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
      </div>

      <div>
        <label class="form-label" for="approvalType">類型</label>
        <select id="approvalType" v-model="typeFilter" class="form-select">
          <option v-for="option in typeOptions" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
      </div>

      <button class="btn btn-dark" type="submit" :disabled="loading">
        <i class="bi bi-funnel" aria-hidden="true"></i>
        篩選
      </button>

      <div class="admin-summary-pill">
        <strong>{{ requests.length }}</strong>
        <span>目前清單</span>
      </div>
      <div class="admin-summary-pill">
        <strong>{{ pendingCount }}</strong>
        <span>待審核</span>
      </div>
      <div class="admin-summary-pill">
        <strong>{{ approvedCount }}</strong>
        <span>已核准</span>
      </div>
      <div class="admin-summary-pill">
        <strong>{{ rejectedCount }}</strong>
        <span>已駁回</span>
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

    <section class="status-panel">
      <div class="section-heading">
        <div>
          <span class="eyebrow">Approvals</span>
          <h2>審核單</h2>
        </div>
      </div>

      <div v-if="loading" class="admin-approval-list">
        <div v-for="index in 4" :key="index" class="admin-approval-card">
          <div class="skeleton-line w-50"></div>
          <div class="skeleton-line"></div>
          <div class="skeleton-line w-75"></div>
        </div>
      </div>

      <div v-else-if="requests.length === 0" class="empty-state">
        <i class="bi bi-clipboard-check" aria-hidden="true"></i>
        <strong>沒有符合條件的審核單</strong>
      </div>

      <div v-else class="admin-approval-list">
        <article v-for="item in requests" :key="item.id" class="admin-approval-card">
          <header class="admin-approval-card__head">
            <div>
              <strong>#{{ item.id }} {{ item.typeLabel }}</strong>
              <span>{{ item.entityType }} #{{ item.entityId }}</span>
            </div>
            <span class="badge" :class="statusBadgeClass(item.status)">
              {{ item.statusLabel }}
            </span>
          </header>

          <div class="admin-approval-card__meta">
            <span>
              <i class="bi bi-person" aria-hidden="true"></i>
              {{ item.requestedByDisplayName }}
            </span>
            <span>
              <i class="bi bi-clock" aria-hidden="true"></i>
              {{ formatTime(item.createdAt) }}
            </span>
            <span v-if="item.reviewedByDisplayName">
              <i class="bi bi-person-check" aria-hidden="true"></i>
              {{ item.reviewedByDisplayName }}
            </span>
            <span v-if="item.reviewedAt">
              <i class="bi bi-check2-square" aria-hidden="true"></i>
              {{ formatTime(item.reviewedAt) }}
            </span>
          </div>

          <p class="admin-approval-card__reason">{{ item.reason }}</p>
          <code class="admin-approval-card__payload">{{ item.payloadJson }}</code>

          <div v-if="item.resultEntityType" class="admin-approval-card__result">
            <i class="bi bi-link-45deg" aria-hidden="true"></i>
            {{ item.resultEntityType }} #{{ item.resultEntityId }}
          </div>

          <div v-if="canReview(item)" class="admin-approval-card__actions">
            <button
              class="btn btn-danger btn-sm"
              type="button"
              :disabled="updatingId === item.id"
              @click="approveRequest(item)"
            >
              <i class="bi bi-check-circle" aria-hidden="true"></i>
              核准
            </button>
            <button
              v-if="rejectingId !== item.id"
              class="btn btn-outline-dark btn-sm"
              type="button"
              :disabled="updatingId === item.id"
              @click="openReject(item)"
            >
              <i class="bi bi-x-circle" aria-hidden="true"></i>
              駁回
            </button>
            <div v-else class="admin-approval-reject">
              <input
                v-model="rejectReason"
                class="form-control form-control-sm"
                type="text"
                maxlength="200"
                placeholder="駁回原因"
              />
              <button
                class="btn btn-outline-danger btn-sm"
                type="button"
                :disabled="updatingId === item.id"
                @click="rejectRequest(item)"
              >
                確認駁回
              </button>
              <button class="btn btn-outline-dark btn-sm" type="button" @click="cancelReject">
                取消
              </button>
            </div>
          </div>
        </article>
      </div>
    </section>
  </article>
</template>

<style scoped>
.admin-approval-list {
  display: grid;
  gap: 0.85rem;
}

.admin-approval-card {
  border: 1px solid var(--lb-border, #dee2e6);
  border-radius: 8px;
  padding: 1rem;
  display: grid;
  gap: 0.75rem;
}

.admin-approval-card__head,
.admin-approval-card__meta,
.admin-approval-card__actions,
.admin-approval-reject {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.65rem;
}

.admin-approval-card__head {
  justify-content: space-between;
}

.admin-approval-card__head div,
.admin-approval-card__meta span {
  display: grid;
  gap: 0.15rem;
}

.admin-approval-card__head span,
.admin-approval-card__meta,
.admin-approval-card__result {
  color: var(--lb-muted, #6c757d);
  font-size: 0.9rem;
}

.admin-approval-card__reason {
  margin: 0;
  color: var(--lb-ink, #212529);
}

.admin-approval-card__payload {
  display: block;
  width: 100%;
  white-space: normal;
  overflow-wrap: anywhere;
  background: var(--lb-soft, #f8f9fa);
  border-radius: 6px;
  padding: 0.65rem;
}

.admin-approval-reject .form-control {
  width: min(320px, 100%);
}
</style>

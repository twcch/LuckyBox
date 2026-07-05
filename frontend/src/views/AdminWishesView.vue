<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { fetchAdminWishes, moderateWish } from '@/services/adminWishApi'
import { useSessionStore } from '@/stores/session'

const router = useRouter()
const session = useSessionStore()

const wishes = ref([])
const loading = ref(true)
const statusFilter = ref('')
const errorMessage = ref('')
const successMessage = ref('')
const pendingId = ref(null)

const statusOptions = [
  { value: '', label: '全部狀態' },
  { value: 'PENDING', label: '審核中' },
  { value: 'APPROVED', label: '已上牆' },
  { value: 'REJECTED', label: '未採用' },
  { value: 'HIDDEN', label: '已隱藏' },
]

const pendingCount = computed(() => wishes.value.filter((w) => w.status === 'PENDING').length)
const approvedCount = computed(() => wishes.value.filter((w) => w.status === 'APPROVED').length)

watch(statusFilter, async () => {
  await loadWishes()
})

onMounted(async () => {
  const user = await session.load()
  if (!user) {
    await router.replace({ path: '/admin/login', query: { redirect: '/admin/wishes' } })
    return
  }
  if (!isAdmin(user)) {
    await router.replace('/account')
    return
  }
  await loadWishes()
})

async function loadWishes() {
  loading.value = true
  errorMessage.value = ''
  try {
    wishes.value = await fetchAdminWishes({ status: statusFilter.value })
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '無法載入願望清單。'
  } finally {
    loading.value = false
  }
}

async function moderate(wish, status) {
  pendingId.value = wish.id
  errorMessage.value = ''
  successMessage.value = ''
  try {
    await moderateWish(wish.id, { status })
    successMessage.value = `已將願望 #${wish.id} 設為「${statusLabel(status)}」。`
    await loadWishes()
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '操作失敗，請稍後再試。'
  } finally {
    pendingId.value = null
  }
}

function isAdmin(user) {
  return user.role === 'SUPER_ADMIN' || user.role === 'ADMIN'
}

function statusLabel(status) {
  switch (status) {
    case 'APPROVED':
      return '已上牆'
    case 'PENDING':
      return '審核中'
    case 'REJECTED':
      return '未採用'
    case 'HIDDEN':
      return '已隱藏'
    default:
      return status
  }
}

function statusBadgeClass(status) {
  if (status === 'APPROVED') {
    return 'text-bg-success'
  }
  if (status === 'PENDING') {
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
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}
</script>

<template>
  <article class="admin-page admin-wishes-page">
    <div class="page-title">
      <span class="eyebrow">Admin</span>
      <h1>許願牆管理</h1>
    </div>

    <div class="admin-toolbar">
      <div>
        <label class="form-label" for="adminWishStatus">狀態</label>
        <select id="adminWishStatus" v-model="statusFilter" class="form-select">
          <option v-for="option in statusOptions" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
      </div>

      <div class="admin-summary-pill">
        <strong>{{ wishes.length }}</strong>
        <span>目前清單</span>
      </div>
      <div class="admin-summary-pill">
        <strong>{{ pendingCount }}</strong>
        <span>待審核</span>
      </div>
      <div class="admin-summary-pill">
        <strong>{{ approvedCount }}</strong>
        <span>已上牆</span>
      </div>
    </div>

    <div v-if="errorMessage" class="state-panel" role="alert">
      <i class="bi bi-exclamation-triangle" aria-hidden="true"></i>
      <span>{{ errorMessage }}</span>
    </div>
    <div v-if="successMessage" class="toast show lb-toast" role="status">
      <div class="toast-body">
        <i class="bi bi-check-circle me-1" aria-hidden="true"></i>
        {{ successMessage }}
      </div>
    </div>

    <div v-if="loading" class="admin-wishes-list">
      <div v-for="index in 3" :key="index" class="admin-wish-card">
        <div class="skeleton-line w-75"></div>
        <div class="skeleton-line w-50"></div>
      </div>
    </div>

    <div v-else-if="wishes.length === 0" class="empty-state">
      <i class="bi bi-stars" aria-hidden="true"></i>
      <strong>沒有符合條件的願望</strong>
    </div>

    <ul v-else class="admin-wishes-list">
      <li v-for="wish in wishes" :key="wish.id" class="admin-wish-card">
        <div class="admin-wish-card__main">
          <p class="admin-wish-card__content">{{ wish.content }}</p>
          <div class="admin-wish-card__meta">
            <span
              ><i class="bi bi-person" aria-hidden="true"></i> {{ wish.authorDisplayName }}</span
            >
            <span>{{ wish.authorEmail }}</span>
            <span>{{ formatTime(wish.createdAt) }}</span>
          </div>
        </div>

        <div class="admin-wish-card__side">
          <span class="badge" :class="statusBadgeClass(wish.status)">{{
            statusLabel(wish.status)
          }}</span>
          <div class="admin-wish-card__actions">
            <button
              v-if="wish.status !== 'APPROVED'"
              class="btn btn-sm btn-success"
              type="button"
              :disabled="pendingId === wish.id"
              @click="moderate(wish, 'APPROVED')"
            >
              核准
            </button>
            <button
              v-if="wish.status !== 'HIDDEN'"
              class="btn btn-sm btn-outline-dark"
              type="button"
              :disabled="pendingId === wish.id"
              @click="moderate(wish, 'HIDDEN')"
            >
              隱藏
            </button>
            <button
              v-if="wish.status !== 'REJECTED'"
              class="btn btn-sm btn-outline-danger"
              type="button"
              :disabled="pendingId === wish.id"
              @click="moderate(wish, 'REJECTED')"
            >
              退回
            </button>
          </div>
        </div>
      </li>
    </ul>
  </article>
</template>

<style scoped>
.admin-wishes-list {
  display: grid;
  gap: 0.75rem;
  margin: 1rem 0 0;
  padding: 0;
  list-style: none;
}

.admin-wish-card {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem 1.5rem;
  align-items: flex-start;
  justify-content: space-between;
  padding: 1rem 1.1rem;
  border: 1px solid var(--bs-border-color, #e3e3e8);
  border-radius: 0.85rem;
  background: var(--bs-body-bg, #fff);
}

.admin-wish-card__main {
  flex: 1 1 18rem;
  min-width: 0;
}

.admin-wish-card__content {
  margin: 0 0 0.5rem;
  font-size: 0.98rem;
  line-height: 1.5;
  word-break: break-word;
}

.admin-wish-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 0.2rem 1rem;
  font-size: 0.78rem;
  color: var(--bs-secondary-color, #6c757d);
}

.admin-wish-card__side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 0.6rem;
}

.admin-wish-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
  justify-content: flex-end;
}
</style>

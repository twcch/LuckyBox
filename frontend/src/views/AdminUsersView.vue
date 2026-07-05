<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { fetchAdminUsers, updateAdminUserRole, updateAdminUserStatus } from '@/services/adminUserApi'
import { useSessionStore } from '@/stores/session'

const router = useRouter()
const session = useSessionStore()

const users = ref([])
const loading = ref(true)
const updatingUserId = ref(null)
const statusFilter = ref('')
const roleFilter = ref('')
const keyword = ref('')
const errorMessage = ref('')
const successMessage = ref('')

const statusOptions = [
  { value: '', label: '全部狀態' },
  { value: 'ACTIVE', label: '啟用' },
  { value: 'SUSPENDED', label: '停權' },
  { value: 'DELETED', label: '已刪除' },
]

const roleOptions = [
  { value: '', label: '全部角色' },
  { value: 'USER', label: '會員' },
  { value: 'CUSTOMER_SERVICE', label: '客服' },
  { value: 'OPERATOR', label: '營運' },
  { value: 'ADMIN', label: '管理員' },
  { value: 'SUPER_ADMIN', label: '超級管理員' },
]

const roleAssignmentOptions = [
  { value: 'USER', label: '會員' },
  { value: 'CUSTOMER_SERVICE', label: '客服' },
  { value: 'OPERATOR', label: '營運' },
  { value: 'ADMIN', label: '管理員' },
]

const activeCount = computed(() => users.value.filter((user) => user.status === 'ACTIVE').length)
const suspendedCount = computed(
  () => users.value.filter((user) => user.status === 'SUSPENDED').length,
)
const adminCount = computed(
  () =>
    users.value.filter((user) =>
      ['SUPER_ADMIN', 'ADMIN', 'OPERATOR', 'CUSTOMER_SERVICE'].includes(user.role),
    ).length,
)

watch([statusFilter, roleFilter], async () => {
  await loadUsers()
})

onMounted(async () => {
  const user = await session.load()
  if (!user) {
    await router.replace({ path: '/admin/login', query: { redirect: '/admin/users' } })
    return
  }
  if (!isAdmin(user)) {
    await router.replace('/account')
    return
  }
  await loadUsers()
})

async function loadUsers() {
  loading.value = true
  errorMessage.value = ''
  try {
    users.value = await fetchAdminUsers({
      status: statusFilter.value,
      role: roleFilter.value,
      q: keyword.value.trim(),
    })
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '無法載入會員清單。'
  } finally {
    loading.value = false
  }
}

async function submitFilters() {
  await loadUsers()
}

async function changeStatus(user, status) {
  updatingUserId.value = user.id
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const updated = await updateAdminUserStatus(user.id, status)
    successMessage.value = `會員「${updated.displayName}」已更新為${updated.statusLabel}。`
    await loadUsers()
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '會員狀態更新失敗。'
  } finally {
    updatingUserId.value = null
  }
}

async function changeRole(user, role) {
  if (!role || role === user.role) {
    return
  }
  updatingUserId.value = user.id
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const updated = await updateAdminUserRole(user.id, role)
    successMessage.value = `會員「${updated.displayName}」角色已更新為${updated.roleLabel}。`
    await loadUsers()
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '會員角色更新失敗。'
    await loadUsers()
  } finally {
    updatingUserId.value = null
  }
}

function isAdmin(user) {
  return user.role === 'SUPER_ADMIN' || user.role === 'ADMIN'
}

function canChangeRole(user) {
  return session.user?.role === 'SUPER_ADMIN' && user.role !== 'SUPER_ADMIN' && session.user?.id !== user.id
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

function roleBadgeClass(role) {
  if (role === 'SUPER_ADMIN' || role === 'ADMIN') {
    return 'text-bg-danger'
  }
  if (role === 'OPERATOR' || role === 'CUSTOMER_SERVICE') {
    return 'text-bg-info'
  }
  return 'text-bg-light border'
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
  <article class="admin-page admin-users-page">
    <div class="page-title">
      <span class="eyebrow">Admin</span>
      <h1>會員管理</h1>
    </div>

    <form class="admin-toolbar admin-users-toolbar" @submit.prevent="submitFilters">
      <div>
        <label class="form-label" for="adminUserKeyword">搜尋</label>
        <div class="input-group">
          <span class="input-group-text">
            <i class="bi bi-search" aria-hidden="true"></i>
          </span>
          <input
            id="adminUserKeyword"
            v-model="keyword"
            class="form-control"
            type="search"
            placeholder="Email、名稱或手機"
          />
        </div>
      </div>

      <div>
        <label class="form-label" for="adminUserStatus">狀態</label>
        <select id="adminUserStatus" v-model="statusFilter" class="form-select">
          <option v-for="option in statusOptions" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
      </div>

      <div>
        <label class="form-label" for="adminUserRole">角色</label>
        <select id="adminUserRole" v-model="roleFilter" class="form-select">
          <option v-for="option in roleOptions" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
      </div>

      <button class="btn btn-dark" type="submit" :disabled="loading">
        <i class="bi bi-funnel" aria-hidden="true"></i>
        篩選
      </button>

      <div class="admin-summary-pill">
        <strong>{{ users.length }}</strong>
        <span>目前清單</span>
      </div>
      <div class="admin-summary-pill">
        <strong>{{ activeCount }}</strong>
        <span>啟用</span>
      </div>
      <div class="admin-summary-pill">
        <strong>{{ suspendedCount }}</strong>
        <span>停權</span>
      </div>
      <div class="admin-summary-pill">
        <strong>{{ adminCount }}</strong>
        <span>後台角色</span>
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
          <span class="eyebrow">Users</span>
          <h2>會員列表</h2>
        </div>
      </div>

      <div v-if="loading" class="admin-user-list">
        <div v-for="index in 4" :key="index" class="admin-user-card">
          <div class="skeleton-line w-50"></div>
          <div class="skeleton-line"></div>
          <div class="skeleton-line w-75"></div>
        </div>
      </div>

      <div v-else-if="users.length === 0" class="empty-state">
        <i class="bi bi-people" aria-hidden="true"></i>
        <strong>沒有符合條件的會員</strong>
      </div>

      <div v-else class="admin-user-list">
        <article v-for="user in users" :key="user.id" class="admin-user-card">
          <header class="admin-user-card__head">
            <div class="admin-user-card__identity">
              <strong>#{{ user.id }} {{ user.displayName }}</strong>
              <span>{{ user.maskedEmail }}</span>
            </div>
            <div class="admin-user-card__badges">
              <span class="badge" :class="statusBadgeClass(user.status)">
                {{ user.statusLabel }}
              </span>
              <span class="badge" :class="roleBadgeClass(user.role)">
                {{ user.roleLabel }}
              </span>
            </div>
          </header>

          <div class="admin-user-card__meta">
            <span>
              <i class="bi bi-phone" aria-hidden="true"></i>
              {{ user.maskedPhone || '-' }}
            </span>
            <span>
              <i class="bi bi-person-badge" aria-hidden="true"></i>
              {{ user.vipLevel }}
            </span>
            <span>
              <i class="bi bi-calendar-plus" aria-hidden="true"></i>
              {{ formatTime(user.createdAt) }}
            </span>
            <span>
              <i class="bi bi-clock-history" aria-hidden="true"></i>
              {{ formatTime(user.lastLoginAt) }}
            </span>
          </div>

          <div class="admin-user-card__stats">
            <span>
              <strong>{{ user.cashPointBalance }}</strong>
              現金點
            </span>
            <span>
              <strong>{{ user.bonusPointBalance }}</strong>
              紅利點
            </span>
            <span>
              <strong>{{ user.drawOrderCount }}</strong>
              抽賞
            </span>
            <span>
              <strong>{{ user.prizeCount }}</strong>
              戰利品
            </span>
            <span>
              <strong>{{ user.shipmentCount }}</strong>
              出貨
            </span>
          </div>

          <div class="admin-user-card__actions">
            <RouterLink class="btn btn-dark btn-sm" :to="`/admin/users/${user.id}`">
              <i class="bi bi-person-vcard" aria-hidden="true"></i>
              查看明細
            </RouterLink>
            <label v-if="canChangeRole(user)" class="visually-hidden" :for="`adminUserRole-${user.id}`">
              調整角色
            </label>
            <select
              v-if="canChangeRole(user)"
              :id="`adminUserRole-${user.id}`"
              class="form-select form-select-sm admin-user-role-select"
              :value="user.role"
              :disabled="updatingUserId === user.id"
              @change="changeRole(user, $event.target.value)"
            >
              <option
                v-for="option in roleAssignmentOptions"
                :key="option.value"
                :value="option.value"
              >
                {{ option.label }}
              </option>
            </select>
            <button
              v-if="user.status === 'ACTIVE' && user.role !== 'SUPER_ADMIN'"
              class="btn btn-outline-danger btn-sm"
              type="button"
              :disabled="updatingUserId === user.id"
              @click="changeStatus(user, 'SUSPENDED')"
            >
              <i class="bi bi-pause-circle" aria-hidden="true"></i>
              停權
            </button>
            <button
              v-if="user.status === 'SUSPENDED' && user.role !== 'SUPER_ADMIN'"
              class="btn btn-outline-success btn-sm"
              type="button"
              :disabled="updatingUserId === user.id"
              @click="changeStatus(user, 'ACTIVE')"
            >
              <i class="bi bi-play-circle" aria-hidden="true"></i>
              恢復啟用
            </button>
            <span v-if="user.role === 'SUPER_ADMIN'" class="admin-user-card__lock">
              <i class="bi bi-shield-lock" aria-hidden="true"></i>
              保護帳號
            </span>
          </div>
        </article>
      </div>
    </section>
  </article>
</template>

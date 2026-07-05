<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { fetchAdminAuditLogs } from '@/services/adminAuditLogApi'
import { useSessionStore } from '@/stores/session'

const router = useRouter()
const session = useSessionStore()

const auditLogs = ref([])
const loading = ref(true)
const action = ref('')
const entityType = ref('')
const actorRoleFilter = ref('')
const keyword = ref('')
const limit = ref(100)
const errorMessage = ref('')

const actorRoleOptions = [
  { value: '', label: '全部角色' },
  { value: 'SYSTEM', label: '系統' },
  { value: 'SUPER_ADMIN', label: '超級管理員' },
  { value: 'ADMIN', label: '管理員' },
  { value: 'OPERATOR', label: '營運' },
  { value: 'CUSTOMER_SERVICE', label: '客服' },
  { value: 'USER', label: '會員' },
]

const limitOptions = [
  { value: 50, label: '50 筆' },
  { value: 100, label: '100 筆' },
  { value: 200, label: '200 筆' },
]

const systemCount = computed(
  () => auditLogs.value.filter((log) => log.actorRole === 'SYSTEM').length,
)
const adminCount = computed(
  () => auditLogs.value.filter((log) => ['SUPER_ADMIN', 'ADMIN'].includes(log.actorRole)).length,
)
const entityCount = computed(() => new Set(auditLogs.value.map((log) => log.entityType)).size)

watch([actorRoleFilter, limit], async () => {
  await loadAuditLogs()
})

onMounted(async () => {
  const user = await session.load()
  if (!user) {
    await router.replace({ path: '/admin/login', query: { redirect: '/admin/audit-logs' } })
    return
  }
  if (!isAdmin(user)) {
    await router.replace('/account')
    return
  }
  await loadAuditLogs()
})

async function loadAuditLogs() {
  loading.value = true
  errorMessage.value = ''
  try {
    auditLogs.value = await fetchAdminAuditLogs({
      action: action.value.trim(),
      entityType: entityType.value.trim(),
      actorRole: actorRoleFilter.value,
      q: keyword.value.trim(),
      limit: limit.value,
    })
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '無法載入審計紀錄。'
  } finally {
    loading.value = false
  }
}

async function submitFilters() {
  await loadAuditLogs()
}

function isAdmin(user) {
  return user.role === 'SUPER_ADMIN' || user.role === 'ADMIN'
}

function actorBadgeClass(role) {
  if (role === 'SYSTEM') {
    return 'text-bg-dark'
  }
  if (role === 'SUPER_ADMIN' || role === 'ADMIN') {
    return 'text-bg-danger'
  }
  if (role === 'OPERATOR' || role === 'CUSTOMER_SERVICE') {
    return 'text-bg-info'
  }
  return 'text-bg-light border'
}

function actorName(log) {
  if (log.actorRole === 'SYSTEM') {
    return '系統'
  }
  return log.actorDisplayName ? `${log.actorDisplayName} #${log.actorId}` : `#${log.actorId || '-'}`
}

function statePreview(value) {
  if (!value) {
    return '-'
  }
  return value.length > 260 ? `${value.slice(0, 260)}...` : value
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
  <article class="admin-page admin-audit-page">
    <div class="page-title">
      <span class="eyebrow">Admin</span>
      <h1>Audit Log</h1>
    </div>

    <form class="admin-toolbar admin-audit-toolbar" @submit.prevent="submitFilters">
      <div>
        <label class="form-label" for="adminAuditKeyword">搜尋</label>
        <div class="input-group">
          <span class="input-group-text">
            <i class="bi bi-search" aria-hidden="true"></i>
          </span>
          <input
            id="adminAuditKeyword"
            v-model="keyword"
            class="form-control"
            type="search"
            placeholder="動作、對象、狀態或操作者"
          />
        </div>
      </div>

      <div>
        <label class="form-label" for="adminAuditAction">Action</label>
        <input
          id="adminAuditAction"
          v-model="action"
          class="form-control"
          type="search"
          placeholder="ADMIN_USER_STATUS_UPDATED"
        />
      </div>

      <div>
        <label class="form-label" for="adminAuditEntity">Entity</label>
        <input
          id="adminAuditEntity"
          v-model="entityType"
          class="form-control"
          type="search"
          placeholder="User"
        />
      </div>

      <div>
        <label class="form-label" for="adminAuditActorRole">角色</label>
        <select id="adminAuditActorRole" v-model="actorRoleFilter" class="form-select">
          <option v-for="option in actorRoleOptions" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
      </div>

      <div>
        <label class="form-label" for="adminAuditLimit">筆數</label>
        <select id="adminAuditLimit" v-model.number="limit" class="form-select">
          <option v-for="option in limitOptions" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
      </div>

      <button class="btn btn-dark" type="submit" :disabled="loading">
        <i class="bi bi-funnel" aria-hidden="true"></i>
        篩選
      </button>

      <div class="admin-summary-pill">
        <strong>{{ auditLogs.length }}</strong>
        <span>目前清單</span>
      </div>
      <div class="admin-summary-pill">
        <strong>{{ systemCount }}</strong>
        <span>系統事件</span>
      </div>
      <div class="admin-summary-pill">
        <strong>{{ adminCount }}</strong>
        <span>管理員操作</span>
      </div>
      <div class="admin-summary-pill">
        <strong>{{ entityCount }}</strong>
        <span>對象類型</span>
      </div>
    </form>

    <div v-if="errorMessage" class="state-panel admin-state" role="alert">
      <i class="bi bi-exclamation-triangle" aria-hidden="true"></i>
      <span>{{ errorMessage }}</span>
    </div>

    <section class="status-panel">
      <div class="section-heading">
        <div>
          <span class="eyebrow">Audit</span>
          <h2>審計紀錄</h2>
        </div>
      </div>

      <div v-if="loading" class="admin-audit-list">
        <div v-for="index in 4" :key="index" class="admin-audit-card">
          <div class="skeleton-line w-50"></div>
          <div class="skeleton-line"></div>
          <div class="skeleton-line w-75"></div>
        </div>
      </div>

      <div v-else-if="auditLogs.length === 0" class="empty-state">
        <i class="bi bi-shield-check" aria-hidden="true"></i>
        <strong>沒有符合條件的審計紀錄</strong>
      </div>

      <div v-else class="admin-audit-list">
        <article v-for="log in auditLogs" :key="log.id" class="admin-audit-card">
          <header class="admin-audit-card__head">
            <div class="admin-audit-card__identity">
              <strong>#{{ log.id }} {{ log.actionLabel }}</strong>
              <span>{{ log.action }}</span>
            </div>
            <span class="badge" :class="actorBadgeClass(log.actorRole)">
              {{ log.actorRoleLabel }}
            </span>
          </header>

          <div class="admin-audit-card__meta">
            <span>
              <i class="bi bi-person-badge" aria-hidden="true"></i>
              {{ actorName(log) }}
            </span>
            <span v-if="log.maskedActorEmail">
              <i class="bi bi-envelope" aria-hidden="true"></i>
              {{ log.maskedActorEmail }}
            </span>
            <span>
              <i class="bi bi-box" aria-hidden="true"></i>
              {{ log.entityTypeLabel }} #{{ log.entityId || '-' }}
            </span>
            <span>
              <i class="bi bi-clock-history" aria-hidden="true"></i>
              {{ formatTime(log.createdAt) }}
            </span>
            <span v-if="log.ipAddress">
              <i class="bi bi-router" aria-hidden="true"></i>
              {{ log.ipAddress }}
            </span>
          </div>

          <div class="admin-audit-card__states">
            <div>
              <span>Before</span>
              <code>{{ statePreview(log.beforeState) }}</code>
            </div>
            <div>
              <span>After</span>
              <code>{{ statePreview(log.afterState) }}</code>
            </div>
          </div>
        </article>
      </div>
    </section>
  </article>
</template>

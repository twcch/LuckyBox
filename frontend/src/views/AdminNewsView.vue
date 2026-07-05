<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { createAdminNews, fetchAdminNews, updateAdminNews } from '@/services/adminNewsApi'
import { useSessionStore } from '@/stores/session'

const router = useRouter()
const session = useSessionStore()

const news = ref([])
const loading = ref(true)
const saving = ref(false)
const selectedNewsId = ref(null)
const statusFilter = ref('')
const keyword = ref('')
const errorMessage = ref('')
const successMessage = ref('')

const statusOptions = [
  { value: '', label: '全部狀態' },
  { value: 'DRAFT', label: '草稿' },
  { value: 'PUBLISHED', label: '已發布' },
  { value: 'ARCHIVED', label: '已封存' },
]

const form = reactive({
  title: '',
  slug: '',
  content: '',
  status: 'DRAFT',
  publishedAt: '',
  unpublishAt: '',
})

const selectedNews = computed(
  () => news.value.find((item) => item.id === selectedNewsId.value) || null,
)
const publishedCount = computed(
  () => news.value.filter((item) => item.status === 'PUBLISHED').length,
)
const draftCount = computed(() => news.value.filter((item) => item.status === 'DRAFT').length)
const archivedCount = computed(() => news.value.filter((item) => item.status === 'ARCHIVED').length)

watch(statusFilter, async () => {
  await loadNews()
})

onMounted(async () => {
  const user = await session.load()
  if (!user) {
    await router.replace({ path: '/admin/login', query: { redirect: '/admin/news' } })
    return
  }
  if (!isAdmin(user)) {
    await router.replace('/account')
    return
  }
  await loadNews()
})

async function loadNews() {
  loading.value = true
  errorMessage.value = ''
  try {
    news.value = await fetchAdminNews({
      status: statusFilter.value,
      q: keyword.value.trim(),
    })
    if (selectedNewsId.value && !news.value.some((item) => item.id === selectedNewsId.value)) {
      resetForm()
    }
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '無法載入公告。'
  } finally {
    loading.value = false
  }
}

async function submitFilters() {
  await loadNews()
}

function selectNews(item) {
  selectedNewsId.value = item.id
  form.title = item.title
  form.slug = item.slug
  form.content = item.content
  form.status = item.status
  form.publishedAt = item.publishedAt || ''
  form.unpublishAt = item.unpublishAt || ''
  successMessage.value = ''
  errorMessage.value = ''
}

function resetForm() {
  selectedNewsId.value = null
  form.title = ''
  form.slug = ''
  form.content = ''
  form.status = 'DRAFT'
  form.publishedAt = ''
  form.unpublishAt = ''
  successMessage.value = ''
  errorMessage.value = ''
}

async function saveNews() {
  saving.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const payload = {
      title: form.title,
      slug: form.slug,
      content: form.content,
      status: form.status,
      publishedAt: form.publishedAt,
      unpublishAt: form.unpublishAt,
    }
    const saved = selectedNewsId.value
      ? await updateAdminNews(selectedNewsId.value, payload)
      : await createAdminNews(payload)
    successMessage.value = `公告「${saved.title}」已儲存。`
    await loadNews()
    selectNews(saved)
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '公告儲存失敗。'
  } finally {
    saving.value = false
  }
}

function isAdmin(user) {
  return user.role === 'SUPER_ADMIN' || user.role === 'ADMIN'
}

function statusBadgeClass(status) {
  if (status === 'PUBLISHED') {
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
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}
</script>

<template>
  <article class="admin-page admin-news-page">
    <div class="page-title">
      <span class="eyebrow">Admin</span>
      <h1>公告管理</h1>
    </div>

    <form class="admin-toolbar admin-news-toolbar" @submit.prevent="submitFilters">
      <div>
        <label class="form-label" for="adminNewsKeyword">搜尋</label>
        <div class="input-group">
          <span class="input-group-text">
            <i class="bi bi-search" aria-hidden="true"></i>
          </span>
          <input
            id="adminNewsKeyword"
            v-model="keyword"
            class="form-control"
            type="search"
            placeholder="標題、代碼或內容"
          />
        </div>
      </div>

      <div>
        <label class="form-label" for="adminNewsStatus">狀態</label>
        <select id="adminNewsStatus" v-model="statusFilter" class="form-select">
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
        <strong>{{ news.length }}</strong>
        <span>目前清單</span>
      </div>
      <div class="admin-summary-pill">
        <strong>{{ publishedCount }}</strong>
        <span>已發布</span>
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

    <section class="admin-news-layout">
      <div class="status-panel">
        <div class="section-heading">
          <div>
            <span class="eyebrow">News</span>
            <h2>公告列表</h2>
          </div>
          <button class="btn btn-outline-dark btn-sm" type="button" @click="resetForm">
            <i class="bi bi-plus-circle" aria-hidden="true"></i>
            新增公告
          </button>
        </div>

        <div v-if="loading" class="admin-news-list">
          <div v-for="index in 4" :key="index" class="admin-news-card">
            <div class="skeleton-line w-50"></div>
            <div class="skeleton-line"></div>
            <div class="skeleton-line w-75"></div>
          </div>
        </div>

        <div v-else-if="news.length === 0" class="empty-state">
          <i class="bi bi-megaphone" aria-hidden="true"></i>
          <strong>沒有符合條件的公告</strong>
        </div>

        <div v-else class="admin-news-list">
          <button
            v-for="item in news"
            :key="item.id"
            class="admin-news-card"
            :class="{ 'admin-news-card--active': selectedNewsId === item.id }"
            type="button"
            @click="selectNews(item)"
          >
            <span class="admin-news-card__head">
              <strong>#{{ item.id }} {{ item.title }}</strong>
              <span class="badge" :class="statusBadgeClass(item.status)">
                {{ item.statusLabel }}
              </span>
            </span>
            <span>{{ item.slug }}</span>
            <small>發布：{{ formatTime(item.publishedAt) }}</small>
          </button>
        </div>
      </div>

      <form class="status-panel admin-news-form" @submit.prevent="saveNews">
        <div class="section-heading">
          <div>
            <span class="eyebrow">Editor</span>
            <h2>{{ selectedNews ? '編輯公告' : '新增公告' }}</h2>
          </div>
        </div>

        <div class="admin-news-form-grid">
          <div>
            <label class="form-label" for="newsTitle">標題</label>
            <input id="newsTitle" v-model="form.title" class="form-control" required />
          </div>
          <div>
            <label class="form-label" for="newsSlug">Slug</label>
            <input
              id="newsSlug"
              v-model="form.slug"
              class="form-control"
              placeholder="shipping-update"
              required
            />
          </div>
          <div>
            <label class="form-label" for="newsStatus">狀態</label>
            <select id="newsStatus" v-model="form.status" class="form-select">
              <option value="DRAFT">草稿</option>
              <option value="PUBLISHED">已發布</option>
              <option value="ARCHIVED">已封存</option>
            </select>
          </div>
          <div>
            <label class="form-label" for="newsPublishedAt">發布時間（可排程上架）</label>
            <input
              id="newsPublishedAt"
              v-model="form.publishedAt"
              class="form-control"
              placeholder="2026-06-18T10:00:00Z，留空自動填入"
            />
          </div>
          <div>
            <label class="form-label" for="newsUnpublishAt">下架時間（選填）</label>
            <input
              id="newsUnpublishAt"
              v-model="form.unpublishAt"
              class="form-control"
              placeholder="2026-07-01T00:00:00Z，留空不自動下架"
            />
          </div>
          <div class="admin-news-form-grid__wide">
            <label class="form-label" for="newsContent">內容</label>
            <textarea
              id="newsContent"
              v-model="form.content"
              class="form-control"
              rows="9"
              required
            ></textarea>
          </div>
        </div>

        <div class="admin-prize-actions">
          <button class="btn btn-danger" type="submit" :disabled="saving">
            <i class="bi bi-save" aria-hidden="true"></i>
            {{ saving ? '儲存中' : '儲存公告' }}
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

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import AdminImageUploadField from '@/components/AdminImageUploadField.vue'
import { createAdminBanner, fetchAdminBanners, updateAdminBanner } from '@/services/adminBannerApi'
import { useSessionStore } from '@/stores/session'

const router = useRouter()
const session = useSessionStore()

const banners = ref([])
const loading = ref(true)
const saving = ref(false)
const selectedBannerId = ref(null)
const statusFilter = ref('')
const positionFilter = ref('')
const keyword = ref('')
const errorMessage = ref('')
const successMessage = ref('')

const statusOptions = [
  { value: '', label: '全部狀態' },
  { value: 'DRAFT', label: '草稿' },
  { value: 'ACTIVE', label: '啟用' },
  { value: 'ARCHIVED', label: '已封存' },
]

const positionOptions = [
  { value: '', label: '全部位置' },
  { value: 'HOME_HERO', label: '首頁主視覺' },
  { value: 'HOME_SECTION', label: '首頁區塊' },
]

const form = reactive({
  title: '',
  imageUrl: '',
  href: '',
  position: 'HOME_HERO',
  status: 'DRAFT',
  publishAt: '',
  unpublishAt: '',
})

function toIso(localValue) {
  if (!localValue) {
    return null
  }
  const date = new Date(localValue)
  return Number.isNaN(date.getTime()) ? null : date.toISOString()
}

function toLocalInput(iso) {
  if (!iso) {
    return ''
  }
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) {
    return ''
  }
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

const selectedBanner = computed(
  () => banners.value.find((item) => item.id === selectedBannerId.value) || null,
)
const activeCount = computed(() => banners.value.filter((item) => item.status === 'ACTIVE').length)
const draftCount = computed(() => banners.value.filter((item) => item.status === 'DRAFT').length)
const archivedCount = computed(
  () => banners.value.filter((item) => item.status === 'ARCHIVED').length,
)

watch([statusFilter, positionFilter], async () => {
  await loadBanners()
})

onMounted(async () => {
  const user = await session.load()
  if (!user) {
    await router.replace({ path: '/admin/login', query: { redirect: '/admin/banners' } })
    return
  }
  if (!isAdmin(user)) {
    await router.replace('/account')
    return
  }
  await loadBanners()
})

async function loadBanners() {
  loading.value = true
  errorMessage.value = ''
  try {
    banners.value = await fetchAdminBanners({
      status: statusFilter.value,
      position: positionFilter.value,
      q: keyword.value.trim(),
    })
    if (
      selectedBannerId.value &&
      !banners.value.some((item) => item.id === selectedBannerId.value)
    ) {
      resetForm()
    }
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '無法載入 Banner。'
  } finally {
    loading.value = false
  }
}

async function submitFilters() {
  await loadBanners()
}

function selectBanner(item) {
  selectedBannerId.value = item.id
  form.title = item.title
  form.imageUrl = item.imageUrl
  form.href = item.href || ''
  form.position = item.position
  form.status = item.status
  form.publishAt = toLocalInput(item.publishAt)
  form.unpublishAt = toLocalInput(item.unpublishAt)
  successMessage.value = ''
  errorMessage.value = ''
}

function resetForm() {
  selectedBannerId.value = null
  form.title = ''
  form.imageUrl = ''
  form.href = ''
  form.position = 'HOME_HERO'
  form.status = 'DRAFT'
  form.publishAt = ''
  form.unpublishAt = ''
  successMessage.value = ''
  errorMessage.value = ''
}

async function saveBanner() {
  saving.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const payload = {
      title: form.title,
      imageUrl: form.imageUrl,
      href: form.href,
      position: form.position,
      status: form.status,
      publishAt: toIso(form.publishAt),
      unpublishAt: toIso(form.unpublishAt),
    }
    const saved = selectedBannerId.value
      ? await updateAdminBanner(selectedBannerId.value, payload)
      : await createAdminBanner(payload)
    successMessage.value = `Banner「${saved.title}」已儲存。`
    await loadBanners()
    selectBanner(saved)
  } catch (error) {
    errorMessage.value = error.response?.data?.message || 'Banner 儲存失敗。'
  } finally {
    saving.value = false
  }
}

function handleImageUploaded(uploaded) {
  errorMessage.value = ''
  successMessage.value = `圖片已上傳：${uploaded.filename}`
}

function handleImageUploadError(message) {
  errorMessage.value = message
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

function usePreviewFallback(event) {
  event.currentTarget.classList.add('image-fallback')
  event.currentTarget.removeAttribute('src')
}
</script>

<template>
  <article class="admin-page admin-banner-page">
    <div class="page-title">
      <span class="eyebrow">Admin</span>
      <h1>Banner 管理</h1>
    </div>

    <form class="admin-toolbar admin-banner-toolbar" @submit.prevent="submitFilters">
      <div>
        <label class="form-label" for="adminBannerKeyword">搜尋</label>
        <div class="input-group">
          <span class="input-group-text">
            <i class="bi bi-search" aria-hidden="true"></i>
          </span>
          <input
            id="adminBannerKeyword"
            v-model="keyword"
            class="form-control"
            type="search"
            placeholder="標題、圖片網址或連結"
          />
        </div>
      </div>

      <div>
        <label class="form-label" for="adminBannerPosition">位置</label>
        <select id="adminBannerPosition" v-model="positionFilter" class="form-select">
          <option v-for="option in positionOptions" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
      </div>

      <div>
        <label class="form-label" for="adminBannerStatus">狀態</label>
        <select id="adminBannerStatus" v-model="statusFilter" class="form-select">
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
        <strong>{{ banners.length }}</strong>
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

    <section class="admin-banner-layout">
      <div class="status-panel">
        <div class="section-heading">
          <div>
            <span class="eyebrow">Banners</span>
            <h2>Banner 列表</h2>
          </div>
          <button class="btn btn-outline-dark btn-sm" type="button" @click="resetForm">
            <i class="bi bi-plus-circle" aria-hidden="true"></i>
            新增 Banner
          </button>
        </div>

        <div v-if="loading" class="admin-banner-list">
          <div v-for="index in 4" :key="index" class="admin-banner-card">
            <div class="skeleton-line w-50"></div>
            <div class="skeleton-line"></div>
            <div class="skeleton-line w-75"></div>
          </div>
        </div>

        <div v-else-if="banners.length === 0" class="empty-state">
          <i class="bi bi-image" aria-hidden="true"></i>
          <strong>沒有符合條件的 Banner</strong>
        </div>

        <div v-else class="admin-banner-list">
          <button
            v-for="item in banners"
            :key="item.id"
            class="admin-banner-card"
            :class="{ 'admin-banner-card--active': selectedBannerId === item.id }"
            type="button"
            @click="selectBanner(item)"
          >
            <span class="admin-banner-card__head">
              <strong>#{{ item.id }} {{ item.title }}</strong>
              <span class="badge" :class="statusBadgeClass(item.status)">
                {{ item.statusLabel }}
              </span>
            </span>
            <span>{{ item.positionLabel }}</span>
            <small>{{ item.href || '未設定連結' }}</small>
          </button>
        </div>
      </div>

      <form class="status-panel admin-banner-form" @submit.prevent="saveBanner">
        <div class="section-heading">
          <div>
            <span class="eyebrow">Editor</span>
            <h2>{{ selectedBanner ? '編輯 Banner' : '新增 Banner' }}</h2>
          </div>
        </div>

        <div class="admin-banner-form-grid">
          <div class="admin-banner-form-grid__wide">
            <label class="form-label" for="bannerTitle">標題</label>
            <input id="bannerTitle" v-model="form.title" class="form-control" required />
          </div>
          <div class="admin-banner-form-grid__wide">
            <AdminImageUploadField
              v-model="form.imageUrl"
              input-id="bannerImageUrl"
              label="圖片網址"
              placeholder="https://images.example.com/banner.png"
              required
              @uploaded="handleImageUploaded"
              @upload-error="handleImageUploadError"
            />
          </div>
          <div class="admin-banner-form-grid__wide">
            <label class="form-label" for="bannerHref">點擊連結</label>
            <input
              id="bannerHref"
              v-model="form.href"
              class="form-control"
              placeholder="/news 或 #campaigns"
            />
          </div>
          <div>
            <label class="form-label" for="bannerPosition">位置</label>
            <select id="bannerPosition" v-model="form.position" class="form-select">
              <option value="HOME_HERO">首頁主視覺</option>
              <option value="HOME_SECTION">首頁區塊</option>
            </select>
          </div>
          <div>
            <label class="form-label" for="bannerStatus">狀態</label>
            <select id="bannerStatus" v-model="form.status" class="form-select">
              <option value="DRAFT">草稿</option>
              <option value="ACTIVE">啟用</option>
              <option value="ARCHIVED">已封存</option>
            </select>
          </div>
          <div>
            <label class="form-label" for="bannerPublishAt">上架時間（選填）</label>
            <input
              id="bannerPublishAt"
              v-model="form.publishAt"
              class="form-control"
              type="datetime-local"
            />
          </div>
          <div>
            <label class="form-label" for="bannerUnpublishAt">下架時間（選填）</label>
            <input
              id="bannerUnpublishAt"
              v-model="form.unpublishAt"
              class="form-control"
              type="datetime-local"
            />
          </div>
        </div>

        <div v-if="form.imageUrl" class="admin-banner-preview">
          <img
            :src="form.imageUrl"
            :alt="form.title || 'Banner 預覽'"
            @error="usePreviewFallback"
          />
        </div>

        <div class="admin-prize-actions">
          <button class="btn btn-danger" type="submit" :disabled="saving">
            <i class="bi bi-save" aria-hidden="true"></i>
            {{ saving ? '儲存中' : '儲存 Banner' }}
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

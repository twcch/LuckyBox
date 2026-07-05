<script setup>
import { computed, onMounted, ref } from 'vue'
import { fetchHealth } from '@/services/healthApi'

const loading = ref(true)
const health = ref(null)
const errorMessage = ref('')
const checkedAt = ref('')

const apiStatusLabel = computed(() => {
  if (loading.value) {
    return '檢查中'
  }
  return health.value?.status === 'UP' ? 'API 正常' : 'API 未連線'
})

const apiStatusClass = computed(() => ({
  'status-health-badge': true,
  'status-health-badge--up': health.value?.status === 'UP',
  'status-health-badge--warning': !loading.value && health.value?.status !== 'UP',
}))

function formatDateTime(value) {
  if (!value) {
    return '尚未確認'
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }

  return new Intl.DateTimeFormat('zh-TW', {
    dateStyle: 'medium',
    timeStyle: 'medium',
  }).format(date)
}

async function loadHealth() {
  loading.value = true
  errorMessage.value = ''
  checkedAt.value = new Date().toISOString()

  try {
    health.value = await fetchHealth()
  } catch {
    health.value = null
    errorMessage.value = '目前無法連線到後端 API。請確認 Spring Boot 服務或 Vite proxy 是否啟動。'
  } finally {
    loading.value = false
  }
}

onMounted(loadHealth)
</script>

<template>
  <main class="container content-section status-page">
    <div class="page-title">
      <span class="eyebrow">System</span>
      <h1>系統狀態</h1>
      <p>檢查 LuckyBox 前端、後端 API 與營運交付文件是否可用。</p>
    </div>

    <section class="status-panel status-health-panel">
      <div class="status-health-panel__header">
        <div>
          <span class="eyebrow">Runtime</span>
          <h2>服務健康檢查</h2>
        </div>
        <div class="status-health-panel__actions">
          <span :class="apiStatusClass">{{ apiStatusLabel }}</span>
          <button class="btn btn-outline-dark btn-sm" type="button" :disabled="loading" @click="loadHealth">
            <i class="bi bi-arrow-clockwise" aria-hidden="true"></i>
            重新檢查
          </button>
        </div>
      </div>

      <div v-if="errorMessage" class="state-panel status-health-panel__error" role="alert">
        <i class="bi bi-exclamation-triangle" aria-hidden="true"></i>
        <span>{{ errorMessage }}</span>
      </div>

      <div class="status-grid">
        <div>
          <strong>Frontend</strong>
          <span>Vue app 已載入</span>
        </div>
        <div>
          <strong>Backend API</strong>
          <span>{{ loading ? '檢查中' : health?.service || '未連線' }}</span>
        </div>
        <div>
          <strong>Last check</strong>
          <span>{{ formatDateTime(health?.timestamp || checkedAt) }}</span>
        </div>
      </div>
    </section>

    <section class="status-panel status-delivery-panel">
      <span class="eyebrow">Operations</span>
      <h2>交付狀態</h2>
      <div class="status-grid">
        <div>
          <strong>API 文件</strong>
          <span>公開、會員與後台端點已整理於 docs/api.md</span>
        </div>
        <div>
          <strong>測試覆蓋</strong>
          <span>後端、前端與 E2E 驗證腳本已保留於 docs/testing.md</span>
        </div>
        <div>
          <strong>上線閘門</strong>
          <span>外部簽核與正式環境檢查已收斂於 launch readiness 文件</span>
        </div>
      </div>
    </section>
  </main>
</template>

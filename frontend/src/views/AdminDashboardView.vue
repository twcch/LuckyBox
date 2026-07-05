<script setup>
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { fetchAdminDashboard } from '@/services/adminDashboardApi'

const dashboard = ref(createEmptyDashboard())
const loading = ref(true)
const errorMessage = ref('')

onMounted(async () => {
  await loadDashboard()
})

async function loadDashboard() {
  loading.value = true
  errorMessage.value = ''
  try {
    dashboard.value = {
      ...createEmptyDashboard(),
      ...(await fetchAdminDashboard()),
    }
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '無法載入後台 Dashboard。'
  } finally {
    loading.value = false
  }
}

function createEmptyDashboard() {
  return {
    metrics: [],
    productMetrics: [],
    requestedShipments: [],
    recentActivities: [],
  }
}

function metricClass(tone) {
  return {
    'admin-dashboard-metric--danger': tone === 'danger',
    'admin-dashboard-metric--warning': tone === 'warning',
    'admin-dashboard-metric--teal': tone === 'teal',
  }
}

function formatTime(value) {
  if (!value) {
    return '-'
  }
  return new Intl.DateTimeFormat('zh-TW', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}
</script>

<template>
  <article class="admin-page admin-dashboard-page">
    <div class="page-title">
      <span class="eyebrow">Overview</span>
      <h1>Dashboard</h1>
      <p>今日營運指標、待處理出貨與最近後台操作。</p>
    </div>

    <div v-if="errorMessage" class="state-panel admin-state" role="alert">
      <i class="bi bi-exclamation-triangle" aria-hidden="true"></i>
      <span>{{ errorMessage }}</span>
    </div>

    <section v-if="loading" class="admin-dashboard-grid">
      <div v-for="index in 7" :key="index" class="admin-dashboard-metric">
        <div class="skeleton-line w-50"></div>
        <div class="skeleton-line"></div>
      </div>
    </section>

    <section v-else class="admin-dashboard-grid" aria-label="後台營運指標">
      <article
        v-for="metric in dashboard.metrics"
        :key="metric.key"
        class="admin-dashboard-metric"
        :class="metricClass(metric.tone)"
      >
        <span>{{ metric.label }}</span>
        <strong>{{ metric.value }}</strong>
        <small>{{ metric.helper }}</small>
      </article>
    </section>

    <section class="admin-dashboard-section" aria-label="產品指標">
      <div class="section-heading">
        <div>
          <span class="eyebrow">Product Metrics</span>
          <h2>產品指標</h2>
        </div>
      </div>

      <div v-if="loading" class="admin-dashboard-grid admin-dashboard-grid--product">
        <div
          v-for="index in 8"
          :key="index"
          class="admin-dashboard-metric admin-dashboard-metric--compact"
        >
          <div class="skeleton-line w-50"></div>
          <div class="skeleton-line"></div>
        </div>
      </div>

      <div v-else class="admin-dashboard-grid admin-dashboard-grid--product">
        <article
          v-for="metric in dashboard.productMetrics"
          :key="metric.key"
          class="admin-dashboard-metric admin-dashboard-metric--compact"
          :class="metricClass(metric.tone)"
        >
          <span>{{ metric.label }}</span>
          <strong>{{ metric.value }}</strong>
          <small>{{ metric.helper }}</small>
        </article>
      </div>
    </section>

    <div class="admin-dashboard-panels">
      <section class="status-panel">
        <div class="section-heading">
          <div>
            <span class="eyebrow">Queue</span>
            <h2>待處理出貨</h2>
          </div>
          <RouterLink class="btn btn-outline-dark btn-sm" to="/admin/shipments">
            <i class="bi bi-arrow-right me-1" aria-hidden="true"></i>
            處理
          </RouterLink>
        </div>

        <div v-if="loading" class="admin-dashboard-list">
          <div v-for="index in 3" :key="index" class="admin-dashboard-row">
            <div class="skeleton-line w-50"></div>
            <div class="skeleton-line"></div>
          </div>
        </div>

        <div v-else-if="dashboard.requestedShipments.length === 0" class="empty-state">
          <i class="bi bi-truck" aria-hidden="true"></i>
          <strong>沒有待處理出貨</strong>
          <span>會員建立出貨申請後會出現在這裡。</span>
        </div>

        <div v-else class="admin-dashboard-list">
          <article
            v-for="shipment in dashboard.requestedShipments"
            :key="shipment.id"
            class="admin-dashboard-row"
          >
            <div>
              <strong>#{{ shipment.id }} {{ shipment.userDisplayName }}</strong>
              <span>{{ shipment.userEmail }}</span>
            </div>
            <div>
              <strong>{{ shipment.itemCount }} 件</strong>
              <time>{{ formatTime(shipment.requestedAt) }}</time>
            </div>
          </article>
        </div>
      </section>

      <section class="status-panel">
        <div class="section-heading">
          <div>
            <span class="eyebrow">Audit</span>
            <h2>最近操作</h2>
          </div>
        </div>

        <div v-if="loading" class="admin-dashboard-list">
          <div v-for="index in 4" :key="index" class="admin-dashboard-row">
            <div class="skeleton-line"></div>
            <div class="skeleton-line w-50"></div>
          </div>
        </div>

        <div v-else-if="dashboard.recentActivities.length === 0" class="empty-state">
          <i class="bi bi-clipboard-data" aria-hidden="true"></i>
          <strong>尚無操作紀錄</strong>
          <span>系統或後台操作會列在這裡。</span>
        </div>

        <div v-else class="admin-dashboard-list">
          <article
            v-for="activity in dashboard.recentActivities"
            :key="activity.id"
            class="admin-dashboard-row"
          >
            <div>
              <strong>{{ activity.action }}</strong>
              <span>{{ activity.entityType }} #{{ activity.entityId || '-' }}</span>
            </div>
            <div>
              <strong>{{ activity.actorRole }}</strong>
              <time>{{ formatTime(activity.createdAt) }}</time>
            </div>
          </article>
        </div>
      </section>
    </div>
  </article>
</template>

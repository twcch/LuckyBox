<script setup>
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { fetchAdminSettings } from '@/services/adminSettingsApi'

const settings = ref({ sections: [] })
const loading = ref(true)
const errorMessage = ref('')

const itemCount = computed(() =>
  settings.value.sections.reduce((sum, section) => sum + section.items.length, 0),
)

onMounted(async () => {
  await loadSettings()
})

async function loadSettings() {
  loading.value = true
  errorMessage.value = ''
  try {
    settings.value = { sections: [], ...(await fetchAdminSettings()) }
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '無法載入系統設定。'
  } finally {
    loading.value = false
  }
}

function toneClass(tone) {
  return {
    'admin-setting-item--teal': tone === 'teal',
    'admin-setting-item--warning': tone === 'warning',
    'admin-setting-item--danger': tone === 'danger',
  }
}
</script>

<template>
  <article class="admin-page admin-settings-page">
    <div class="page-title">
      <span class="eyebrow">Admin</span>
      <h1>系統設定</h1>
      <p>檢視目前 runtime、金流、SMTP、安全與促銷設定摘要。</p>
    </div>

    <div class="admin-toolbar admin-settings-toolbar">
      <button class="btn btn-dark" type="button" :disabled="loading" @click="loadSettings">
        <i class="bi bi-arrow-clockwise" aria-hidden="true"></i>
        重新整理
      </button>
      <RouterLink class="btn btn-outline-dark" to="/admin/security">
        <i class="bi bi-shield-lock" aria-hidden="true"></i>
        2FA 安全設定
      </RouterLink>
      <div class="admin-summary-pill">
        <strong>{{ settings.sections.length }}</strong>
        <span>設定區塊</span>
      </div>
      <div class="admin-summary-pill">
        <strong>{{ itemCount }}</strong>
        <span>設定項目</span>
      </div>
    </div>

    <div v-if="errorMessage" class="state-panel admin-state" role="alert">
      <i class="bi bi-exclamation-triangle" aria-hidden="true"></i>
      <span>{{ errorMessage }}</span>
    </div>

    <section v-if="loading" class="admin-settings-grid">
      <div v-for="index in 4" :key="index" class="status-panel">
        <div class="skeleton-line w-50"></div>
        <div class="skeleton-line"></div>
        <div class="skeleton-line"></div>
      </div>
    </section>

    <section v-else class="admin-settings-grid">
      <article v-for="section in settings.sections" :key="section.key" class="status-panel">
        <div class="section-heading">
          <div>
            <span class="eyebrow">{{ section.key }}</span>
            <h2>{{ section.label }}</h2>
          </div>
        </div>
        <p class="admin-settings-helper">{{ section.helper }}</p>

        <div class="admin-setting-list">
          <div
            v-for="item in section.items"
            :key="item.key"
            class="admin-setting-item"
            :class="toneClass(item.tone)"
          >
            <div>
              <strong>{{ item.label }}</strong>
              <span>{{ item.helper }}</span>
            </div>
            <code>{{ item.value }}</code>
          </div>
        </div>
      </article>
    </section>
  </article>
</template>

<style scoped>
.admin-settings-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1rem;
}

.admin-settings-helper {
  margin: 0 0 0.75rem;
  color: var(--lb-muted, #6c757d);
}

.admin-setting-list {
  display: grid;
  gap: 0.55rem;
}

.admin-setting-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 0.75rem;
  align-items: center;
  border: 1px solid var(--bs-border-color, #e3e3e8);
  border-radius: 0.45rem;
  padding: 0.65rem 0.75rem;
  background: var(--bs-body-bg, #fff);
}

.admin-setting-item strong,
.admin-setting-item span {
  display: block;
}

.admin-setting-item span {
  color: var(--lb-muted, #6c757d);
  font-size: 0.8rem;
}

.admin-setting-item code {
  max-width: 18rem;
  overflow-wrap: anywhere;
  border-radius: 999px;
  padding: 0.28rem 0.6rem;
  background: var(--lb-surface-cool, #f6f8fb);
  color: var(--lb-ink, #1f2933);
}

.admin-setting-item--teal code {
  background: #e6f7f4;
  color: #0f766e;
}

.admin-setting-item--warning code {
  background: #fff7df;
  color: #946200;
}

.admin-setting-item--danger code {
  background: #fdecea;
  color: #b42318;
}

@media (max-width: 900px) {
  .admin-settings-grid {
    grid-template-columns: 1fr;
  }

  .admin-setting-item {
    grid-template-columns: 1fr;
  }

  .admin-setting-item code {
    max-width: 100%;
    width: max-content;
  }
}
</style>

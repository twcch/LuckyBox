<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { fetchAdminPrizes } from '@/services/adminPrizeLibraryApi'

const prizes = ref([])
const loading = ref(true)
const errorMessage = ref('')
const campaignStatus = ref('')
const rank = ref('')
const lastPrize = ref('')
const keyword = ref('')

const statusOptions = [
  { value: '', label: '全部賞池' },
  { value: 'DRAFT', label: '草稿' },
  { value: 'SCHEDULED', label: '即將開抽' },
  { value: 'LIVE', label: '開抽中' },
  { value: 'PAUSED', label: '暫停中' },
  { value: 'SOLD_OUT', label: '已完抽' },
  { value: 'ENDED', label: '已結束' },
]

const lastPrizeOptions = [
  { value: '', label: '全部獎項' },
  { value: 'false', label: '一般獎項' },
  { value: 'true', label: '最後賞' },
]

const totalGeneratedTickets = computed(() =>
  prizes.value.reduce((sum, prize) => sum + Number(prize.generatedTickets || 0), 0),
)
const totalAvailableTickets = computed(() =>
  prizes.value.reduce((sum, prize) => sum + Number(prize.availableTickets || 0), 0),
)
const lastPrizeCount = computed(() => prizes.value.filter((prize) => prize.lastPrize).length)

watch([campaignStatus, lastPrize], async () => {
  await loadPrizes()
})

onMounted(async () => {
  await loadPrizes()
})

async function loadPrizes() {
  loading.value = true
  errorMessage.value = ''
  try {
    prizes.value = await fetchAdminPrizes({
      campaignStatus: campaignStatus.value,
      rank: rank.value.trim(),
      lastPrize: lastPrize.value,
      q: keyword.value.trim(),
      limit: 300,
    })
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '無法載入獎品清單。'
  } finally {
    loading.value = false
  }
}

async function submitFilters() {
  await loadPrizes()
}

function statusBadgeClass(status) {
  if (status === 'LIVE') {
    return 'text-bg-danger'
  }
  if (status === 'DRAFT') {
    return 'text-bg-light border'
  }
  if (status === 'SCHEDULED') {
    return 'text-bg-info'
  }
  if (status === 'PAUSED') {
    return 'text-bg-warning'
  }
  return 'text-bg-dark'
}

function formatNumber(value) {
  return new Intl.NumberFormat('zh-TW').format(value || 0)
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
  <article class="admin-page admin-prizes-page">
    <div class="page-title">
      <span class="eyebrow">Admin</span>
      <h1>獎品管理</h1>
      <p>跨賞池檢視獎項數量、剩餘票券與最後賞配置。</p>
    </div>

    <form class="admin-toolbar admin-prizes-toolbar" @submit.prevent="submitFilters">
      <div>
        <label class="form-label" for="adminPrizeKeyword">搜尋</label>
        <div class="input-group">
          <span class="input-group-text">
            <i class="bi bi-search" aria-hidden="true"></i>
          </span>
          <input
            id="adminPrizeKeyword"
            v-model="keyword"
            class="form-control"
            type="search"
            placeholder="獎品、等級或賞池"
          />
        </div>
      </div>

      <div>
        <label class="form-label" for="adminPrizeStatus">賞池狀態</label>
        <select id="adminPrizeStatus" v-model="campaignStatus" class="form-select">
          <option v-for="option in statusOptions" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
      </div>

      <div>
        <label class="form-label" for="adminPrizeRank">等級</label>
        <input
          id="adminPrizeRank"
          v-model="rank"
          class="form-control"
          type="search"
          placeholder="A / S / LAST"
        />
      </div>

      <div>
        <label class="form-label" for="adminPrizeLastPrize">類型</label>
        <select id="adminPrizeLastPrize" v-model="lastPrize" class="form-select">
          <option v-for="option in lastPrizeOptions" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
      </div>

      <button class="btn btn-dark" type="submit" :disabled="loading">
        <i class="bi bi-funnel" aria-hidden="true"></i>
        篩選
      </button>

      <div class="admin-summary-pill">
        <strong>{{ prizes.length }}</strong>
        <span>目前清單</span>
      </div>
      <div class="admin-summary-pill">
        <strong>{{ formatNumber(totalGeneratedTickets) }}</strong>
        <span>已生成 Ticket</span>
      </div>
      <div class="admin-summary-pill">
        <strong>{{ formatNumber(totalAvailableTickets) }}</strong>
        <span>可抽 Ticket</span>
      </div>
      <div class="admin-summary-pill">
        <strong>{{ lastPrizeCount }}</strong>
        <span>最後賞</span>
      </div>
    </form>

    <div v-if="errorMessage" class="state-panel admin-state" role="alert">
      <i class="bi bi-exclamation-triangle" aria-hidden="true"></i>
      <span>{{ errorMessage }}</span>
    </div>

    <section class="status-panel">
      <div class="section-heading">
        <div>
          <span class="eyebrow">Prize Library</span>
          <h2>獎品庫</h2>
        </div>
        <RouterLink class="btn btn-outline-dark btn-sm" to="/admin/campaigns">
          <i class="bi bi-box-seam me-1" aria-hidden="true"></i>
          賞池管理
        </RouterLink>
      </div>

      <div v-if="loading" class="admin-prize-library-list">
        <div v-for="index in 4" :key="index" class="admin-prize-library-card">
          <div class="skeleton-line w-50"></div>
          <div class="skeleton-line"></div>
          <div class="skeleton-line w-75"></div>
        </div>
      </div>

      <div v-else-if="prizes.length === 0" class="empty-state">
        <i class="bi bi-gift" aria-hidden="true"></i>
        <strong>沒有符合條件的獎品</strong>
      </div>

      <div v-else class="admin-prize-library-list">
        <article v-for="prize in prizes" :key="prize.id" class="admin-prize-library-card">
          <div class="admin-prize-library-card__image">
            <img v-if="prize.imageUrl" :src="prize.imageUrl" :alt="prize.name" />
            <i v-else class="bi bi-gift" aria-hidden="true"></i>
          </div>
          <div class="admin-prize-library-card__body">
            <div class="admin-prize-library-card__head">
              <div>
                <strong>{{ prize.rank }}｜{{ prize.name }}</strong>
                <span>{{ prize.campaignTitle }}</span>
              </div>
              <div class="admin-prize-library-card__badges">
                <span class="badge" :class="statusBadgeClass(prize.campaignStatus)">
                  {{ prize.campaignStatusLabel }}
                </span>
                <span v-if="prize.lastPrize" class="badge text-bg-dark">最後賞</span>
              </div>
            </div>

            <p v-if="prize.description">{{ prize.description }}</p>

            <div class="admin-prize-library-card__stats">
              <span
                >數量 {{ formatNumber(prize.remainingQuantity) }} /
                {{ formatNumber(prize.originalQuantity) }}</span
              >
              <span
                >Ticket {{ formatNumber(prize.availableTickets) }} /
                {{ formatNumber(prize.generatedTickets) }}</span
              >
              <span>已抽出 {{ formatNumber(prize.drawnTickets) }}</span>
              <span>更新 {{ formatTime(prize.updatedAt) }}</span>
            </div>

            <div class="admin-prize-library-card__actions">
              <RouterLink
                class="btn btn-sm btn-outline-dark"
                :to="`/admin/campaigns/${prize.campaignId}`"
              >
                <i class="bi bi-pencil-square me-1" aria-hidden="true"></i>
                編輯賞池
              </RouterLink>
              <RouterLink
                class="btn btn-sm btn-outline-dark"
                :to="`/admin/campaigns/${prize.campaignId}/tickets`"
              >
                <i class="bi bi-ticket-perforated me-1" aria-hidden="true"></i>
                Ticket
              </RouterLink>
            </div>
          </div>
        </article>
      </div>
    </section>
  </article>
</template>

<style scoped>
.admin-prize-library-list {
  display: grid;
  gap: 0.75rem;
}

.admin-prize-library-card {
  display: grid;
  grid-template-columns: 5rem minmax(0, 1fr);
  gap: 0.85rem;
  border: 1px solid var(--bs-border-color, #e3e3e8);
  border-radius: 0.5rem;
  padding: 0.85rem;
  background: var(--bs-body-bg, #fff);
}

.admin-prize-library-card__image {
  width: 5rem;
  aspect-ratio: 1;
  display: grid;
  place-items: center;
  overflow: hidden;
  border-radius: 0.45rem;
  background: var(--lb-surface-cool, #f6f8fb);
  color: var(--lb-muted, #6c757d);
  font-size: 1.6rem;
}

.admin-prize-library-card__image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.admin-prize-library-card__body,
.admin-prize-library-card__head {
  min-width: 0;
}

.admin-prize-library-card__body {
  display: grid;
  gap: 0.55rem;
}

.admin-prize-library-card__head {
  display: flex;
  gap: 0.75rem;
  justify-content: space-between;
  align-items: flex-start;
}

.admin-prize-library-card__head strong,
.admin-prize-library-card__head span {
  display: block;
}

.admin-prize-library-card__head span {
  color: var(--lb-muted, #6c757d);
  font-size: 0.85rem;
}

.admin-prize-library-card__badges,
.admin-prize-library-card__actions,
.admin-prize-library-card__stats {
  display: flex;
  flex-wrap: wrap;
  gap: 0.45rem;
}

.admin-prize-library-card__stats span {
  color: var(--lb-muted, #6c757d);
  font-size: 0.82rem;
}

.admin-prize-library-card p {
  margin: 0;
  color: var(--lb-ink, #1f2933);
}

@media (max-width: 640px) {
  .admin-prize-library-card {
    grid-template-columns: 1fr;
  }
}
</style>

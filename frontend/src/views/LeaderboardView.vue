<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { fetchLeaderboard } from '@/services/leaderboardApi'

const leaderboard = ref({
  liveDraws: [],
  popularCampaigns: [],
  luckyMembers: [],
  generatedAt: '',
})
const loading = ref(true)
const refreshing = ref(false)
const errorMessage = ref('')
let refreshTimer = null

const totalDrawCount = computed(() =>
  leaderboard.value.popularCampaigns.reduce(
    (sum, campaign) => sum + Number(campaign.drawCount || 0),
    0,
  ),
)
const leadingCampaign = computed(() => leaderboard.value.popularCampaigns[0] || null)
const liveDrawCount = computed(() => leaderboard.value.liveDraws.length)
const topLuckyMember = computed(() => leaderboard.value.luckyMembers[0] || null)

onMounted(async () => {
  await loadLeaderboard()
  refreshTimer = window.setInterval(() => {
    loadLeaderboard({ silent: true })
  }, 30000)
})

onUnmounted(() => {
  if (refreshTimer) {
    window.clearInterval(refreshTimer)
  }
})

async function loadLeaderboard(options = {}) {
  if (options.silent) {
    refreshing.value = true
  } else {
    loading.value = true
  }
  errorMessage.value = ''
  try {
    leaderboard.value = await fetchLeaderboard({ liveLimit: 16, popularLimit: 8, luckyLimit: 10 })
  } catch {
    errorMessage.value = '目前無法載入抽況榜單。'
  } finally {
    loading.value = false
    refreshing.value = false
  }
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

function formatPoint(value) {
  return new Intl.NumberFormat('zh-TW').format(value || 0)
}

function progressWidth(campaign) {
  return `${Math.min(Math.max(Number(campaign.soldRate || 0), 0), 100)}%`
}

function medalLabel(position) {
  if (position === 1) {
    return '🥇'
  }
  if (position === 2) {
    return '🥈'
  }
  if (position === 3) {
    return '🥉'
  }
  return `#${position}`
}
</script>

<template>
  <main class="leaderboard-page">
    <section class="container content-section">
      <div class="leaderboard-header">
        <div class="page-title">
          <span class="eyebrow">Leaderboard</span>
          <h1>抽況 LIVE / 熱門賞池</h1>
          <p>即時彙整公開抽出紀錄、熱門抽數與賞池剩餘進度。</p>
        </div>

        <button
          class="btn btn-dark"
          type="button"
          :disabled="loading || refreshing"
          @click="loadLeaderboard()"
        >
          <i class="bi bi-arrow-clockwise" aria-hidden="true"></i>
          {{ refreshing ? '更新中' : '重新整理' }}
        </button>
      </div>

      <div v-if="errorMessage" class="state-panel" role="alert">
        <i class="bi bi-exclamation-triangle" aria-hidden="true"></i>
        <span>{{ errorMessage }}</span>
      </div>

      <div class="status-grid leaderboard-metrics">
        <div>
          <strong>{{ liveDrawCount }}</strong>
          <span>最近公開抽況</span>
        </div>
        <div>
          <strong>{{ formatPoint(totalDrawCount) }}</strong>
          <span>熱門榜單抽數</span>
        </div>
        <div>
          <strong>{{ leadingCampaign?.title || '-' }}</strong>
          <span>目前熱門賞池</span>
        </div>
        <div>
          <strong>{{ topLuckyMember?.displayName || '-' }}</strong>
          <span>本期歐氣王</span>
        </div>
      </div>

      <div class="leaderboard-grid">
        <section class="status-panel leaderboard-panel">
          <div class="section-heading">
            <div>
              <span class="eyebrow">Live Draws</span>
              <h2>最近抽出</h2>
            </div>
            <span class="leaderboard-timestamp">
              <i class="bi bi-clock" aria-hidden="true"></i>
              {{ formatTime(leaderboard.generatedAt) }}
            </span>
          </div>

          <div v-if="loading" class="leaderboard-live-list">
            <article v-for="index in 5" :key="index" class="leaderboard-live-card">
              <div class="skeleton-line w-50"></div>
              <div class="skeleton-line"></div>
            </article>
          </div>

          <div v-else-if="leaderboard.liveDraws.length === 0" class="empty-state">
            <i class="bi bi-broadcast" aria-hidden="true"></i>
            <strong>目前還沒有公開抽況</strong>
          </div>

          <div v-else class="leaderboard-live-list">
            <article
              v-for="item in leaderboard.liveDraws"
              :key="item.drawResultId"
              class="leaderboard-live-card"
            >
              <span class="leaderboard-live-card__avatar">{{ item.maskedDisplayName }}</span>
              <div>
                <strong>{{ item.prizeRank }}賞 {{ item.prizeName }}</strong>
                <span>{{ item.campaignTitle }}</span>
              </div>
              <time>{{ formatTime(item.createdAt) }}</time>
            </article>
          </div>
        </section>

        <section class="status-panel leaderboard-panel">
          <div class="section-heading">
            <div>
              <span class="eyebrow">Popular</span>
              <h2>熱門賞池</h2>
            </div>
          </div>

          <div v-if="loading" class="leaderboard-campaign-list">
            <article v-for="index in 4" :key="index" class="leaderboard-campaign-card">
              <div class="skeleton-line w-50"></div>
              <div class="skeleton-line"></div>
              <div class="skeleton-line w-75"></div>
            </article>
          </div>

          <div v-else-if="leaderboard.popularCampaigns.length === 0" class="empty-state">
            <i class="bi bi-bar-chart-line" aria-hidden="true"></i>
            <strong>目前沒有可顯示的賞池</strong>
          </div>

          <div v-else class="leaderboard-campaign-list">
            <article
              v-for="campaign in leaderboard.popularCampaigns"
              :key="campaign.campaignId"
              class="leaderboard-campaign-card"
            >
              <div class="leaderboard-campaign-card__head">
                <div>
                  <span class="badge text-bg-danger">{{ campaign.statusLabel }}</span>
                  <h3>{{ campaign.title }}</h3>
                </div>
                <RouterLink class="btn btn-sm btn-outline-dark" :to="`/kuji/${campaign.slug}`">
                  <i class="bi bi-box2-heart" aria-hidden="true"></i>
                  查看
                </RouterLink>
              </div>

              <div class="leaderboard-campaign-card__stats">
                <span>
                  <strong>{{ formatPoint(campaign.drawCount) }}</strong>
                  抽數
                </span>
                <span>
                  <strong>{{ campaign.uniqueDrawers }}</strong>
                  玩家
                </span>
                <span>
                  <strong>{{ campaign.pricePerDraw }}</strong>
                  LP / 抽
                </span>
              </div>

              <div>
                <div
                  class="progress"
                  role="progressbar"
                  :aria-label="`${campaign.title} 已售比例`"
                  :aria-valuenow="campaign.soldTickets"
                  :aria-valuemax="campaign.totalTickets"
                >
                  <div class="progress-bar" :style="{ width: progressWidth(campaign) }"></div>
                </div>
                <div class="leaderboard-campaign-card__meta">
                  <span>已抽 {{ campaign.soldTickets }} / {{ campaign.totalTickets }}</span>
                  <span>{{ campaign.rareHint }}</span>
                </div>
              </div>
            </article>
          </div>
        </section>
      </div>

      <section class="status-panel leaderboard-panel leaderboard-lucky">
        <div class="section-heading">
          <div>
            <span class="eyebrow">Lucky Members</span>
            <h2>歐氣榜</h2>
          </div>
          <span class="leaderboard-lucky__hint">統計近期抽中 S / A 賞與最後賞的幸運會員</span>
        </div>

        <div v-if="loading" class="leaderboard-lucky-list">
          <article v-for="index in 5" :key="index" class="leaderboard-lucky-card">
            <div class="skeleton-line w-50"></div>
            <div class="skeleton-line"></div>
          </article>
        </div>

        <div v-else-if="leaderboard.luckyMembers.length === 0" class="empty-state">
          <i class="bi bi-stars" aria-hidden="true"></i>
          <strong>目前還沒有歐氣紀錄</strong>
          <span>抽中 S / A 賞或最後賞即可登上歐氣榜。</span>
        </div>

        <ol v-else class="leaderboard-lucky-list">
          <li
            v-for="member in leaderboard.luckyMembers"
            :key="member.position"
            class="leaderboard-lucky-card"
            :class="{ 'leaderboard-lucky-card--top': member.position <= 3 }"
          >
            <span class="leaderboard-lucky-card__rank">{{ medalLabel(member.position) }}</span>
            <div class="leaderboard-lucky-card__name">
              <strong>{{ member.displayName }}</strong>
              <span class="leaderboard-lucky-card__tags">
                <span v-if="member.topRankWins > 0" class="leaderboard-lucky-tag is-top">
                  S賞 ×{{ member.topRankWins }}
                </span>
                <span v-if="member.lastPrizeWins > 0" class="leaderboard-lucky-tag is-last">
                  最後賞 ×{{ member.lastPrizeWins }}
                </span>
              </span>
            </div>
            <span class="leaderboard-lucky-card__score">
              <strong>{{ formatPoint(member.luckyWins) }}</strong>
              歐氣
            </span>
          </li>
        </ol>
      </section>
    </section>
  </main>
</template>

<style scoped>
.leaderboard-lucky {
  margin-top: 1.5rem;
}

.leaderboard-lucky__hint {
  font-size: 0.85rem;
  color: var(--bs-secondary-color, #6c757d);
}

.leaderboard-lucky-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  margin: 0;
  padding: 0;
  list-style: none;
}

.leaderboard-lucky-card {
  display: grid;
  grid-template-columns: 2.5rem 1fr auto;
  align-items: center;
  gap: 0.85rem;
  padding: 0.75rem 1rem;
  border: 1px solid var(--bs-border-color, #e3e3e8);
  border-radius: 0.85rem;
  background: var(--bs-body-bg, #fff);
  transition:
    transform 0.18s ease,
    box-shadow 0.18s ease,
    border-color 0.18s ease;
}

.leaderboard-lucky-card:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.08);
}

.leaderboard-lucky-card--top {
  border-color: rgba(217, 119, 6, 0.45);
  background: linear-gradient(135deg, rgba(255, 247, 224, 0.9), rgba(255, 255, 255, 0.95));
}

.leaderboard-lucky-card__rank {
  font-size: 1.35rem;
  font-weight: 700;
  text-align: center;
  color: var(--bs-emphasis-color, #1f2933);
}

.leaderboard-lucky-card__name {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  min-width: 0;
}

.leaderboard-lucky-card__name strong {
  font-size: 1rem;
  line-height: 1.2;
}

.leaderboard-lucky-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
}

.leaderboard-lucky-tag {
  font-size: 0.72rem;
  font-weight: 600;
  padding: 0.1rem 0.5rem;
  border-radius: 999px;
  line-height: 1.5;
}

.leaderboard-lucky-tag.is-top {
  background: rgba(220, 38, 38, 0.12);
  color: #b91c1c;
}

.leaderboard-lucky-tag.is-last {
  background: rgba(13, 148, 136, 0.14);
  color: #0f766e;
}

.leaderboard-lucky-card__score {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  font-size: 0.78rem;
  color: var(--bs-secondary-color, #6c757d);
  white-space: nowrap;
}

.leaderboard-lucky-card__score strong {
  font-size: 1.15rem;
  color: var(--bs-emphasis-color, #1f2933);
  line-height: 1.1;
}

@media (max-width: 575.98px) {
  .leaderboard-lucky-card {
    grid-template-columns: 2rem 1fr auto;
    gap: 0.6rem;
    padding: 0.65rem 0.8rem;
  }
}
</style>

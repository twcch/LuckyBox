<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import prizeBanner from '@/assets/images/luckybox-prize-banner-optimized.jpg'
import { fetchBanners } from '@/services/bannersApi'
import { fetchCampaigns } from '@/services/campaignApi'
import { fetchLeaderboard } from '@/services/leaderboardApi'

const route = useRoute()
const router = useRouter()
const campaignPage = ref({
  content: [],
  page: 0,
  size: 3,
  totalElements: 0,
  totalPages: 0,
})
const loading = ref(true)
const errorMessage = ref('')
const heroBanner = ref(null)
const liveDraws = ref([])
const popularCampaigns = ref([])
const sourceTypeOptions = [
  { value: '', label: '全部類型' },
  { value: 'MIXED', label: '自營混套賞' },
  { value: 'CARD', label: '卡牌賞' },
  { value: 'BLIND_BOX', label: '盲盒賞' },
  { value: 'GK', label: 'GK 賞' },
  { value: 'OFFICIAL', label: '官方賞' },
  { value: 'SELF_MADE', label: '自製賞' },
  { value: 'PREORDER', label: '預購賞' },
]
const statusOptions = [
  { value: '', label: '全部' },
  { value: 'LIVE', label: '開抽中' },
  { value: 'SCHEDULED', label: '即將開抽' },
  { value: 'SOLD_OUT', label: '已完抽' },
]
const sortOptions = [
  { value: 'default', label: '預設排序' },
  { value: 'latest', label: '最新' },
  { value: 'popular', label: '熱門' },
  { value: 'priceAsc', label: '價格低到高' },
  { value: 'priceDesc', label: '價格高到低' },
  { value: 'remainingAsc', label: '剩餘少到多' },
]

const filters = reactive(readFiltersFromRoute())

const campaigns = computed(() => campaignPage.value.content)

const liveCampaignCount = computed(
  () => campaigns.value.filter((campaign) => campaign.status === 'LIVE').length,
)

const resultSummary = computed(() => {
  if (campaignPage.value.totalElements === 0) {
    return '目前沒有符合條件的賞池'
  }
  const start = campaignPage.value.page * campaignPage.value.size + 1
  const end = start + campaigns.value.length - 1
  return `顯示 ${start}-${end} 筆，共 ${campaignPage.value.totalElements} 筆`
})

const hasActiveFilters = computed(
  () =>
    Boolean(filters.q.trim()) ||
    Boolean(filters.sourceType) ||
    Boolean(filters.status) ||
    filters.sort !== 'default',
)

const canGoPrevious = computed(() => campaignPage.value.page > 0)
const canGoNext = computed(() => campaignPage.value.page + 1 < campaignPage.value.totalPages)
const activeSearchText = computed(() => filters.q.trim())
const heroImageSrc = computed(() => heroBanner.value?.imageUrl || prizeBanner)
const heroImageAlt = computed(() => heroBanner.value?.title || '無品牌收藏盒與盲抽測試商品展示')
const heroLinkTarget = computed(() =>
  heroBanner.value?.href?.startsWith('http') ? '_blank' : undefined,
)
const heroLinkRel = computed(() =>
  heroBanner.value?.href?.startsWith('http') ? 'noopener noreferrer' : undefined,
)

function useImageFallback(event) {
  event.currentTarget.classList.add('image-fallback')
  event.currentTarget.src = prizeBanner
}

async function loadHeroBanner() {
  try {
    const banners = await fetchBanners({ position: 'HOME_HERO' })
    heroBanner.value = banners[0] || null
  } catch {
    heroBanner.value = null
  }
}

async function loadCampaigns() {
  loading.value = true
  errorMessage.value = ''
  try {
    campaignPage.value = await fetchCampaigns({
      q: filters.q.trim() || undefined,
      sourceType: filters.sourceType || undefined,
      status: filters.status || undefined,
      sort: filters.sort,
      page: filters.page,
      size: filters.size,
    })
  } catch {
    errorMessage.value = '目前無法載入賞池，請確認後端服務已啟動。'
  } finally {
    loading.value = false
  }
}

async function loadLeaderboard() {
  try {
    const data = await fetchLeaderboard({ liveLimit: 6, popularLimit: 5 })
    liveDraws.value = data.liveDraws || []
    popularCampaigns.value = data.popularCampaigns || []
  } catch {
    liveDraws.value = []
    popularCampaigns.value = []
  }
}

function liveDrawLabel(item) {
  return `${item.maskedDisplayName} 抽中 ${item.prizeRank}賞 ${item.prizeName}`
}

function campaignImageSrc(campaign) {
  return campaign.coverImageUrl || prizeBanner
}

function campaignCta(campaign) {
  if (campaign.status === 'LIVE' && campaign.remainingTickets > 0) {
    return {
      icon: 'bi-stars',
      label: '立即開抽',
      tone: 'danger',
    }
  }
  if (campaign.status === 'SOLD_OUT' || campaign.remainingTickets <= 0) {
    return {
      icon: 'bi-clipboard-data',
      label: '查看結果',
      tone: 'outline-dark',
    }
  }
  return {
    icon: 'bi-eye',
    label: '查看詳情',
    tone: 'outline-dark',
  }
}

function campaignStockTone(campaign) {
  if (campaign.remainingTickets <= 0) {
    return 'sold-out'
  }
  if (campaign.remainingRate <= 20) {
    return 'low'
  }
  return 'available'
}

function formatPoint(value) {
  return new Intl.NumberFormat('zh-TW').format(value || 0)
}

function soldProgressWidth(campaign) {
  return `${Math.min(Math.max(Number(campaign.soldRate || 0), 0), 100)}%`
}

function firstQueryValue(value) {
  return Array.isArray(value) ? value[0] : value
}

function queryText(value) {
  return typeof firstQueryValue(value) === 'string' ? firstQueryValue(value).trim() : ''
}

function queryInteger(value, fallback) {
  const parsed = Number.parseInt(queryText(value), 10)
  return Number.isInteger(parsed) && parsed >= 0 ? parsed : fallback
}

function optionValue(options, value, fallback) {
  const text = queryText(value)
  return options.some((option) => option.value === text) ? text : fallback
}

function readFiltersFromRoute() {
  return {
    q: queryText(route.query.q),
    sourceType: optionValue(sourceTypeOptions, route.query.sourceType, ''),
    status: optionValue(statusOptions, route.query.status, ''),
    sort: optionValue(sortOptions, route.query.sort, 'default'),
    page: queryInteger(route.query.page, 0),
    size: 3,
  }
}

function applyRouteFilters() {
  const nextFilters = readFiltersFromRoute()
  filters.q = nextFilters.q
  filters.sourceType = nextFilters.sourceType
  filters.status = nextFilters.status
  filters.sort = nextFilters.sort
  filters.page = nextFilters.page
}

function campaignRouteQuery() {
  const query = {}
  if (filters.q.trim()) {
    query.q = filters.q.trim()
  }
  if (filters.sourceType) {
    query.sourceType = filters.sourceType
  }
  if (filters.status) {
    query.status = filters.status
  }
  if (filters.sort !== 'default') {
    query.sort = filters.sort
  }
  if (filters.page > 0) {
    query.page = String(filters.page)
  }
  return query
}

async function syncCampaignRoute() {
  await router.replace({ path: '/', query: campaignRouteQuery(), hash: '#campaigns' })
}

async function submitFilters() {
  filters.page = 0
  await syncCampaignRoute()
}

function setStatus(status) {
  filters.status = status
  submitFilters()
}

function resetFilters() {
  filters.q = ''
  filters.sourceType = ''
  filters.status = ''
  filters.sort = 'default'
  submitFilters()
}

function goToPage(page) {
  filters.page = page
  syncCampaignRoute()
}

onMounted(async () => {
  applyRouteFilters()
  await Promise.all([loadCampaigns(), loadHeroBanner(), loadLeaderboard()])
})

watch(
  () => [
    route.query.q,
    route.query.sourceType,
    route.query.status,
    route.query.sort,
    route.query.page,
  ],
  async () => {
    if (route.path !== '/') {
      return
    }
    applyRouteFilters()
    await loadCampaigns()
  },
)
</script>

<template>
  <main class="home-page">
    <section class="hero-band home-hero">
      <img
        class="home-hero__image"
        :src="heroImageSrc"
        :alt="heroImageAlt"
        decoding="async"
        fetchpriority="high"
        loading="eager"
        @error="useImageFallback"
      />
      <div class="home-hero__overlay" aria-hidden="true"></div>

      <div class="container home-hero__inner">
        <div class="home-hero__copy">
          <span class="eyebrow">公開剩餘賞池 / 可追溯抽籤紀錄</span>
          <h1>LuckyBox 線上抽賞平台</h1>
          <p>先看剩餘數、機率、價格與售後資訊，再決定要不要開抽。每一次抽賞都有紀錄，戰利品也能集中管理。</p>

          <div class="hero-actions">
            <a class="btn btn-danger btn-lg" href="#campaigns">
              <i class="bi bi-stars me-2" aria-hidden="true"></i>
              查看開抽賞池
            </a>
            <RouterLink class="btn btn-light btn-lg" to="/leaderboard">
              <i class="bi bi-broadcast me-2" aria-hidden="true"></i>
              抽況 LIVE
            </RouterLink>
            <a
              v-if="heroBanner?.href"
              class="btn btn-outline-light btn-lg"
              :href="heroBanner.href"
              :target="heroLinkTarget"
              :rel="heroLinkRel"
            >
              <i class="bi bi-arrow-up-right-circle me-2" aria-hidden="true"></i>
              主打活動
            </a>
          </div>

          <div class="home-hero__trust" aria-label="平台狀態">
            <span>
              <strong>{{ campaignPage.totalElements }}</strong>
              已建立賞池
            </span>
            <span>
              <strong>{{ liveCampaignCount }}</strong>
              開抽中
            </span>
            <span>
              <strong>公開</strong>
              剩餘數與售後
            </span>
          </div>
        </div>

        <div class="home-hero__flow" aria-label="使用流程">
          <span>
            <i class="bi bi-search" aria-hidden="true"></i>
            看賞池
          </span>
          <span>
            <i class="bi bi-bar-chart-line" aria-hidden="true"></i>
            比剩餘
          </span>
          <span>
            <i class="bi bi-stars" aria-hidden="true"></i>
            開抽
          </span>
          <span>
            <i class="bi bi-box-seam" aria-hidden="true"></i>
            出貨
          </span>
        </div>
      </div>
    </section>

    <section id="campaigns" class="container content-section">
      <div class="section-heading">
        <div>
          <span class="eyebrow">賞池</span>
          <h2>現在可以抽什麼</h2>
        </div>
      </div>

      <form class="campaign-toolbar" role="search" @submit.prevent="submitFilters">
        <div class="campaign-search">
          <label class="form-label" for="campaignSearch">搜尋賞池</label>
          <div class="input-group">
            <span class="input-group-text" aria-hidden="true">
              <i class="bi bi-search"></i>
            </span>
            <input
              id="campaignSearch"
              v-model="filters.q"
              class="form-control"
              type="search"
              placeholder="輸入名稱、描述或品牌"
            />
            <button class="btn btn-dark" type="submit">搜尋</button>
          </div>
        </div>

        <div>
          <label class="form-label" for="sourceTypeFilter">類型</label>
          <select
            id="sourceTypeFilter"
            v-model="filters.sourceType"
            class="form-select"
            @change="submitFilters"
          >
            <option v-for="option in sourceTypeOptions" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </select>
        </div>

        <div>
          <label class="form-label" for="sortFilter">排序</label>
          <select
            id="sortFilter"
            v-model="filters.sort"
            class="form-select"
            @change="submitFilters"
          >
            <option v-for="option in sortOptions" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </select>
        </div>

        <button
          class="btn btn-outline-dark campaign-toolbar__reset"
          type="button"
          :disabled="!hasActiveFilters"
          @click="resetFilters"
        >
          <i class="bi bi-arrow-counterclockwise me-1" aria-hidden="true"></i>
          重設
        </button>
      </form>

      <div class="campaign-status-tabs" role="group" aria-label="賞池狀態">
        <button
          v-for="option in statusOptions"
          :key="option.value"
          class="btn btn-sm"
          :class="filters.status === option.value ? 'btn-dark' : 'btn-outline-dark'"
          type="button"
          @click="setStatus(option.value)"
        >
          {{ option.label }}
        </button>
      </div>

      <div class="campaign-result-bar">
        <div class="campaign-result-bar__summary">
          <span>{{ resultSummary }}</span>
          <span v-if="activeSearchText" class="campaign-result-bar__query">
            搜尋「{{ activeSearchText }}」
          </span>
        </div>
        <span v-if="campaignPage.totalPages > 1"
          >第 {{ campaignPage.page + 1 }} / {{ campaignPage.totalPages }} 頁</span
        >
      </div>

      <div v-if="loading" class="campaign-grid" aria-label="賞池載入中">
        <article v-for="index in 3" :key="index" class="campaign-card campaign-card--loading">
          <div class="campaign-card__image skeleton-block"></div>
          <div class="campaign-card__body">
            <div class="skeleton-line w-50"></div>
            <div class="skeleton-line w-75"></div>
            <div class="skeleton-line"></div>
          </div>
        </article>
      </div>

      <div v-else-if="errorMessage" class="state-panel" role="alert">
        <i class="bi bi-exclamation-triangle" aria-hidden="true"></i>
        <span>{{ errorMessage }}</span>
      </div>

      <div v-else-if="campaigns.length === 0" class="empty-state">
        <i class="bi bi-search" aria-hidden="true"></i>
        <strong>找不到符合條件的賞池</strong>
        <span>調整搜尋字或篩選條件後再試一次。</span>
        <button
          v-if="hasActiveFilters"
          class="btn btn-outline-dark btn-sm"
          type="button"
          @click="resetFilters"
        >
          <i class="bi bi-arrow-counterclockwise" aria-hidden="true"></i>
          清除篩選
        </button>
      </div>

      <div v-else class="campaign-grid">
        <article v-for="campaign in campaigns" :key="campaign.slug" class="campaign-card">
          <div class="campaign-card__media">
            <img
              :src="campaignImageSrc(campaign)"
              :alt="campaign.title"
              class="campaign-card__image"
              decoding="async"
              loading="lazy"
              @error="useImageFallback"
            />
            <span class="campaign-card__status">{{ campaign.statusLabel }}</span>
            <span v-if="campaign.hasLastPrize" class="campaign-card__last-prize">
              <i class="bi bi-award" aria-hidden="true"></i>
              最後賞
            </span>
          </div>
          <div class="campaign-card__body">
            <div class="campaign-card__badges">
              <span class="badge text-bg-light border">{{ campaign.sourceTypeLabel }}</span>
              <span
                class="campaign-stock-badge"
                :class="`campaign-stock-badge--${campaignStockTone(campaign)}`"
              >
                {{ campaign.remainingTickets > 0 ? '可抽取' : '已完抽' }}
              </span>
            </div>
            <h3>{{ campaign.title }}</h3>
            <p v-if="campaign.subtitle" class="campaign-card__subtitle">
              {{ campaign.subtitle }}
            </p>

            <div class="campaign-card__stats">
              <span class="campaign-card__stat">
                <i class="bi bi-coin me-1" aria-hidden="true"></i>
                <small>每抽</small>
                <strong>{{ campaign.pricePerDraw }} LP</strong>
              </span>
              <span class="campaign-card__stat">
                <i class="bi bi-ticket-perforated me-1" aria-hidden="true"></i>
                <small>剩餘</small>
                <strong>{{ campaign.remainingTickets }} / {{ campaign.totalTickets }}</strong>
              </span>
            </div>

            <div class="campaign-rare-hint">
              <i class="bi bi-gem" aria-hidden="true"></i>
              <span>{{ campaign.rareHint }}</span>
            </div>

            <div
              class="campaign-card__progress"
              role="progressbar"
              :aria-label="`${campaign.title} 剩餘比例`"
              :aria-valuenow="campaign.remainingTickets"
              :aria-valuemax="campaign.totalTickets"
            >
              <div class="campaign-card__progress-track" aria-hidden="true">
                <span :style="{ width: `${campaign.remainingRate}%` }"></span>
              </div>
              <span>{{ campaign.remainingRate }}%</span>
            </div>

            <div class="campaign-footer">
              <span>{{ campaign.remainingTickets > 0 ? '剩餘籤數公開' : '可查看抽出紀錄' }}</span>
              <RouterLink
                class="btn btn-sm"
                :class="`btn-${campaignCta(campaign).tone}`"
                :to="`/kuji/${campaign.slug}`"
              >
                <i :class="`bi ${campaignCta(campaign).icon} me-1`" aria-hidden="true"></i>
                {{ campaignCta(campaign).label }}
              </RouterLink>
            </div>
          </div>
        </article>
      </div>

      <nav v-if="campaignPage.totalPages > 1" class="campaign-pagination" aria-label="賞池分頁">
        <button
          class="btn btn-outline-dark"
          type="button"
          :disabled="!canGoPrevious"
          @click="goToPage(filters.page - 1)"
        >
          <i class="bi bi-chevron-left me-1" aria-hidden="true"></i>
          上一頁
        </button>
        <button
          class="btn btn-outline-dark"
          type="button"
          :disabled="!canGoNext"
          @click="goToPage(filters.page + 1)"
        >
          下一頁
          <i class="bi bi-chevron-right ms-1" aria-hidden="true"></i>
        </button>
      </nav>
    </section>

    <section id="live" class="container content-section pt-0">
      <div class="live-strip" aria-label="抽況 LIVE">
        <span class="live-strip__label">
          <i class="bi bi-broadcast-pin me-1" aria-hidden="true"></i>
          LIVE
        </span>
        <span v-if="liveDraws.length === 0" class="live-strip__item">等待第一筆公開抽賞</span>
        <span v-for="item in liveDraws" :key="item.drawResultId" class="live-strip__item">
          {{ liveDrawLabel(item) }}
        </span>
        <RouterLink class="btn btn-sm btn-outline-dark ms-auto" to="/leaderboard">
          <i class="bi bi-bar-chart-line" aria-hidden="true"></i>
          榜單
        </RouterLink>
      </div>
    </section>

    <section class="container content-section pt-0">
      <div class="home-discovery-grid">
        <section class="status-panel home-popular-panel">
          <div class="section-heading">
            <div>
              <span class="eyebrow">熱門</span>
              <h2>熱門賞池榜</h2>
            </div>
            <RouterLink class="btn btn-sm btn-outline-dark" to="/leaderboard">
              <i class="bi bi-bar-chart-line" aria-hidden="true"></i>
              完整榜單
            </RouterLink>
          </div>

          <div v-if="popularCampaigns.length === 0" class="empty-state">
            <i class="bi bi-bar-chart-line" aria-hidden="true"></i>
            <strong>等待熱門資料</strong>
            <span>有抽賞紀錄後會顯示熱門賞池。</span>
          </div>

          <div v-else class="home-popular-list">
            <article
              v-for="(campaign, index) in popularCampaigns"
              :key="campaign.campaignId"
              class="home-popular-card"
            >
              <span class="home-popular-card__rank">#{{ index + 1 }}</span>
              <div class="home-popular-card__body">
                <div class="home-popular-card__head">
                  <div>
                    <span class="badge text-bg-danger">{{ campaign.statusLabel }}</span>
                    <h3>{{ campaign.title }}</h3>
                  </div>
                  <RouterLink class="btn btn-sm btn-outline-dark" :to="`/kuji/${campaign.slug}`">
                    <i class="bi bi-box2-heart" aria-hidden="true"></i>
                    查看
                  </RouterLink>
                </div>
                <div class="home-popular-card__stats">
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
                <div class="home-popular-card__progress">
                  <div class="campaign-card__progress-track" aria-hidden="true">
                    <span :style="{ width: soldProgressWidth(campaign) }"></span>
                  </div>
                  <small>
                    已抽 {{ campaign.soldTickets }} / {{ campaign.totalTickets }}｜{{
                      campaign.rareHint
                    }}
                  </small>
                </div>
              </div>
            </article>
          </div>
        </section>

        <section class="status-panel home-starter-panel">
          <div class="section-heading">
            <div>
              <span class="eyebrow">開始</span>
              <h2>新手任務</h2>
            </div>
          </div>

          <div class="home-starter-list">
            <RouterLink class="home-starter-card" to="/register">
              <span>1</span>
              <div>
                <strong>建立會員</strong>
                <small>保留點數、抽賞與戰利品紀錄</small>
              </div>
              <i class="bi bi-person-plus" aria-hidden="true"></i>
            </RouterLink>
            <a class="home-starter-card" href="#campaigns">
              <span>2</span>
              <div>
                <strong>挑選賞池</strong>
                <small>看價格、剩餘數、稀有提示與最後賞</small>
              </div>
              <i class="bi bi-ticket-perforated" aria-hidden="true"></i>
            </a>
            <RouterLink class="home-starter-card" to="/shipping-policy">
              <span>3</span>
              <div>
                <strong>累積出貨</strong>
                <small>戰利品可合併申請出貨</small>
              </div>
              <i class="bi bi-truck" aria-hidden="true"></i>
            </RouterLink>
          </div>
        </section>
      </div>
    </section>

    <section class="container content-section pt-0">
      <div class="status-panel home-support-panel">
        <div>
          <span class="eyebrow">支援</span>
          <h2>抽賞前後都找得到協助</h2>
          <p>出貨、瑕疵、點數、優惠券與帳號資料問題，都可以先從常見問題與政策頁快速確認。</p>
        </div>

        <div class="home-support-actions" aria-label="客服與政策入口">
          <RouterLink to="/faq">
            <i class="bi bi-question-circle" aria-hidden="true"></i>
            <span>
              <strong>常見問題</strong>
              <small>帳號、點數、抽賞與客服</small>
            </span>
          </RouterLink>
          <RouterLink to="/shipping-policy">
            <i class="bi bi-truck" aria-hidden="true"></i>
            <span>
              <strong>出貨政策</strong>
              <small>合併出貨、運費與瑕疵處理</small>
            </span>
          </RouterLink>
          <RouterLink to="/privacy">
            <i class="bi bi-shield-lock" aria-hidden="true"></i>
            <span>
              <strong>隱私權政策</strong>
              <small>資料匯出與刪除帳號流程</small>
            </span>
          </RouterLink>
        </div>
      </div>
    </section>
  </main>
</template>

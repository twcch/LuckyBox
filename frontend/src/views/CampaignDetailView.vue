<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import prizeBanner from '@/assets/images/luckybox-prize-banner-optimized.jpg'
import { fetchCampaign } from '@/services/campaignApi'
import { createDrawOrder } from '@/services/drawApi'
import { fetchCampaignDrawHistory } from '@/services/leaderboardApi'
import { useSessionStore } from '@/stores/session'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const campaign = ref(null)
const loading = ref(true)
const errorMessage = ref('')
const drawErrorMessage = ref('')
const drawSuccessMessage = ref('')
const drawing = ref(false)
const drawQuantity = ref(1)
const couponCode = ref('')
const drawOrder = ref(null)
const recentDraws = ref([])
const drawHistoryLoading = ref(false)
const drawHistoryError = ref('')
const showDrawConfirm = ref(false)
const revealedResultCount = ref(0)
const shareMessage = ref('')
const shareErrorMessage = ref('')
const selectedGalleryIndex = ref(0)
let revealTimers = []

const detailFaqs = computed(() => {
  const priceText = campaign.value ? `${campaign.value.pricePerDraw} LP` : '頁面標示 LP'
  const remainingText = campaign.value
    ? `${campaign.value.remainingTickets} / ${campaign.value.totalTickets}`
    : '頁面目前數字'
  const lastPrizeText = campaign.value?.hasLastPrize
    ? '此賞池有最後賞，抽完最後一張普通 ticket 的該筆抽賞會額外取得最後賞。'
    : '此賞池目前未設定最後賞，獎項以列表內的剩餘數與機率為準。'

  return [
    {
      icon: 'bi-coin',
      question: '抽一次會扣多少 LP？',
      answer: `此賞池每抽 ${priceText}，多抽會依選擇抽數即時計算原始扣點；若套用折扣券，結果區會顯示原價、折抵與實扣 LP。`,
    },
    {
      icon: 'bi-bar-chart-line',
      question: '剩餘數與機率怎麼看？',
      answer: `目前剩餘籤數為 ${remainingText}，獎項列表會依各獎項剩餘數與總剩餘 ticket 顯示即時機率。`,
    },
    {
      icon: 'bi-award',
      question: '最後賞怎麼取得？',
      answer: lastPrizeText,
    },
    {
      icon: 'bi-box-seam',
      question: '抽到的商品在哪裡？',
      answer:
        '完成抽賞後，中獎結果會進入戰利品盒；可到會員中心查看未出貨獎品，累積後一起申請出貨。',
    },
    {
      icon: 'bi-truck',
      question: '可以合併出貨嗎？',
      answer: '可以。出貨申請會把已選獎品合併成一張出貨單，運費與免運券會在申請出貨時確認。',
    },
    {
      icon: 'bi-headset',
      question: '結果或出貨有疑問怎麼辦？',
      answer:
        '請保留會員 Email、抽賞訂單或出貨單號，透過客服頁寄信，我們會依紀錄查核 ticket serial、點數流水與出貨狀態。',
    },
  ]
})

const quantityOptions = computed(() => {
  if (!campaign.value || campaign.value.remainingTickets <= 0) {
    return []
  }
  return [1, 3, 5, 10].filter((quantity) => quantity <= campaign.value.remainingTickets)
})

const normalizedDrawQuantity = computed(() => normalizeDrawQuantity(drawQuantity.value))

const drawAvailable = computed(
  () => campaign.value?.status === 'LIVE' && campaign.value?.remainingTickets > 0,
)

const drawAvailability = computed(() => {
  if (!campaign.value) {
    return {
      icon: 'bi-hourglass-split',
      label: '載入中',
      title: '正在讀取賞池',
      description: '完成讀取後會顯示抽賞狀態。',
      tone: 'muted',
    }
  }
  if (campaign.value.remainingTickets <= 0) {
    return {
      icon: 'bi-check2-circle',
      label: '已完抽',
      title: '此賞池已完抽',
      description: '可查看獎項、最近抽出紀錄與相關公告。',
      tone: 'ended',
    }
  }
  if (campaign.value.status !== 'LIVE') {
    return {
      icon: 'bi-clock-history',
      label: campaign.value.statusLabel,
      title: '目前尚未開放抽賞',
      description: '可以先查看獎項、最後賞與出貨規則。',
      tone: 'muted',
    }
  }
  return {
    icon: 'bi-lightning-charge',
    label: '可立即開抽',
    title: '已開放抽賞',
    description: '選擇抽數並確認扣點後即可抽取 ticket。',
    tone: 'live',
  }
})

const drawCost = computed(() => {
  if (!campaign.value) {
    return 0
  }
  return campaign.value.pricePerDraw * normalizedDrawQuantity.value
})

const walletTotal = computed(() => {
  if (!session.user) {
    return 0
  }
  return session.user.cashPointBalance + session.user.bonusPointBalance
})

const estimatedWalletAfterDraw = computed(() => walletTotal.value - drawCost.value)

const visibleDrawResults = computed(() => {
  if (!drawOrder.value) {
    return []
  }
  return drawOrder.value.results.slice(0, revealedResultCount.value)
})

const resultRevealComplete = computed(
  () => drawOrder.value && revealedResultCount.value >= drawOrder.value.results.length,
)

const hasRareDrawResult = computed(
  () => drawOrder.value?.results.some((result) => isRareResult(result)) || false,
)

const lastPrize = computed(() => campaign.value?.prizes.find((prize) => prize.lastPrize) || null)

const lastPrizeAvailable = computed(
  () => Boolean(lastPrize.value) && lastPrize.value.remainingQuantity > 0,
)

const prizeRows = computed(() => {
  if (!campaign.value) {
    return []
  }
  return campaign.value.prizes.map((prize) => {
    const remainingQuantity = Number(prize.remainingQuantity || 0)
    const originalQuantity = Number(prize.originalQuantity || 0)
    const remainingRate =
      originalQuantity > 0 ? Math.round((remainingQuantity / originalQuantity) * 100) : 0
    const probability = Number(
      prize.probability ??
        (campaign.value.remainingTickets > 0
          ? (remainingQuantity / campaign.value.remainingTickets) * 100
          : 0),
    )
    const soldOut = remainingQuantity <= 0
    const lowStock = !soldOut && !prize.lastPrize && remainingRate <= 25
    const claimedLastPrize = prize.lastPrize && soldOut

    return {
      ...prize,
      remainingQuantity,
      originalQuantity,
      remainingRate,
      probabilityText: prize.lastPrize ? '最後賞' : `${formatProbability(probability)}%`,
      probabilityHint: prize.lastPrize ? '抽完最後普通籤觸發' : '依目前剩餘普通籤計算',
      stockLabel: prize.lastPrize
        ? claimedLastPrize
          ? '已發送'
          : '仍可取得'
        : soldOut
          ? '已抽完'
          : lowStock
            ? '低庫存'
            : '可抽取',
      statusTone: prize.lastPrize
        ? claimedLastPrize
          ? 'claimed'
          : 'last'
        : soldOut
          ? 'sold-out'
          : lowStock
            ? 'low'
            : 'available',
    }
  })
})

const prizeSummary = computed(() => {
  const rows = prizeRows.value
  const normalRows = rows.filter((prize) => !prize.lastPrize)
  return {
    rankCount: rows.length,
    normalRemaining: normalRows.reduce((sum, prize) => sum + prize.remainingQuantity, 0),
    normalOriginal: normalRows.reduce((sum, prize) => sum + prize.originalQuantity, 0),
    soldOutCount: normalRows.filter((prize) => prize.remainingQuantity <= 0).length,
  }
})

const mediaGallery = computed(() => {
  if (!campaign.value) {
    return [{ label: '預設主圖', src: prizeBanner }]
  }
  return [
    { label: '主圖', src: campaign.value.coverImageUrl || prizeBanner },
    { label: '橫幅', src: campaign.value.bannerImageUrl || prizeBanner },
  ].filter(
    (item, index, items) =>
      item.src && items.findIndex((entry) => entry.src === item.src) === index,
  )
})

const selectedGalleryImage = computed(
  () => mediaGallery.value[selectedGalleryIndex.value] || mediaGallery.value[0],
)

const campaignPolicyCards = computed(() => {
  if (!campaign.value) {
    return []
  }
  return [
    {
      key: 'rights',
      eyebrow: 'Rights',
      icon: 'bi-patch-check',
      title: '來源與素材確認',
      note: campaign.value.rightsNotice || '商品來源與圖片素材已由營運確認可於平台展示。',
      highlights: [
        `來源標示：${campaign.value.sourceTypeLabel}`,
        campaign.value.sourceType === 'OFFICIAL'
          ? '官方或授權商品須保留授權或進貨佐證'
          : '非官方賞不以官方、授權或正版代理名義宣傳',
        '正式公開前由營運完成商用素材檢查',
      ],
      links: [
        { to: '/terms', icon: 'bi-file-earmark-text', label: '服務條款' },
        { to: '/contact', icon: 'bi-headset', label: '詢問來源說明' },
      ],
    },
    {
      key: 'age',
      eyebrow: 'Age',
      icon: 'bi-person-badge',
      title: '年齡與使用資格',
      note: campaign.value.ageRestricted
        ? `此賞池限制 ${campaign.value.ageRestrictionLabel} 以上會員參與；${campaign.value.ageVerificationNote}`
        : '此賞池目前未設定特定年齡限制；未成年會員仍應依平台條款取得法定代理人同意。',
      highlights: campaign.value.ageRestricted
        ? [
            `最低年齡：${campaign.value.ageRestrictionLabel}`,
            campaign.value.ageVerificationNote,
            '不符合資格時請勿下單或抽賞',
          ]
        : ['未設定特定年齡門檻', '仍須遵守會員條款與付款規則', '營運可依商品性質調整限制'],
      links: [
        { to: '/terms', icon: 'bi-shield-check', label: '會員條款' },
        { to: '/contact', icon: 'bi-headset', label: '資格問題' },
      ],
    },
    {
      key: 'shipping',
      eyebrow: 'Shipping',
      icon: 'bi-truck',
      title: '出貨說明',
      note: campaign.value.shippingNote,
      highlights: [
        '戰利品可累積後合併出貨',
        '申請時可選擇收件地址與免運券',
        '出貨進度可在會員中心追蹤',
      ],
      links: [
        { to: '/shipping-policy', icon: 'bi-file-text', label: '完整出貨政策' },
        { to: '/account/prizes', icon: 'bi-box-seam', label: '查看戰利品' },
      ],
    },
    {
      key: 'returns',
      eyebrow: 'Returns',
      icon: 'bi-arrow-counterclockwise',
      title: '退換貨說明',
      note: campaign.value.returnPolicyNote,
      highlights: [
        '抽賞完成後不可因個人喜好取消',
        '瑕疵、破損或錯品請保留包裝與照片',
        '客服會依訂單、ticket 與出貨紀錄查核',
      ],
      links: [
        { to: '/shipping-policy', icon: 'bi-shield-check', label: '退換貨規則' },
        { to: '/contact', icon: 'bi-headset', label: '聯絡客服' },
      ],
    },
  ]
})

function useImageFallback(event) {
  event.currentTarget.src = prizeBanner
  event.currentTarget.classList.add('image-fallback')
}

onMounted(async () => {
  await session.load()
  await loadCampaign()
})

onBeforeUnmount(() => {
  clearResultRevealTimers()
})

async function loadCampaign() {
  loading.value = true
  errorMessage.value = ''
  try {
    campaign.value = await fetchCampaign(route.params.slug)
    selectedGalleryIndex.value = 0
    clampDrawQuantity()
    await loadDrawHistory()
  } catch {
    errorMessage.value = '找不到賞池或後端服務目前無法回應。'
  } finally {
    loading.value = false
  }
}

async function loadDrawHistory() {
  drawHistoryLoading.value = true
  drawHistoryError.value = ''
  try {
    const history = await fetchCampaignDrawHistory(route.params.slug, { limit: 8 })
    recentDraws.value = history.draws || []
  } catch {
    recentDraws.value = []
    drawHistoryError.value = '目前無法載入此賞池的抽出紀錄。'
  } finally {
    drawHistoryLoading.value = false
  }
}

async function requestDraw() {
  if (!session.authenticated) {
    await router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  if (!drawAvailable.value) {
    return
  }
  drawErrorMessage.value = ''
  clampDrawQuantity()
  showDrawConfirm.value = true
}

async function submitDraw() {
  if (!session.authenticated) {
    await router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  if (!drawAvailable.value) {
    return
  }
  clampDrawQuantity()
  showDrawConfirm.value = false
  drawing.value = true
  drawErrorMessage.value = ''
  drawSuccessMessage.value = ''
  drawOrder.value = null
  revealedResultCount.value = 0
  shareMessage.value = ''
  shareErrorMessage.value = ''
  clearResultRevealTimers()
  try {
    const normalizedCouponCode = couponCode.value.trim()
    drawOrder.value = await createDrawOrder({
      campaignSlug: campaign.value.slug,
      quantity: normalizedDrawQuantity.value,
      idempotencyKey: newRequestKey(),
      ...(normalizedCouponCode ? { couponCode: normalizedCouponCode } : {}),
    })
    const discountText =
      drawOrder.value.discountAmount > 0 ? `，優惠券折抵 ${drawOrder.value.discountAmount} LP` : ''
    drawSuccessMessage.value = `完成 ${drawOrder.value.quantity} 抽${discountText}。`
    if (drawOrder.value.discountAmount > 0) {
      couponCode.value = ''
    }
    await refreshAfterDraw()
    startResultReveal()
  } catch (error) {
    drawErrorMessage.value = error.response?.data?.message || '抽賞失敗，請稍後再試。'
  } finally {
    drawing.value = false
  }
}

async function refreshAfterDraw() {
  const refreshed = await fetchCampaign(route.params.slug)
  campaign.value = refreshed
  clampDrawQuantity()
  await session.load()
  await loadDrawHistory()
}

function setDrawQuantity(quantity) {
  drawQuantity.value = normalizeDrawQuantity(quantity)
}

function clampDrawQuantity() {
  drawQuantity.value = normalizedDrawQuantity.value
}

function normalizeDrawQuantity(value) {
  const max = Math.max(1, campaign.value?.remainingTickets || 1)
  const number = Number.parseInt(value, 10)
  if (!Number.isFinite(number)) {
    return 1
  }
  return Math.min(Math.max(number, 1), max)
}

function newRequestKey() {
  return globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random()}`
}

function startResultReveal() {
  clearResultRevealTimers()
  revealedResultCount.value = 0
  const resultCount = drawOrder.value?.results.length || 0
  if (resultCount === 0) {
    return
  }
  for (let index = 0; index < resultCount; index += 1) {
    const timer = window.setTimeout(
      () => {
        revealedResultCount.value = Math.min(index + 1, resultCount)
      },
      380 * (index + 1),
    )
    revealTimers.push(timer)
  }
}

function skipResultReveal() {
  clearResultRevealTimers()
  revealedResultCount.value = drawOrder.value?.results.length || 0
}

function clearResultRevealTimers() {
  revealTimers.forEach((timer) => window.clearTimeout(timer))
  revealTimers = []
}

function continueDrawing() {
  drawOrder.value = null
  revealedResultCount.value = 0
  drawSuccessMessage.value = ''
  shareMessage.value = ''
  shareErrorMessage.value = ''
  clearResultRevealTimers()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

async function shareDrawResult() {
  if (!drawOrder.value) {
    return
  }
  const rareText = hasRareDrawResult.value ? '，還抽到稀有獎項' : ''
  const prizeText = drawOrder.value.results
    .map((result) => `${result.prizeRank}賞 ${result.prizeName}`)
    .join('、')
  const text = `我在 LuckyBox「${drawOrder.value.campaignTitle}」完成 ${drawOrder.value.quantity} 抽${rareText}：${prizeText}`
  shareMessage.value = ''
  shareErrorMessage.value = ''
  try {
    await navigator.clipboard.writeText(text)
    shareMessage.value = '已複製分享文字。'
  } catch {
    shareErrorMessage.value = text
  }
}

function isRareResult(result) {
  return ['S', 'A', 'LAST'].includes(String(result.prizeRank).toUpperCase())
}

function formatProbability(value) {
  if (!Number.isFinite(value) || value <= 0) {
    return '0'
  }
  if (value < 1) {
    return value.toFixed(2)
  }
  return value.toFixed(1).replace(/\.0$/, '')
}

function formatDrawTime(value) {
  if (!value) {
    return ''
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
  <main class="campaign-detail-page">
    <section class="container content-section">
      <RouterLink class="back-link" to="/">
        <i class="bi bi-arrow-left" aria-hidden="true"></i>
        回首頁
      </RouterLink>

      <div v-if="loading" class="detail-layout">
        <div class="detail-media skeleton-block"></div>
        <div class="status-panel">
          <div class="skeleton-line w-50"></div>
          <div class="skeleton-line w-75"></div>
          <div class="skeleton-line"></div>
        </div>
      </div>

      <div v-else-if="errorMessage" class="state-panel" role="alert">
        <i class="bi bi-exclamation-triangle" aria-hidden="true"></i>
        <span>{{ errorMessage }}</span>
      </div>

      <article v-else class="detail-layout">
        <div class="detail-gallery">
          <img
            class="detail-media"
            :src="selectedGalleryImage.src"
            :alt="`${campaign.title} ${selectedGalleryImage.label}`"
            decoding="async"
            fetchpriority="high"
            loading="eager"
            @error="useImageFallback"
          />
          <div class="detail-gallery__thumbs" aria-label="商品圖片切換">
            <button
              v-for="(image, index) in mediaGallery"
              :key="`${image.label}-${image.src}`"
              class="detail-gallery__thumb"
              :class="{ active: selectedGalleryIndex === index }"
              type="button"
              :aria-pressed="selectedGalleryIndex === index"
              @click="selectedGalleryIndex = index"
            >
              <img
                :src="image.src"
                :alt="`${campaign.title} ${image.label}縮圖`"
                decoding="async"
                loading="lazy"
                @error="useImageFallback"
              />
              <span>{{ image.label }}</span>
            </button>
          </div>
        </div>

        <section class="detail-summary">
          <div class="detail-title-block">
            <div class="detail-status-row">
              <span class="badge text-bg-danger">{{ campaign.statusLabel }}</span>
              <span class="badge text-bg-light border">{{ campaign.sourceTypeLabel }}</span>
              <span v-if="campaign.ageRestricted" class="badge text-bg-warning">
                {{ campaign.ageRestrictionLabel }}
              </span>
              <span
                class="detail-availability-pill"
                :class="`detail-availability-pill--${drawAvailability.tone}`"
              >
                <i :class="`bi ${drawAvailability.icon}`" aria-hidden="true"></i>
                {{ drawAvailability.label }}
              </span>
            </div>
            <h1>{{ campaign.title }}</h1>
            <p v-if="campaign.subtitle" class="detail-subtitle">{{ campaign.subtitle }}</p>
            <p>{{ campaign.description }}</p>
          </div>

          <div class="detail-metrics">
            <div>
              <span>每抽價格</span>
              <strong>{{ campaign.pricePerDraw }} LP</strong>
            </div>
            <div>
              <span>剩餘籤數</span>
              <strong>{{ campaign.remainingTickets }} / {{ campaign.totalTickets }}</strong>
            </div>
            <div>
              <span>公平模式</span>
              <strong>{{ campaign.fairnessMode }}</strong>
            </div>
          </div>

          <div class="detail-cta-callout">
            <i :class="`bi ${drawAvailability.icon}`" aria-hidden="true"></i>
            <div>
              <strong>{{ drawAvailability.title }}</strong>
              <small>{{ drawAvailability.description }}</small>
            </div>
          </div>

          <div
            class="progress"
            role="progressbar"
            :aria-label="`${campaign.title} 剩餘比例`"
            :aria-valuenow="campaign.remainingTickets"
            :aria-valuemax="campaign.totalTickets"
          >
            <div class="progress-bar" :style="{ width: `${campaign.remainingRate}%` }"></div>
          </div>

          <div id="draw" class="draw-panel">
            <div v-if="drawErrorMessage" class="state-panel" role="alert">
              <i class="bi bi-exclamation-triangle" aria-hidden="true"></i>
              <span>{{ drawErrorMessage }}</span>
            </div>
            <div v-if="drawSuccessMessage" class="toast show lb-toast" role="status">
              <div class="toast-body">
                <i class="bi bi-stars me-1" aria-hidden="true"></i>
                {{ drawSuccessMessage }}
              </div>
            </div>

            <div v-if="drawing" class="draw-animation-stage" role="status" aria-live="polite">
              <span class="draw-animation-stage__ring" aria-hidden="true"></span>
              <div>
                <strong>正在抽取 ticket</strong>
                <small>系統正在確認點數、鎖定可用籤並產生抽賞結果。</small>
              </div>
            </div>

            <div class="draw-controls">
              <div class="draw-field">
                <span class="form-label">抽數</span>
                <div class="draw-quantity-picker" role="group" aria-label="選擇抽數">
                  <button
                    v-for="quantity in quantityOptions"
                    :key="quantity"
                    class="draw-quantity-picker__preset"
                    :class="{ active: normalizedDrawQuantity === quantity }"
                    type="button"
                    :disabled="!drawAvailable || drawing"
                    :aria-pressed="normalizedDrawQuantity === quantity"
                    @click="setDrawQuantity(quantity)"
                  >
                    {{ quantity }}
                  </button>
                  <label class="draw-quantity-picker__custom" for="drawQuantity">
                    <span>自訂</span>
                    <input
                      id="drawQuantity"
                      v-model="drawQuantity"
                      class="form-control"
                      type="number"
                      inputmode="numeric"
                      min="1"
                      :max="campaign.remainingTickets"
                      step="1"
                      :disabled="!drawAvailable || drawing"
                      @change="clampDrawQuantity"
                      @blur="clampDrawQuantity"
                    />
                  </label>
                </div>
              </div>
              <div class="draw-cost">
                <span>原始扣點</span>
                <strong>{{ drawCost }} LP</strong>
              </div>
              <div class="draw-cost">
                <span>目前餘額</span>
                <strong>{{ walletTotal }} LP</strong>
              </div>
            </div>

            <div class="coupon-apply-row">
              <div class="draw-field">
                <label class="form-label" for="drawCouponCode">優惠碼</label>
                <input
                  id="drawCouponCode"
                  v-model.trim="couponCode"
                  class="form-control"
                  type="text"
                  inputmode="latin"
                  autocomplete="off"
                  placeholder="輸入折扣券代碼"
                  :disabled="!drawAvailable || drawing"
                />
              </div>
              <RouterLink class="btn btn-outline-dark" to="/account/coupons">
                <i class="bi bi-ticket-perforated me-2" aria-hidden="true"></i>
                我的優惠券
              </RouterLink>
            </div>

            <button
              class="btn btn-danger btn-lg"
              type="button"
              :disabled="drawing || !drawAvailable"
              @click="requestDraw"
            >
              <i class="bi bi-stars me-2" aria-hidden="true"></i>
              <span v-if="drawing">抽賞中</span>
              <span v-else-if="!session.authenticated">登入後抽賞</span>
              <span v-else-if="drawAvailable">立即抽賞</span>
              <span v-else>目前不可抽</span>
            </button>
          </div>
        </section>
      </article>
    </section>

    <section v-if="campaign" class="container content-section pt-0">
      <div v-if="drawOrder" class="detail-section draw-result-section">
        <div class="section-heading">
          <div>
            <span class="eyebrow">Results</span>
            <h2>抽賞結果</h2>
          </div>
          <div class="draw-result-summary">
            <strong>實扣 {{ drawOrder.pointSpent }} LP</strong>
            <span v-if="drawOrder.discountAmount > 0">
              原價 {{ drawOrder.originalPointSpent }} LP，折抵 {{ drawOrder.discountAmount }} LP
              <template v-if="drawOrder.couponCode">（{{ drawOrder.couponCode }}）</template>
            </span>
          </div>
          <button
            v-if="!resultRevealComplete"
            class="btn btn-sm btn-outline-dark"
            type="button"
            @click="skipResultReveal"
          >
            <i class="bi bi-skip-forward" aria-hidden="true"></i>
            略過揭曉
          </button>
        </div>

        <div v-if="!resultRevealComplete" class="draw-reveal-status" role="status">
          <span class="draw-reveal-status__spark" aria-hidden="true"></span>
          <div>
            <strong>結果揭曉中</strong>
            <small>
              已揭曉 {{ revealedResultCount }} / {{ drawOrder.results.length }} 張，可隨時略過動畫。
            </small>
          </div>
        </div>

        <div class="draw-result-grid">
          <article
            v-for="result in visibleDrawResults"
            :key="result.id"
            class="draw-result-card"
            :class="{ 'draw-result-card--rare': isRareResult(result) }"
          >
            <span class="rank-badge">{{ result.prizeRank }}</span>
            <div>
              <strong>{{ result.prizeName }}</strong>
              <small>{{ result.ticketSerialNumber }}</small>
            </div>
            <span v-if="isRareResult(result)" class="draw-result-card__rare">
              <i class="bi bi-stars" aria-hidden="true"></i>
              稀有
            </span>
          </article>
        </div>

        <div v-if="resultRevealComplete" class="draw-result-actions">
          <button class="btn btn-outline-dark" type="button" @click="continueDrawing">
            <i class="bi bi-arrow-repeat" aria-hidden="true"></i>
            繼續抽
          </button>
          <RouterLink class="btn btn-outline-dark" to="/account/prizes">
            <i class="bi bi-box-seam" aria-hidden="true"></i>
            查看戰利品
          </RouterLink>
          <RouterLink class="btn btn-outline-dark" to="/account/prizes">
            <i class="bi bi-truck" aria-hidden="true"></i>
            申請出貨
          </RouterLink>
          <button class="btn btn-danger" type="button" @click="shareDrawResult">
            <i class="bi bi-share" aria-hidden="true"></i>
            分享結果
          </button>
        </div>

        <div v-if="shareMessage" class="toast show lb-toast" role="status">
          <div class="toast-body">
            <i class="bi bi-check-circle me-1" aria-hidden="true"></i>
            {{ shareMessage }}
          </div>
        </div>

        <div v-if="shareErrorMessage" class="state-panel" role="status">
          <i class="bi bi-clipboard" aria-hidden="true"></i>
          <span>{{ shareErrorMessage }}</span>
        </div>
      </div>

      <div class="detail-section">
        <div class="section-heading">
          <div>
            <span class="eyebrow">Prizes</span>
            <h2>獎項與目前機率</h2>
          </div>
          <div class="prize-summary-chips" aria-label="獎項摘要">
            <span>{{ prizeSummary.rankCount }} 個獎項</span>
            <span
              >{{ prizeSummary.normalRemaining }} / {{ prizeSummary.normalOriginal }} 普通籤</span
            >
            <span>{{ prizeSummary.soldOutCount }} 個已抽完</span>
          </div>
        </div>

        <div class="prize-disclosure-note">
          <i class="bi bi-info-circle" aria-hidden="true"></i>
          <span>
            目前機率以普通獎項剩餘數除以此賞池目前剩餘普通 ticket
            計算；最後賞依最後賞規則取得，不併入普通獎項機率。
          </span>
        </div>

        <div v-if="prizeRows.length > 0" class="prize-table">
          <div class="prize-row prize-row--head">
            <span>等級</span>
            <span>獎項</span>
            <span>剩餘 / 原始</span>
            <span>目前機率</span>
            <span>剩餘比例</span>
          </div>
          <div
            v-for="prize in prizeRows"
            :key="prize.id"
            class="prize-row"
            :class="`prize-row--${prize.statusTone}`"
          >
            <span class="rank-badge">{{ prize.rank }}</span>
            <span class="prize-row__name">
              <strong>{{ prize.name }}</strong>
              <small>{{ prize.description }}</small>
            </span>
            <span class="prize-quantity-stack">
              <strong>{{ prize.remainingQuantity }} / {{ prize.originalQuantity }}</strong>
              <small :class="`prize-stock-label prize-stock-label--${prize.statusTone}`">
                {{ prize.stockLabel }}
              </small>
            </span>
            <span class="prize-probability" :class="{ 'is-last-prize': prize.lastPrize }">
              <strong>{{ prize.probabilityText }}</strong>
              <small>{{ prize.probabilityHint }}</small>
            </span>
            <span class="prize-stock-meter">
              <span class="prize-stock-meter__track" aria-hidden="true">
                <span :style="{ width: `${prize.remainingRate}%` }"></span>
              </span>
              <small>{{ prize.remainingRate }}%</small>
            </span>
          </div>
        </div>
        <div v-else class="empty-state">
          <i class="bi bi-gift" aria-hidden="true"></i>
          <strong>尚未設定獎項</strong>
          <span>此賞池目前沒有公開獎項資料。</span>
        </div>
      </div>

      <div v-if="campaign.hasLastPrize" class="detail-section last-prize-section">
        <div class="section-heading">
          <div>
            <span class="eyebrow">Last Prize</span>
            <h2>最後賞</h2>
          </div>
          <span
            class="last-prize-section__status"
            :class="{ 'last-prize-section__status--ended': !lastPrizeAvailable }"
          >
            {{ lastPrizeAvailable ? '仍可取得' : '已發送或不可取得' }}
          </span>
        </div>

        <div class="last-prize-panel">
          <span class="last-prize-panel__badge">
            <i class="bi bi-award" aria-hidden="true"></i>
          </span>
          <div>
            <strong>{{ lastPrize?.name || '最後賞' }}</strong>
            <p>{{ lastPrize?.description || campaign.lastPrizeRule }}</p>
          </div>
        </div>

        <div class="last-prize-rule-grid">
          <div>
            <span>觸發條件</span>
            <strong>抽完最後一張普通 ticket</strong>
          </div>
          <div>
            <span>目前剩餘普通籤</span>
            <strong>{{ campaign.remainingTickets }} / {{ campaign.totalTickets }}</strong>
          </div>
          <div>
            <span>最後賞剩餘</span>
            <strong>
              {{
                lastPrize
                  ? `${lastPrize.remainingQuantity} / ${lastPrize.originalQuantity}`
                  : '未設定'
              }}
            </strong>
          </div>
        </div>

        <p class="last-prize-section__rule">{{ campaign.lastPrizeRule }}</p>
      </div>

      <div class="detail-section campaign-draw-history">
        <div class="section-heading">
          <div>
            <span class="eyebrow">Draw History</span>
            <h2>最近抽出紀錄</h2>
          </div>
          <RouterLink class="btn btn-sm btn-outline-dark" to="/leaderboard">
            <i class="bi bi-broadcast" aria-hidden="true"></i>
            全站抽況
          </RouterLink>
        </div>

        <div
          v-if="drawHistoryLoading"
          class="campaign-draw-history__list"
          aria-label="抽出紀錄載入中"
        >
          <article v-for="index in 3" :key="index" class="campaign-draw-history__item">
            <div class="skeleton-line w-50"></div>
            <div class="skeleton-line w-75"></div>
          </article>
        </div>

        <div v-else-if="drawHistoryError" class="state-panel" role="alert">
          <i class="bi bi-exclamation-triangle" aria-hidden="true"></i>
          <span>{{ drawHistoryError }}</span>
        </div>

        <div v-else-if="recentDraws.length === 0" class="empty-state">
          <i class="bi bi-ticket-perforated" aria-hidden="true"></i>
          <strong>尚無公開抽出紀錄</strong>
          <span>完成第一筆抽賞後，近期結果會顯示在這裡。</span>
        </div>

        <div v-else class="campaign-draw-history__list">
          <article
            v-for="item in recentDraws"
            :key="item.drawResultId"
            class="campaign-draw-history__item"
          >
            <span class="rank-badge">{{ item.prizeRank }}</span>
            <div>
              <strong>{{ item.prizeName }}</strong>
              <small>{{ item.maskedDisplayName }}｜第 {{ item.resultIndex }} 筆結果</small>
            </div>
            <time :datetime="item.createdAt">{{ formatDrawTime(item.createdAt) }}</time>
          </article>
        </div>
      </div>

      <div class="detail-section campaign-detail-faq">
        <div class="section-heading">
          <div>
            <span class="eyebrow">FAQ</span>
            <h2>抽賞前常見問題</h2>
          </div>
        </div>

        <div class="campaign-detail-faq__grid">
          <article
            v-for="item in detailFaqs"
            :key="item.question"
            class="campaign-detail-faq__item"
          >
            <span class="campaign-detail-faq__icon">
              <i :class="`bi ${item.icon}`" aria-hidden="true"></i>
            </span>
            <div>
              <h3>{{ item.question }}</h3>
              <p>{{ item.answer }}</p>
            </div>
          </article>
        </div>

        <div class="campaign-detail-faq__links">
          <RouterLink class="btn btn-sm btn-outline-dark" to="/faq">
            <i class="bi bi-question-circle" aria-hidden="true"></i>
            完整 FAQ
          </RouterLink>
          <RouterLink class="btn btn-sm btn-outline-dark" to="/shipping-policy">
            <i class="bi bi-truck" aria-hidden="true"></i>
            出貨政策
          </RouterLink>
          <RouterLink class="btn btn-sm btn-outline-dark" to="/contact">
            <i class="bi bi-headset" aria-hidden="true"></i>
            聯絡客服
          </RouterLink>
        </div>
      </div>

      <div class="detail-section campaign-policy-section">
        <div class="section-heading">
          <div>
            <span class="eyebrow">Policies</span>
            <h2>出貨與退換貨</h2>
          </div>
          <RouterLink class="btn btn-sm btn-outline-dark" to="/shipping-policy">
            <i class="bi bi-file-text" aria-hidden="true"></i>
            完整政策
          </RouterLink>
        </div>

        <div class="campaign-policy-grid">
          <article
            v-for="card in campaignPolicyCards"
            :key="card.key"
            class="campaign-policy-card"
            :class="`campaign-policy-card--${card.key}`"
          >
            <span class="campaign-policy-card__icon">
              <i :class="`bi ${card.icon}`" aria-hidden="true"></i>
            </span>
            <div class="campaign-policy-card__body">
              <span class="eyebrow">{{ card.eyebrow }}</span>
              <h3>{{ card.title }}</h3>
              <p>{{ card.note }}</p>
              <ul>
                <li v-for="highlight in card.highlights" :key="highlight">
                  <i class="bi bi-check2" aria-hidden="true"></i>
                  <span>{{ highlight }}</span>
                </li>
              </ul>
              <div class="campaign-policy-card__links">
                <RouterLink
                  v-for="link in card.links"
                  :key="link.to"
                  class="btn btn-sm btn-outline-dark"
                  :to="link.to"
                >
                  <i :class="`bi ${link.icon}`" aria-hidden="true"></i>
                  {{ link.label }}
                </RouterLink>
              </div>
            </div>
          </article>
        </div>

        <div class="campaign-assurance-strip">
          <div>
            <i class="bi bi-shield-check" aria-hidden="true"></i>
            <span>抽賞由後端從可用 ticket 中抽取，保留 ticket serial、點數流水與抽賞紀錄。</span>
          </div>
          <div>
            <i class="bi bi-headset" aria-hidden="true"></i>
            <span>若對結果或出貨有疑義，請附會員 Email、訂單或出貨單號聯繫客服。</span>
          </div>
          <RouterLink class="btn btn-sm btn-outline-dark" to="/fairness">公平性說明</RouterLink>
        </div>
      </div>
    </section>

    <Teleport to="body">
      <div
        v-if="showDrawConfirm"
        class="modal fade show d-block draw-confirm-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="drawConfirmTitle"
        tabindex="-1"
      >
        <div class="modal-dialog modal-dialog-centered">
          <div class="modal-content lb-modal">
            <div class="modal-header">
              <h2 id="drawConfirmTitle" class="modal-title fs-5">確認抽賞內容</h2>
              <button
                class="btn-close"
                type="button"
                aria-label="關閉"
                :disabled="drawing"
                @click="showDrawConfirm = false"
              ></button>
            </div>

            <div class="modal-body">
              <div class="draw-confirm-summary">
                <div>
                  <span>賞池</span>
                  <strong>{{ campaign.title }}</strong>
                </div>
                <div>
                  <span>抽數</span>
                  <strong>{{ normalizedDrawQuantity }} 抽</strong>
                </div>
                <div>
                  <span>原始扣點</span>
                  <strong>{{ drawCost }} LP</strong>
                </div>
                <div>
                  <span>目前餘額</span>
                  <strong>{{ walletTotal }} LP</strong>
                </div>
                <div>
                  <span>抽後預估餘額</span>
                  <strong :class="{ 'text-danger': estimatedWalletAfterDraw < 0 }">
                    {{ estimatedWalletAfterDraw }} LP
                  </strong>
                </div>
              </div>

              <p v-if="couponCode" class="draw-confirm-coupon">
                <i class="bi bi-ticket-perforated" aria-hidden="true"></i>
                已輸入優惠碼「{{ couponCode }}」。優惠券會在送出後由系統驗證，實扣 LP
                可能低於原始扣點。
              </p>

              <p class="draw-confirm-warning">
                <i class="bi bi-exclamation-triangle" aria-hidden="true"></i>
                抽賞送出後會立即扣點並抽取 ticket，完成後不可取消或復原。
              </p>
            </div>

            <div class="modal-footer">
              <button
                class="btn btn-outline-dark"
                type="button"
                :disabled="drawing"
                @click="showDrawConfirm = false"
              >
                取消
              </button>
              <button class="btn btn-danger" type="button" :disabled="drawing" @click="submitDraw">
                <i class="bi bi-stars" aria-hidden="true"></i>
                <span v-if="drawing">抽賞中</span>
                <span v-else>確認抽賞</span>
              </button>
            </div>
          </div>
        </div>
      </div>
      <div v-if="showDrawConfirm" class="modal-backdrop fade show"></div>
    </Teleport>
  </main>
</template>

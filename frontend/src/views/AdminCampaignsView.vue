<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AdminImageUploadField from '@/components/AdminImageUploadField.vue'
import {
  createAdminCampaign,
  createAdminCampaignPrize,
  dryRunAdminCampaign,
  fetchAdminCampaigns,
  fetchAdminCampaignPrizes,
  fetchAdminCampaignTickets,
  generateAdminCampaignTickets,
  pauseAdminCampaign,
  publishAdminCampaign,
  updateAdminCampaign,
  updateAdminCampaignPrize,
} from '@/services/adminCampaignApi'

const route = useRoute()
const router = useRouter()

const campaigns = ref([])
const loading = ref(true)
const saving = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const statusFilter = ref('')
const sortFilter = ref('latest')
const keyword = ref('')
const selectedCampaignId = ref(null)
const mode = ref('edit')
const prizeOverview = ref(emptyPrizeOverview())
const tickets = ref([])
const loadingPrizes = ref(false)
const loadingTickets = ref(false)
const savingPrize = ref(false)
const generatingTickets = ref(false)
const changingCampaignStatus = ref(false)
const dryRunningCampaign = ref(false)
const dryRunResult = ref(null)
const dryRunPassedCampaignId = ref(null)
const selectedPrizeId = ref(null)
const ticketStatusFilter = ref('')
const ticketKeyword = ref('')

const statusFilters = [
  { value: '', label: '全部狀態' },
  { value: 'DRAFT', label: '草稿' },
  { value: 'SCHEDULED', label: '即將開抽' },
  { value: 'LIVE', label: '開抽中' },
  { value: 'PAUSED', label: '暫停中' },
  { value: 'SOLD_OUT', label: '已完抽' },
  { value: 'ENDED', label: '已結束' },
]

const campaignStatuses = statusFilters.filter((option) => option.value)
const ticketStatusOptions = [
  { value: '', label: '全部 Ticket' },
  { value: 'AVAILABLE', label: '可抽' },
  { value: 'DRAWN', label: '已抽出' },
  { value: 'VOIDED', label: '已作廢' },
]
const sortOptions = [
  { value: 'latest', label: '最新建立' },
  { value: 'updatedDesc', label: '最近更新' },
  { value: 'status', label: '營運狀態' },
  { value: 'titleAsc', label: '標題 A-Z' },
  { value: 'priceAsc', label: '價格低到高' },
  { value: 'priceDesc', label: '價格高到低' },
  { value: 'remainingAsc', label: '剩餘少到多' },
  { value: 'remainingDesc', label: '剩餘多到少' },
]
const sourceTypes = [
  { value: 'OFFICIAL', label: '官方賞' },
  { value: 'SELF_MADE', label: '自製賞' },
  { value: 'MIXED', label: '自營混套賞' },
  { value: 'BLIND_BOX', label: '盲盒賞' },
  { value: 'CARD', label: '卡牌賞' },
  { value: 'GK', label: 'GK 賞' },
  { value: 'PREORDER', label: '預購賞' },
]
const fairnessModes = [
  { value: 'SERVER_RANDOM', label: 'Server Random' },
  { value: 'HASH_COMMIT_REVEAL', label: 'Hash Commit Reveal' },
]

const form = reactive(defaultForm())
const prizeForm = reactive(defaultPrizeForm())

const selectedCampaign = computed(
  () => campaigns.value.find((campaign) => campaign.id === selectedCampaignId.value) || null,
)
const liveCount = computed(
  () => campaigns.value.filter((campaign) => campaign.status === 'LIVE').length,
)
const draftCount = computed(
  () => campaigns.value.filter((campaign) => campaign.status === 'DRAFT').length,
)
const isNewMode = computed(() => mode.value === 'new')
const isTicketRoute = computed(() => route.name === 'admin-campaign-tickets')
const soldTickets = computed(() => selectedCampaign.value?.soldTickets || 0)
const canManagePrizes = computed(() => !isNewMode.value && Boolean(selectedCampaign.value))
const campaignSensitiveFieldsLocked = computed(() => {
  const campaign = selectedCampaign.value
  return (
    !isNewMode.value &&
    Boolean(campaign) &&
    (Number(campaign.soldTickets || 0) > 0 ||
      ['LIVE', 'PAUSED', 'SOLD_OUT', 'ENDED'].includes(campaign.status))
  )
})
const prizeEditingLocked = computed(() => campaignSensitiveFieldsLocked.value)
const selectedPrize = computed(
  () => prizeOverview.value.prizes.find((prize) => prize.id === selectedPrizeId.value) || null,
)
const draftPrizeTicketQuantity = computed(() => {
  const savedQuantity =
    selectedPrize.value && !selectedPrize.value.lastPrize ? selectedPrize.value.originalQuantity : 0
  const nextQuantity = prizeForm.lastPrize ? 0 : normalizedPrizeQuantity(prizeForm.originalQuantity)
  return Math.max(0, prizeOverview.value.totalPrizeQuantity - savedQuantity + nextQuantity)
})
const prizeQuantityGap = computed(() => {
  if (!selectedCampaign.value) {
    return 0
  }
  return selectedCampaign.value.totalTickets - draftPrizeTicketQuantity.value
})
const prizeQuantityPreviewClass = computed(() => {
  if (prizeQuantityGap.value === 0) {
    return 'admin-prize-quantity-preview--matched'
  }
  if (prizeQuantityGap.value > 0) {
    return 'admin-prize-quantity-preview--short'
  }
  return 'admin-prize-quantity-preview--over'
})
const prizeQuantityPreviewText = computed(() => {
  if (!selectedCampaign.value) {
    return ''
  }
  if (prizeForm.lastPrize) {
    return '最後賞不會生成一般 ticket。'
  }
  if (prizeQuantityGap.value === 0) {
    return '普通獎項數量與賞池總抽數相符。'
  }
  if (prizeQuantityGap.value > 0) {
    return `尚差 ${prizeQuantityGap.value} 張普通 ticket。`
  }
  return `超出賞池總抽數 ${Math.abs(prizeQuantityGap.value)} 張。`
})
const normalPrizes = computed(() => prizeOverview.value.prizes.filter((prize) => !prize.lastPrize))
const lastPrizeEntries = computed(() =>
  prizeOverview.value.prizes.filter((prize) => prize.lastPrize),
)
const drawnTicketCount = computed(
  () => tickets.value.filter((ticket) => ticket.status === 'DRAWN').length,
)
const availableTicketCount = computed(
  () => tickets.value.filter((ticket) => ticket.status === 'AVAILABLE').length,
)
const hasCampaignImage = computed(() =>
  Boolean(selectedCampaign.value?.coverImageUrl || selectedCampaign.value?.bannerImageUrl),
)
const hasPrizeQuantityMatch = computed(
  () =>
    Boolean(selectedCampaign.value) &&
    normalPrizes.value.length > 0 &&
    prizeOverview.value.totalPrizeQuantity === selectedCampaign.value.totalTickets,
)
const hasDrawableTickets = computed(
  () =>
    Boolean(selectedCampaign.value) &&
    prizeOverview.value.generatedTickets === selectedCampaign.value.totalTickets &&
    prizeOverview.value.availableTickets > 0,
)
const hasLastPrizeReadiness = computed(() => {
  const campaign = selectedCampaign.value
  return (
    Boolean(campaign) &&
    (!campaign.hasLastPrize ||
      (Boolean(campaign.lastPrizeRule) && lastPrizeEntries.value.length > 0))
  )
})
const hasPublishableStatus = computed(() =>
  ['DRAFT', 'SCHEDULED', 'PAUSED'].includes(selectedCampaign.value?.status),
)
const publishProbabilityPreviewItems = computed(() => {
  const totalTickets = Number(selectedCampaign.value?.totalTickets || 0)
  return normalPrizes.value.map((prize) => ({
    id: prize.id,
    label: `${prize.rank}｜${prize.name}`,
    quantity: prize.originalQuantity,
    probabilityText: formatProbability(prize.originalQuantity, totalTickets),
  }))
})
const publishProbabilityTotalText = computed(() => {
  const totalTickets = Number(selectedCampaign.value?.totalTickets || 0)
  const previewTotal = normalPrizes.value.reduce(
    (sum, prize) => sum + Number(prize.originalQuantity || 0),
    0,
  )
  return formatProbability(previewTotal, totalTickets)
})
const hasProbabilityPreview = computed(
  () => hasPrizeQuantityMatch.value && publishProbabilityPreviewItems.value.length > 0,
)
const dryRunChecklistPassed = computed(
  () =>
    dryRunPassedCampaignId.value === selectedCampaign.value?.id &&
    Boolean(dryRunResult.value?.results?.length),
)
const complianceChecklistPassed = computed(() => {
  const campaign = selectedCampaign.value
  if (!campaign) {
    return false
  }
  const ageReady =
    !campaign.ageRestricted ||
    (Number(campaign.minimumAge || 0) > 0 && Boolean(campaign.ageVerificationNote))
  return (
    Boolean(campaign.commercialUseConfirmed) &&
    (campaign.sourceType !== 'OFFICIAL' || Boolean(campaign.officialLicenseConfirmed)) &&
    ageReady
  )
})
const publishChecklistItems = computed(() => {
  const campaign = selectedCampaign.value
  if (!campaign) {
    return []
  }

  return [
    {
      key: 'productName',
      done: Boolean(campaign.title),
      label: '商品名稱',
      detail: campaign.title ? '商品名稱已填寫。' : '需先填寫商品名稱。',
    },
    {
      key: 'productImage',
      done: hasCampaignImage.value,
      label: '商品圖',
      detail: hasCampaignImage.value
        ? '封面或 Banner 圖 URL 已填寫。'
        : '需補上封面或 Banner 圖 URL。',
    },
    {
      key: 'sourceType',
      done: Boolean(campaign.sourceType),
      label: '商品來源',
      detail: campaign.sourceTypeLabel
        ? `已標示為 ${campaign.sourceTypeLabel}。`
        : '需選擇商品來源。',
    },
    {
      key: 'commercialUse',
      done: Boolean(campaign.commercialUseConfirmed),
      label: '商用素材',
      detail: campaign.commercialUseConfirmed
        ? '已確認商品圖與素材可公開商用。'
        : '發布前需確認商品圖與素材可商用。',
    },
    {
      key: 'officialLicense',
      done: campaign.sourceType !== 'OFFICIAL' || Boolean(campaign.officialLicenseConfirmed),
      label: '官方授權',
      detail:
        campaign.sourceType === 'OFFICIAL'
          ? campaign.officialLicenseConfirmed
            ? '已確認官方授權或進貨佐證。'
            : '官方賞發布前需確認授權或進貨佐證。'
          : '非官方賞不宣稱官方、授權或正版代理。',
    },
    {
      key: 'ageRestriction',
      done:
        !campaign.ageRestricted ||
        (Number(campaign.minimumAge || 0) > 0 && Boolean(campaign.ageVerificationNote)),
      label: '年齡限制',
      detail: campaign.ageRestricted
        ? Number(campaign.minimumAge || 0) > 0 && campaign.ageVerificationNote
          ? `已標示 ${campaign.minimumAge}+ 與驗證方式。`
          : '啟用年齡限制時需填最低年齡與驗證方式。'
        : '已標示為全年齡或未限制。',
    },
    {
      key: 'price',
      done: Number(campaign.pricePerDraw) > 0,
      label: '價格',
      detail:
        Number(campaign.pricePerDraw) > 0
          ? `每抽 ${campaign.pricePerDraw} LP。`
          : '每抽價格需大於 0。',
    },
    {
      key: 'totalTickets',
      done: Number(campaign.totalTickets) > 0,
      label: '總籤數',
      detail:
        Number(campaign.totalTickets) > 0
          ? `總籤數 ${campaign.totalTickets} 張。`
          : '總籤數需大於 0。',
    },
    {
      key: 'prizeQuantity',
      done: hasPrizeQuantityMatch.value,
      label: 'Prize 數量',
      detail: hasPrizeQuantityMatch.value
        ? `普通獎項總量等於 ${campaign.totalTickets} 張。`
        : `普通獎項總量需等於賞池總抽數 ${campaign.totalTickets} 張。`,
    },
    {
      key: 'lastPrize',
      done: hasLastPrizeReadiness.value,
      label: '最後賞',
      detail: hasLastPrizeReadiness.value
        ? campaign.hasLastPrize
          ? '最後賞規則與最後賞獎項已建立。'
          : '已標示為無最後賞。'
        : '啟用最後賞時需填規則並建立最後賞獎項。',
    },
    {
      key: 'shippingNote',
      done: Boolean(campaign.shippingNote),
      label: '出貨說明',
      detail: campaign.shippingNote ? '出貨說明已填寫。' : '需補齊出貨說明。',
    },
    {
      key: 'returnPolicyNote',
      done: Boolean(campaign.returnPolicyNote),
      label: '退換貨說明',
      detail: campaign.returnPolicyNote ? '退換貨說明已填寫。' : '需補齊退換貨說明。',
    },
    {
      key: 'probability',
      done: hasProbabilityPreview.value,
      label: '機率預覽',
      detail: hasProbabilityPreview.value
        ? `普通獎項機率合計 ${publishProbabilityTotalText.value}。`
        : '需先讓普通獎項總量等於總籤數。',
    },
    {
      key: 'tickets',
      done: hasDrawableTickets.value,
      label: 'Ticket 生成',
      detail: hasDrawableTickets.value
        ? `已生成 ${prizeOverview.value.generatedTickets} 張，尚可抽 ${prizeOverview.value.availableTickets} 張。`
        : '需先生成完整可抽 tickets。',
    },
    {
      key: 'dryRun',
      done: dryRunChecklistPassed.value,
      label: 'Dry Run',
      detail: dryRunChecklistPassed.value
        ? `已模擬 ${dryRunResult.value.requestedQuantity} 抽，ticket 與 prize 連結正常。`
        : '需執行測試抽籤 dry run。',
    },
    {
      key: 'status',
      done: hasPublishableStatus.value,
      label: '狀態',
      detail: hasPublishableStatus.value
        ? '目前狀態允許發布。'
        : '只有草稿、即將開抽或暫停中可發布。',
    },
  ]
})
const publishChecklistPassedCount = computed(
  () => publishChecklistItems.value.filter((item) => item.done).length,
)
const publishChecklistComplete = computed(
  () =>
    publishChecklistItems.value.length > 0 &&
    publishChecklistPassedCount.value === publishChecklistItems.value.length,
)
const canPublishCampaign = computed(() => {
  const status = selectedCampaign.value?.status
  return (
    canManagePrizes.value &&
    ['DRAFT', 'SCHEDULED', 'PAUSED'].includes(status) &&
    publishChecklistComplete.value &&
    complianceChecklistPassed.value
  )
})
const canPauseCampaign = computed(() => {
  const status = selectedCampaign.value?.status
  return canManagePrizes.value && ['LIVE', 'SCHEDULED'].includes(status)
})
const publishReadinessText = computed(() => {
  if (isNewMode.value || !selectedCampaign.value) {
    return '建立賞池後可進行發布檢查。'
  }
  if (selectedCampaign.value.status === 'LIVE') {
    return `已上架，尚有 ${selectedCampaign.value.remainingTickets} 張可抽。`
  }
  if (selectedCampaign.value.status === 'SOLD_OUT' || selectedCampaign.value.status === 'ENDED') {
    return '已完抽或已結束賞池不可重新發布。'
  }
  if (!publishChecklistComplete.value) {
    return `發布檢查 ${publishChecklistPassedCount.value} / ${publishChecklistItems.value.length} 完成。`
  }
  return `可發布，已有 ${prizeOverview.value.availableTickets} 張可抽 ticket。`
})

watch(statusFilter, async () => {
  await loadCampaigns()
})

watch(sortFilter, async () => {
  await loadCampaigns()
})

watch(
  () => route.fullPath,
  async () => {
    resolveRouteSelection()
    await loadTickets()
  },
)

watch(selectedCampaignId, async () => {
  await loadPrizes()
  await loadTickets()
})

watch(ticketStatusFilter, async () => {
  await loadTickets()
})

onMounted(async () => {
  await loadCampaigns()
})

async function loadCampaigns() {
  loading.value = true
  errorMessage.value = ''
  try {
    campaigns.value = await fetchAdminCampaigns({
      status: statusFilter.value,
      q: keyword.value.trim(),
      sort: sortFilter.value,
    })
    resolveRouteSelection()
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '無法載入賞池清單。'
  } finally {
    loading.value = false
  }
}

function resolveRouteSelection() {
  if (route.name === 'admin-campaign-new') {
    startNew(false)
    return
  }
  const routeId = Number(route.params.campaignId)
  const routeCampaign = campaigns.value.find((campaign) => campaign.id === routeId)
  if (routeCampaign) {
    selectCampaign(routeCampaign, false)
    return
  }
  if (campaigns.value.length > 0) {
    selectCampaign(campaigns.value[0], false)
    return
  }
  startNew(false)
}

function submitFilters() {
  loadCampaigns()
}

function startNew(pushRoute = true) {
  mode.value = 'new'
  selectedCampaignId.value = null
  resetDryRunState()
  Object.assign(form, defaultForm())
  if (pushRoute && route.name !== 'admin-campaign-new') {
    router.push('/admin/campaigns/new')
  }
}

function selectCampaign(campaign, pushRoute = true) {
  mode.value = 'edit'
  selectedCampaignId.value = campaign.id
  if (dryRunPassedCampaignId.value !== campaign.id) {
    resetDryRunState()
  }
  syncForm(campaign)
  if (pushRoute && route.params.campaignId !== String(campaign.id)) {
    router.push(`/admin/campaigns/${campaign.id}`)
  }
}

function syncForm(campaign) {
  Object.assign(form, {
    slug: campaign.slug,
    title: campaign.title,
    subtitle: campaign.subtitle || '',
    description: campaign.description,
    coverImageUrl: campaign.coverImageUrl || '',
    bannerImageUrl: campaign.bannerImageUrl || '',
    sourceType: campaign.sourceType,
    commercialUseConfirmed: Boolean(campaign.commercialUseConfirmed),
    officialLicenseConfirmed: Boolean(campaign.officialLicenseConfirmed),
    rightsNotice: campaign.rightsNotice || '',
    ageRestricted: Boolean(campaign.ageRestricted),
    minimumAge: campaign.minimumAge || 18,
    ageVerificationNote: campaign.ageVerificationNote || '',
    ipName: campaign.ipName || '',
    brandName: campaign.brandName || '',
    pricePerDraw: campaign.pricePerDraw,
    totalTickets: campaign.totalTickets,
    status: campaign.status,
    salesStartAt: campaign.salesStartAt || '',
    salesEndAt: campaign.salesEndAt || '',
    shippingNote: campaign.shippingNote,
    returnPolicyNote: campaign.returnPolicyNote,
    hasLastPrize: campaign.hasLastPrize,
    lastPrizeRule: campaign.lastPrizeRule || '',
    fairnessMode: campaign.fairnessMode,
    seedHash: campaign.seedHash || '',
  })
}

async function submitCampaign() {
  saving.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const payload = buildPayload()
    const saved = isNewMode.value
      ? await createAdminCampaign(payload)
      : await updateAdminCampaign(selectedCampaign.value.id, payload)
    successMessage.value = isNewMode.value
      ? `賞池「${saved.title}」已建立。`
      : `賞池「${saved.title}」已更新。`
    resetDryRunState()
    await loadCampaigns()
    selectCampaign(saved)
    await loadPrizes()
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '賞池儲存失敗。'
  } finally {
    saving.value = false
  }
}

async function loadPrizes() {
  if (!canManagePrizes.value) {
    resetPrizeState()
    return
  }
  loadingPrizes.value = true
  try {
    prizeOverview.value = await fetchAdminCampaignPrizes(selectedCampaign.value.id)
    if (!selectedPrize.value) {
      const firstPrize = prizeOverview.value.prizes[0]
      if (firstPrize) {
        selectPrize(firstPrize)
      } else {
        startNewPrize()
      }
    } else {
      syncPrizeForm(selectedPrize.value)
    }
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '無法載入獎項資料。'
  } finally {
    loadingPrizes.value = false
  }
}

function resetPrizeState() {
  prizeOverview.value = emptyPrizeOverview()
  selectedPrizeId.value = null
  tickets.value = []
  Object.assign(prizeForm, defaultPrizeForm())
}

async function loadTickets() {
  if (!isTicketRoute.value || !selectedCampaign.value) {
    tickets.value = []
    return
  }
  loadingTickets.value = true
  try {
    tickets.value = await fetchAdminCampaignTickets(selectedCampaign.value.id, {
      status: ticketStatusFilter.value,
      q: ticketKeyword.value.trim(),
      limit: 500,
    })
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '無法載入 Ticket 清單。'
  } finally {
    loadingTickets.value = false
  }
}

async function submitTicketFilters() {
  await loadTickets()
}

function startNewPrize() {
  if (prizeEditingLocked.value) {
    return
  }
  selectedPrizeId.value = null
  Object.assign(prizeForm, defaultPrizeForm(prizeOverview.value.prizes.length + 1))
}

function selectPrize(prize) {
  selectedPrizeId.value = prize.id
  syncPrizeForm(prize)
}

function syncPrizeForm(prize) {
  Object.assign(prizeForm, {
    rank: prize.rank,
    name: prize.name,
    description: prize.description || '',
    imageUrl: prize.imageUrl || '',
    originalQuantity: prize.originalQuantity,
    sortOrder: prize.sortOrder,
    lastPrize: prize.lastPrize,
  })
}

async function submitPrize() {
  if (!selectedCampaign.value) {
    return
  }
  if (prizeEditingLocked.value) {
    errorMessage.value = '已開抽、暫停或完抽的賞池不可直接修改獎項，請建立修正版本。'
    return
  }
  savingPrize.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const payload = buildPrizePayload()
    const saved = selectedPrize.value
      ? await updateAdminCampaignPrize(selectedCampaign.value.id, selectedPrize.value.id, payload)
      : await createAdminCampaignPrize(selectedCampaign.value.id, payload)
    successMessage.value = selectedPrize.value
      ? `獎項「${saved.name}」已更新。`
      : `獎項「${saved.name}」已建立。`
    resetDryRunState()
    await loadPrizes()
    selectPrize(saved)
    await loadCampaigns()
    await loadTickets()
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '獎項儲存失敗。'
  } finally {
    savingPrize.value = false
  }
}

function handleCampaignImageUploaded(label, uploaded) {
  errorMessage.value = ''
  successMessage.value = `${label}已上傳：${uploaded.filename}`
}

function handleImageUploadError(message) {
  errorMessage.value = message
}

async function generateTickets() {
  if (!selectedCampaign.value) {
    return
  }
  if (prizeEditingLocked.value) {
    errorMessage.value = '已開抽、暫停或完抽的賞池不可直接生成 ticket，請建立修正版本。'
    return
  }
  if (
    !confirmDangerousAction(
      `確認要為「${selectedCampaign.value.title}」生成缺少的 tickets？生成後會影響可抽籤數。`,
    )
  ) {
    return
  }
  generatingTickets.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const result = await generateAdminCampaignTickets(selectedCampaign.value.id)
    resetDryRunState()
    successMessage.value =
      result.generatedCount > 0
        ? `已產生 ${result.generatedCount} 張 tickets，目前可抽 ${result.availableTickets} / ${result.totalTickets}。`
        : `tickets 已是最新，目前可抽 ${result.availableTickets} / ${result.totalTickets}。`
    await loadPrizes()
    await loadTickets()
    await loadCampaigns()
  } catch (error) {
    errorMessage.value = error.response?.data?.message || 'Ticket 生成失敗。'
  } finally {
    generatingTickets.value = false
  }
}

async function dryRunCampaign() {
  if (!selectedCampaign.value) {
    return
  }
  dryRunningCampaign.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const result = await dryRunAdminCampaign(selectedCampaign.value.id)
    dryRunResult.value = result
    dryRunPassedCampaignId.value = selectedCampaign.value.id
    successMessage.value = `Dry run 通過，已模擬 ${result.requestedQuantity} 抽。`
  } catch (error) {
    resetDryRunState()
    errorMessage.value = error.response?.data?.message || 'Dry run 測試失敗。'
  } finally {
    dryRunningCampaign.value = false
  }
}

async function publishCampaign() {
  await runCampaignStatusCommand('publish')
}

async function pauseCampaign() {
  await runCampaignStatusCommand('pause')
}

async function runCampaignStatusCommand(command) {
  if (!selectedCampaign.value) {
    return
  }
  const confirmMessage =
    command === 'publish'
      ? `確認要發布「${selectedCampaign.value.title}」？發布後前台使用者即可抽賞。`
      : `確認要暫停「${selectedCampaign.value.title}」？暫停後前台使用者將不能抽賞。`
  if (!confirmDangerousAction(confirmMessage)) {
    return
  }
  changingCampaignStatus.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const saved =
      command === 'publish'
        ? await publishAdminCampaign(selectedCampaign.value.id)
        : await pauseAdminCampaign(selectedCampaign.value.id)
    successMessage.value =
      command === 'publish'
        ? `賞池「${saved.title}」已發布上架。`
        : `賞池「${saved.title}」已暫停下架。`
    if (statusFilter.value) {
      statusFilter.value = ''
    }
    await loadCampaigns()
    selectCampaign(saved, false)
    await loadPrizes()
  } catch (error) {
    errorMessage.value =
      error.response?.data?.message || (command === 'publish' ? '賞池發布失敗。' : '賞池暫停失敗。')
  } finally {
    changingCampaignStatus.value = false
  }
}

function buildPrizePayload() {
  return {
    rank: prizeForm.rank,
    name: prizeForm.name,
    description: prizeForm.description,
    imageUrl: prizeForm.imageUrl,
    originalQuantity: Number(prizeForm.originalQuantity),
    sortOrder: Number(prizeForm.sortOrder),
    lastPrize: prizeForm.lastPrize,
  }
}

function buildPayload() {
  return {
    slug: form.slug,
    title: form.title,
    subtitle: form.subtitle,
    description: form.description,
    coverImageUrl: form.coverImageUrl,
    bannerImageUrl: form.bannerImageUrl,
    sourceType: form.sourceType,
    commercialUseConfirmed: Boolean(form.commercialUseConfirmed),
    officialLicenseConfirmed: Boolean(form.officialLicenseConfirmed),
    rightsNotice: form.rightsNotice,
    ageRestricted: Boolean(form.ageRestricted),
    minimumAge: form.ageRestricted ? Number(form.minimumAge || 0) : null,
    ageVerificationNote: form.ageRestricted ? form.ageVerificationNote : '',
    ipName: form.ipName,
    brandName: form.brandName,
    pricePerDraw: Number(form.pricePerDraw),
    totalTickets: Number(form.totalTickets),
    status: form.status,
    salesStartAt: form.salesStartAt,
    salesEndAt: form.salesEndAt,
    shippingNote: form.shippingNote,
    returnPolicyNote: form.returnPolicyNote,
    hasLastPrize: form.hasLastPrize,
    lastPrizeRule: form.lastPrizeRule,
    fairnessMode: form.fairnessMode,
    seedHash: form.seedHash,
  }
}

function defaultForm() {
  return {
    slug: '',
    title: '',
    subtitle: '',
    description: '',
    coverImageUrl: '',
    bannerImageUrl: '',
    sourceType: 'MIXED',
    commercialUseConfirmed: true,
    officialLicenseConfirmed: false,
    rightsNotice: '商品來源與圖片素材由營運確認可於平台展示。',
    ageRestricted: false,
    minimumAge: 18,
    ageVerificationNote: '',
    ipName: '',
    brandName: '',
    pricePerDraw: 120,
    totalTickets: 0,
    status: 'DRAFT',
    salesStartAt: '',
    salesEndAt: '',
    shippingNote: '完成抽賞後可於戰利品盒申請出貨。',
    returnPolicyNote: '抽賞商品依平台規則辦理退換貨。',
    hasLastPrize: false,
    lastPrizeRule: '',
    fairnessMode: 'SERVER_RANDOM',
    seedHash: '',
  }
}

function defaultPrizeForm(sortOrder = 1) {
  return {
    rank: '',
    name: '',
    description: '',
    imageUrl: '',
    originalQuantity: 1,
    sortOrder,
    lastPrize: false,
  }
}

function emptyPrizeOverview() {
  return {
    campaignId: null,
    totalPrizeQuantity: 0,
    remainingPrizeQuantity: 0,
    generatedTickets: 0,
    availableTickets: 0,
    prizes: [],
  }
}

function normalizedPrizeQuantity(value) {
  const quantity = Number(value)
  return Number.isFinite(quantity) ? Math.max(0, quantity) : 0
}

function resetDryRunState() {
  dryRunResult.value = null
  dryRunPassedCampaignId.value = null
}

function formatProbability(quantity, total) {
  if (!total || total <= 0) {
    return '0.00%'
  }
  return `${((Number(quantity || 0) / total) * 100).toFixed(2)}%`
}

function confirmDangerousAction(message) {
  return window.confirm(message)
}

function statusBadgeClass(status) {
  if (status === 'LIVE') {
    return 'text-bg-danger'
  }
  if (status === 'DRAFT' || status === 'PAUSED') {
    return 'text-bg-warning'
  }
  return 'text-bg-light border'
}

function ticketStatusBadgeClass(status) {
  if (status === 'AVAILABLE') {
    return 'text-bg-success'
  }
  if (status === 'DRAWN') {
    return 'text-bg-dark'
  }
  if (status === 'VOIDED') {
    return 'text-bg-warning'
  }
  return 'text-bg-light border'
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
  <article class="admin-page admin-campaigns-page">
    <div class="page-title">
      <span class="eyebrow">Admin</span>
      <h1>賞池管理</h1>
      <p>維護賞池主檔、銷售狀態、抽數與公開說明。</p>
    </div>

    <form
      class="admin-toolbar admin-campaign-toolbar"
      role="search"
      @submit.prevent="submitFilters"
    >
      <div>
        <label class="form-label" for="adminCampaignKeyword">搜尋</label>
        <input
          id="adminCampaignKeyword"
          v-model.trim="keyword"
          class="form-control"
          placeholder="slug / 標題 / 品牌"
        />
      </div>
      <div>
        <label class="form-label" for="adminCampaignStatus">狀態</label>
        <select id="adminCampaignStatus" v-model="statusFilter" class="form-select">
          <option v-for="option in statusFilters" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
      </div>
      <div>
        <label class="form-label" for="adminCampaignSort">排序</label>
        <select id="adminCampaignSort" v-model="sortFilter" class="form-select">
          <option v-for="option in sortOptions" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
      </div>
      <button class="btn btn-outline-dark" type="submit">
        <i class="bi bi-search me-1" aria-hidden="true"></i>
        搜尋
      </button>
      <button class="btn btn-danger" type="button" @click="startNew()">
        <i class="bi bi-plus-lg me-1" aria-hidden="true"></i>
        新增賞池
      </button>
      <div class="admin-summary-pill">
        <strong>{{ campaigns.length }}</strong>
        <span>目前清單</span>
      </div>
      <div class="admin-summary-pill">
        <strong>{{ liveCount }}</strong>
        <span>開抽中</span>
      </div>
      <div class="admin-summary-pill">
        <strong>{{ draftCount }}</strong>
        <span>草稿</span>
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

    <div class="admin-campaigns-layout">
      <section class="status-panel admin-campaign-list-panel">
        <div class="section-heading">
          <div>
            <span class="eyebrow">Campaigns</span>
            <h2>賞池列表</h2>
          </div>
        </div>

        <div v-if="loading" class="admin-campaign-list">
          <div v-for="index in 3" :key="index" class="admin-campaign-card">
            <div class="skeleton-line w-50"></div>
            <div class="skeleton-line"></div>
          </div>
        </div>

        <div v-else-if="campaigns.length === 0" class="empty-state">
          <i class="bi bi-box-seam" aria-hidden="true"></i>
          <strong>沒有符合條件的賞池</strong>
          <span>建立第一個賞池後會出現在這裡。</span>
        </div>

        <div v-else class="admin-campaign-list">
          <button
            v-for="campaign in campaigns"
            :key="campaign.id"
            class="admin-campaign-card"
            :class="{ 'admin-campaign-card--active': campaign.id === selectedCampaignId }"
            type="button"
            @click="selectCampaign(campaign)"
          >
            <span class="admin-campaign-card__head">
              <strong>{{ campaign.title }}</strong>
              <span class="badge" :class="statusBadgeClass(campaign.status)">
                {{ campaign.statusLabel }}
              </span>
            </span>
            <span>{{ campaign.slug }}</span>
            <span class="admin-campaign-card__badges">
              <span>{{ campaign.sourceTypeLabel }}｜{{ campaign.pricePerDraw }} LP / 抽</span>
              <span v-if="campaign.sourceType === 'OFFICIAL'" class="badge text-bg-light border">
                {{ campaign.officialLicenseConfirmed ? '授權已確認' : '授權待確認' }}
              </span>
              <span v-if="campaign.ageRestricted" class="badge text-bg-warning">
                {{ campaign.minimumAge }}+
              </span>
            </span>
            <span class="admin-campaign-card__meta">
              <span>剩 {{ campaign.remainingTickets }} / {{ campaign.totalTickets }}</span>
              <span>{{ campaign.prizeCount }} 獎項</span>
            </span>
          </button>
        </div>
      </section>

      <aside class="status-panel admin-campaign-editor">
        <div>
          <span class="eyebrow">{{
            isNewMode ? 'New Campaign' : `Campaign #${selectedCampaign?.id}`
          }}</span>
          <h2>{{ isNewMode ? '新增賞池' : selectedCampaign?.title || '編輯賞池' }}</h2>
          <p v-if="!isNewMode && selectedCampaign">
            已售 {{ selectedCampaign.soldTickets }} 抽｜更新
            {{ formatTime(selectedCampaign.updatedAt) }}
          </p>
          <div
            v-if="campaignSensitiveFieldsLocked"
            class="admin-sensitive-lock-alert"
            role="status"
          >
            <i class="bi bi-lock-fill" aria-hidden="true"></i>
            <div>
              <strong>敏感欄位已鎖定</strong>
              <span>價格、總籤數、狀態、公平性、最後賞與獎項需建立修正版本。</span>
            </div>
          </div>

          <div v-if="canManagePrizes" class="admin-campaign-command-panel">
            <div>
              <span class="eyebrow">Publish</span>
              <strong>{{ selectedCampaign.statusLabel }}</strong>
              <small>{{ publishReadinessText }}</small>
            </div>
            <div class="admin-campaign-command-panel__actions">
              <button
                class="btn btn-danger btn-sm"
                type="button"
                :disabled="changingCampaignStatus || !canPublishCampaign"
                @click="publishCampaign"
              >
                <i class="bi bi-broadcast me-1" aria-hidden="true"></i>
                {{ changingCampaignStatus ? '處理中' : '發布上架' }}
              </button>
              <button
                class="btn btn-outline-dark btn-sm"
                type="button"
                :disabled="changingCampaignStatus || !canPauseCampaign"
                @click="pauseCampaign"
              >
                <i class="bi bi-pause-circle me-1" aria-hidden="true"></i>
                暫停下架
              </button>
            </div>
          </div>

          <section
            v-if="canManagePrizes"
            class="admin-publish-checklist"
            aria-label="發布前 checklist"
          >
            <div class="admin-publish-checklist__head">
              <div>
                <span class="eyebrow">Checklist</span>
                <h3>發布前檢查</h3>
              </div>
              <div class="admin-publish-checklist__head-actions">
                <button
                  class="btn btn-outline-dark btn-sm"
                  type="button"
                  :disabled="dryRunningCampaign || !hasDrawableTickets"
                  title="模擬抽取可抽 ticket，不會扣除庫存或建立訂單"
                  @click="dryRunCampaign"
                >
                  <i class="bi bi-play-circle me-1" aria-hidden="true"></i>
                  {{ dryRunningCampaign ? '測試中' : 'Dry Run' }}
                </button>
                <span
                  class="badge"
                  :class="publishChecklistComplete ? 'text-bg-success' : 'text-bg-warning'"
                >
                  {{ publishChecklistPassedCount }} / {{ publishChecklistItems.length }}
                </span>
              </div>
            </div>
            <div class="admin-publish-checklist__items">
              <div
                v-for="item in publishChecklistItems"
                :key="item.key"
                class="admin-publish-checklist__item"
                :class="{ 'admin-publish-checklist__item--done': item.done }"
              >
                <i
                  class="bi"
                  :class="item.done ? 'bi-check-circle-fill' : 'bi-exclamation-circle-fill'"
                  aria-hidden="true"
                ></i>
                <div>
                  <strong>{{ item.label }}</strong>
                  <span>{{ item.detail }}</span>
                </div>
              </div>
            </div>
            <section
              v-if="publishProbabilityPreviewItems.length > 0"
              class="admin-publish-probability-preview"
              aria-label="機率預覽"
            >
              <div class="admin-publish-probability-preview__head">
                <strong>機率預覽</strong>
                <span>合計 {{ publishProbabilityTotalText }}</span>
              </div>
              <div
                v-for="item in publishProbabilityPreviewItems"
                :key="item.id"
                class="admin-publish-probability-preview__row"
              >
                <span>{{ item.label }}</span>
                <strong>{{ item.probabilityText }}</strong>
                <small>{{ item.quantity }} 張</small>
              </div>
            </section>
            <section v-if="dryRunResult" class="admin-dry-run-preview" aria-label="Dry Run 結果">
              <div class="admin-dry-run-preview__head">
                <strong>Dry Run 結果</strong>
                <span>
                  可抽 {{ dryRunResult.availableTickets }} / {{ dryRunResult.totalTickets }}
                </span>
              </div>
              <div
                v-for="result in dryRunResult.results"
                :key="result.serialNumber"
                class="admin-dry-run-preview__row"
              >
                <span>{{ result.serialNumber }}</span>
                <strong>{{ result.rank }}｜{{ result.prizeName }}</strong>
              </div>
            </section>
          </section>

          <form class="admin-campaign-form" @submit.prevent="submitCampaign">
            <fieldset class="admin-campaign-form-section">
              <legend>
                <i class="bi bi-card-heading" aria-hidden="true"></i>
                <span>基本資料</span>
              </legend>
              <div class="admin-campaign-form-grid">
                <div>
                  <label class="form-label" for="campaignSlug">Slug</label>
                  <input
                    id="campaignSlug"
                    v-model.trim="form.slug"
                    class="form-control"
                    pattern="[a-z0-9]+(-[a-z0-9]+)*"
                    :disabled="campaignSensitiveFieldsLocked"
                    required
                  />
                </div>
                <div>
                  <label class="form-label" for="campaignStatus">狀態</label>
                  <select
                    id="campaignStatus"
                    v-model="form.status"
                    class="form-select"
                    :disabled="campaignSensitiveFieldsLocked"
                  >
                    <option
                      v-for="option in campaignStatuses"
                      :key="option.value"
                      :value="option.value"
                    >
                      {{ option.label }}
                    </option>
                  </select>
                </div>
                <div class="admin-campaign-form-grid__wide">
                  <label class="form-label" for="campaignTitle">標題</label>
                  <input
                    id="campaignTitle"
                    v-model.trim="form.title"
                    class="form-control"
                    required
                  />
                </div>
                <div class="admin-campaign-form-grid__wide">
                  <label class="form-label" for="campaignSubtitle">副標題</label>
                  <input id="campaignSubtitle" v-model.trim="form.subtitle" class="form-control" />
                </div>
              </div>
            </fieldset>

            <fieldset class="admin-campaign-form-section">
              <legend>
                <i class="bi bi-box-seam" aria-hidden="true"></i>
                <span>商品與銷售</span>
              </legend>
              <div class="admin-campaign-form-grid">
                <div>
                  <label class="form-label" for="campaignSourceType">來源</label>
                  <select
                    id="campaignSourceType"
                    v-model="form.sourceType"
                    class="form-select"
                    :disabled="campaignSensitiveFieldsLocked"
                  >
                    <option v-for="option in sourceTypes" :key="option.value" :value="option.value">
                      {{ option.label }}
                    </option>
                  </select>
                </div>
                <div>
                  <label class="form-label" for="campaignBrandName">品牌</label>
                  <input
                    id="campaignBrandName"
                    v-model.trim="form.brandName"
                    class="form-control"
                  />
                </div>
                <div>
                  <label class="form-label" for="campaignIpName">IP 名稱</label>
                  <input id="campaignIpName" v-model.trim="form.ipName" class="form-control" />
                </div>
                <div class="form-check admin-campaign-form-check">
                  <input
                    id="campaignCommercialUseConfirmed"
                    v-model="form.commercialUseConfirmed"
                    class="form-check-input"
                    type="checkbox"
                    :disabled="campaignSensitiveFieldsLocked"
                  />
                  <label class="form-check-label" for="campaignCommercialUseConfirmed">
                    商品圖與素材可商用
                  </label>
                </div>
                <div
                  v-if="form.sourceType === 'OFFICIAL'"
                  class="form-check admin-campaign-form-check"
                >
                  <input
                    id="campaignOfficialLicenseConfirmed"
                    v-model="form.officialLicenseConfirmed"
                    class="form-check-input"
                    type="checkbox"
                    :disabled="campaignSensitiveFieldsLocked"
                  />
                  <label class="form-check-label" for="campaignOfficialLicenseConfirmed">
                    已確認官方授權或進貨佐證
                  </label>
                </div>
                <div class="form-check admin-campaign-form-check">
                  <input
                    id="campaignAgeRestricted"
                    v-model="form.ageRestricted"
                    class="form-check-input"
                    type="checkbox"
                    :disabled="campaignSensitiveFieldsLocked"
                  />
                  <label class="form-check-label" for="campaignAgeRestricted">啟用年齡限制</label>
                </div>
                <div v-if="form.ageRestricted">
                  <label class="form-label" for="campaignMinimumAge">最低年齡</label>
                  <input
                    id="campaignMinimumAge"
                    v-model.number="form.minimumAge"
                    class="form-control"
                    min="1"
                    type="number"
                    :disabled="campaignSensitiveFieldsLocked"
                    required
                  />
                </div>
                <div v-if="form.ageRestricted" class="admin-campaign-form-grid__wide">
                  <label class="form-label" for="campaignAgeVerificationNote">年齡驗證方式</label>
                  <textarea
                    id="campaignAgeVerificationNote"
                    v-model.trim="form.ageVerificationNote"
                    class="form-control"
                    rows="2"
                    required
                  ></textarea>
                </div>
                <div class="admin-campaign-form-grid__wide">
                  <label class="form-label" for="campaignRightsNotice">公開來源與授權說明</label>
                  <textarea
                    id="campaignRightsNotice"
                    v-model.trim="form.rightsNotice"
                    class="form-control"
                    rows="2"
                  ></textarea>
                </div>
                <div>
                  <label class="form-label" for="campaignPrice">單抽 LP</label>
                  <input
                    id="campaignPrice"
                    v-model.number="form.pricePerDraw"
                    class="form-control"
                    min="1"
                    type="number"
                    :disabled="campaignSensitiveFieldsLocked"
                    required
                  />
                </div>
                <div>
                  <label class="form-label" for="campaignTotalTickets">總抽數</label>
                  <input
                    id="campaignTotalTickets"
                    v-model.number="form.totalTickets"
                    class="form-control"
                    :min="soldTickets"
                    type="number"
                    :disabled="campaignSensitiveFieldsLocked"
                    required
                  />
                </div>
                <div>
                  <label class="form-label" for="campaignSalesStart">開始時間</label>
                  <input
                    id="campaignSalesStart"
                    v-model="form.salesStartAt"
                    class="form-control"
                    type="datetime-local"
                    :disabled="campaignSensitiveFieldsLocked"
                  />
                </div>
                <div>
                  <label class="form-label" for="campaignSalesEnd">結束時間</label>
                  <input
                    id="campaignSalesEnd"
                    v-model="form.salesEndAt"
                    class="form-control"
                    type="datetime-local"
                  />
                </div>
              </div>
            </fieldset>

            <fieldset class="admin-campaign-form-section">
              <legend>
                <i class="bi bi-image" aria-hidden="true"></i>
                <span>媒體與說明</span>
              </legend>
              <div class="admin-campaign-form-grid">
                <div class="admin-campaign-form-grid__wide">
                  <AdminImageUploadField
                    v-model.trim="form.coverImageUrl"
                    input-id="campaignCoverImage"
                    label="封面圖 URL"
                    @uploaded="handleCampaignImageUploaded('封面圖', $event)"
                    @upload-error="handleImageUploadError"
                  />
                </div>
                <div class="admin-campaign-form-grid__wide">
                  <AdminImageUploadField
                    v-model.trim="form.bannerImageUrl"
                    input-id="campaignBannerImage"
                    label="Banner 圖 URL"
                    @uploaded="handleCampaignImageUploaded('Banner 圖', $event)"
                    @upload-error="handleImageUploadError"
                  />
                </div>
                <div class="admin-campaign-form-grid__wide">
                  <label class="form-label" for="campaignDescription">描述</label>
                  <textarea
                    id="campaignDescription"
                    v-model.trim="form.description"
                    class="form-control"
                    rows="3"
                    required
                  ></textarea>
                </div>
              </div>
            </fieldset>

            <fieldset class="admin-campaign-form-section">
              <legend>
                <i class="bi bi-truck" aria-hidden="true"></i>
                <span>出貨與售後</span>
              </legend>
              <div class="admin-campaign-form-grid">
                <div class="admin-campaign-form-grid__wide">
                  <label class="form-label" for="campaignShippingNote">出貨說明</label>
                  <textarea
                    id="campaignShippingNote"
                    v-model.trim="form.shippingNote"
                    class="form-control"
                    rows="2"
                    required
                  ></textarea>
                </div>
                <div class="admin-campaign-form-grid__wide">
                  <label class="form-label" for="campaignReturnPolicy">退換貨說明</label>
                  <textarea
                    id="campaignReturnPolicy"
                    v-model.trim="form.returnPolicyNote"
                    class="form-control"
                    rows="2"
                    required
                  ></textarea>
                </div>
              </div>
            </fieldset>

            <fieldset class="admin-campaign-form-section">
              <legend>
                <i class="bi bi-shield-check" aria-hidden="true"></i>
                <span>公平性與最後賞</span>
              </legend>
              <div class="admin-campaign-form-grid">
                <div>
                  <label class="form-label" for="campaignFairnessMode">公平性</label>
                  <select
                    id="campaignFairnessMode"
                    v-model="form.fairnessMode"
                    class="form-select"
                    :disabled="campaignSensitiveFieldsLocked"
                  >
                    <option
                      v-for="option in fairnessModes"
                      :key="option.value"
                      :value="option.value"
                    >
                      {{ option.label }}
                    </option>
                  </select>
                </div>
                <div class="form-check admin-campaign-form-check">
                  <input
                    id="campaignHasLastPrize"
                    v-model="form.hasLastPrize"
                    class="form-check-input"
                    type="checkbox"
                    :disabled="campaignSensitiveFieldsLocked"
                  />
                  <label class="form-check-label" for="campaignHasLastPrize">啟用最後賞</label>
                </div>
                <div v-if="form.hasLastPrize" class="admin-campaign-form-grid__wide">
                  <label class="form-label" for="campaignLastPrizeRule">最後賞規則</label>
                  <textarea
                    id="campaignLastPrizeRule"
                    v-model.trim="form.lastPrizeRule"
                    class="form-control"
                    rows="2"
                    :disabled="campaignSensitiveFieldsLocked"
                    required
                  ></textarea>
                </div>
                <div class="admin-campaign-form-grid__wide">
                  <label class="form-label" for="campaignSeedHash">Seed Hash</label>
                  <input
                    id="campaignSeedHash"
                    v-model.trim="form.seedHash"
                    class="form-control"
                    :disabled="campaignSensitiveFieldsLocked"
                  />
                </div>
              </div>
            </fieldset>

            <button class="btn btn-danger btn-lg" type="submit" :disabled="saving">
              <i class="bi bi-save me-2" aria-hidden="true"></i>
              {{ saving ? '儲存中' : isNewMode ? '建立賞池' : '儲存賞池' }}
            </button>
          </form>

          <section v-if="canManagePrizes" class="admin-prize-manager" aria-label="獎項與票券管理">
            <div class="section-heading">
              <div>
                <span class="eyebrow">Prizes & Tickets</span>
                <h3>獎項與票券</h3>
              </div>
              <div class="admin-prize-actions">
                <RouterLink
                  class="btn btn-outline-dark btn-sm"
                  :to="`/admin/campaigns/${selectedCampaign.id}/tickets`"
                >
                  <i class="bi bi-ticket-perforated me-1" aria-hidden="true"></i>
                  票券頁
                </RouterLink>
                <button
                  class="btn btn-outline-dark btn-sm"
                  type="button"
                  :disabled="prizeEditingLocked"
                  @click="startNewPrize"
                >
                  <i class="bi bi-plus-lg me-1" aria-hidden="true"></i>
                  新增獎項
                </button>
                <button
                  class="btn btn-danger btn-sm"
                  type="button"
                  :disabled="generatingTickets || loadingPrizes || prizeEditingLocked"
                  @click="generateTickets"
                >
                  <i class="bi bi-magic me-1" aria-hidden="true"></i>
                  {{ generatingTickets ? '生成中' : '生成 Ticket' }}
                </button>
              </div>
            </div>

            <div class="admin-prize-summary-grid">
              <div class="admin-summary-pill">
                <strong>{{ prizeOverview.prizes.length }}</strong>
                <span>獎項</span>
              </div>
              <div class="admin-summary-pill">
                <strong>{{ prizeOverview.totalPrizeQuantity }}</strong>
                <span>普通總籤數</span>
              </div>
              <div class="admin-summary-pill">
                <strong>{{ prizeOverview.remainingPrizeQuantity }}</strong>
                <span>獎項剩餘</span>
              </div>
              <div class="admin-summary-pill">
                <strong>{{ prizeOverview.generatedTickets }}</strong>
                <span>已生成 Ticket</span>
              </div>
              <div class="admin-summary-pill">
                <strong>{{ prizeOverview.availableTickets }}</strong>
                <span>可抽 Ticket</span>
              </div>
            </div>

            <section v-if="isTicketRoute" class="admin-ticket-panel" aria-label="完整 Ticket 清單">
              <form
                class="admin-toolbar admin-ticket-toolbar"
                @submit.prevent="submitTicketFilters"
              >
                <div>
                  <label class="form-label" for="adminTicketKeyword">搜尋 Ticket</label>
                  <input
                    id="adminTicketKeyword"
                    v-model="ticketKeyword"
                    class="form-control"
                    type="search"
                    placeholder="序號、獎項或 ID"
                  />
                </div>
                <div>
                  <label class="form-label" for="adminTicketStatus">狀態</label>
                  <select id="adminTicketStatus" v-model="ticketStatusFilter" class="form-select">
                    <option
                      v-for="option in ticketStatusOptions"
                      :key="option.value"
                      :value="option.value"
                    >
                      {{ option.label }}
                    </option>
                  </select>
                </div>
                <button class="btn btn-dark" type="submit" :disabled="loadingTickets">
                  <i class="bi bi-funnel" aria-hidden="true"></i>
                  篩選
                </button>
                <RouterLink
                  class="btn btn-outline-dark"
                  :to="`/admin/campaigns/${selectedCampaign.id}`"
                >
                  <i class="bi bi-arrow-left" aria-hidden="true"></i>
                  回主檔
                </RouterLink>
                <div class="admin-summary-pill">
                  <strong>{{ tickets.length }}</strong>
                  <span>目前清單</span>
                </div>
                <div class="admin-summary-pill">
                  <strong>{{ availableTicketCount }}</strong>
                  <span>可抽</span>
                </div>
                <div class="admin-summary-pill">
                  <strong>{{ drawnTicketCount }}</strong>
                  <span>已抽出</span>
                </div>
              </form>

              <div v-if="loadingTickets" class="admin-ticket-list">
                <div v-for="index in 4" :key="index" class="admin-ticket-card">
                  <div class="skeleton-line w-50"></div>
                  <div class="skeleton-line"></div>
                </div>
              </div>

              <div v-else-if="tickets.length === 0" class="empty-state empty-state--compact">
                <i class="bi bi-ticket-perforated" aria-hidden="true"></i>
                <strong>沒有符合條件的 Ticket</strong>
              </div>

              <div v-else class="admin-ticket-list">
                <article v-for="ticket in tickets" :key="ticket.id" class="admin-ticket-card">
                  <div class="admin-ticket-card__main">
                    <strong>{{ ticket.serialNumber }}</strong>
                    <span>{{ ticket.prizeRank }}｜{{ ticket.prizeName }}</span>
                  </div>
                  <div class="admin-ticket-card__meta">
                    <span class="badge" :class="ticketStatusBadgeClass(ticket.status)">
                      {{ ticket.statusLabel }}
                    </span>
                    <span>Ticket #{{ ticket.id }}</span>
                    <span v-if="ticket.drawId">Draw #{{ ticket.drawId }}</span>
                    <span v-if="ticket.drawnByUserId">
                      {{ ticket.drawnByDisplayName }} #{{ ticket.drawnByUserId }}
                    </span>
                    <span v-if="ticket.drawnByEmail">{{ ticket.drawnByEmail }}</span>
                    <time v-if="ticket.drawnAt">抽出 {{ formatTime(ticket.drawnAt) }}</time>
                  </div>
                </article>
              </div>
            </section>

            <div v-if="loadingPrizes" class="admin-prize-layout">
              <div class="admin-prize-list">
                <div v-for="index in 2" :key="index" class="admin-prize-card">
                  <div class="skeleton-line w-50"></div>
                  <div class="skeleton-line"></div>
                </div>
              </div>
              <div class="admin-prize-form">
                <div class="skeleton-line w-50"></div>
                <div class="skeleton-line"></div>
                <div class="skeleton-line"></div>
              </div>
            </div>

            <div v-else class="admin-prize-layout">
              <div class="admin-prize-list">
                <button
                  v-for="prize in prizeOverview.prizes"
                  :key="prize.id"
                  class="admin-prize-card"
                  :class="{ 'admin-prize-card--active': prize.id === selectedPrizeId }"
                  type="button"
                  @click="selectPrize(prize)"
                >
                  <span class="admin-prize-card__head">
                    <strong>{{ prize.rank }}｜{{ prize.name }}</strong>
                    <span v-if="prize.lastPrize" class="badge text-bg-dark">最後賞</span>
                  </span>
                  <span class="admin-prize-card__meta">
                    <span>數量 {{ prize.remainingQuantity }} / {{ prize.originalQuantity }}</span>
                    <span>Ticket {{ prize.availableTickets }} / {{ prize.generatedTickets }}</span>
                  </span>
                  <small>排序 {{ prize.sortOrder }}</small>
                </button>

                <div v-if="prizeOverview.prizes.length === 0" class="empty-state">
                  <i class="bi bi-gift" aria-hidden="true"></i>
                  <strong>尚未建立獎項</strong>
                  <span>先新增至少一個非最後賞獎項，再生成 tickets。</span>
                </div>
              </div>

              <form class="admin-prize-form" @submit.prevent="submitPrize">
                <div>
                  <span class="eyebrow">{{
                    selectedPrize ? `Prize #${selectedPrize.id}` : 'New Prize'
                  }}</span>
                  <h4>{{ selectedPrize ? '編輯獎項' : '新增獎項' }}</h4>
                </div>
                <div v-if="prizeEditingLocked" class="admin-prize-lock-note" role="status">
                  <i class="bi bi-shield-lock" aria-hidden="true"></i>
                  <span>已開抽、暫停或完抽後，獎項與 ticket 需以修正版本處理。</span>
                </div>

                <div
                  class="admin-prize-quantity-preview"
                  :class="prizeQuantityPreviewClass"
                  role="status"
                >
                  <i class="bi bi-calculator" aria-hidden="true"></i>
                  <div>
                    <strong>
                      儲存後普通總籤數 {{ draftPrizeTicketQuantity }} /
                      {{ selectedCampaign.totalTickets }}
                    </strong>
                    <span>{{ prizeQuantityPreviewText }}</span>
                  </div>
                </div>

                <div class="admin-prize-form-grid">
                  <div>
                    <label class="form-label" for="prizeRank">獎項等級</label>
                    <input
                      id="prizeRank"
                      v-model.trim="prizeForm.rank"
                      class="form-control"
                      :disabled="prizeEditingLocked"
                      required
                    />
                  </div>
                  <div>
                    <label class="form-label" for="prizeSortOrder">排序</label>
                    <input
                      id="prizeSortOrder"
                      v-model.number="prizeForm.sortOrder"
                      class="form-control"
                      min="1"
                      type="number"
                      :disabled="prizeEditingLocked"
                      required
                    />
                  </div>
                  <div class="admin-prize-form-grid__wide">
                    <label class="form-label" for="prizeName">獎項名稱</label>
                    <input
                      id="prizeName"
                      v-model.trim="prizeForm.name"
                      class="form-control"
                      :disabled="prizeEditingLocked"
                      required
                    />
                  </div>
                  <div>
                    <label class="form-label" for="prizeOriginalQuantity">原始數量</label>
                    <input
                      id="prizeOriginalQuantity"
                      v-model.number="prizeForm.originalQuantity"
                      class="form-control"
                      min="0"
                      type="number"
                      :disabled="prizeEditingLocked"
                      required
                    />
                  </div>
                  <div>
                    <AdminImageUploadField
                      v-model.trim="prizeForm.imageUrl"
                      input-id="prizeImageUrl"
                      label="圖片 URL"
                      :disabled="prizeEditingLocked"
                      @uploaded="handleCampaignImageUploaded('獎項圖片', $event)"
                      @upload-error="handleImageUploadError"
                    />
                  </div>
                  <div class="form-check admin-prize-form-grid__wide">
                    <input
                      id="prizeLastPrize"
                      v-model="prizeForm.lastPrize"
                      class="form-check-input"
                      type="checkbox"
                      :disabled="prizeEditingLocked"
                    />
                    <label class="form-check-label" for="prizeLastPrize">此獎項是最後賞</label>
                  </div>
                  <div class="admin-prize-form-grid__wide">
                    <label class="form-label" for="prizeDescription">獎項描述</label>
                    <textarea
                      id="prizeDescription"
                      v-model.trim="prizeForm.description"
                      class="form-control"
                      rows="3"
                      :disabled="prizeEditingLocked"
                    ></textarea>
                  </div>
                </div>

                <button
                  class="btn btn-dark"
                  type="submit"
                  :disabled="savingPrize || prizeEditingLocked"
                >
                  <i class="bi bi-save me-2" aria-hidden="true"></i>
                  {{ savingPrize ? '儲存中' : selectedPrize ? '儲存獎項' : '建立獎項' }}
                </button>
              </form>
            </div>
          </section>
        </div>
      </aside>
    </div>
  </article>
</template>

<style scoped>
.admin-ticket-panel {
  display: grid;
  gap: 0.75rem;
  margin-top: 0.85rem;
}

.admin-ticket-list {
  display: grid;
  gap: 0.55rem;
}

.admin-ticket-card {
  display: flex;
  gap: 0.75rem;
  justify-content: space-between;
  align-items: flex-start;
  border: 1px solid var(--bs-border-color, #e3e3e8);
  border-radius: 0.45rem;
  padding: 0.65rem 0.75rem;
  background: var(--bs-body-bg, #fff);
}

.admin-ticket-card__main,
.admin-ticket-card__meta {
  min-width: 0;
}

.admin-ticket-card__main {
  display: grid;
  gap: 0.15rem;
}

.admin-ticket-card__main span,
.admin-ticket-card__meta {
  color: var(--lb-muted, #6c757d);
  font-size: 0.82rem;
}

.admin-ticket-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 0.45rem;
  justify-content: flex-end;
}

@media (max-width: 720px) {
  .admin-ticket-card {
    display: grid;
  }

  .admin-ticket-card__meta {
    justify-content: flex-start;
  }
}
</style>

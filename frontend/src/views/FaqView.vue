<script setup>
import { computed, ref } from 'vue'

const keyword = ref('')
const activeCategory = ref('全部')

const categories = [
  { label: '全部', icon: 'bi-grid' },
  { label: '帳號', icon: 'bi-person-circle' },
  { label: '點數', icon: 'bi-wallet2' },
  { label: '抽賞', icon: 'bi-stars' },
  { label: '戰利品', icon: 'bi-box-seam' },
  { label: '出貨', icon: 'bi-truck' },
  { label: '客服', icon: 'bi-headset' },
]

const faqs = [
  {
    category: '帳號',
    icon: 'bi-person-check',
    question: '如何註冊與登入 LuckyBox？',
    answer:
      '使用 Email、顯示名稱與密碼即可註冊；登入後可進入會員中心查看錢包、戰利品、優惠券、訂單與出貨紀錄。',
    links: [
      { label: '註冊會員', to: '/register', icon: 'bi-person-plus' },
      { label: '會員登入', to: '/login', icon: 'bi-box-arrow-in-right' },
    ],
  },
  {
    category: '帳號',
    icon: 'bi-person-lines-fill',
    question: '顯示名稱、手機或基本資料可以修改嗎？',
    answer:
      '登入後可在個人資料頁更新顯示名稱與手機；Email、角色、會員狀態與 VIP 等級會依系統紀錄顯示。',
    links: [{ label: '個人資料', to: '/account/profile', icon: 'bi-pencil-square' }],
  },
  {
    category: '點數',
    icon: 'bi-coin',
    question: '現金點與贈點有什麼差異？',
    answer:
      '現金點來自儲值或付款入點，贈點來自活動、優惠券或客服補償；抽賞會依系統規則扣抵並保留錢包流水。',
    links: [{ label: '點數錢包', to: '/account/wallet', icon: 'bi-wallet2' }],
  },
  {
    category: '點數',
    icon: 'bi-ticket-perforated',
    question: '優惠券要在哪裡查看與使用？',
    answer:
      '可在優惠券頁查看目前可用券。折扣券可於賞池抽賞時輸入代碼，贈點券可直接領取入錢包，免運券可於申請出貨時折抵運費。',
    links: [{ label: '優惠券', to: '/account/coupons', icon: 'bi-ticket-detailed' }],
  },
  {
    category: '抽賞',
    icon: 'bi-dice-5',
    question: '抽賞後在哪裡看結果？',
    answer:
      '抽賞完成後可在付款/抽賞訂單查看實付 LP、折抵 LP、中獎結果與品項明細；公開抽況頁會以遮罩暱稱顯示近期出賞紀錄。',
    links: [
      { label: '訂單紀錄', to: '/account/orders', icon: 'bi-receipt' },
      { label: '抽況榜單', to: '/leaderboard', icon: 'bi-broadcast' },
    ],
  },
  {
    category: '抽賞',
    icon: 'bi-shield-check',
    question: '平台如何揭露抽賞狀態？',
    answer:
      '賞池會顯示價格、剩餘籤數、獎項配置與公開狀態；熱門排行與近期抽出紀錄可協助使用者快速掌握目前抽況。',
    links: [{ label: '抽況榜單', to: '/leaderboard', icon: 'bi-graph-up-arrow' }],
  },
  {
    category: '戰利品',
    icon: 'bi-box2-heart',
    question: '抽到的商品會放在哪裡？',
    answer: '中獎品項會進入戰利品盒。會員可在戰利品盒查看保管中的商品，勾選多件後合併申請出貨。',
    links: [{ label: '戰利品盒', to: '/account/prizes', icon: 'bi-box-seam' }],
  },
  {
    category: '出貨',
    icon: 'bi-truck',
    question: '可以合併出貨嗎？運費怎麼算？',
    answer:
      '同一會員可將多件可出貨戰利品合併成一張出貨單。目前固定運費為 80 LP，若有符合資格的免運券可於申請出貨時折抵。',
    links: [
      { label: '出貨政策', to: '/shipping-policy', icon: 'bi-file-earmark-text' },
      { label: '戰利品盒', to: '/account/prizes', icon: 'bi-box-seam' },
    ],
  },
  {
    category: '出貨',
    icon: 'bi-search',
    question: '出貨後如何查物流與追蹤碼？',
    answer:
      '出貨單建立後可在出貨紀錄查看狀態、物流商、追蹤碼、收件資訊與品項列表；已出貨與已送達時間也會在詳情頁呈現。',
    links: [{ label: '出貨紀錄', to: '/account/shipments', icon: 'bi-list-check' }],
  },
  {
    category: '客服',
    icon: 'bi-exclamation-diamond',
    question: '收到破損、缺件或錯品怎麼辦？',
    answer:
      '請於到貨後 7 日內保留外箱、包材、商品照片與出貨單號，寄信至 support@luckybox.local；客服會依出貨紀錄與物流狀態協助處理。',
    links: [{ label: '出貨政策', to: '/shipping-policy', icon: 'bi-life-preserver' }],
  },
  {
    category: '客服',
    icon: 'bi-lock',
    question: '如何申請資料匯出或刪除帳號？',
    answer:
      '請以會員 Email 寄信至 support@luckybox.local，註明申請項目與帳號資訊；若仍有未完成交易、出貨或爭議，會先完成查核。',
    links: [{ label: '隱私權政策', to: '/privacy', icon: 'bi-shield-lock' }],
  },
]

const supportItems = [
  { label: '客服信箱', value: 'support@luckybox.local' },
  { label: '回覆時間', value: '1 個工作天內初步回覆' },
  { label: '處理時段', value: '週一至週五 10:00-18:00' },
]

const normalizeText = (value) => value.toString().trim().toLowerCase()

const filteredFaqs = computed(() => {
  const query = normalizeText(keyword.value)
  return faqs.filter((item) => {
    const inCategory = activeCategory.value === '全部' || item.category === activeCategory.value
    const searchable = normalizeText(
      [item.category, item.question, item.answer, ...item.links.map((link) => link.label)].join(
        ' ',
      ),
    )
    return inCategory && (!query || searchable.includes(query))
  })
})

const categoryOptions = computed(() =>
  categories.map((category) => ({
    ...category,
    count:
      category.label === '全部'
        ? faqs.length
        : faqs.filter((item) => item.category === category.label).length,
  })),
)

const clearSearch = () => {
  keyword.value = ''
  activeCategory.value = '全部'
}
</script>

<template>
  <main class="faq-page">
    <section class="container content-section">
      <div class="page-title faq-title">
        <span class="eyebrow">Support</span>
        <h1>常見問題</h1>
        <p>查詢 LuckyBox 帳號、點數、抽賞、戰利品、出貨與客服處理方式。</p>
      </div>

      <section class="status-panel faq-toolbar" aria-label="常見問題篩選">
        <div class="faq-search">
          <i class="bi bi-search" aria-hidden="true"></i>
          <input
            v-model.trim="keyword"
            type="search"
            class="form-control"
            placeholder="搜尋問題、答案或頁面"
            aria-label="搜尋常見問題"
          />
          <button
            v-if="keyword"
            type="button"
            class="btn btn-sm btn-outline-dark"
            @click="keyword = ''"
          >
            <i class="bi bi-x-lg" aria-hidden="true"></i>
            清除
          </button>
        </div>

        <div class="faq-category-list" role="tablist" aria-label="問題分類">
          <button
            v-for="category in categoryOptions"
            :key="category.label"
            type="button"
            class="faq-category"
            :class="{ 'faq-category--active': activeCategory === category.label }"
            @click="activeCategory = category.label"
          >
            <i :class="`bi ${category.icon}`" aria-hidden="true"></i>
            <span>{{ category.label }}</span>
            <strong>{{ category.count }}</strong>
          </button>
        </div>
      </section>

      <div class="faq-layout">
        <section class="faq-list" aria-live="polite">
          <article v-for="item in filteredFaqs" :key="item.question" class="faq-item">
            <header class="faq-item__head">
              <span class="faq-item__icon">
                <i :class="`bi ${item.icon}`" aria-hidden="true"></i>
              </span>
              <div>
                <small>{{ item.category }}</small>
                <h2>{{ item.question }}</h2>
              </div>
            </header>
            <p>{{ item.answer }}</p>
            <div v-if="item.links.length" class="faq-item__links">
              <RouterLink v-for="link in item.links" :key="link.to + link.label" :to="link.to">
                <i :class="`bi ${link.icon}`" aria-hidden="true"></i>
                {{ link.label }}
              </RouterLink>
            </div>
          </article>

          <section v-if="!filteredFaqs.length" class="status-panel faq-empty">
            <i class="bi bi-search" aria-hidden="true"></i>
            <h2>沒有符合的問題</h2>
            <p>換個關鍵字，或直接透過客服信箱聯繫 LuckyBox。</p>
            <button type="button" class="btn btn-dark" @click="clearSearch">
              <i class="bi bi-arrow-counterclockwise" aria-hidden="true"></i>
              重設篩選
            </button>
          </section>
        </section>

        <aside class="status-panel faq-contact-panel" aria-label="客服聯絡方式">
          <div>
            <span class="eyebrow">Contact</span>
            <h2>客服入口</h2>
            <p>提供會員 Email、訂單或出貨單號、照片與問題描述，可加快查核。</p>
          </div>

          <dl>
            <div v-for="item in supportItems" :key="item.label">
              <dt>{{ item.label }}</dt>
              <dd>{{ item.value }}</dd>
            </div>
          </dl>

          <div class="faq-contact-panel__links">
            <RouterLink to="/shipping-policy">
              <i class="bi bi-truck" aria-hidden="true"></i>
              出貨政策
            </RouterLink>
            <RouterLink to="/privacy">
              <i class="bi bi-shield-lock" aria-hidden="true"></i>
              隱私權政策
            </RouterLink>
            <RouterLink to="/account/shipments">
              <i class="bi bi-list-check" aria-hidden="true"></i>
              出貨紀錄
            </RouterLink>
          </div>
        </aside>
      </div>
    </section>
  </main>
</template>

<script setup>
const supportEmail = 'support@luckybox.local'
const mailSubject = encodeURIComponent('LuckyBox 客服協助')
const mailBody = encodeURIComponent(
  [
    '請填寫以下資料，客服會依紀錄協助查核：',
    '',
    '會員 Email：',
    '問題類型：',
    '訂單/出貨單/抽賞單號：',
    '問題描述：',
    '附件或截圖：',
  ].join('\n'),
)
const mailtoHref = `mailto:${supportEmail}?subject=${mailSubject}&body=${mailBody}`

const summaryCards = [
  {
    icon: 'bi-envelope',
    title: '客服信箱',
    text: supportEmail,
  },
  {
    icon: 'bi-clock-history',
    title: '初步回覆',
    text: '1 個工作天內',
  },
  {
    icon: 'bi-calendar-week',
    title: '處理時段',
    text: '週一至週五 10:00-18:00',
  },
]

const issueTypes = [
  {
    icon: 'bi-truck',
    title: '出貨、物流、瑕疵',
    text: '適用於出貨進度、追蹤碼、破損、缺件、錯品與退換貨問題。',
    details: ['出貨單號', '商品照片', '外箱與包材照片', '到貨日期'],
    links: [
      { label: '出貨紀錄', to: '/account/shipments', icon: 'bi-list-check' },
      { label: '出貨政策', to: '/shipping-policy', icon: 'bi-file-earmark-text' },
    ],
  },
  {
    icon: 'bi-ticket-perforated',
    title: '抽賞、點數、優惠券',
    text: '適用於扣點、抽賞結果、折扣券、贈點券、免運券與錢包流水問題。',
    details: ['抽賞訂單號', '付款或錢包流水', '優惠券代碼', '問題發生時間'],
    links: [
      { label: '訂單紀錄', to: '/account/orders', icon: 'bi-receipt' },
      { label: '點數錢包', to: '/account/wallet', icon: 'bi-wallet2' },
    ],
  },
  {
    icon: 'bi-person-gear',
    title: '帳號、登入、資料權利',
    text: '適用於登入異常、個人資料、資料匯出、刪除帳號與隱私權申請。',
    details: ['會員 Email', '顯示名稱', '申請項目', '可驗證身分的必要資訊'],
    links: [
      { label: '個人資料', to: '/account/profile', icon: 'bi-person-circle' },
      { label: '隱私權政策', to: '/privacy', icon: 'bi-shield-lock' },
    ],
  },
]

const checklist = [
  '使用註冊會員的 Email 來信。',
  '附上訂單、抽賞或出貨單號。',
  '清楚描述發生時間、操作步驟與錯誤畫面。',
  '出貨或瑕疵問題請保留商品、外箱、包材與照片。',
  '若涉及點數或優惠券，請附上金額、代碼與使用時間。',
]

const quickLinks = [
  { label: '常見問題', to: '/faq', icon: 'bi-question-circle' },
  { label: '會員服務條款', to: '/terms', icon: 'bi-file-earmark-text' },
  { label: '公平性說明', to: '/fairness', icon: 'bi-shield-check' },
]
</script>

<template>
  <main class="contact-page">
    <section class="container content-section">
      <div class="page-title contact-title">
        <span class="eyebrow">Contact</span>
        <h1>聯絡我們</h1>
        <p>提供會員 Email、訂單或出貨單號與問題描述，LuckyBox 客服會依平台紀錄協助查核。</p>
      </div>

      <div class="status-grid contact-summary">
        <div v-for="card in summaryCards" :key="card.title">
          <i :class="`bi ${card.icon}`" aria-hidden="true"></i>
          <strong>{{ card.title }}</strong>
          <span>{{ card.text }}</span>
        </div>
      </div>

      <section class="status-panel contact-hero-panel">
        <div>
          <span class="eyebrow">Email Support</span>
          <h2>寄信給客服</h2>
          <p>
            建議先查看常見問題與相關紀錄；若仍需協助，可直接寄信給客服，信件會帶入基本資料模板。
          </p>
        </div>
        <a class="btn btn-danger" :href="mailtoHref">
          <i class="bi bi-envelope-paper" aria-hidden="true"></i>
          寄信給客服
        </a>
      </section>

      <div class="contact-grid">
        <section
          v-for="item in issueTypes"
          :key="item.title"
          class="status-panel contact-issue-card"
        >
          <span class="contact-issue-card__icon">
            <i :class="`bi ${item.icon}`" aria-hidden="true"></i>
          </span>
          <div>
            <h2>{{ item.title }}</h2>
            <p>{{ item.text }}</p>
          </div>

          <dl class="contact-issue-card__details">
            <div v-for="detail in item.details" :key="detail">
              <dt>
                <i class="bi bi-check2-circle" aria-hidden="true"></i>
              </dt>
              <dd>{{ detail }}</dd>
            </div>
          </dl>

          <div class="contact-issue-card__links">
            <RouterLink v-for="link in item.links" :key="link.to" :to="link.to">
              <i :class="`bi ${link.icon}`" aria-hidden="true"></i>
              {{ link.label }}
            </RouterLink>
          </div>
        </section>
      </div>

      <section class="status-panel contact-checklist-panel">
        <div>
          <span class="eyebrow">Before Sending</span>
          <h2>寄信前資料清單</h2>
          <p>資料越完整，客服越能快速比對抽賞、點數、出貨與 audit log 紀錄。</p>
        </div>

        <ul class="contact-checklist">
          <li v-for="item in checklist" :key="item">
            <i class="bi bi-check2-circle" aria-hidden="true"></i>
            <span>{{ item }}</span>
          </li>
        </ul>
      </section>

      <section class="status-panel contact-links-panel">
        <div>
          <span class="eyebrow">Resources</span>
          <h2>相關說明</h2>
          <p>抽賞規則、平台條款與常見問題可先在公開頁確認。</p>
        </div>

        <div class="contact-links">
          <RouterLink v-for="link in quickLinks" :key="link.to" :to="link.to">
            <i :class="`bi ${link.icon}`" aria-hidden="true"></i>
            {{ link.label }}
          </RouterLink>
        </div>
      </section>
    </section>
  </main>
</template>

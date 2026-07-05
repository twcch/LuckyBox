<script setup>
const summaryCards = [
  {
    icon: 'bi-shield-check',
    title: '必要蒐集',
    text: '註冊、付款、抽賞、出貨與客服所需資料會依功能目的分段蒐集。',
  },
  {
    icon: 'bi-lock',
    title: '用途限定',
    text: '會員資料僅用於帳號、交易、出貨、通知、客服、安全與法遵稽核。',
  },
  {
    icon: 'bi-person-check',
    title: '權利可申請',
    text: '會員可透過客服申請查詢、更正、匯出或刪除帳號相關資料。',
  },
]

const dataCategories = [
  {
    label: '帳號資料',
    value: 'Email、顯示名稱、手機、登入狀態、角色、會員狀態與 VIP 等級。',
  },
  {
    label: '交易資料',
    value: '點數錢包、付款訂單、抽賞訂單、優惠券使用紀錄與 audit log。',
  },
  {
    label: '出貨資料',
    value: '收件人、電話、郵遞區號、縣市、行政區、地址、物流商與追蹤碼。',
  },
  {
    label: '安全資料',
    value: '登入 session、操作時間、錯誤紀錄與必要的系統安全事件。',
  },
]

const purposeItems = [
  '建立與維護會員帳號、登入狀態與身分權限。',
  '處理點數儲值、抽賞扣點、戰利品保管、出貨與退款/補償查核。',
  '提供公告、站內通知、客服回覆、出貨通知與交易紀錄查詢。',
  '防止未授權存取、重複交易、抽賞異常、濫用優惠券與安全風險。',
]

const retentionItems = [
  {
    label: '帳號資料',
    value: '帳號有效期間保存；會員申請刪除後，會先確認是否仍有未完成交易、出貨或爭議案件。',
  },
  {
    label: '交易與抽賞資料',
    value: '為了點數、抽賞公平性、客服爭議與稽核需求，會保留必要交易紀錄與不可竄改的 audit log。',
  },
  {
    label: '出貨快照',
    value: '出貨單會保留申請當下的收件資訊快照，用於物流查詢、瑕疵/錯品處理與客服查核。',
  },
]

const thirdPartyItems = [
  {
    label: '金流服務',
    value: '處理付款授權、付款結果回傳與必要的交易對帳資訊。',
  },
  {
    label: '物流服務',
    value: '交寄商品、產生物流追蹤碼與處理配送狀態查詢。',
  },
  {
    label: '通知服務',
    value: '寄送出貨、帳號、安全或客服通知；若未啟用外部服務，會以站內通知為主。',
  },
  {
    label: '分析服務',
    value: '若未來啟用產品分析，只會用於整體使用狀態與服務改善，避免公開識別個人。',
  },
]

const rights = [
  '查詢或取得自己的帳號資料摘要',
  '更正顯示名稱、手機與收件資料',
  '申請資料匯出',
  '申請刪除帳號或停止使用服務',
]
</script>

<template>
  <main class="privacy-policy-page">
    <section class="container content-section">
      <div class="page-title privacy-policy-title">
        <span class="eyebrow">Privacy</span>
        <h1>隱私權政策</h1>
        <p>LuckyBox 只在提供會員、抽賞、付款、戰利品、出貨與客服服務所需範圍內蒐集與使用資料。</p>
      </div>

      <div class="status-grid privacy-policy-summary">
        <div v-for="card in summaryCards" :key="card.title">
          <i :class="`bi ${card.icon}`" aria-hidden="true"></i>
          <strong>{{ card.title }}</strong>
          <span>{{ card.text }}</span>
        </div>
      </div>

      <div class="privacy-policy-grid">
        <section class="status-panel privacy-policy-panel">
          <span class="eyebrow">Data</span>
          <h2>蒐集的資料類型</h2>
          <dl class="privacy-policy-list">
            <div v-for="item in dataCategories" :key="item.label">
              <dt>{{ item.label }}</dt>
              <dd>{{ item.value }}</dd>
            </div>
          </dl>
        </section>

        <section class="status-panel privacy-policy-panel">
          <span class="eyebrow">Purpose</span>
          <h2>使用目的</h2>
          <ul class="privacy-policy-bullets">
            <li v-for="item in purposeItems" :key="item">{{ item }}</li>
          </ul>
        </section>
      </div>

      <div class="privacy-policy-grid">
        <section class="status-panel privacy-policy-panel">
          <span class="eyebrow">Retention</span>
          <h2>保存期間與方式</h2>
          <dl class="privacy-policy-list">
            <div v-for="item in retentionItems" :key="item.label">
              <dt>{{ item.label }}</dt>
              <dd>{{ item.value }}</dd>
            </div>
          </dl>
        </section>

        <section class="status-panel privacy-policy-panel">
          <span class="eyebrow">Partners</span>
          <h2>第三方服務</h2>
          <dl class="privacy-policy-list">
            <div v-for="item in thirdPartyItems" :key="item.label">
              <dt>{{ item.label }}</dt>
              <dd>{{ item.value }}</dd>
            </div>
          </dl>
        </section>
      </div>

      <section class="status-panel privacy-policy-rights">
        <div>
          <span class="eyebrow">Rights</span>
          <h2>資料權利與申請流程</h2>
          <p>
            請以會員 Email 寄信至 support@luckybox.local，註明申請項目、帳號 Email
            與可驗證身分的必要資訊。若帳號仍有未完成出貨、付款、客服爭議或必要稽核紀錄，刪除作業會在完成查核後處理。
          </p>
        </div>

        <div class="privacy-policy-rights__items">
          <span v-for="right in rights" :key="right">
            <i class="bi bi-check2-circle" aria-hidden="true"></i>
            {{ right }}
          </span>
        </div>
      </section>
    </section>
  </main>
</template>

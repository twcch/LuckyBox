<script setup>
const summaryCards = [
  {
    icon: 'bi-person-check',
    title: '會員帳號',
    text: '會員需使用可聯繫的 Email 與正確資料，並自行保管登入憑證。',
  },
  {
    icon: 'bi-coin',
    title: 'LP 點數',
    text: 'Lucky Point 僅能於平台內抽賞、出貨與活動折抵使用，不提供提領或兌換現金。',
  },
  {
    icon: 'bi-shield-check',
    title: '公平與售後',
    text: '平台保留抽賞紀錄、點數流水、出貨紀錄與必要稽核資料，以處理客服與爭議。',
  },
]

const termsSections = [
  {
    eyebrow: 'Account',
    title: '帳號與使用資格',
    items: [
      '會員註冊時應提供可聯繫的 Email、顯示名稱與必要資料，並維持資料正確。',
      '同一自然人不得以自動化、冒用或不正方式大量建立帳號、規避活動限制或取得不當利益。',
      '未成年人或限制行為能力人使用服務時，應取得法定代理人同意並由代理人負擔監督責任。',
    ],
  },
  {
    eyebrow: 'Lucky Point',
    title: '點數與付款',
    items: [
      'Lucky Point（LP）為平台內服務使用額度，可用於抽賞、出貨費用、優惠券或活動指定用途。',
      'LP 不屬於電子支付帳戶、金融商品或可提領餘額，除法律或平台公告另有規定外，不得兌換現金。',
      '現金點、贈點、優惠券折抵與客服補償會分別紀錄於錢包流水；平台可依活動規則限制使用順序、期限與範圍。',
    ],
  },
  {
    eyebrow: 'Draw',
    title: '抽賞與獎項',
    items: [
      '會員抽賞前應自行確認賞池價格、剩餘籤數、獎項配置、最後賞規則、出貨與退換貨說明。',
      '抽賞完成後會產生抽賞訂單、扣點紀錄與中獎結果；已完成抽賞原則上不得任意取消。',
      '若發生系統異常、庫存資料錯誤或不可抗力事件，平台得暫停賞池、保留紀錄並依個案補償或更正。',
    ],
  },
  {
    eyebrow: 'Prize Box',
    title: '戰利品與出貨',
    items: [
      '中獎商品會進入會員戰利品盒，會員可依平台提供的流程合併多件商品申請出貨。',
      '出貨費用、免運券、出貨批次、物流追蹤、瑕疵/破損/錯品處理依出貨與退換貨政策辦理。',
      '會員應確認收件人、電話與地址正確；出貨單進入已出貨後，地址通常無法再修改。',
    ],
  },
  {
    eyebrow: 'Campaigns',
    title: '優惠券與活動',
    items: [
      '優惠券、贈點、免運券、任務或活動獎勵可能設定使用期限、最低消費、使用次數與會員限制。',
      '優惠券不得轉售、交換現金或以非平台允許方式取得；異常使用時平台得撤銷優惠或限制帳號。',
      '活動內容若有明顯錯誤、系統異常或不可抗力，平台得於合理範圍內調整、暫停或終止活動。',
    ],
  },
  {
    eyebrow: 'Safety',
    title: '禁止行為與風險控管',
    items: [
      '禁止使用外掛、機器人、攻擊工具、批次腳本或任何干擾抽賞、公平性、金流、庫存與系統安全的行為。',
      '禁止冒用他人資料、盜刷、詐欺、洗點、濫用優惠券、逆向工程或散布未授權內容。',
      '平台可為了安全、稽核、法遵與客服爭議需求，保留必要操作紀錄並限制異常帳號使用。',
    ],
  },
  {
    eyebrow: 'Termination',
    title: '暫停、終止與爭議處理',
    items: [
      '會員違反條款、法令或活動規則時，平台得暫停抽賞、出貨、優惠券使用、登入或其他服務功能。',
      '若會員對點數、抽賞、戰利品、出貨或資料處理有疑義，應先提供會員 Email、訂單/出貨單號與證明資料聯繫客服。',
      '平台會依點數流水、抽賞紀錄、出貨紀錄、audit log 與客服資料進行查核並回覆處理結果。',
    ],
  },
]

const quickLinks = [
  { label: '常見問題', to: '/faq', icon: 'bi-question-circle' },
  { label: '出貨政策', to: '/shipping-policy', icon: 'bi-truck' },
  { label: '隱私權政策', to: '/privacy', icon: 'bi-shield-lock' },
]
</script>

<template>
  <main class="terms-policy-page">
    <section class="container content-section">
      <div class="page-title terms-policy-title">
        <span class="eyebrow">Terms</span>
        <h1>會員服務條款</h1>
        <p>
          最後更新：2026-06-19。使用 LuckyBox 服務前，請確認帳號、LP
          點數、抽賞、戰利品、出貨與客服規則。
        </p>
      </div>

      <div class="status-grid terms-policy-summary">
        <div v-for="card in summaryCards" :key="card.title">
          <i :class="`bi ${card.icon}`" aria-hidden="true"></i>
          <strong>{{ card.title }}</strong>
          <span>{{ card.text }}</span>
        </div>
      </div>

      <div class="terms-policy-grid">
        <section
          v-for="section in termsSections"
          :key="section.title"
          class="status-panel terms-policy-panel"
        >
          <span class="eyebrow">{{ section.eyebrow }}</span>
          <h2>{{ section.title }}</h2>
          <ul class="terms-policy-list">
            <li v-for="item in section.items" :key="item">{{ item }}</li>
          </ul>
        </section>
      </div>

      <section class="status-panel terms-policy-notice">
        <div>
          <span class="eyebrow">Support</span>
          <h2>客服與相關政策</h2>
          <p>
            條款、資料權利、出貨瑕疵或抽賞紀錄有疑義時，請以會員 Email 寄信至
            support@luckybox.local，並附上訂單、出貨單號或相關截圖。
          </p>
        </div>

        <div class="terms-policy-links">
          <RouterLink v-for="link in quickLinks" :key="link.to" :to="link.to">
            <i :class="`bi ${link.icon}`" aria-hidden="true"></i>
            {{ link.label }}
          </RouterLink>
        </div>
      </section>
    </section>
  </main>
</template>

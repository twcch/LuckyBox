<script setup>
const summaryCards = [
  {
    icon: 'bi-ticket-perforated',
    title: '先產生票券',
    text: '賞池發布前由後台依獎項數量產生可抽 ticket，最後賞不混入普通 ticket。',
  },
  {
    icon: 'bi-database-lock',
    title: '後端決定結果',
    text: '抽賞由後端在交易中扣點、抽取 ticket、扣庫存並建立中獎紀錄。',
  },
  {
    icon: 'bi-shield-check',
    title: 'Proof 可驗證',
    text: 'HASH_COMMIT_REVEAL 賞池會公開 seedHash、完抽 revealedSeed、每抽 random_proof 與 ticket serial。',
  },
]

const fairnessSteps = [
  {
    label: 'Step 1',
    title: '建立完整 ticket 清單',
    text: '管理員設定獎項與數量後，系統依非最後賞獎項補產生可抽 ticket，並同步賞池總抽數與剩餘抽數。',
    icon: 'bi-list-check',
  },
  {
    label: 'Step 2',
    title: '公開剩餘數與機率',
    text: '前台賞池詳情顯示剩餘籤數、總籤數、各獎項剩餘數量與目前機率，使用者可在抽賞前確認。',
    icon: 'bi-bar-chart-line',
  },
  {
    label: 'Step 3',
    title: '依公平模式抽取 ticket',
    text: '前端不指定結果；HASH_COMMIT_REVEAL 模式會用 server seed 派生 HMAC offset，SERVER_RANDOM 模式使用伺服器安全亂數。',
    icon: 'bi-cpu',
  },
  {
    label: 'Step 4',
    title: '同一交易完成扣點與中獎',
    text: '扣點、抽籤、扣庫存、建立 DrawOrder、DrawResult 與 UserPrize 會在同一個資料庫交易中完成，失敗時回滾。',
    icon: 'bi-arrow-repeat',
  },
  {
    label: 'Step 5',
    title: '最後賞獨立判斷',
    text: '抽完最後一張普通 ticket 且賞池啟用最後賞時，系統額外發放最後賞，並以條件更新避免多人重複取得。',
    icon: 'bi-award',
  },
]

const currentControls = [
  'ticket 有唯一序號，抽賞結果會回傳 ticket serial 供會員在訂單與戰利品中查閱。',
  'HASH_COMMIT_REVEAL 賞池發布時公開 seedHash，完抽後公開 revealedSeed，可驗證 seed 未被替換。',
  '每次抽籤保存 random_proof，使用 revealedSeed 與抽賞 nonce 可重算 HMAC-SHA256 proof。',
  'draw_results.ticket_id 具唯一性約束，搭配資料庫條件更新避免同一 ticket 重複出獎。',
  '抽賞 API 支援 idempotency，避免同一請求因重送而重複扣點或重複套用優惠券。',
  '公開 /api/campaigns/:slug/fairness 會輸出 seedHash、revealedSeed、抽籤 proof、ticket serial 與驗證演算法說明。',
]

const verificationItems = [
  {
    title: 'Seed Hash / Revealed Seed',
    text: '發布時以 SHA-256(serverSeed) 形成公開 seedHash；完抽後公開 revealedSeed，可驗證 hash 是否一致。',
  },
  {
    title: 'Random Proof',
    text: '每抽保存 hmac-sha256:{orderId}:{index}:{proof}，公開 seed 後可用相同 nonce 重新計算。',
  },
  {
    title: 'Fairness API',
    text: '公開公平性 API 回傳 fairnessMode、seedHash、revealedSeed、draws 與演算法文字，讓客服與玩家查核同一份資料。',
  },
  {
    title: '一致性檢查',
    text: '後台一致性檢查會偵測剩餘數、獎項數量、ticket 狀態與 seed reveal 是否不一致。',
  },
]

const quickLinks = [
  { label: '會員服務條款', to: '/terms', icon: 'bi-file-earmark-text' },
  { label: '抽況榜單', to: '/leaderboard', icon: 'bi-broadcast' },
  { label: '常見問題', to: '/faq', icon: 'bi-question-circle' },
]
</script>

<template>
  <main class="fairness-policy-page">
    <section class="container content-section">
      <div class="page-title fairness-policy-title">
        <span class="eyebrow">Fairness</span>
        <h1>抽賞公平性說明</h1>
        <p>
          LuckyBox 以完整 ticket
          清單、後端抽籤、資料庫交易、唯一性限制與可追溯紀錄，降低抽賞黑箱與重複出獎風險。
        </p>
      </div>

      <div class="status-grid fairness-policy-summary">
        <div v-for="card in summaryCards" :key="card.title">
          <i :class="`bi ${card.icon}`" aria-hidden="true"></i>
          <strong>{{ card.title }}</strong>
          <span>{{ card.text }}</span>
        </div>
      </div>

      <section class="status-panel fairness-policy-panel">
        <div class="section-heading">
          <div>
            <span class="eyebrow">Flow</span>
            <h2>抽賞如何產生結果</h2>
          </div>
          <RouterLink class="btn btn-sm btn-outline-dark" to="/leaderboard">
            <i class="bi bi-broadcast" aria-hidden="true"></i>
            查看抽況
          </RouterLink>
        </div>

        <div class="fairness-policy-flow">
          <article v-for="step in fairnessSteps" :key="step.title">
            <span class="fairness-policy-flow__icon">
              <i :class="`bi ${step.icon}`" aria-hidden="true"></i>
            </span>
            <div>
              <small>{{ step.label }}</small>
              <strong>{{ step.title }}</strong>
              <p>{{ step.text }}</p>
            </div>
          </article>
        </div>
      </section>

      <div class="fairness-policy-grid">
        <section class="status-panel fairness-policy-panel">
          <span class="eyebrow">Current</span>
          <h2>目前控制點</h2>
          <ul class="fairness-policy-list">
            <li v-for="item in currentControls" :key="item">
              <i class="bi bi-check2-circle" aria-hidden="true"></i>
              <span>{{ item }}</span>
            </li>
          </ul>
        </section>

        <section class="status-panel fairness-policy-panel">
          <span class="eyebrow">Proof</span>
          <h2>可驗證能力</h2>
          <div class="fairness-policy-proof">
            <article v-for="item in verificationItems" :key="item.title">
              <strong>{{ item.title }}</strong>
              <p>{{ item.text }}</p>
            </article>
          </div>
        </section>
      </div>

      <section class="status-panel fairness-policy-notice">
        <div>
          <span class="eyebrow">Audit</span>
          <h2>紀錄查核與客服</h2>
          <p>
            若對抽賞結果、點數扣抵或獎項歸屬有疑義，請提供會員 Email、抽賞訂單號與畫面截圖寄至
            support@luckybox.local。客服會依抽賞訂單、ticket serial、點數流水與 audit log 協助查核。
          </p>
        </div>

        <div class="fairness-policy-links">
          <RouterLink v-for="link in quickLinks" :key="link.to" :to="link.to">
            <i :class="`bi ${link.icon}`" aria-hidden="true"></i>
            {{ link.label }}
          </RouterLink>
        </div>
      </section>
    </section>
  </main>
</template>

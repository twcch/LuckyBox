<script setup>
const shipmentSteps = [
  {
    title: '建立出貨申請',
    text: '會員可在戰利品盒勾選可出貨商品，合併多件送出同一張出貨單。',
    icon: 'bi-box-seam',
  },
  {
    title: '後台包貨處理',
    text: '客服與倉儲人員會依出貨批次核對品項、收件資訊與包材保護。',
    icon: 'bi-clipboard-check',
  },
  {
    title: '物流交寄追蹤',
    text: '出貨後會更新物流商與追蹤碼，會員可在出貨紀錄查詢狀態。',
    icon: 'bi-truck',
  },
]

const returnRules = [
  '非商品瑕疵、非寄送錯誤且包裝已拆封者，原則上不接受任意退換。',
  '若收到破損、缺件、錯品，請於到貨後 7 日內保留外箱、包材與商品照片。',
  '預購、延遲到貨、特殊材積商品會於賞池詳情或公告揭露預估出貨時間。',
  '已完成抽賞的數位紀錄、點數折抵與優惠券使用，會依個案核對後處理。',
]

const supportItems = [
  { label: '客服信箱', value: 'support@luckybox.local' },
  { label: '回覆時間', value: '1 個工作天內初步回覆' },
  { label: '處理時段', value: '週一至週五 10:00-18:00，國定假日順延' },
]
</script>

<template>
  <main class="shipping-policy-page">
    <section class="container content-section">
      <div class="page-title shipping-policy-title">
        <span class="eyebrow">Policy</span>
        <h1>出貨與退換貨政策</h1>
        <p>LuckyBox 以戰利品盒合併出貨為核心，提供固定運費、免運券折抵、物流追蹤與瑕疵處理流程。</p>
      </div>

      <div class="status-grid shipping-policy-summary">
        <div>
          <i class="bi bi-box2-heart" aria-hidden="true"></i>
          <strong>可合併出貨</strong>
          <span>同一會員可將多件戰利品合併成一張出貨單。</span>
        </div>
        <div>
          <i class="bi bi-ticket-perforated" aria-hidden="true"></i>
          <strong>免運券折抵</strong>
          <span>符合資格的免運券可於申請出貨時折抵固定運費。</span>
        </div>
        <div>
          <i class="bi bi-search" aria-hidden="true"></i>
          <strong>物流可追蹤</strong>
          <span>出貨後可於會員出貨紀錄查看物流商、追蹤碼與時間線。</span>
        </div>
      </div>

      <section class="status-panel shipping-policy-panel">
        <div class="section-heading">
          <div>
            <span class="eyebrow">Shipment</span>
            <h2>出貨流程</h2>
          </div>
          <RouterLink class="btn btn-sm btn-outline-dark" to="/account/shipments">
            <i class="bi bi-list-ul" aria-hidden="true"></i>
            出貨紀錄
          </RouterLink>
        </div>

        <div class="shipping-policy-flow">
          <article v-for="(step, index) in shipmentSteps" :key="step.title">
            <span class="shipping-policy-flow__icon">
              <i :class="`bi ${step.icon}`" aria-hidden="true"></i>
            </span>
            <div>
              <small>Step {{ index + 1 }}</small>
              <strong>{{ step.title }}</strong>
              <p>{{ step.text }}</p>
            </div>
          </article>
        </div>
      </section>

      <div class="shipping-policy-grid">
        <section class="status-panel shipping-policy-panel">
          <span class="eyebrow">Fees</span>
          <h2>運費與批次</h2>
          <dl class="shipping-policy-list">
            <div>
              <dt>固定運費</dt>
              <dd>每張出貨單 80 LP，免運券折抵後為 0 LP。</dd>
            </div>
            <div>
              <dt>出貨批次</dt>
              <dd>現貨商品預計 3 到 5 個工作天安排出貨；活動尖峰與預購商品依公告調整。</dd>
            </div>
            <div>
              <dt>地址修改</dt>
              <dd>出貨單進入已出貨後不可修改地址；申請後如需調整，請盡快聯繫客服。</dd>
            </div>
          </dl>
        </section>

        <section class="status-panel shipping-policy-panel">
          <span class="eyebrow">Returns</span>
          <h2>退換貨與瑕疵</h2>
          <ul class="shipping-policy-bullets">
            <li v-for="rule in returnRules" :key="rule">{{ rule }}</li>
          </ul>
        </section>
      </div>

      <section class="status-panel shipping-policy-contact">
        <div>
          <span class="eyebrow">Support</span>
          <h2>客服聯絡方式</h2>
          <p>
            請提供會員
            Email、出貨單號、商品照片與問題描述；客服會依出貨紀錄、抽賞紀錄與物流狀態協助處理。
          </p>
        </div>
        <dl>
          <div v-for="item in supportItems" :key="item.label">
            <dt>{{ item.label }}</dt>
            <dd>{{ item.value }}</dd>
          </div>
        </dl>
      </section>
    </section>
  </main>
</template>

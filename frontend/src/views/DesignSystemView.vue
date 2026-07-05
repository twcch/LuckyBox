<script setup>
const samplePrizes = [
  { rank: 'S', name: '星光大賞收藏盒', remaining: 1, total: 1, probability: '1.25%' },
  { rank: 'A', name: '壓克力展示組', remaining: 2, total: 2, probability: '2.50%' },
  { rank: 'LAST', name: '最後賞展示品', remaining: 1, total: 1, probability: '最後賞' },
]
</script>

<template>
  <main class="design-page">
    <section class="container content-section">
      <div class="page-title">
        <span class="eyebrow">UI Kit</span>
        <h1>設計系統與 Layout</h1>
        <p>這一頁集中展示 LuckyBox 已可重用的版面與 UI 元件。</p>
      </div>

      <div class="design-grid">
        <section class="design-panel">
          <div class="section-heading">
            <div>
              <span class="eyebrow">Buttons</span>
              <h2>按鈕與徽章</h2>
            </div>
          </div>
          <div class="component-row">
            <button class="btn btn-danger" type="button">
              <i class="bi bi-stars me-1" aria-hidden="true"></i>
              主要行動
            </button>
            <button class="btn btn-outline-dark" type="button">次要行動</button>
            <button class="btn btn-dark" type="button" disabled>鎖定狀態</button>
          </div>
          <div class="component-row">
            <span class="badge text-bg-danger">開抽中</span>
            <span class="badge text-bg-warning">即將開抽</span>
            <span class="badge text-bg-light border">卡牌賞</span>
          </div>
        </section>

        <section class="design-panel">
          <div class="section-heading">
            <div>
              <span class="eyebrow">Cards</span>
              <h2>卡片與圖像比例</h2>
            </div>
          </div>
          <article class="sample-card">
            <div class="sample-card__image image-fallback" aria-label="圖像 fallback"></div>
            <div>
              <span class="badge text-bg-danger">開抽中</span>
              <h3>星光收藏盒 Vol.1</h3>
              <p>卡片固定圖像比例，圖片失效時會顯示一致的 fallback 區塊。</p>
            </div>
          </article>
        </section>

        <section class="design-panel">
          <div class="section-heading">
            <div>
              <span class="eyebrow">Tabs</span>
              <h2>Tabs 與表單</h2>
            </div>
          </div>
          <ul class="nav nav-pills lb-tabs" role="tablist">
            <li class="nav-item" role="presentation">
              <button
                id="profile-tab"
                class="nav-link active"
                data-bs-toggle="tab"
                data-bs-target="#profile-pane"
                type="button"
                role="tab"
                aria-controls="profile-pane"
                aria-selected="true"
              >
                會員資料
              </button>
            </li>
            <li class="nav-item" role="presentation">
              <button
                id="address-tab"
                class="nav-link"
                data-bs-toggle="tab"
                data-bs-target="#address-pane"
                type="button"
                role="tab"
                aria-controls="address-pane"
                aria-selected="false"
              >
                收件資訊
              </button>
            </li>
          </ul>
          <div class="tab-content lb-tab-content">
            <div
              id="profile-pane"
              class="tab-pane fade show active"
              role="tabpanel"
              aria-labelledby="profile-tab"
            >
              <label class="form-label" for="displayName">顯示名稱</label>
              <input id="displayName" class="form-control" type="text" value="LuckyBox 玩家" />
            </div>
            <div
              id="address-pane"
              class="tab-pane fade"
              role="tabpanel"
              aria-labelledby="address-tab"
            >
              <label class="form-label" for="address">收件地址</label>
              <input id="address" class="form-control" type="text" placeholder="台北市..." />
            </div>
          </div>
        </section>

        <section class="design-panel">
          <div class="section-heading">
            <div>
              <span class="eyebrow">States</span>
              <h2>Progress、Empty、Error、Toast</h2>
            </div>
          </div>
          <div
            class="progress"
            role="progressbar"
            aria-label="賞池剩餘比例"
            aria-valuenow="68"
            aria-valuemax="100"
          >
            <div class="progress-bar" style="width: 68%"></div>
          </div>
          <div class="empty-state">
            <i class="bi bi-box-seam" aria-hidden="true"></i>
            <strong>目前沒有戰利品</strong>
            <span>抽賞完成後會出現在這裡。</span>
          </div>
          <div class="state-panel" role="alert">
            <i class="bi bi-exclamation-triangle" aria-hidden="true"></i>
            <span>後端暫時無法回應，請稍後再試。</span>
          </div>
          <div class="toast show lb-toast" role="status" aria-live="polite" aria-atomic="true">
            <div class="toast-body">
              <i class="bi bi-check-circle me-1" aria-hidden="true"></i>
              已更新賞池顯示狀態
            </div>
          </div>
        </section>

        <section class="design-panel design-panel--wide">
          <div class="section-heading">
            <div>
              <span class="eyebrow">Dialog</span>
              <h2>Modal 與獎項資料列</h2>
            </div>
            <button
              class="btn btn-outline-dark btn-sm"
              data-bs-toggle="modal"
              data-bs-target="#demoModal"
              type="button"
            >
              <i class="bi bi-window me-1" aria-hidden="true"></i>
              開啟 Modal
            </button>
          </div>

          <div class="prize-table">
            <div class="prize-row prize-row--head">
              <span>等級</span>
              <span>獎項</span>
              <span>剩餘 / 原始</span>
              <span>目前機率</span>
            </div>
            <div v-for="prize in samplePrizes" :key="prize.rank" class="prize-row">
              <span class="rank-badge">{{ prize.rank }}</span>
              <span>
                <strong>{{ prize.name }}</strong>
                <small>樣式檢查用資料。</small>
              </span>
              <span>{{ prize.remaining }} / {{ prize.total }}</span>
              <span>{{ prize.probability }}</span>
            </div>
          </div>
        </section>
      </div>
    </section>

    <div
      id="demoModal"
      class="modal fade"
      tabindex="-1"
      aria-labelledby="demoModalTitle"
      aria-hidden="true"
    >
      <div class="modal-dialog modal-dialog-centered">
        <div class="modal-content lb-modal">
          <div class="modal-header">
            <h2 id="demoModalTitle" class="modal-title fs-5">抽賞確認樣式</h2>
            <button
              class="btn-close"
              type="button"
              data-bs-dismiss="modal"
              aria-label="關閉"
            ></button>
          </div>
          <div class="modal-body">
            <p>抽賞、出貨與後台高風險操作會沿用這種確認視窗。</p>
          </div>
          <div class="modal-footer">
            <button class="btn btn-outline-dark" type="button" data-bs-dismiss="modal">取消</button>
            <button class="btn btn-danger" type="button" data-bs-dismiss="modal">確認</button>
          </div>
        </div>
      </div>
    </div>
  </main>
</template>

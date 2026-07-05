<script setup>
import { RouterLink, RouterView, useRoute } from 'vue-router'

const route = useRoute()
const adminNavItems = [
  { to: '/admin', icon: 'bi-speedometer2', label: '營運總覽', exact: true },
  { to: '/admin/campaigns', icon: 'bi-box-seam', label: '賞池管理' },
  { to: '/admin/prizes', icon: 'bi-gift', label: '獎品管理' },
  { to: '/admin/news', icon: 'bi-megaphone', label: '公告管理' },
  { to: '/admin/banners', icon: 'bi-image', label: 'Banner 管理' },
  { to: '/admin/coupons', icon: 'bi-ticket-perforated', label: '優惠券管理' },
  { to: '/admin/shipments', icon: 'bi-truck', label: '出貨管理' },
  { to: '/admin/orders', icon: 'bi-receipt', label: '訂單管理' },
  { to: '/admin/wallet-ledger', icon: 'bi-wallet2', label: '點數流水' },
  { to: '/admin/approvals', icon: 'bi-clipboard-check', label: '審核中心' },
  { to: '/admin/draws', icon: 'bi-ticket-perforated', label: '抽賞紀錄' },
  { to: '/admin/users', icon: 'bi-people', label: '會員管理' },
  { to: '/admin/wishes', icon: 'bi-stars', label: '許願牆管理' },
  { to: '/admin/security', icon: 'bi-shield-lock', label: '安全設定' },
  { to: '/admin/settings', icon: 'bi-sliders', label: '系統設定' },
  { to: '/admin/audit-logs', icon: 'bi-shield-check', label: '稽核紀錄' },
]

function isAdminNavActive(item) {
  if (item.exact) {
    return route.path === item.to
  }
  return route.path === item.to || route.path.startsWith(`${item.to}/`)
}
</script>

<template>
  <main class="admin-console-page">
    <section class="container content-section">
      <div class="admin-console-shell">
        <aside class="admin-console-sidebar">
          <div>
            <span class="eyebrow">後台</span>
            <h1>營運後台</h1>
            <p>快速查看營運狀態，處理待辦事項。</p>
          </div>

          <nav class="admin-console-nav" aria-label="後台導覽">
            <RouterLink
              v-for="item in adminNavItems"
              :key="item.to"
              :class="{ 'admin-console-nav__link--active': isAdminNavActive(item) }"
              :to="item.to"
            >
              <i :class="`bi ${item.icon}`" aria-hidden="true"></i>
              <span>{{ item.label }}</span>
            </RouterLink>
          </nav>
        </aside>

        <section class="admin-console-content">
          <div class="admin-console-workbar" aria-label="營運工作台">
            <span>
              <i class="bi bi-lightning-charge" aria-hidden="true"></i>
              優先處理待出貨、審核與金流異常
            </span>
            <RouterLink class="btn btn-sm btn-outline-dark" to="/status">
              <i class="bi bi-activity" aria-hidden="true"></i>
              系統狀態
            </RouterLink>
          </div>
          <RouterView />
        </section>
      </div>
    </section>
  </main>
</template>

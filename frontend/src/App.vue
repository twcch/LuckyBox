<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { recordVisit } from '@/services/analyticsApi'
import { useSessionStore } from '@/stores/session'

const session = useSessionStore()
const route = useRoute()
const router = useRouter()
const navSearch = ref('')
let lastTrackedPath = ''

const navItems = [
  { to: '/', icon: 'bi-house-door', label: '首頁' },
  { to: '/leaderboard', icon: 'bi-broadcast', label: '抽況' },
  { to: '/news', icon: 'bi-megaphone', label: '公告' },
]

const wishesNavItem = { to: '/wishes', icon: 'bi-stars', label: '許願牆' }
const adminNavItem = { to: '/admin', icon: 'bi-speedometer2', label: '後台' }

const isAdmin = computed(() => ['SUPER_ADMIN', 'ADMIN'].includes(session.user?.role))

const primaryNavItems = computed(() => {
  const base = [navItems[0], navItems[1], wishesNavItem, ...navItems.slice(2)]
  return session.authenticated
    ? [...base, { to: '/account/prizes', icon: 'bi-box-seam', label: '戰利品' }]
    : base
})

const accountNavItem = computed(() =>
  session.authenticated
    ? { to: '/account', icon: 'bi-person-circle', label: '會員' }
    : { to: '/login', icon: 'bi-box-arrow-in-right', label: '登入' },
)

const mobileNavItems = computed(() =>
  session.authenticated
    ? [
        navItems[0],
        navItems[1],
        wishesNavItem,
        { to: '/account/prizes', icon: 'bi-box-seam', label: '戰利品' },
        isAdmin.value ? adminNavItem : accountNavItem.value,
      ]
    : [navItems[0], navItems[1], wishesNavItem, navItems[2], accountNavItem.value],
)

const walletLabel = computed(() => {
  if (!session.user) {
    return '0 LP'
  }
  return `${session.user.cashPointBalance + session.user.bonusPointBalance} LP`
})

const normalizedNavSearch = computed(() => navSearch.value.trim())

function syncNavSearchFromRoute() {
  navSearch.value = route.path === '/' && typeof route.query.q === 'string' ? route.query.q : ''
}

async function submitGlobalSearch() {
  const query = normalizedNavSearch.value ? { q: normalizedNavSearch.value } : {}
  await router.push({ path: '/', query, hash: '#campaigns' })
}

async function clearGlobalSearch() {
  navSearch.value = ''
  await router.push({ path: '/', hash: '#campaigns' })
}

watch(() => [route.path, route.query.q], syncNavSearchFromRoute, { immediate: true })

watch(
  () => route.fullPath,
  () => {
    trackCurrentVisit()
  },
)

onMounted(() => {
  session.load().finally(() => {
    trackCurrentVisit()
  })
})

function trackCurrentVisit() {
  const path = route.fullPath || '/'
  if (path === lastTrackedPath) {
    return
  }
  lastTrackedPath = path
  recordVisit(path).catch(() => {})
}
</script>

<template>
  <div class="app-shell">
    <header class="site-header">
      <div class="site-header__utility">
        <div class="container site-header__utility-inner">
          <span>
            <i class="bi bi-shield-check" aria-hidden="true"></i>
            透明剩餘數、清楚售後、可追溯抽賞紀錄
          </span>
          <span class="site-header__utility-links">
            <RouterLink to="/status">系統狀態</RouterLink>
            <RouterLink to="/contact">客服協助</RouterLink>
          </span>
        </div>
      </div>
      <nav class="navbar navbar-expand-lg container py-3">
        <RouterLink class="navbar-brand d-flex align-items-center gap-2 fw-bold" to="/">
          <span class="brand-mark" aria-hidden="true">LB</span>
          LuckyBox
        </RouterLink>

        <button
          class="navbar-toggler"
          type="button"
          data-bs-toggle="collapse"
          data-bs-target="#mainNavigation"
          aria-controls="mainNavigation"
          aria-expanded="false"
          aria-label="切換導覽"
        >
          <span class="navbar-toggler-icon"></span>
        </button>

        <div id="mainNavigation" class="collapse navbar-collapse">
          <form class="nav-search" role="search" @submit.prevent="submitGlobalSearch">
            <label class="visually-hidden" for="globalCampaignSearch">搜尋賞池</label>
            <div class="input-group">
              <span class="input-group-text" aria-hidden="true">
                <i class="bi bi-search"></i>
              </span>
              <input
                id="globalCampaignSearch"
                v-model="navSearch"
                class="form-control"
                type="search"
                placeholder="搜尋賞池、品牌、描述"
                autocomplete="off"
              />
              <button
                v-if="navSearch"
                class="btn btn-outline-dark nav-search__button"
                type="button"
                title="清除搜尋"
                aria-label="清除搜尋"
                @click="clearGlobalSearch"
              >
                <i class="bi bi-x-lg" aria-hidden="true"></i>
              </button>
              <button
                class="btn btn-dark nav-search__button"
                type="submit"
                title="搜尋賞池"
                aria-label="搜尋賞池"
              >
                <i class="bi bi-search" aria-hidden="true"></i>
              </button>
            </div>
          </form>

          <ul class="navbar-nav ms-auto align-items-lg-center gap-lg-2">
            <li v-for="item in primaryNavItems" :key="item.to" class="nav-item">
              <RouterLink class="nav-link" :to="item.to">{{ item.label }}</RouterLink>
            </li>
            <li class="nav-item">
              <RouterLink class="nav-link" :to="accountNavItem.to">{{
                accountNavItem.label
              }}</RouterLink>
            </li>
            <li v-if="isAdmin" class="nav-item">
              <RouterLink class="nav-link" :to="adminNavItem.to">{{
                adminNavItem.label
              }}</RouterLink>
            </li>
            <li v-if="session.authenticated" class="nav-item">
              <RouterLink class="btn btn-outline-dark btn-sm" to="/account/wallet">
                <i class="bi bi-wallet2 me-1" aria-hidden="true"></i>
                {{ walletLabel }}
              </RouterLink>
            </li>
            <li v-else class="nav-item">
              <RouterLink class="btn btn-danger btn-sm" to="/register">
                <i class="bi bi-person-plus me-1" aria-hidden="true"></i>
                免費加入
              </RouterLink>
            </li>
          </ul>
        </div>
      </nav>
    </header>

    <form class="mobile-search-bar" role="search" @submit.prevent="submitGlobalSearch">
      <label class="visually-hidden" for="mobileCampaignSearch">搜尋賞池</label>
      <div class="input-group">
        <span class="input-group-text" aria-hidden="true">
          <i class="bi bi-search"></i>
        </span>
        <input
          id="mobileCampaignSearch"
          v-model="navSearch"
          class="form-control"
          type="search"
          placeholder="搜尋賞池、品牌、描述"
          autocomplete="off"
        />
        <button
          v-if="navSearch"
          class="btn btn-outline-dark nav-search__button"
          type="button"
          title="清除搜尋"
          aria-label="清除搜尋"
          @click="clearGlobalSearch"
        >
          <i class="bi bi-x-lg" aria-hidden="true"></i>
        </button>
        <button
          class="btn btn-dark nav-search__button"
          type="submit"
          title="搜尋賞池"
          aria-label="搜尋賞池"
        >
          <i class="bi bi-search" aria-hidden="true"></i>
        </button>
      </div>
    </form>

    <RouterView class="app-main" />

    <footer class="site-footer">
      <div class="container site-footer__inner">
        <div>
          <strong>LuckyBox</strong>
          <span>透明剩餘數、清楚售後、可追溯抽賞紀錄。</span>
        </div>
        <div class="site-footer__links">
          <RouterLink to="/">賞池列表</RouterLink>
          <RouterLink to="/leaderboard">抽況榜單</RouterLink>
          <RouterLink to="/wishes">許願牆</RouterLink>
          <RouterLink to="/news">公告</RouterLink>
          <RouterLink to="/account">會員中心</RouterLink>
          <RouterLink to="/faq">常見問題</RouterLink>
          <RouterLink to="/contact">聯絡我們</RouterLink>
          <RouterLink to="/fairness">公平性說明</RouterLink>
          <RouterLink to="/shipping-policy">出貨政策</RouterLink>
          <RouterLink to="/terms">服務條款</RouterLink>
          <RouterLink to="/privacy">隱私權政策</RouterLink>
          <RouterLink to="/status">系統狀態</RouterLink>
        </div>
      </div>
    </footer>

    <nav class="mobile-bottom-nav" aria-label="手機主要導覽">
      <RouterLink
        v-for="item in mobileNavItems"
        :key="item.to"
        :to="item.to"
        class="mobile-bottom-nav__item"
      >
        <i :class="`bi ${item.icon}`" aria-hidden="true"></i>
        <span>{{ item.label }}</span>
      </RouterLink>
    </nav>
  </div>
</template>

<style scoped>
.app-shell {
  display: flex;
  min-height: 100vh;
  flex-direction: column;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.9) 0%, rgba(246, 247, 248, 0) 18rem),
    var(--lb-page-bg);
}

.app-main {
  flex: 1;
}

.site-header {
  position: sticky;
  top: 0;
  z-index: 10;
  border-bottom: 1px solid rgba(226, 232, 240, 0.88);
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 0.25rem 1.1rem rgba(15, 23, 42, 0.04);
  backdrop-filter: blur(18px);
}

.site-header__utility {
  border-bottom: 1px solid rgba(226, 232, 240, 0.78);
  background: rgba(248, 250, 252, 0.82);
  color: var(--lb-muted);
  font-size: 0.82rem;
  font-weight: 750;
}

.site-header__utility-inner {
  display: flex;
  min-height: 2rem;
  gap: 1rem;
  align-items: center;
  justify-content: space-between;
}

.site-header__utility-inner > span {
  display: inline-flex;
  min-width: 0;
  gap: 0.4rem;
  align-items: center;
}

.site-header__utility-inner i {
  color: var(--lb-teal-dark);
}

.site-header__utility-links {
  flex: 0 0 auto;
}

.site-header__utility-links a {
  color: var(--lb-muted);
  text-decoration: none;
}

.site-header__utility-links a + a::before {
  margin: 0 0.6rem;
  color: var(--lb-border-strong);
  content: '/';
}

.site-header__utility-links a:hover {
  color: var(--lb-teal-dark);
}

.brand-mark {
  display: inline-grid;
  width: 2.25rem;
  height: 2.25rem;
  place-items: center;
  border-radius: 8px;
  color: #ffffff;
  background: linear-gradient(135deg, var(--lb-ink), var(--lb-teal-dark));
  font-size: 0.85rem;
  letter-spacing: 0;
  box-shadow: 0 0.45rem 1rem rgba(15, 23, 42, 0.14);
}

.navbar-brand,
.nav-link {
  color: #18202f;
}

.navbar-brand {
  font-weight: 900;
  letter-spacing: 0;
}

.nav-link {
  min-height: 2.3rem;
  align-content: center;
  border-radius: 8px;
  color: var(--lb-muted);
  font-weight: 750;
  transition:
    background-color 0.16s ease,
    color 0.16s ease;
}

.nav-link:hover {
  background: var(--lb-surface-soft);
  color: var(--lb-ink);
}

.nav-link.router-link-exact-active {
  background: var(--lb-surface-cool);
  color: var(--lb-teal-dark);
  font-weight: 850;
}

.nav-link--disabled {
  color: var(--lb-muted);
  cursor: not-allowed;
}

.navbar-toggler {
  border: 1px solid var(--lb-border);
  border-radius: 8px;
  background: var(--lb-surface-soft);
}

.site-header .btn-outline-dark {
  border-color: var(--lb-border-strong);
  background: #ffffff;
}

.nav-search {
  flex: 1 1 16rem;
  min-width: min(100%, 14rem);
  max-width: 28rem;
  margin-inline: clamp(0.85rem, 2vw, 1.4rem);
}

.nav-search .input-group,
.mobile-search-bar .input-group {
  min-width: 0;
  flex-wrap: nowrap;
}

.nav-search .input-group-text,
.mobile-search-bar .input-group-text {
  border-color: var(--lb-border);
  background: var(--lb-surface-soft);
  color: var(--lb-muted);
}

.nav-search .form-control,
.mobile-search-bar .form-control {
  min-width: 0;
  min-height: 2.35rem;
  border-color: var(--lb-border);
  font-weight: 700;
}

.nav-search__button {
  width: 2.45rem;
  min-width: 2.45rem;
  min-height: 2.35rem;
  padding: 0;
  white-space: nowrap;
}

.mobile-search-bar {
  display: none;
  padding: 0.65rem clamp(1rem, 4vw, 1.5rem);
  border-bottom: 1px solid var(--lb-border);
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 0.35rem 1.25rem rgba(15, 23, 42, 0.05);
}

.site-footer {
  margin-top: auto;
  padding: 1.25rem 0;
  border-top: 1px solid var(--lb-border);
  background: rgba(255, 255, 255, 0.92);
}

.site-footer__inner {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem 2rem;
  align-items: center;
  justify-content: space-between;
}

.site-footer__links {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem 0.9rem;
  align-items: center;
  justify-content: flex-end;
}

.site-footer__inner span,
.site-footer__links a {
  color: var(--lb-muted);
}

.site-footer__links a {
  font-weight: 750;
  text-decoration: none;
  white-space: nowrap;
  transition: color 0.16s ease;
}

.site-footer__links a:hover {
  color: var(--lb-coral-dark);
}

.mobile-bottom-nav {
  position: fixed;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 20;
  display: none;
  border-top: 1px solid var(--lb-border);
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 -0.5rem 1.5rem rgba(15, 23, 42, 0.07);
  backdrop-filter: blur(12px);
}

.mobile-bottom-nav__item {
  display: grid;
  min-height: calc(3.55rem + env(safe-area-inset-bottom, 0px));
  flex: 1;
  place-items: center;
  gap: 0.15rem;
  padding-bottom: env(safe-area-inset-bottom, 0px);
  color: var(--lb-muted);
  font-size: 0.7rem;
  font-weight: 800;
  text-decoration: none;
  transition:
    background-color 0.16s ease,
    color 0.16s ease;
}

.mobile-bottom-nav__item i {
  font-size: 1.15rem;
}

.mobile-bottom-nav__item.router-link-exact-active {
  background: var(--lb-surface-cool);
  color: var(--lb-teal-dark);
}

.mobile-bottom-nav__item:focus-visible {
  outline: 0;
  box-shadow: inset 0 0 0 0.2rem rgba(29, 143, 138, 0.22);
}

/* Bold app shell */
.app-shell {
  background:
    linear-gradient(90deg, rgba(16, 21, 34, 0.04) 1px, transparent 1px),
    linear-gradient(180deg, rgba(16, 21, 34, 0.04) 1px, transparent 1px),
    linear-gradient(135deg, rgba(242, 184, 59, 0.18), rgba(19, 169, 154, 0.1) 38%, rgba(112, 72, 232, 0.08)),
    var(--lb-page-bg);
  background-size:
    44px 44px,
    44px 44px,
    auto,
    auto;
}

.site-header {
  border-bottom: 3px solid var(--lb-ink);
  background: var(--lb-ink);
  box-shadow: 0 0.55rem 0 rgba(242, 184, 59, 0.85);
  backdrop-filter: none;
}

.site-header__utility {
  border-bottom: 1px solid rgba(255, 255, 255, 0.16);
  background:
    repeating-linear-gradient(135deg, rgba(242, 184, 59, 0.2) 0 10px, rgba(19, 169, 154, 0.18) 10px 20px),
    #151c2b;
  color: rgba(255, 255, 255, 0.82);
}

.site-header__utility-inner i,
.site-header__utility-links a:hover {
  color: var(--lb-gold);
}

.site-header__utility-links a {
  color: rgba(255, 255, 255, 0.78);
}

.navbar-brand,
.navbar-brand:hover,
.nav-link {
  color: #ffffff;
}

.brand-mark {
  border: 2px solid #ffffff;
  background: linear-gradient(135deg, var(--lb-coral), var(--lb-teal));
  box-shadow: 4px 4px 0 var(--lb-gold);
}

.nav-link {
  border: 1px solid transparent;
  color: rgba(255, 255, 255, 0.76);
}

.nav-link:hover {
  border-color: rgba(255, 255, 255, 0.24);
  background: rgba(255, 255, 255, 0.1);
  color: #ffffff;
}

.nav-link.router-link-exact-active {
  border-color: var(--lb-gold);
  background: var(--lb-gold);
  color: var(--lb-ink);
}

.navbar-toggler {
  border: 2px solid rgba(255, 255, 255, 0.68);
  background: #ffffff;
}

.site-header .btn-outline-dark {
  border-color: #ffffff;
  background: transparent;
  color: #ffffff;
}

.site-header .btn-outline-dark:hover {
  background: #ffffff;
  color: var(--lb-ink);
}

.nav-search .input-group {
  border: 2px solid rgba(255, 255, 255, 0.72);
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 4px 4px 0 rgba(242, 184, 59, 0.88);
}

.nav-search .input-group-text,
.nav-search .form-control {
  border: 0;
  background: #ffffff;
}

.nav-search__button.btn-dark {
  border-radius: 0 6px 6px 0;
  background: var(--lb-teal);
  color: #ffffff;
}

.mobile-search-bar {
  border-bottom: 3px solid var(--lb-ink);
  background: var(--lb-ink);
  box-shadow: 0 0.45rem 0 rgba(242, 184, 59, 0.85);
}

.mobile-search-bar .input-group {
  border: 2px solid #ffffff;
  border-radius: 8px;
  background: #ffffff;
}

.mobile-search-bar .input-group-text,
.mobile-search-bar .form-control {
  border: 0;
  background: #ffffff;
}

.site-footer {
  border-top: 3px solid var(--lb-ink);
  background:
    linear-gradient(90deg, rgba(242, 184, 59, 0.12), rgba(19, 169, 154, 0.1)),
    #ffffff;
}

.site-footer strong {
  color: var(--lb-ink);
}

.site-footer__links a {
  color: var(--lb-ink-soft);
}

.mobile-bottom-nav {
  border-top: 3px solid var(--lb-ink);
  background: var(--lb-ink);
  box-shadow: 0 -0.45rem 0 rgba(19, 169, 154, 0.85);
}

.mobile-bottom-nav__item {
  color: rgba(255, 255, 255, 0.72);
}

.mobile-bottom-nav__item.router-link-exact-active {
  background: var(--lb-gold);
  color: var(--lb-ink);
}

.mobile-bottom-nav__item:focus-visible {
  box-shadow: inset 0 0 0 0.18rem var(--lb-gold);
}

@media (max-width: 991.98px) {
  #mainNavigation .nav-search {
    width: 100%;
    max-width: none;
    margin: 0.85rem 0 0.5rem;
  }

  #mainNavigation .navbar-nav {
    align-items: stretch !important;
  }

  #mainNavigation .btn-outline-dark {
    width: 100%;
  }
}

@media (max-width: 767.98px) {
  .app-shell {
    padding-bottom: 3.7rem;
  }

  .site-header {
    display: none;
  }

  .mobile-search-bar {
    position: sticky;
    top: 0;
    z-index: 12;
    display: block;
  }

  .site-footer {
    padding-bottom: 1rem;
  }

  .site-footer__inner {
    align-items: flex-start;
    flex-direction: column;
  }

  .site-footer__links {
    justify-content: flex-start;
  }

  .mobile-bottom-nav {
    display: flex;
  }
}
</style>

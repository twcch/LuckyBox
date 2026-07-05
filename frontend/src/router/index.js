import { createRouter, createWebHistory } from 'vue-router'
import { useSessionStore } from '@/stores/session'
import HomeView from '../views/HomeView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomeView,
    },
    {
      path: '/kuji',
      name: 'campaign-list',
      redirect: { path: '/', hash: '#campaigns' },
    },
    {
      path: '/status',
      name: 'status',
      component: () => import('../views/StatusView.vue'),
    },
    {
      path: '/design-system',
      name: 'design-system',
      component: () => import('../views/DesignSystemView.vue'),
    },
    {
      path: '/news',
      name: 'news',
      component: () => import('../views/NewsView.vue'),
    },
    {
      path: '/news/:slug',
      name: 'news-detail',
      component: () => import('../views/NewsDetailView.vue'),
    },
    {
      path: '/leaderboard',
      name: 'leaderboard',
      component: () => import('../views/LeaderboardView.vue'),
    },
    {
      path: '/wishes',
      name: 'wishes',
      component: () => import('../views/WishesView.vue'),
    },
    {
      path: '/shipping-policy',
      name: 'shipping-policy',
      component: () => import('../views/ShippingPolicyView.vue'),
    },
    {
      path: '/faq',
      name: 'faq',
      component: () => import('../views/FaqView.vue'),
    },
    {
      path: '/contact',
      name: 'contact',
      component: () => import('../views/ContactView.vue'),
    },
    {
      path: '/terms',
      name: 'terms',
      component: () => import('../views/TermsPolicyView.vue'),
    },
    {
      path: '/fairness',
      name: 'fairness',
      component: () => import('../views/FairnessPolicyView.vue'),
    },
    {
      path: '/privacy',
      name: 'privacy',
      component: () => import('../views/PrivacyPolicyView.vue'),
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('../views/RegisterView.vue'),
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/LoginView.vue'),
    },
    {
      path: '/forgot-password',
      name: 'forgot-password',
      component: () => import('../views/ForgotPasswordView.vue'),
    },
    {
      path: '/reset-password',
      name: 'reset-password',
      component: () => import('../views/ResetPasswordView.vue'),
    },
    {
      path: '/admin/login',
      name: 'admin-login',
      component: () => import('../views/LoginView.vue'),
    },
    {
      path: '/account',
      name: 'account',
      component: () => import('../views/AccountView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/account/profile',
      name: 'account-profile',
      component: () => import('../views/AccountProfileView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/account/wallet',
      name: 'wallet',
      component: () => import('../views/WalletView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/account/top-up',
      name: 'account-top-up',
      redirect: { path: '/account/wallet', hash: '#top-up' },
      meta: { requiresAuth: true },
    },
    {
      path: '/payment/mock/:orderId',
      name: 'mock-payment-checkout',
      component: () => import('../views/MockPaymentCheckoutView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/account/prizes',
      name: 'prize-box',
      component: () => import('../views/PrizeBoxView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/account/coupons',
      name: 'account-coupons',
      component: () => import('../views/AccountCouponsView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/account/orders',
      name: 'account-orders',
      component: () => import('../views/AccountOrdersView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/account/shipments',
      name: 'account-shipments',
      component: () => import('../views/AccountShipmentsView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/account/shipments/:shipmentId',
      name: 'account-shipment-detail',
      component: () => import('../views/AccountShipmentDetailView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/admin',
      component: () => import('../views/AdminLayoutView.vue'),
      meta: { requiresAuth: true, requiresAdmin: true },
      children: [
        {
          path: '',
          name: 'admin-dashboard',
          component: () => import('../views/AdminDashboardView.vue'),
        },
        {
          path: 'campaigns',
          name: 'admin-campaigns',
          component: () => import('../views/AdminCampaignsView.vue'),
        },
        {
          path: 'campaigns/new',
          name: 'admin-campaign-new',
          component: () => import('../views/AdminCampaignsView.vue'),
        },
        {
          path: 'campaigns/:campaignId',
          name: 'admin-campaign-edit',
          component: () => import('../views/AdminCampaignsView.vue'),
        },
        {
          path: 'campaigns/:campaignId/tickets',
          name: 'admin-campaign-tickets',
          component: () => import('../views/AdminCampaignsView.vue'),
        },
        {
          path: 'shipments',
          name: 'admin-shipments',
          component: () => import('../views/AdminShipmentsView.vue'),
        },
        {
          path: 'orders',
          name: 'admin-orders',
          component: () => import('../views/AdminPaymentOrdersView.vue'),
        },
        {
          path: 'wallet-ledger',
          name: 'admin-wallet-ledger',
          component: () => import('../views/AdminWalletLedgerView.vue'),
        },
        {
          path: 'approvals',
          name: 'admin-approvals',
          component: () => import('../views/AdminApprovalsView.vue'),
        },
        {
          path: 'draws',
          name: 'admin-draws',
          component: () => import('../views/AdminDrawOrdersView.vue'),
        },
        {
          path: 'users',
          name: 'admin-users',
          component: () => import('../views/AdminUsersView.vue'),
        },
        {
          path: 'users/:userId',
          name: 'admin-member-detail',
          component: () => import('../views/AdminMemberDetailView.vue'),
        },
        {
          path: 'news',
          name: 'admin-news',
          component: () => import('../views/AdminNewsView.vue'),
        },
        {
          path: 'banners',
          name: 'admin-banners',
          component: () => import('../views/AdminBannersView.vue'),
        },
        {
          path: 'coupons',
          name: 'admin-coupons',
          component: () => import('../views/AdminCouponsView.vue'),
        },
        {
          path: 'wishes',
          name: 'admin-wishes',
          component: () => import('../views/AdminWishesView.vue'),
        },
        {
          path: 'security',
          name: 'admin-security',
          component: () => import('../views/AdminSecurityView.vue'),
        },
        {
          path: 'settings',
          name: 'admin-settings',
          component: () => import('../views/AdminSettingsView.vue'),
        },
        {
          path: 'prizes',
          name: 'admin-prizes',
          component: () => import('../views/AdminPrizesView.vue'),
        },
        {
          path: 'audit-logs',
          name: 'admin-audit-logs',
          component: () => import('../views/AdminAuditLogsView.vue'),
        },
      ],
    },
    {
      path: '/kuji/:slug',
      name: 'campaign-detail',
      component: () => import('../views/CampaignDetailView.vue'),
    },
    {
      path: '/kuji/:slug/draw',
      name: 'campaign-draw',
      redirect: (to) => ({ path: `/kuji/${to.params.slug}`, hash: '#draw' }),
    },
    {
      path: '/result/:drawId',
      name: 'draw-result',
      redirect: (to) => ({ path: '/account/orders', query: { drawId: to.params.drawId } }),
      meta: { requiresAuth: true },
    },
  ],
})

router.beforeEach(async (to) => {
  if (!to.meta.requiresAuth) {
    return true
  }

  const session = useSessionStore()
  if (!session.initialized) {
    await session.load()
  }
  if (session.authenticated) {
    if (to.meta.requiresAdmin && !['SUPER_ADMIN', 'ADMIN'].includes(session.user?.role)) {
      return { path: '/account' }
    }
    return true
  }
  return {
    path: to.meta.requiresAdmin ? '/admin/login' : '/login',
    query: { redirect: to.fullPath },
  }
})

export default router

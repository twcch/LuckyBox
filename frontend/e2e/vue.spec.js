import { test, expect } from '@playwright/test'

const memberUser = {
  id: 701,
  email: 'e2e-member@example.com',
  displayName: 'E2E 玩家',
  phone: '0912345678',
  role: 'USER',
  status: 'ACTIVE',
  vipLevel: 'REGULAR',
  cashPointBalance: 300,
  bonusPointBalance: 45,
}

const adminUser = {
  id: 1,
  email: 'admin@example.com',
  displayName: 'E2E 管理員',
  phone: '0900000000',
  role: 'ADMIN',
  status: 'ACTIVE',
  vipLevel: 'REGULAR',
  cashPointBalance: 0,
  bonusPointBalance: 0,
}

test.beforeEach(async ({ page }) => {
  let currentUser = null
  let remainingTickets = 24
  let drawOrderCreated = false
  let nextAdminCampaignId = 9201
  let nextAdminPrizeId = 9301
  let adminCampaigns = []
  let adminPrizesByCampaign = new Map()
  let adminGeneratedTicketsByCampaign = new Map()
  let accountCoupons = [
    {
      id: 301,
      code: 'SHIPFREE',
      title: 'E2E 免運券',
      type: 'FREE_SHIPPING',
      status: 'ACTIVE',
      endsAt: '2026-12-31T15:59:59Z',
    },
  ]
  let prizeItems = [
    {
      id: 8101,
      campaignSlug: 'e2e-star-box',
      campaignTitle: '星光測試賞池',
      prizeRank: 'A',
      prizeName: '金色星光立牌',
      ticketSerialNumber: 'E2E-0001',
      status: 'IN_BOX',
      shipmentId: null,
      acquiredAt: '2026-07-02T12:10:00Z',
    },
    {
      id: 8102,
      campaignSlug: 'e2e-star-box',
      campaignTitle: '星光測試賞池',
      prizeRank: 'C',
      prizeName: '星雲徽章',
      ticketSerialNumber: 'E2E-0002',
      status: 'IN_BOX',
      shipmentId: null,
      acquiredAt: '2026-07-02T12:10:10Z',
    },
    {
      id: 8103,
      campaignSlug: 'e2e-moon-box',
      campaignTitle: '月光測試賞池',
      prizeRank: 'B',
      prizeName: '已申請徽章組',
      ticketSerialNumber: 'E2E-0003',
      status: 'SHIPMENT_REQUESTED',
      shipmentId: 7001,
      acquiredAt: '2026-07-01T10:00:00Z',
    },
  ]
  let shipments = []

  function campaignSummary() {
    return {
      id: 101,
      slug: 'e2e-star-box',
      title: '星光測試賞池',
      subtitle: '訪客瀏覽 E2E',
      coverImageUrl: null,
      sourceType: 'MIXED',
      sourceTypeLabel: '自營混套賞',
      status: 'LIVE',
      statusLabel: '開抽中',
      pricePerDraw: 120,
      totalTickets: 80,
      remainingTickets,
      hasLastPrize: true,
      rareHint: drawOrderCreated ? 'B賞剩 5' : 'A賞剩 1',
      remainingRate: Math.round((remainingTickets / 80) * 100),
      soldRate: 100 - Math.round((remainingTickets / 80) * 100),
      ageRestricted: false,
      minimumAge: null,
      ageRestrictionLabel: '全年齡',
    }
  }

  function campaignDetail() {
    const aRemaining = drawOrderCreated ? 0 : 1
    const cRemaining = Math.max(0, remainingTickets - aRemaining - 5)
    return {
      ...campaignSummary(),
      description: '透明揭露剩餘 ticket、獎項機率與出貨政策的 E2E 測試賞池。',
      bannerImageUrl: null,
      fairnessMode: 'SERVER_RANDOM',
      rightsNotice: '商品來源與圖片素材已由營運確認可於平台展示。',
      ageVerificationNote: null,
      shippingNote: '戰利品可累積後合併出貨，申請時填寫收件資訊。',
      returnPolicyNote: '商品瑕疵請保留包裝與照片，客服會依抽賞紀錄查核。',
      lastPrizeRule: '抽完最後一張普通 ticket 的訂單取得最後賞。',
      prizes: [
        {
          id: 1001,
          rank: 'A',
          name: '金色星光立牌',
          description: '稀有主賞',
          imageUrl: null,
          originalQuantity: 1,
          remainingQuantity: aRemaining,
          probability: remainingTickets > 0 ? (aRemaining / remainingTickets) * 100 : 0,
          lastPrize: false,
        },
        {
          id: 1002,
          rank: 'B',
          name: '銀河資料夾',
          description: '人氣賞',
          imageUrl: null,
          originalQuantity: 8,
          remainingQuantity: 5,
          probability: remainingTickets > 0 ? (5 / remainingTickets) * 100 : 0,
          lastPrize: false,
        },
        {
          id: 1003,
          rank: 'C',
          name: '星雲徽章',
          description: '收藏徽章',
          imageUrl: null,
          originalQuantity: 71,
          remainingQuantity: cRemaining,
          probability: remainingTickets > 0 ? (cRemaining / remainingTickets) * 100 : 0,
          lastPrize: false,
        },
        {
          id: 1004,
          rank: 'LAST',
          name: '最後賞特典海報',
          description: '最後賞',
          imageUrl: null,
          originalQuantity: 1,
          remainingQuantity: 1,
          probability: 0,
          lastPrize: true,
        },
      ],
    }
  }

  function campaignDrawHistory() {
    const draws = [
      {
        drawResultId: 501,
        drawOrderId: 401,
        maskedDisplayName: 'Lu**',
        campaignSlug: 'e2e-star-box',
        campaignTitle: '星光測試賞池',
        prizeRank: 'A',
        prizeName: '壓克力立牌',
        resultIndex: 1,
        drawnAt: '2026-07-02T12:00:00Z',
      },
    ]
    if (drawOrderCreated) {
      draws.unshift({
        drawResultId: 9001,
        drawOrderId: 7301,
        maskedDisplayName: 'E2**',
        campaignSlug: 'e2e-star-box',
        campaignTitle: '星光測試賞池',
        prizeRank: 'A',
        prizeName: '金色星光立牌',
        resultIndex: 1,
        drawnAt: '2026-07-02T12:10:00Z',
      })
    }
    return {
      draws,
      generatedAt: '2026-07-02T12:10:00Z',
    }
  }

  function prizeBoxOverview(requestUrl) {
    const status = requestUrl.searchParams.get('status') || ''
    const campaignSlug = requestUrl.searchParams.get('campaignSlug') || ''
    const filteredItems = prizeItems.filter(
      (item) =>
        (!status || item.status === status) && (!campaignSlug || item.campaignSlug === campaignSlug),
    )
    const campaigns = Array.from(
      prizeItems
        .reduce((entries, item) => {
          const entry = entries.get(item.campaignSlug) || {
            slug: item.campaignSlug,
            title: item.campaignTitle,
            itemCount: 0,
          }
          entry.itemCount += 1
          entries.set(item.campaignSlug, entry)
          return entries
        }, new Map())
        .values(),
    )
    return {
      items: filteredItems,
      campaigns,
      statusCounts: prizeItems.reduce((counts, item) => {
        counts[item.status] = (counts[item.status] || 0) + 1
        return counts
      }, {}),
      status: status || null,
      campaignSlug: campaignSlug || null,
    }
  }

  function accountAddresses() {
    return [
      {
        id: 401,
        recipientName: 'E2E 玩家',
        phone: '0912345678',
        postalCode: '100',
        city: '台北市',
        district: '中正區',
        addressLine: '測試路 1 號',
        defaultAddress: true,
      },
    ]
  }

  function sourceTypeLabel(sourceType) {
    return (
      {
        OFFICIAL: '官方賞',
        SELF_MADE: '自製賞',
        MIXED: '自營混套賞',
        BLIND_BOX: '盲盒賞',
        CARD: '卡牌賞',
        GK: 'GK 賞',
        PREORDER: '預購賞',
      }[sourceType] || sourceType
    )
  }

  function statusLabel(status) {
    return (
      {
        DRAFT: '草稿',
        SCHEDULED: '即將開抽',
        LIVE: '開抽中',
        PAUSED: '暫停中',
        SOLD_OUT: '已完抽',
        ENDED: '已結束',
      }[status] || status
    )
  }

  function normalizeAdminCampaign(payload, existing = {}) {
    const status = payload.status || existing.status || 'DRAFT'
    const totalTickets = Number(payload.totalTickets ?? existing.totalTickets ?? 0)
    const generatedTickets = adminGeneratedTicketsByCampaign.get(existing.id) || 0
    return {
      id: existing.id || nextAdminCampaignId++,
      slug: payload.slug ?? existing.slug,
      title: payload.title ?? existing.title,
      subtitle: payload.subtitle ?? existing.subtitle ?? '',
      description: payload.description ?? existing.description,
      coverImageUrl: payload.coverImageUrl ?? existing.coverImageUrl ?? '',
      bannerImageUrl: payload.bannerImageUrl ?? existing.bannerImageUrl ?? '',
      sourceType: payload.sourceType ?? existing.sourceType ?? 'MIXED',
      sourceTypeLabel: sourceTypeLabel(payload.sourceType ?? existing.sourceType ?? 'MIXED'),
      commercialUseConfirmed: Boolean(payload.commercialUseConfirmed ?? existing.commercialUseConfirmed ?? true),
      officialLicenseConfirmed: Boolean(
        payload.officialLicenseConfirmed ?? existing.officialLicenseConfirmed ?? false,
      ),
      rightsNotice:
        payload.rightsNotice ??
        existing.rightsNotice ??
        '商品來源與圖片素材由營運確認可於平台展示。',
      ageRestricted: Boolean(payload.ageRestricted ?? existing.ageRestricted ?? false),
      minimumAge: payload.minimumAge ?? existing.minimumAge ?? null,
      ageVerificationNote: payload.ageVerificationNote ?? existing.ageVerificationNote ?? '',
      ipName: payload.ipName ?? existing.ipName ?? '',
      brandName: payload.brandName ?? existing.brandName ?? '',
      pricePerDraw: Number(payload.pricePerDraw ?? existing.pricePerDraw ?? 120),
      totalTickets,
      remainingTickets: status === 'LIVE' ? generatedTickets || totalTickets : totalTickets,
      soldTickets: existing.soldTickets || 0,
      status,
      statusLabel: statusLabel(status),
      salesStartAt: payload.salesStartAt ?? existing.salesStartAt ?? '',
      salesEndAt: payload.salesEndAt ?? existing.salesEndAt ?? '',
      shippingNote: payload.shippingNote ?? existing.shippingNote,
      returnPolicyNote: payload.returnPolicyNote ?? existing.returnPolicyNote,
      hasLastPrize: Boolean(payload.hasLastPrize ?? existing.hasLastPrize ?? false),
      lastPrizeRule: payload.lastPrizeRule ?? existing.lastPrizeRule ?? '',
      fairnessMode: payload.fairnessMode ?? existing.fairnessMode ?? 'SERVER_RANDOM',
      seedHash: payload.seedHash ?? existing.seedHash ?? '',
      prizeCount: adminPrizesByCampaign.get(existing.id)?.length || existing.prizeCount || 0,
      createdAt: existing.createdAt || '2026-07-03T08:00:00Z',
      updatedAt: '2026-07-03T08:10:00Z',
    }
  }

  function adminPrizeOverview(campaignId) {
    const prizes = adminPrizesByCampaign.get(campaignId) || []
    const generatedTickets = adminGeneratedTicketsByCampaign.get(campaignId) || 0
    return {
      campaignId,
      totalPrizeQuantity: prizes
        .filter((prize) => !prize.lastPrize)
        .reduce((sum, prize) => sum + Number(prize.originalQuantity || 0), 0),
      remainingPrizeQuantity: prizes
        .filter((prize) => !prize.lastPrize)
        .reduce((sum, prize) => sum + Number(prize.remainingQuantity || 0), 0),
      generatedTickets,
      availableTickets: generatedTickets,
      prizes,
    }
  }

  function refreshAdminCampaignPrizeCount(campaignId) {
    adminCampaigns = adminCampaigns.map((campaign) =>
      campaign.id === campaignId
        ? {
            ...campaign,
            prizeCount: adminPrizesByCampaign.get(campaignId)?.length || 0,
          }
        : campaign,
    )
  }

  function walletOverview() {
    const cashPointBalance = currentUser?.cashPointBalance || 0
    const bonusPointBalance = currentUser?.bonusPointBalance || 0
    return {
      wallet: {
        cashPointBalance,
        bonusPointBalance,
        lockedBalance: 0,
        totalAvailableBalance: cashPointBalance + bonusPointBalance,
      },
      ledger:
        cashPointBalance + bonusPointBalance > 345
          ? [
              {
                id: 2,
                type: 'TOP_UP_BONUS',
                amount: 50,
                pointKind: 'BONUS',
                balanceAfter: bonusPointBalance,
                referenceType: 'PaymentOrder',
                referenceId: 9101,
                reason: 'Mock 儲值贈點',
                createdAt: '2026-07-02T12:03:00Z',
              },
              {
                id: 1,
                type: 'TOP_UP',
                amount: 500,
                pointKind: 'CASH',
                balanceAfter: cashPointBalance,
                referenceType: 'PaymentOrder',
                referenceId: 9101,
                reason: 'Mock 儲值入點',
                createdAt: '2026-07-02T12:03:00Z',
              },
            ]
          : [],
      topUpPlans: [
        {
          id: 'value',
          label: '人氣方案',
          amount: 500,
          pointAmount: 500,
          bonusPointAmount: 50,
        },
      ],
      firstDepositPromo: { bonusPoints: 100, eligible: false },
      spendThresholdPromo: {
        active: false,
        threshold: 0,
        bonusPoints: 0,
        totalSpend: 0,
        remaining: 0,
        reached: false,
      },
    }
  }

  await page.route('**/api/auth/me', async (route) => {
    if (!currentUser) {
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({ code: 'AUTH_REQUIRED', message: '請先登入。' }),
      })
      return
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(currentUser),
    })
  })
  await page.route('**/api/auth/register', async (route) => {
    currentUser = { ...memberUser }
    await route.fulfill({
      status: 201,
      contentType: 'application/json',
      body: JSON.stringify(currentUser),
    })
  })
  await page.route('**/api/auth/login', async (route) => {
    const payload = route.request().postDataJSON()
    currentUser =
      payload.email === adminUser.email ? { ...adminUser } : { ...memberUser }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(currentUser),
    })
  })
  await page.route('**/api/account/wallet', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(walletOverview()),
    })
  })
  await page.route('**/api/account/payment-orders', async (route) => {
    await route.fulfill({
      status: 201,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 9101,
        provider: 'MOCK',
        merchantTradeNo: 'MOCK-E2E-9101',
        amount: 500,
        pointAmount: 500,
        bonusPointAmount: 50,
        status: 'PENDING',
        checkoutUrl: '/payment/mock/9101',
        createdAt: '2026-07-02T12:02:00Z',
        paidAt: null,
      }),
    })
  })
  await page.route('**/api/account/payment-orders/9101/mock-checkout/confirm', async (route) => {
    currentUser = {
      ...(currentUser || memberUser),
      cashPointBalance: 800,
      bonusPointBalance: 95,
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 9101,
        provider: 'MOCK',
        merchantTradeNo: 'MOCK-E2E-9101',
        amount: 500,
        pointAmount: 500,
        bonusPointAmount: 50,
        status: 'PAID',
        checkoutUrl: '/payment/mock/9101',
        createdAt: '2026-07-02T12:02:00Z',
        paidAt: '2026-07-02T12:03:00Z',
      }),
    })
  })
  await page.route('**/api/analytics/visit', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ visitorId: 'e2e-visitor', visitCount: 1 }),
    })
  })
  await page.route('**/api/admin/campaigns**', async (route) => {
    const request = route.request()
    const requestUrl = new URL(request.url())
    const pathname = requestUrl.pathname
    const method = request.method()
    const segments = pathname.split('/').filter(Boolean)
    const campaignId = Number(segments[3])

    if (pathname === '/api/admin/campaigns' && method === 'GET') {
      const keywordText = (requestUrl.searchParams.get('q') || '').toLowerCase()
      const status = requestUrl.searchParams.get('status') || ''
      const filtered = adminCampaigns.filter(
        (campaign) =>
          (!status || campaign.status === status) &&
          (!keywordText ||
            campaign.slug.toLowerCase().includes(keywordText) ||
            campaign.title.toLowerCase().includes(keywordText) ||
            campaign.brandName.toLowerCase().includes(keywordText)),
      )
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(filtered),
      })
      return
    }

    if (pathname === '/api/admin/campaigns' && method === 'POST') {
      const created = normalizeAdminCampaign(request.postDataJSON())
      adminCampaigns = [created, ...adminCampaigns]
      adminPrizesByCampaign.set(created.id, [])
      adminGeneratedTicketsByCampaign.set(created.id, 0)
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify(created),
      })
      return
    }

    const campaign = adminCampaigns.find((item) => item.id === campaignId)
    if (!campaign) {
      await route.fulfill({
        status: 404,
        contentType: 'application/json',
        body: JSON.stringify({ code: 'CAMPAIGN_NOT_FOUND', message: '找不到賞池。' }),
      })
      return
    }

    if (segments[4] === 'prizes' && segments.length === 5 && method === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(adminPrizeOverview(campaignId)),
      })
      return
    }

    if (segments[4] === 'prizes' && segments.length === 5 && method === 'POST') {
      const payload = request.postDataJSON()
      const generatedTickets = payload.lastPrize ? 0 : Number(payload.originalQuantity || 0)
      const prize = {
        id: nextAdminPrizeId++,
        campaignId,
        rank: payload.rank,
        name: payload.name,
        description: payload.description || '',
        imageUrl: payload.imageUrl || '',
        originalQuantity: Number(payload.originalQuantity || 0),
        remainingQuantity: Number(payload.originalQuantity || 0),
        sortOrder: Number(payload.sortOrder || 1),
        lastPrize: Boolean(payload.lastPrize),
        generatedTickets: adminGeneratedTicketsByCampaign.get(campaignId) ? generatedTickets : 0,
        availableTickets: adminGeneratedTicketsByCampaign.get(campaignId) ? generatedTickets : 0,
        createdAt: '2026-07-03T08:12:00Z',
        updatedAt: '2026-07-03T08:12:00Z',
      }
      adminPrizesByCampaign.set(campaignId, [...(adminPrizesByCampaign.get(campaignId) || []), prize])
      refreshAdminCampaignPrizeCount(campaignId)
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify(prize),
      })
      return
    }

    if (segments[4] === 'prizes' && segments[6] === undefined && method === 'PATCH') {
      const prizeId = Number(segments[5])
      const payload = request.postDataJSON()
      let updatedPrize = null
      adminPrizesByCampaign.set(
        campaignId,
        (adminPrizesByCampaign.get(campaignId) || []).map((prize) => {
          if (prize.id !== prizeId) {
            return prize
          }
          updatedPrize = {
            ...prize,
            rank: payload.rank,
            name: payload.name,
            description: payload.description || '',
            imageUrl: payload.imageUrl || '',
            originalQuantity: Number(payload.originalQuantity || 0),
            remainingQuantity: Number(payload.originalQuantity || 0),
            sortOrder: Number(payload.sortOrder || 1),
            lastPrize: Boolean(payload.lastPrize),
            updatedAt: '2026-07-03T08:13:00Z',
          }
          return updatedPrize
        }),
      )
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(updatedPrize),
      })
      return
    }

    if (segments[4] === 'tickets' && segments[5] === 'generate' && method === 'POST') {
      const overview = adminPrizeOverview(campaignId)
      adminGeneratedTicketsByCampaign.set(campaignId, overview.totalPrizeQuantity)
      adminPrizesByCampaign.set(
        campaignId,
        (adminPrizesByCampaign.get(campaignId) || []).map((prize) =>
          prize.lastPrize
            ? prize
            : {
                ...prize,
                generatedTickets: Number(prize.originalQuantity || 0),
                availableTickets: Number(prize.originalQuantity || 0),
              },
        ),
      )
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          generatedCount: overview.totalPrizeQuantity,
          totalTickets: campaign.totalTickets,
          availableTickets: overview.totalPrizeQuantity,
        }),
      })
      return
    }

    if (segments[4] === 'dry-run' && method === 'POST') {
      const generatedTickets = adminGeneratedTicketsByCampaign.get(campaignId) || 0
      const firstPrize = (adminPrizesByCampaign.get(campaignId) || []).find(
        (prize) => !prize.lastPrize,
      )
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          campaignId,
          requestedQuantity: Math.min(5, generatedTickets),
          totalTickets: campaign.totalTickets,
          availableTickets: generatedTickets,
          results: [
            {
              serialNumber: 'E2E-ADMIN-0001',
              rank: firstPrize?.rank || 'A',
              prizeName: firstPrize?.name || '測試獎項',
            },
          ],
        }),
      })
      return
    }

    if (segments[4] === 'publish' && method === 'POST') {
      const generatedTickets = adminGeneratedTicketsByCampaign.get(campaignId) || campaign.totalTickets
      const published = {
        ...campaign,
        status: 'LIVE',
        statusLabel: '開抽中',
        remainingTickets: generatedTickets,
        updatedAt: '2026-07-03T08:20:00Z',
      }
      adminCampaigns = adminCampaigns.map((item) => (item.id === campaignId ? published : item))
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(published),
      })
      return
    }

    if (segments[4] === 'pause' && method === 'POST') {
      const paused = {
        ...campaign,
        status: 'PAUSED',
        statusLabel: '暫停中',
        updatedAt: '2026-07-03T08:20:00Z',
      }
      adminCampaigns = adminCampaigns.map((item) => (item.id === campaignId ? paused : item))
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(paused),
      })
      return
    }

    if (segments.length === 4 && method === 'PATCH') {
      const updated = normalizeAdminCampaign(request.postDataJSON(), campaign)
      adminCampaigns = adminCampaigns.map((item) => (item.id === campaignId ? updated : item))
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(updated),
      })
      return
    }

    await route.fulfill({
      status: 405,
      contentType: 'application/json',
      body: JSON.stringify({ code: 'METHOD_NOT_ALLOWED', message: 'Unsupported admin mock.' }),
    })
  })
  await page.route('**/api/account/addresses', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(accountAddresses()),
    })
  })
  await page.route('**/api/account/coupons', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(accountCoupons),
    })
  })
  await page.route('**/api/account/notifications**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        unreadCount: 0,
        items: [],
      }),
    })
  })
  await page.route('**/api/account/prizes**', async (route) => {
    const requestUrl = new URL(route.request().url())
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(prizeBoxOverview(requestUrl)),
    })
  })
  await page.route('**/api/account/shipments', async (route) => {
    if (route.request().method() === 'POST') {
      const payload = route.request().postDataJSON()
      const selectedIds = new Set(payload.prizeIds || [])
      const shipment = {
        id: 8201,
        status: 'REQUESTED',
        recipientName: 'E2E 玩家',
        phone: '0912345678',
        postalCode: '100',
        city: '台北市',
        district: '中正區',
        addressLine: '測試路 1 號',
        itemCount: selectedIds.size,
        shippingFee: payload.couponId ? 0 : 80,
        requestedAt: '2026-07-02T12:20:00Z',
        shippedAt: null,
        deliveredAt: null,
        carrier: null,
        trackingNumber: null,
        items: prizeItems
          .filter((item) => selectedIds.has(item.id))
          .map((item) => ({
            id: item.id,
            prizeRank: item.prizeRank,
            prizeName: item.prizeName,
            campaignTitle: item.campaignTitle,
            ticketSerialNumber: item.ticketSerialNumber,
          })),
      }
      prizeItems = prizeItems.map((item) =>
        selectedIds.has(item.id)
          ? { ...item, status: 'SHIPMENT_REQUESTED', shipmentId: shipment.id }
          : item,
      )
      shipments = [shipment, ...shipments]
      if (payload.couponId) {
        accountCoupons = accountCoupons.filter((coupon) => coupon.id !== Number(payload.couponId))
      }
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify(shipment),
      })
      return
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(shipments),
    })
  })
  await page.route('**/api/account/draw-orders', async (route) => {
    const payload = route.request().postDataJSON()
    const quantity = Number(payload.quantity || 1)
    const originalPointSpent = quantity * 120
    const discountAmount = payload.couponCode ? 40 : 0
    const pointSpent = originalPointSpent - discountAmount
    remainingTickets = Math.max(0, remainingTickets - quantity)
    drawOrderCreated = true
    currentUser = {
      ...(currentUser || memberUser),
      cashPointBalance: 100,
      bonusPointBalance: 45,
    }
    await route.fulfill({
      status: 201,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 7301,
        campaignId: 101,
        campaignSlug: 'e2e-star-box',
        campaignTitle: '星光測試賞池',
        quantity,
        status: 'COMPLETED',
        originalPointSpent,
        discountAmount,
        pointSpent,
        couponCode: payload.couponCode || null,
        createdAt: '2026-07-02T12:10:00Z',
        results: [
          {
            id: 9001,
            drawOrderId: 7301,
            prizeId: 1001,
            prizeRank: 'A',
            prizeName: '金色星光立牌',
            ticketSerialNumber: 'E2E-0001',
            resultIndex: 1,
            lastPrize: false,
          },
          {
            id: 9002,
            drawOrderId: 7301,
            prizeId: 1003,
            prizeRank: 'C',
            prizeName: '星雲徽章',
            ticketSerialNumber: 'E2E-0002',
            resultIndex: 2,
            lastPrize: false,
          },
        ].slice(0, quantity),
      }),
    })
  })
  await page.route('**/api/banners**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([]),
    })
  })
  await page.route('**/api/campaigns**', async (route) => {
    const requestUrl = new URL(route.request().url())
    if (requestUrl.pathname === '/api/campaigns/e2e-star-box') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(campaignDetail()),
      })
      return
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        content: [campaignSummary()],
        page: 0,
        size: 3,
        totalElements: 1,
        totalPages: 1,
        sort: 'default',
        keyword: null,
        sourceType: null,
        status: null,
      }),
    })
  })
  await page.route('**/api/leaderboard**', async (route) => {
    const requestUrl = new URL(route.request().url())
    if (requestUrl.pathname === '/api/leaderboard/campaigns/e2e-star-box/draws') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(campaignDrawHistory()),
      })
      return
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        liveDraws: [
          {
            drawResultId: 501,
            drawOrderId: 401,
            maskedDisplayName: 'Lu**',
            campaignSlug: 'e2e-star-box',
            campaignTitle: '星光測試賞池',
            prizeRank: 'A',
            prizeName: '壓克力立牌',
            resultIndex: 1,
            drawnAt: '2026-07-02T12:00:00Z',
          },
        ],
        popularCampaigns: [
          {
            campaignId: 101,
            slug: 'e2e-star-box',
            title: '熱門測試賞池',
            status: 'LIVE',
            statusLabel: '開抽中',
            pricePerDraw: 120,
            totalTickets: 80,
            remainingTickets: 24,
            soldTickets: 56,
            soldRate: 70,
            drawCount: 56,
            uniqueDrawers: 9,
            rareHint: 'A賞剩 1',
          },
        ],
        luckyMembers: [],
        generatedAt: '2026-07-02T12:00:00Z',
      }),
    })
  })
  await page.route('**/api/news', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([
        {
          id: 1,
          slug: 'e2e-news',
          title: '測試公告',
          excerpt: '訪客可以閱讀公告摘要。',
          publishedAt: '2026-07-02T12:00:00Z',
        },
      ]),
    })
  })
})

test('visitor browses public homepage and campaign discovery', async ({ page }) => {
  await page.goto('/')
  await expect(page.locator('h1')).toHaveText('LuckyBox 線上抽賞平台')
  await expect(page.getByRole('link', { name: /查看開抽賞池/ })).toBeVisible()
  await expect(page.getByRole('heading', { name: '現在可以抽什麼' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '星光測試賞池' })).toBeVisible()
  await expect(page.getByText('Lu** 抽中 A賞 壓克力立牌')).toBeVisible()
  await expect(page.getByRole('heading', { name: '熱門測試賞池' })).toBeVisible()

  await page.getByLabel('搜尋賞池').first().fill('星光')
  await page.getByRole('button', { name: '搜尋賞池' }).click()

  await expect(page).toHaveURL(/q=%E6%98%9F%E5%85%89/)
  await expect(page.getByText('搜尋「星光」')).toBeVisible()
})

test('captures desktop and mobile homepage screenshots', async ({ page }) => {
  const viewports = [
    { label: 'desktop', width: 1440, height: 960 },
    { label: 'mobile', width: 390, height: 844 },
  ]

  for (const viewport of viewports) {
    await page.setViewportSize({ width: viewport.width, height: viewport.height })
    await page.goto('/')
    await expect(page.locator('h1')).toHaveText('LuckyBox 線上抽賞平台')
    await expect(page.getByRole('heading', { name: '現在可以抽什麼' })).toBeVisible()

    const screenshot = await page.screenshot({ fullPage: true })
    expect(screenshot.byteLength, `${viewport.label} screenshot should not be blank`).toBeGreaterThan(
      12_000,
    )
  }
})

test('visitor navigates to public news', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('link', { name: '公告' }).first().click()

  await expect(page.getByRole('heading', { name: '公告與活動' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '測試公告' })).toBeVisible()
  await expect(page.getByText('訪客可以閱讀公告摘要。')).toBeVisible()
})

test('visitor reads current fairness proof controls', async ({ page }) => {
  await page.goto('/fairness')

  await expect(page.getByRole('heading', { name: '抽賞公平性說明' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '可驗證能力' })).toBeVisible()
  await expect(page.getByText('SHA-256(serverSeed)')).toBeVisible()
  await expect(page.getByText(/hmac-sha256:\{orderId\}:\{index\}:\{proof\}/)).toBeVisible()
  await expect(page.getByText('公開公平性 API')).toBeVisible()
  await expect(page.getByText('後續驗證能力')).toHaveCount(0)
})

test('visitor registers and becomes an authenticated member', async ({ page }) => {
  await page.goto('/register?redirect=/')

  await expect(page.getByRole('heading', { name: '建立會員' })).toBeVisible()
  await page.getByLabel('Email').fill('e2e-member@example.com')
  await page.getByLabel('顯示名稱').fill('E2E 玩家')
  await page.getByRole('textbox', { name: '手機' }).fill('0912345678')
  await page.getByLabel('密碼').fill('Password123!')
  await page.getByRole('button', { name: /建立帳號/ }).click()

  await expect(page).toHaveURL('/')
  await expect(page.getByRole('link', { name: '會員', exact: true })).toBeVisible()
  await expect(page.getByRole('link', { name: /345 LP/ })).toBeVisible()
})

test('visitor logs in and becomes an authenticated member', async ({ page }) => {
  await page.goto('/login?redirect=/')

  await expect(page.getByRole('heading', { name: '登入 LuckyBox' })).toBeVisible()
  await page.getByLabel('Email').fill('e2e-member@example.com')
  await page.getByLabel('密碼').fill('Password123!')
  await page.getByRole('button', { name: /^登入$/ }).click()

  await expect(page).toHaveURL('/')
  await expect(page.getByRole('link', { name: '會員', exact: true })).toBeVisible()
  await expect(page.getByRole('link', { name: /345 LP/ })).toBeVisible()
})

test('member tops up through mock checkout and sees updated wallet', async ({ page }) => {
  await page.goto('/login?redirect=/account/wallet')
  await page.getByLabel('Email').fill('e2e-member@example.com')
  await page.getByLabel('密碼').fill('Password123!')
  await page.getByRole('button', { name: /^登入$/ }).click()

  await expect(page).toHaveURL('/account/wallet')
  await expect(page.getByRole('heading', { name: '點數錢包' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '345 LP' })).toBeVisible()

  await page.getByRole('button', { name: /前往付款/ }).click()

  await expect(page).toHaveURL(/\/payment\/mock\/9101/)
  await expect(page.getByRole('heading', { name: '付款確認' })).toBeVisible()
  await expect(page.getByText('MOCK-E2E-9101')).toBeVisible()
  await page.getByRole('button', { name: /確認付款/ }).click()

  await expect(page.getByRole('heading', { name: '付款成功' })).toBeVisible()
  await expect(page.getByText(/共\s+550 LP/)).toBeVisible()

  await page.getByRole('button', { name: /回到錢包/ }).click()
  await expect(page).toHaveURL(/\/account\/wallet\?payment=success/)
  await expect(page.getByText('付款已完成，點數已更新。')).toBeVisible()
  await expect(page.getByRole('heading', { name: '895 LP' })).toBeVisible()
  await expect(page.getByRole('link', { name: /895 LP/ })).toBeVisible()
})

test('member draws from a campaign and sees refreshed results', async ({ page }) => {
  await page.goto('/login?redirect=/kuji/e2e-star-box')
  await page.getByLabel('Email').fill('e2e-member@example.com')
  await page.getByLabel('密碼').fill('Password123!')
  await page.getByRole('button', { name: /^登入$/ }).click()

  await expect(page).toHaveURL('/kuji/e2e-star-box')
  await expect(page.getByRole('heading', { name: '星光測試賞池' })).toBeVisible()
  await expect(page.locator('.detail-metrics').getByText('24 / 80')).toBeVisible()
  await expect(page.getByRole('link', { name: /345 LP/ })).toBeVisible()

  await page.locator('#drawQuantity').fill('2')
  await page.locator('#drawCouponCode').fill('E2E40')
  await expect(page.locator('.draw-cost').getByText('240 LP')).toBeVisible()
  await page.getByRole('button', { name: /立即抽賞/ }).click()

  await expect(page.getByRole('heading', { name: '確認抽賞內容' })).toBeVisible()
  await expect(page.getByText('2 抽')).toBeVisible()
  await expect(page.getByText('已輸入優惠碼「E2E40」')).toBeVisible()
  await page.getByRole('button', { name: /確認抽賞/ }).click()

  await expect(page.getByText('完成 2 抽，優惠券折抵 40 LP。')).toBeVisible()
  await expect(page.getByRole('heading', { name: '抽賞結果' })).toBeVisible()
  await expect(page.locator('.draw-result-summary').getByText('實扣 200 LP')).toBeVisible()
  await expect(
    page.locator('.draw-result-summary').getByText(/原價 240 LP，折抵 40 LP\s*（E2E40）/),
  ).toBeVisible()
  await expect(page.locator('.draw-result-grid').getByText('金色星光立牌')).toBeVisible()
  await expect(page.locator('.draw-result-grid').getByText('星雲徽章')).toBeVisible()
  await expect(page.locator('.detail-metrics').getByText('22 / 80')).toBeVisible()
  await expect(page.getByRole('link', { name: /145 LP/ })).toBeVisible()
  await expect(
    page.locator('.draw-result-actions').getByRole('link', { name: '查看戰利品' }),
  ).toBeVisible()
})

test('member views prize box and filters shippable prizes', async ({ page }) => {
  await page.goto('/login?redirect=/account/prizes')
  await page.getByLabel('Email').fill('e2e-member@example.com')
  await page.getByLabel('密碼').fill('Password123!')
  await page.getByRole('button', { name: /^登入$/ }).click()

  await expect(page).toHaveURL('/account/prizes')
  await expect(page.getByRole('heading', { name: '戰利品盒' })).toBeVisible()
  await expect(page.locator('.prize-list-panel').getByText('金色星光立牌')).toBeVisible()
  await expect(page.locator('.prize-list-panel').getByText('星雲徽章')).toBeVisible()
  await expect(page.locator('.prize-list-panel').getByText('已申請徽章組')).toBeVisible()
  await expect(page.locator('.prize-box-counts').getByText('可申請出貨')).toBeVisible()
  await expect(page.locator('.prize-box-counts').getByText('已申請出貨')).toBeVisible()

  await page.locator('#statusFilter').selectOption('IN_BOX')

  await expect(page.locator('.prize-list-panel').getByText('金色星光立牌')).toBeVisible()
  await expect(page.locator('.prize-list-panel').getByText('星雲徽章')).toBeVisible()
  await expect(page.locator('.prize-list-panel').getByText('已申請徽章組')).toHaveCount(0)
})

test('member requests shipment from prize box', async ({ page }) => {
  await page.goto('/login?redirect=/account/prizes')
  await page.getByLabel('Email').fill('e2e-member@example.com')
  await page.getByLabel('密碼').fill('Password123!')
  await page.getByRole('button', { name: /^登入$/ }).click()

  await expect(page).toHaveURL('/account/prizes')
  await expect(page.getByRole('heading', { name: '戰利品盒' })).toBeVisible()

  await page.locator('.prize-box-item').filter({ hasText: '金色星光立牌' }).locator('input').check()
  await page.locator('.prize-box-item').filter({ hasText: '星雲徽章' }).locator('input').check()
  await expect(page.locator('.shipment-summary').getByText('2 件')).toBeVisible()
  await expect(page.locator('.shipment-summary').getByText('80 LP')).toBeVisible()

  await expect(page.locator('#freeShippingCoupon')).toBeEnabled()
  await page.locator('#freeShippingCoupon').selectOption('301')
  await expect(page.getByText('本次運費折抵 80 LP')).toBeVisible()
  await expect(page.locator('.shipment-summary').getByText('0 LP')).toBeVisible()
  await page.getByRole('button', { name: '建立出貨申請' }).click()

  await expect(page.getByText('已建立出貨申請 #8201，共 2 件，已套用免運券。')).toBeVisible()
  await expect(page.locator('.shipment-history-panel').getByText('#8201 待處理')).toBeVisible()
  await expect(page.locator('.shipment-history-panel').getByText('0 LP')).toBeVisible()
  await expect(page.locator('.prize-list-panel .badge').filter({ hasText: '已申請' })).toHaveCount(
    3,
  )

  await page.goto('/account/shipments')
  await expect(page.getByRole('heading', { name: '出貨紀錄' })).toBeVisible()
  await expect(page.locator('.account-shipment-list').getByText('#8201 出貨申請')).toBeVisible()
  await expect(page.locator('.account-shipment-list').getByText('金色星光立牌')).toBeVisible()
  await expect(page.locator('.account-shipment-list').getByText('星雲徽章')).toBeVisible()
})

test('admin creates a campaign, generates tickets, and publishes it', async ({ page }) => {
  page.on('dialog', async (dialog) => {
    await dialog.accept()
  })

  await page.goto('/admin/login?redirect=/admin/campaigns')
  await expect(page.getByRole('heading', { name: '登入營運後台' })).toBeVisible()
  await page.getByLabel('Email').fill('admin@example.com')
  await page.getByLabel('密碼').fill('Password123!')
  await page.getByRole('button', { name: /^登入$/ }).click()

  await expect(page).toHaveURL('/admin/campaigns')
  await expect(page.getByRole('heading', { name: '賞池管理' })).toBeVisible()
  await expect(page.getByText('沒有符合條件的賞池')).toBeVisible()
  await page.getByRole('button', { name: '新增賞池' }).click()
  await expect(page).toHaveURL('/admin/campaigns/new')
  await expect(page.getByRole('heading', { name: '新增賞池' })).toBeVisible()

  await page.locator('#campaignSlug').fill('e2e-admin-box')
  await page.locator('#campaignTitle').fill('後台 E2E 賞池')
  await page.locator('#campaignSubtitle').fill('營運建立流程')
  await page.locator('#campaignBrandName').fill('LuckyBox Lab')
  await page.locator('#campaignIpName').fill('E2E IP')
  await page.locator('#campaignPrice').fill('90')
  await page.locator('#campaignTotalTickets').fill('5')
  await page.locator('#campaignCoverImage').fill('/uploads/images/e2e/admin-cover.webp')
  await page.locator('#campaignBannerImage').fill('/uploads/images/e2e/admin-banner.webp')
  await page.locator('#campaignDescription').fill('後台 E2E 建立賞池，驗證主檔、獎項與發布流程。')
  await page.locator('#campaignShippingNote').fill('付款抽賞後可於戰利品盒申請合併出貨。')
  await page.locator('#campaignReturnPolicy').fill('瑕疵請於到貨後 7 日內聯繫客服並保留照片。')
  await page.locator('#campaignSeedHash').fill('e2e-admin-seed-hash')
  await expect(page.locator('#campaignSlug')).toHaveValue('e2e-admin-box')
  await page.getByRole('button', { name: '建立賞池' }).click()

  await expect(page.getByText('賞池「後台 E2E 賞池」已建立。')).toBeVisible()
  await expect(page).toHaveURL('/admin/campaigns/9201')
  await expect(page.locator('.admin-campaign-list').getByText('後台 E2E 賞池')).toBeVisible()
  await expect(page.locator('.admin-publish-checklist').getByText('12 / 16')).toBeVisible()

  await page.locator('#prizeRank').fill('A')
  await page.locator('#prizeName').fill('後台測試立牌')
  await page.locator('#prizeOriginalQuantity').fill('5')
  await page.locator('#prizeImageUrl').fill('/uploads/images/e2e/admin-prize.webp')
  await page.locator('#prizeDescription').fill('由 E2E 建立的普通獎項。')
  await page.getByRole('button', { name: '建立獎項' }).click()

  await expect(page.getByText('獎項「後台測試立牌」已建立。')).toBeVisible()
  await expect(page.locator('.admin-prize-list').getByText('A｜後台測試立牌')).toBeVisible()
  await expect(page.locator('.admin-prize-summary-grid').getByText('普通總籤數')).toBeVisible()
  await expect(page.locator('.admin-publish-checklist').getByText('14 / 16')).toBeVisible()

  await page.getByRole('button', { name: '生成 Ticket' }).click()
  await expect(page.getByText('已產生 5 張 tickets，目前可抽 5 / 5。')).toBeVisible()
  await expect(page.locator('.admin-prize-summary-grid').getByText('可抽 Ticket')).toBeVisible()

  await page.getByRole('button', { name: 'Dry Run' }).click()
  await expect(page.getByText('Dry run 通過，已模擬 5 抽。')).toBeVisible()
  await expect(page.locator('.admin-dry-run-preview').getByText('E2E-ADMIN-0001')).toBeVisible()
  await expect(page.locator('.admin-publish-checklist').getByText('16 / 16')).toBeVisible()

  await expect(page.getByRole('button', { name: '發布上架' })).toBeEnabled()
  await page.getByRole('button', { name: '發布上架' }).click()

  await expect(page.getByText('賞池「後台 E2E 賞池」已發布上架。')).toBeVisible()
  await expect(page.locator('.admin-campaign-list').getByText('開抽中')).toBeVisible()
  await expect(page.getByText('敏感欄位已鎖定')).toBeVisible()
})

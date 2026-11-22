import type { AppMemberPriceVO } from '@/api/promotion'
import type { AppClearancePriceItem, AppClearancePriceVO } from '@/utils/clearance-price'

import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * 列表专区价批量取数（会员价 + 出清价 + 卡面营销标签）的行为守门。
 *
 * 关键契约：
 * - 一次 load 只按本页 SPU 各发一次请求，缓存命中的 SPU 不重复请求；
 * - 会员价按资格三态展示（达标切主价、未达标给引导）；
 * - 出清价与会员价取低、同价会员优先；到期/倒挂忽略；
 * - 卡面标签（新人价/秒杀价）按 SPU 聚合上卡，秒杀硬互斥压过身份价；
 * - 接口失败静默回落原价，不影响列表渲染。
 */

const mocks = vi.hoisted(() => ({
  getCalendarSpecials: vi.fn(),
  getCardTags: vi.fn(),
  getClearanceSpuPrices: vi.fn(),
  getMemberSpuPrices: vi.fn(),
  getPolicyPrices: vi.fn(),
}))

vi.mock('@/api/promotion', () => ({
  getMemberSpuPrices: mocks.getMemberSpuPrices,
}))
vi.mock('@/api/product/spu', () => ({
  getCardTags: mocks.getCardTags,
  getClearanceSpuPrices: mocks.getClearanceSpuPrices,
}))
vi.mock('@/api/promotion/policy-price', () => ({
  getPolicyPrices: mocks.getPolicyPrices,
}))
vi.mock('@/api/promotion/trade-calendar', () => ({
  getCalendarSpecials: mocks.getCalendarSpecials,
}))

const { resetPolicyPriceContextResolver, resetZonePriceCache, setPolicyPriceContextResolver, useZonePrices } = await import('./useZonePrices')

/** 会员价响应：山竹 39.90 → 会员价 35.91（未登录时 eligible=null，只出引导） */
function memberVO(overrides: Partial<AppMemberPriceVO> = {}): AppMemberPriceVO {
  return {
    eligible: true,
    items: [
      {
        memberPrice: 35.91,
        minLevelId: 'level-1',
        minLevelName: '新客会员',
        originalPrice: 39.9,
        skuId: 'sku-303',
        spuId: 'spu-303',
      },
    ],
    userDiscountRate: null,
    userLevelId: 'level-1',
    userLevelName: '新客会员',
    ...overrides,
  }
}

/** 出清价响应（按 spuIds 查价，每行带 spuId） */
function clearanceVO(items: AppClearancePriceItem[]): AppClearancePriceVO {
  return { items }
}

function clearanceItem(overrides: Partial<AppClearancePriceItem> = {}): AppClearancePriceItem {
  return {
    skuId: 'sku-318',
    spuId: 'spu-318',
    salesPrice: 7.9,
    clearancePrice: 5.9,
    clearanceExpireTime: '2099-01-01 00:00:00',
    ...overrides,
  }
}

/** uni storage 桩：登录态指纹比对依赖 uni.getStorageSync('auth') */
const storage: Record<string, unknown> = {}

beforeEach(() => {
  for (const key of Object.keys(storage))
    delete storage[key]
  vi.stubGlobal('uni', {
    getStorageSync: (key: string) => storage[key],
  })
  resetZonePriceCache()
  resetPolicyPriceContextResolver()
  mocks.getMemberSpuPrices.mockReset()
  mocks.getClearanceSpuPrices.mockReset()
  mocks.getCardTags.mockReset()
  mocks.getPolicyPrices.mockReset()
  mocks.getCalendarSpecials.mockReset()
  mocks.getMemberSpuPrices.mockResolvedValue(memberVO())
  mocks.getClearanceSpuPrices.mockResolvedValue({ items: [] })
  mocks.getClearanceSpuPrices.mockResolvedValue(clearanceVO([]))
  mocks.getCardTags.mockResolvedValue([])
  mocks.getPolicyPrices.mockResolvedValue([])
  mocks.getCalendarSpecials.mockResolvedValue([])
})

describe('useZonePrices 列表专区价', () => {
  it('一次 load 按本页 SPU 各发一次请求，命中缓存后不重复请求', async () => {
    const zone = useZonePrices()
    await zone.loadForSpuIds(['spu-303', 'spu-318'])
    expect(mocks.getMemberSpuPrices).toHaveBeenCalledTimes(1)
    expect(mocks.getMemberSpuPrices.mock.calls[0][0]).toEqual(['spu-303', 'spu-318'])
    expect(mocks.getClearanceSpuPrices).toHaveBeenCalledTimes(1)

    await zone.loadForSpuIds(['spu-303'])
    expect(mocks.getMemberSpuPrices).toHaveBeenCalledTimes(1)
    expect(mocks.getClearanceSpuPrices).toHaveBeenCalledTimes(1)
  })

  it('会员价达标：卡片主价切会员价、角标 member', async () => {
    const zone = useZonePrices()
    await zone.loadForSpuIds(['spu-303'])
    const display = zone.cardPriceOf({ id: 'spu-303', price: 39.9, originalPrice: 51.87 })
    expect(display.badgeKind).toBe('member')
    expect(display.mainPrice).toBe(35.91)
    expect(display.strikePrice).toBe(39.9)
  })

  it('未登录/未达标：主价保持售价并给「会员专享」引导', async () => {
    mocks.getMemberSpuPrices.mockResolvedValue(memberVO({ eligible: null, userDiscountRate: null }))
    const zone = useZonePrices()
    await zone.loadForSpuIds(['spu-303'])
    const display = zone.cardPriceOf({ id: 'spu-303', price: 39.9, originalPrice: 51.87 })
    expect(display.badgeKind).toBe('')
    expect(display.mainPrice).toBe(39.9)
    expect(display.strikePrice).toBe(51.87)
    expect(display.guideText).toContain('35.91')
  })

  it('出清价低于会员价：捡漏价胜出', async () => {
    mocks.getClearanceSpuPrices.mockResolvedValue(clearanceVO([
      clearanceItem({ spuId: 'spu-303', skuId: 'sku-303', clearancePrice: 29.9, salesPrice: 39.9 }),
    ]))
    const zone = useZonePrices()
    await zone.loadForSpuIds(['spu-303'])
    const display = zone.cardPriceOf({ id: 'spu-303', price: 39.9 })
    expect(display.badgeKind).toBe('clearance')
    expect(display.mainPrice).toBe(29.9)
    expect(display.strikePrice).toBe(39.9)
  })

  it('出清价到期/倒挂不展示', async () => {
    mocks.getClearanceSpuPrices.mockResolvedValue(clearanceVO([
      clearanceItem({ clearanceExpireTime: '2020-01-01 00:00:00' }),
      clearanceItem({ skuId: 'sku-2', clearancePrice: 9.9, salesPrice: 7.9 }),
    ]))
    const zone = useZonePrices()
    await zone.loadForSpuIds(['spu-318'])
    const display = zone.cardPriceOf({ id: 'spu-318', price: 7.9 })
    expect(display.badgeKind).toBe('')
    expect(display.mainPrice).toBe(7.9)
  })

  it('多规格：按 SKU 出专区成交价（会员达标 / 出清取低）', async () => {
    mocks.getMemberSpuPrices.mockResolvedValue(memberVO({
      items: [
        { memberPrice: 35.91, minLevelId: 'level-1', minLevelName: '新客会员', originalPrice: 39.9, skuId: 'sku-a', spuId: 'spu-303' },
        { memberPrice: 9, minLevelId: 'level-1', minLevelName: '新客会员', originalPrice: 10, skuId: 'sku-b', spuId: 'spu-303' },
      ],
    }))
    mocks.getClearanceSpuPrices.mockResolvedValue(clearanceVO([
      clearanceItem({ skuId: 'sku-b', spuId: 'spu-303', salesPrice: 10, clearancePrice: 9 }),
      clearanceItem({ skuId: 'sku-c', spuId: 'spu-303', salesPrice: 20, clearancePrice: 18 }),
    ]))
    const zone = useZonePrices()
    await zone.loadForSpuIds(['spu-303', 'spu-318'])
    const skuPrices = zone.skuZonePricesOf('spu-303')
    expect(skuPrices['sku-a']).toBe(35.91)
    // sku-b：会员价 9 与出清价 9 同价，归属会员价（值相同，但不得被出清覆盖成别的价格）
    expect(skuPrices['sku-b']).toBe(9)
    expect(skuPrices['sku-c']).toBe(18)
    expect(zone.skuZonePriceOf('spu-303', 'sku-c')).toBe(18)
    expect(zone.skuZonePriceOf('spu-303', 'sku-x')).toBeNull()
  })

  it('登录态切换后缓存整体失效并重新补价（避免游客/旧等级价格残留）', async () => {
    // 游客：未登录引导
    mocks.getMemberSpuPrices.mockResolvedValue(memberVO({ eligible: null, userDiscountRate: null }))
    const zone = useZonePrices()
    await zone.loadForSpuIds(['spu-303'])
    expect(mocks.getMemberSpuPrices).toHaveBeenCalledTimes(1)
    expect(zone.cardPriceOf({ id: 'spu-303', price: 39.9 }).badgeKind).toBe('')

    // 登录成达标会员：同一批 SPU 必须重新请求，展示切换为会员价
    storage.auth = { token: 'token-member-1' }
    mocks.getMemberSpuPrices.mockResolvedValue(memberVO({ eligible: true }))
    await zone.loadForSpuIds(['spu-303'])
    expect(mocks.getMemberSpuPrices).toHaveBeenCalledTimes(2)
    expect(zone.cardPriceOf({ id: 'spu-303', price: 39.9 }).badgeKind).toBe('member')
  })

  it('接口失败静默降级：回落原价，不抛错', async () => {
    mocks.getMemberSpuPrices.mockRejectedValue(new Error('boom'))
    mocks.getClearanceSpuPrices.mockRejectedValue(new Error('boom'))
    const zone = useZonePrices()
    await expect(zone.loadForSpuIds(['spu-303'])).resolves.toBeUndefined()
    const display = zone.cardPriceOf({ id: 'spu-303', price: 39.9, originalPrice: 51.87 })
    expect(display.badgeKind).toBe('')
    expect(display.mainPrice).toBe(39.9)
    expect(display.strikePrice).toBe(51.87)
  })

  it('会员价接口空响应（活动未配置）：不发卡片价格、不崩', async () => {
    mocks.getMemberSpuPrices.mockResolvedValue(null)
    const zone = useZonePrices()
    await zone.loadForSpuIds(['spu-303'])
    const display = zone.cardPriceOf({ id: 'spu-303', price: 39.9 })
    expect(display.mainPrice).toBe(39.9)
    expect(display.badgeKind).toBe('')
  })

  it('card-tags 按本页 SPU 请求一次，缓存命中后不重复请求', async () => {
    const zone = useZonePrices()
    await zone.loadForSpuIds(['spu-303', 'spu-318'])
    expect(mocks.getCardTags).toHaveBeenCalledTimes(1)
    expect(mocks.getCardTags.mock.calls[0][0]).toEqual(['spu-303', 'spu-318'])

    await zone.loadForSpuIds(['spu-303'])
    expect(mocks.getCardTags).toHaveBeenCalledTimes(1)
  })

  it('秒杀价上卡：主价切秒杀价、角标 seckill，压过会员/出清', async () => {
    mocks.getCardTags.mockResolvedValue([{ spuId: 'spu-303', seckillPrice: 16.92 }])
    const zone = useZonePrices()
    await zone.loadForSpuIds(['spu-303'])
    const display = zone.cardPriceOf({ id: 'spu-303', price: 39.9, originalPrice: 51.87 })
    expect(display.badgeKind).toBe('seckill')
    expect(display.mainPrice).toBe(16.92)
    expect(display.strikePrice).toBe(39.9)
  })

  it('新人价上卡（无更高优先级命中）：主价切新人价、角标 newbie', async () => {
    // 会员价未达标（只有引导）：新人价是引擎优先级最低的身份价，必须先让开会员/出清
    mocks.getMemberSpuPrices.mockResolvedValue(memberVO({ eligible: null, userDiscountRate: null }))
    mocks.getCardTags.mockResolvedValue([{ spuId: 'spu-303', newbiePrice: 0.01 }])
    const zone = useZonePrices()
    await zone.loadForSpuIds(['spu-303'])
    const display = zone.cardPriceOf({ id: 'spu-303', price: 39.9, originalPrice: 51.87 })
    expect(display.badgeKind).toBe('newbie')
    expect(display.mainPrice).toBe(0.01)
    expect(display.strikePrice).toBe(39.9)
  })

  it('同 SPU 多 SKU 标签聚合取最低（服务端已聚合，前端防御性再取低）', async () => {
    mocks.getMemberSpuPrices.mockResolvedValue(memberVO({ eligible: null, userDiscountRate: null }))
    mocks.getCardTags.mockResolvedValue([
      { spuId: 'spu-303', newbiePrice: 2 },
      { spuId: 'spu-303', newbiePrice: 1.5 },
    ])
    const zone = useZonePrices()
    await zone.loadForSpuIds(['spu-303'])
    const display = zone.cardPriceOf({ id: 'spu-303', price: 39.9 })
    expect(display.badgeKind).toBe('newbie')
    expect(display.mainPrice).toBe(1.5)
  })

  it('card-tags 失败静默：回落原价不抛错', async () => {
    mocks.getMemberSpuPrices.mockResolvedValue(memberVO({ eligible: null, userDiscountRate: null }))
    mocks.getCardTags.mockRejectedValue(new Error('boom'))
    const zone = useZonePrices()
    await expect(zone.loadForSpuIds(['spu-303'])).resolves.toBeUndefined()
    const display = zone.cardPriceOf({ id: 'spu-303', price: 39.9, originalPrice: 51.87 })
    expect(display.badgeKind).toBe('')
    expect(display.mainPrice).toBe(39.9)
    expect(display.strikePrice).toBe(51.87)
  })

  it('社证文案随 card-tags 透传到卡片展示结果（服务端成文，前端不拼装）', async () => {
    mocks.getCardTags.mockResolvedValue([
      { spuId: 'spu-303', socialProofText: '月4000+人已下单' },
      { spuId: 'spu-318', socialProofText: '  ' },
    ])
    const zone = useZonePrices()
    await zone.loadForSpuIds(['spu-303', 'spu-318'])
    expect(zone.cardPriceOf({ id: 'spu-303', price: 39.9 }).socialProofText).toBe('月4000+人已下单')
    // 空白文案不展示
    expect(zone.cardPriceOf({ id: 'spu-318', price: 7.9 }).socialProofText).toBeNull()
  })

  it('近N天最低价声明随 card-tags 透传（服务端判定成文，前端零逻辑；空白不展示）', async () => {
    mocks.getCardTags.mockResolvedValue([
      { spuId: 'spu-303', priceTagText: '近14天最低价' },
      { spuId: 'spu-318', priceTagText: ' ' },
    ])
    const zone = useZonePrices()
    await zone.loadForSpuIds(['spu-303', 'spu-318'])
    expect(zone.cardPriceOf({ id: 'spu-303', price: 39.9 }).priceTagText).toBe('近14天最低价')
    // 与身份价角标并存：秒杀价命中时声明照常透传
    mocks.getCardTags.mockResolvedValue([
      { spuId: 'spu-319', seckillPrice: 9.9, priceTagText: '近14天最低价' },
    ])
    await zone.loadForSpuIds(['spu-319'])
    const display = zone.cardPriceOf({ id: 'spu-319', price: 39.9 })
    expect(display.badgeKind).toBe('seckill')
    expect(display.priceTagText).toBe('近14天最低价')
    // 空白声明不展示
    expect(zone.cardPriceOf({ id: 'spu-318', price: 7.9 }).priceTagText).toBeNull()
  })

  it('card-tags 失败降级后 priceTagText 置空（商品卡不渲染新元素）', async () => {
    mocks.getCardTags.mockRejectedValue(new Error('boom'))
    const zone = useZonePrices()
    await zone.loadForSpuIds(['spu-303'])
    const display = zone.cardPriceOf({ id: 'spu-303', price: 39.9 })
    expect(display.priceTagText).toBeNull()
    expect(display.socialProofText).toBeNull()
  })

  it('登录态切换后 card-tags 缓存整体失效并重新拉取（新人资格随身份变化）', async () => {
    mocks.getMemberSpuPrices.mockResolvedValue(memberVO({ eligible: null, userDiscountRate: null }))
    mocks.getCardTags.mockResolvedValue([{ spuId: 'spu-303', newbiePrice: 0.01 }])
    const zone = useZonePrices()
    await zone.loadForSpuIds(['spu-303'])
    expect(zone.cardPriceOf({ id: 'spu-303', price: 39.9 }).badgeKind).toBe('newbie')

    // 登录（已下过单的用户）：资格变化必须重新请求，新人价角标消失
    storage.auth = { token: 'token-old-user' }
    mocks.getCardTags.mockResolvedValue([])
    await zone.loadForSpuIds(['spu-303'])
    expect(mocks.getCardTags).toHaveBeenCalledTimes(2)
    const display = zone.cardPriceOf({ id: 'spu-303', price: 39.9 })
    expect(display.badgeKind).toBe('')
    expect(display.mainPrice).toBe(39.9)
  })
})

describe('useZonePrices 协议价（2026-11-14 B2B）', () => {
  it('船上下文 + 登录态：协议价上卡（角标 policy 压过会员价），SKU map 覆盖会员成交价', async () => {
    storage.auth = { token: 'token-1' }
    setPolicyPriceContextResolver(() => ({ vesselId: 'vessel-1' }))
    mocks.getPolicyPrices.mockResolvedValue([
      { skuId: 'sku-303', spuId: 'spu-303', policyPrice: 30, policyName: 'XX船年度协议价', originalPrice: 39.9 },
    ])
    const { loadForSpuIds, cardPriceOf, skuZonePriceOf } = useZonePrices()
    await loadForSpuIds(['spu-303'])

    expect(mocks.getPolicyPrices).toHaveBeenCalledTimes(1)
    const body = mocks.getPolicyPrices.mock.calls[0][0]
    expect(body.vesselId).toBe('vessel-1')
    expect(body.spuIds).toEqual(['spu-303'])

    const card = cardPriceOf({ id: 'spu-303', price: 39.9 })
    expect(card.badgeKind).toBe('policy')
    expect(card.mainPrice).toBe(30)
    expect(card.strikePrice).toBe(39.9)
    expect(skuZonePriceOf('spu-303', 'sku-303')).toBe(30)
  })

  it('无上下文（个人无船）：仍请求协议价（免登端点，服务端清空主体），未命中保持会员价口径', async () => {
    storage.auth = { token: 'token-1' }
    const { loadForSpuIds, cardPriceOf } = useZonePrices()
    await loadForSpuIds(['spu-303'])

    expect(mocks.getPolicyPrices).toHaveBeenCalledTimes(1)
    const body = mocks.getPolicyPrices.mock.calls[0][0]
    expect(body.spuIds).toEqual(['spu-303'])
    expect(body.vesselId).toBeUndefined()
    const card = cardPriceOf({ id: 'spu-303', price: 39.9 })
    expect(card.badgeKind).toBe('member')
  })

  it('未登录（游客）：协议价/特供价照常请求，不带登录门槛', async () => {
    setPolicyPriceContextResolver(() => ({ purchaseScene: '2', vesselId: 'vessel-1' }))
    const { loadForSpuIds } = useZonePrices()
    await loadForSpuIds(['spu-303'])

    expect(mocks.getPolicyPrices).toHaveBeenCalledTimes(1)
    expect(mocks.getPolicyPrices.mock.calls[0][0].vesselId).toBe('vessel-1')
    expect(mocks.getCalendarSpecials).toHaveBeenCalledTimes(1)
    expect(mocks.getCalendarSpecials.mock.calls[0][0]).toEqual({ purchaseScene: '2', vesselId: 'vessel-1' })
  })

  it('协议价起订量 > 1：卡面/单件口径不上协议价（minQty 参与成交校验）', async () => {
    storage.auth = { token: 'token-1' }
    setPolicyPriceContextResolver(() => ({ vesselId: 'vessel-1' }))
    mocks.getPolicyPrices.mockResolvedValue([
      { skuId: 'sku-303', spuId: 'spu-303', policyPrice: 30, minQty: 10, policyName: '整箱协议价', originalPrice: 39.9 },
    ])
    const { loadForSpuIds, cardPriceOf, skuZonePriceOf } = useZonePrices()
    await loadForSpuIds(['spu-303'])

    const card = cardPriceOf({ id: 'spu-303', price: 39.9 })
    expect(card.badgeKind).toBe('member')
    expect(card.mainPrice).toBe(35.91)
    expect(skuZonePriceOf('spu-303', 'sku-303')).toBe(35.91)
  })

  it('协议价接口失败静默：回落会员价口径不抛错', async () => {
    storage.auth = { token: 'token-1' }
    setPolicyPriceContextResolver(() => ({ vesselId: 'vessel-1' }))
    mocks.getPolicyPrices.mockRejectedValue(new Error('boom'))
    const { loadForSpuIds, cardPriceOf } = useZonePrices()
    await loadForSpuIds(['spu-303'])

    const card = cardPriceOf({ id: 'spu-303', price: 39.9 })
    expect(card.badgeKind).toBe('member')
    expect(card.mainPrice).toBe(35.91)
  })
})

/** 特供响应：服务端已按场景/船舶圈定并剔除限量耗尽活动 */
function specialVO(price: number, overrides: Record<string, unknown> = {}) {
  return [{
    activityId: 'act-1',
    activityName: '开渔档期特供',
    calendarId: 'cal-1',
    calendarName: '开渔档期',
    endTime: '2099-01-01 00:00:00',
    items: [{ skuId: 'sku-303', spuId: 'spu-303', price, originalPrice: 39.9, ...overrides }],
  }]
}

describe('useZonePrices 档期特供补价（2026-11-14 B2B）', () => {
  it('特供价低于会员价：卡面切特供价、角标 special，SKU 成交价同源', async () => {
    mocks.getCalendarSpecials.mockResolvedValue(specialVO(30))
    const { loadForSpuIds, cardPriceOf, skuZonePriceOf } = useZonePrices()
    await loadForSpuIds(['spu-303'])

    const card = cardPriceOf({ id: 'spu-303', price: 39.9 })
    expect(card.badgeKind).toBe('special')
    expect(card.mainPrice).toBe(30)
    expect(card.strikePrice).toBe(39.9)
    expect(skuZonePriceOf('spu-303', 'sku-303')).toBe(30)
  })

  it('特供与会员同价：归属特供；特供高于会员价时仍取会员价', async () => {
    mocks.getCalendarSpecials.mockResolvedValue(specialVO(35.91))
    const zone = useZonePrices()
    await zone.loadForSpuIds(['spu-303'])
    expect(zone.cardPriceOf({ id: 'spu-303', price: 39.9 }).badgeKind).toBe('special')

    resetZonePriceCache()
    mocks.getCalendarSpecials.mockResolvedValue(specialVO(38))
    await zone.loadForSpuIds(['spu-303'])
    const card = zone.cardPriceOf({ id: 'spu-303', price: 39.9 })
    expect(card.badgeKind).toBe('member')
    expect(card.mainPrice).toBe(35.91)
  })

  it('特供价倒挂/脏数据忽略；协议价命中时特供不覆盖', async () => {
    mocks.getCalendarSpecials.mockResolvedValue(specialVO(39.9))
    const zone = useZonePrices()
    await zone.loadForSpuIds(['spu-303'])
    expect(zone.cardPriceOf({ id: 'spu-303', price: 39.9 }).badgeKind).toBe('member')

    resetZonePriceCache()
    storage.auth = { token: 'token-1' }
    setPolicyPriceContextResolver(() => ({ vesselId: 'vessel-1' }))
    mocks.getCalendarSpecials.mockResolvedValue(specialVO(20))
    mocks.getPolicyPrices.mockResolvedValue([
      { skuId: 'sku-303', spuId: 'spu-303', policyPrice: 33, policyName: 'XX船年度协议价', originalPrice: 39.9 },
    ])
    await zone.loadForSpuIds(['spu-303'])
    const card = zone.cardPriceOf({ id: 'spu-303', price: 39.9 })
    expect(card.badgeKind).toBe('policy')
    expect(card.mainPrice).toBe(33)
  })

  it('特供接口失败静默：回落会员价不抛错；options.special=false 时不请求', async () => {
    mocks.getCalendarSpecials.mockRejectedValue(new Error('boom'))
    const zone = useZonePrices()
    await expect(zone.loadForSpuIds(['spu-303'])).resolves.toBeUndefined()
    expect(zone.cardPriceOf({ id: 'spu-303', price: 39.9 }).badgeKind).toBe('member')

    resetZonePriceCache()
    mocks.getCalendarSpecials.mockClear()
    await zone.loadForSpuIds(['spu-303'], { special: false })
    expect(mocks.getCalendarSpecials).not.toHaveBeenCalled()
  })

  it('同一上下文整量缓存复用：第二次 load 不重复请求特供', async () => {
    setPolicyPriceContextResolver(() => ({ purchaseScene: '2', vesselId: 'vessel-1' }))
    mocks.getCalendarSpecials.mockResolvedValue(specialVO(30))
    const zone = useZonePrices()
    await zone.loadForSpuIds(['spu-303'])
    await zone.loadForSpuIds(['spu-318'])
    expect(mocks.getCalendarSpecials).toHaveBeenCalledTimes(1)
  })

  it('切船后缓存整体失效：不得继续显示上一条船的合同价/特供价', async () => {
    storage.auth = { token: 'token-1' }
    let vesselId = 'vessel-1'
    setPolicyPriceContextResolver(() => ({ purchaseScene: '2', vesselId }))
    mocks.getPolicyPrices.mockResolvedValue([
      { skuId: 'sku-303', spuId: 'spu-303', policyPrice: 30, policyName: 'A船协议价', originalPrice: 39.9 },
    ])
    mocks.getCalendarSpecials.mockResolvedValue(specialVO(28))
    const zone = useZonePrices()
    await zone.loadForSpuIds(['spu-303'])
    expect(zone.cardPriceOf({ id: 'spu-303', price: 39.9 }).badgeKind).toBe('policy')

    // 切到 B 船：该船无协议价/特供 → 必须重新请求并回落会员价，而不是残留 A 船合同价
    vesselId = 'vessel-2'
    mocks.getPolicyPrices.mockResolvedValue([])
    mocks.getCalendarSpecials.mockResolvedValue([])
    await zone.loadForSpuIds(['spu-303'])

    expect(mocks.getPolicyPrices).toHaveBeenCalledTimes(2)
    expect(mocks.getPolicyPrices.mock.calls[1][0].vesselId).toBe('vessel-2')
    expect(mocks.getCalendarSpecials).toHaveBeenCalledTimes(2)
    expect(mocks.getCalendarSpecials.mock.calls[1][0].vesselId).toBe('vessel-2')
    const card = zone.cardPriceOf({ id: 'spu-303', price: 39.9 })
    expect(card.badgeKind).toBe('member')
    expect(card.mainPrice).toBe(35.91)
    expect(zone.skuZonePriceOf('spu-303', 'sku-303')).toBe(35.91)
  })

  it('切采购场景（个人 → 船供）同样失效特供缓存并带上场景参数', async () => {
    let purchaseScene = '1'
    setPolicyPriceContextResolver(() => ({ purchaseScene }))
    mocks.getCalendarSpecials.mockResolvedValue([])
    const zone = useZonePrices()
    await zone.loadForSpuIds(['spu-303'])
    expect(mocks.getCalendarSpecials.mock.calls[0][0].purchaseScene).toBe('1')

    purchaseScene = '2'
    mocks.getCalendarSpecials.mockResolvedValue(specialVO(30))
    await zone.loadForSpuIds(['spu-303'])
    expect(mocks.getCalendarSpecials).toHaveBeenCalledTimes(2)
    expect(mocks.getCalendarSpecials.mock.calls[1][0].purchaseScene).toBe('2')
    expect(zone.cardPriceOf({ id: 'spu-303', price: 39.9 }).badgeKind).toBe('special')
  })
})

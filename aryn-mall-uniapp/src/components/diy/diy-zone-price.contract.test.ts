import type { AppClearancePriceItem, AppClearancePriceVO } from '@/utils/clearance-price'
import type { AppMemberPriceVO } from '@/api/promotion'
import type { ListMemberPriceDisplay } from '@/components/diy/retail-types'

import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

import { beforeEach, describe, expect, it, vi } from 'vitest'

import { resetZonePriceCache, useZonePrices } from '@/composables/useZonePrices'
import { resolveListZonePrice } from '@/utils/zone-price'

/**
 * 装修（DIY）商品楼层接入会员价/捡漏价的守门测试。
 *
 * 背景（真实价格不一致）：会员专区/低价捡漏展示了会员价/捡漏价，但同一商品出现在
 * 首页其它 Tab、商品分组、排行、推荐、瀑布流、横滑条、新人楼层时只显示原售价，
 * 用户看到同一个商品两种价格。修法是把「会员价/出清价 vs 原价」的判定收敛到共享
 * 口径（`utils/zone-price` + `composables/useZonePrices`），各楼层只做数据接线与渲染。
 *
 * 这里钉两件事：
 * 1. 源码契约：每个楼层都必须真的补价（`zone.loadForSpuIds`）并用共享口径取主价/划线/
 *    角标/引导，quick-cart 透传 SKU 级专区价——类型检查发现不了这类跨文件断线；
 * 2. 展示口径：非会员 Tab 出清价、达标会员价、未登录引导、会员/出清取低，
 *    用真实 composable + mock 接口复算楼层卡片口径。
 */
const projectRoot = fileURLToPath(new URL('../../../', import.meta.url))

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

const TABS = 'src/components/diy/diy-goods-tabs/index.vue'

/** 接入专区价的装修商品楼层（会员价/捡漏价 + 划线 + 未达标引导） */
const ZONE_PRICE_FLOORS = [
  'src/components/diy/diy-goods/index.vue',
  'src/components/diy/diy-goods-group/index.vue',
  'src/components/diy/diy-goods-ranking/index.vue',
  'src/components/diy/diy-goods-recommend/index.vue',
  'src/components/diy/diy-goods-waterfall/index.vue',
  'src/components/diy/diy-goods-scroll/index.vue',
]

/** 带快捷加购按钮、需要把 SKU 级专区价透传给弹层的楼层 */
const QUICK_CART_FLOORS = [
  TABS,
  'src/components/diy/diy-goods/index.vue',
  'src/components/diy/diy-goods-group/index.vue',
  'src/components/diy/diy-goods-waterfall/index.vue',
]

const NEWBIE_FLOOR = 'src/components/diy/diy-newbie-floor/index.vue'

describe('装修商品楼层专区价接线契约', () => {
  it.each(ZONE_PRICE_FLOORS)('%s 列表加载后批量补会员价/出清价，不阻塞渲染', (file) => {
    const code = source(file)
    expect(code).toContain('useZonePrices')
    // 必须真的发起补价：只引入 composable 不调用 = 卡片永远原价
    expect(code).toContain('zone.loadForSpuIds(')
    // 列表先渲染、价格后补：补价不能 await 阻塞首屏
    expect(code).toMatch(/void zone\.loadForSpuIds\(/)
  })

  it('新人价楼层经 useNewbieGoodsActions 接线补价（2026-11-08 逻辑下沉 composable）', () => {
    const floor = source(NEWBIE_FLOOR)
    // 楼层不再直持 useZonePrices：卡片展示价与「抢」加购统一走 composable
    expect(floor).toContain('useNewbieGoodsActions')
    expect(floor).toContain('loadZonePrices')
    // composable 内部必须真的发起补价且不阻塞首屏
    const actions = source('src/composables/useNewbieGoodsActions.ts')
    expect(actions).toContain('useZonePrices')
    expect(actions).toMatch(/void zone\.loadForSpuIds\(/)
  })

  it.each(ZONE_PRICE_FLOORS)('%s 主价/划线/角标/引导都走共享 zone-price 口径', (file) => {
    const code = source(file)
    // 主价与划线不再直读 item.price / originalPrice，而是消费共享展示结果
    expect(code).toMatch(/zone\.cardPriceOf\(|resolveListZonePrice\(/)
    expect(code).toContain('zonePriceBadgeText(')
    // 专区价是成交价：主价/角标不受「显示原价」开关影响，只有划线受它约束
    expect(code).toMatch(/cardStrikePrice\(/)
    expect(code).toMatch(/cardGuideText\(|cardMemberGuide\(/)
  })

  it('新人价楼层：命中更高优先级身份价时切换主价/角标并给加购弹层同一成交价', () => {
    const floor = source(NEWBIE_FLOOR)
    const actions = source('src/composables/useNewbieGoodsActions.ts')
    // 会员价/出清价优先级高于新人价：命中时卡片展示身份价并给角标（判定下沉 composable）
    expect(actions).toContain('resolveListZonePrice(')
    expect(floor).toContain('cardZoneBadgeText')
    // 「抢」弹层单价取身份价（SKU 级优先），避免「卡片一个价、弹层另一个价」
    expect(actions).toContain('zone.skuZonePriceOf(')
    expect(actions).toContain('identityPriced')
  })

  it('所有 Tab 统一补会员价/出清价，会员专区卡片优先用数据层会员价', () => {
    const tabs = source(TABS)
    // 会员专区 Tab 也要补一次价格：卡片加购弹层按 SKU 取价，依赖模块级价格缓存，
    // 少补一次会出现「卡片会员价、弹层普通价」的漂移；共享层有 60s 缓存与并发合并
    expect(tabs).toContain('zone.loadForSpuIds(ids)')
    // 会员专区 Tab 卡片优先用数据层（retail-data）按活动货盘补好的会员价，避免两套口径
    expect(tabs).toContain('item?.memberPriceDisplay')
    // 出清价对所有 Tab 生效（不只低价捡漏 Tab）
    expect(tabs).toContain('zone.clearanceDisplayOf(')
    // 会员价 vs 出清价取低、同价会员优先由共享纯函数收敛
    expect(tabs).toContain('resolveListZonePrice(')
    // 会员专区内会员本人的专区价兜底口径保留（有真实划线才标会员价）
    expect(tabs).toContain('memberZonePricing')
    expect(tabs).toContain('item.memberPriceDisplay')
  })

  it('会员专区 Tab 的会员价取数据层聚合结果，其它 Tab 取批量补价缓存', () => {
    const tabs = source(TABS)
    const resolver = tabs.slice(
      tabs.indexOf('function memberDisplayOf('),
      tabs.indexOf('/** 卡片是否按会员价样式呈现'),
    )
    expect(resolver).toContain('item?.memberPriceDisplay')
    expect(resolver).toContain('zone.memberDisplayOf(')
  })

  it.each(QUICK_CART_FLOORS)('%s 快捷加购透传 SKU 级专区价', (file) => {
    expect(source(file)).toContain(':zone-prices="zone.skuZonePricesOf(')
  })
})

// ============ 展示口径（真实 composable + mock 接口 ============

const mocks = vi.hoisted(() => ({
  getClearanceSpuPrices: vi.fn(),
  getMemberSpuPrices: vi.fn(),
}))

vi.mock('@/api/promotion', () => ({
  getMemberSpuPrices: mocks.getMemberSpuPrices,
}))
vi.mock('@/api/product/spu', () => ({
  getClearanceSpuPrices: mocks.getClearanceSpuPrices,
}))

/** 会员价响应：售价 39.90 → 会员价 35.91 */
function memberVO(overrides: Partial<AppMemberPriceVO> = {}): AppMemberPriceVO {
  return {
    eligible: true,
    items: [
      {
        memberPrice: 35.91,
        minLevelId: 'level-1',
        minLevelName: '新客会员',
        originalPrice: 39.9,
        skuId: 'sku-1',
        spuId: 'spu-1',
      },
    ],
    userDiscountRate: null,
    userLevelId: 'level-1',
    userLevelName: '新客会员',
    ...overrides,
  }
}

function clearanceItem(overrides: Partial<AppClearancePriceItem> = {}): AppClearancePriceItem {
  return {
    skuId: 'sku-1',
    spuId: 'spu-1',
    salesPrice: 39.9,
    clearancePrice: 33.9,
    clearanceExpireTime: '2099-01-01 00:00:00',
    ...overrides,
  }
}

function clearanceVO(items: AppClearancePriceItem[]): AppClearancePriceVO {
  return { items }
}

/** 楼层卡片口径复算：与 diy-goods-tabs 的 cardDisplay 同一套共享纯函数 */
function cardDisplay(
  item: { id: string, price: number, originalPrice?: number },
  showOriginal = false,
) {
  const zone = useZonePrices()
  return resolveListZonePrice({
    salesPrice: item.price,
    originalPrice: item.originalPrice,
    showOriginal,
    memberDisplay: zone.memberDisplayOf(item.id, item.price),
    clearanceDisplay: zone.clearanceDisplayOf(item.id, item.price),
  })
}

/** 会员专区 Tab 的口径复算：会员价用数据层按活动补好的结果，出清价用批量补价 */
function tabsCardDisplay(
  item: { id: string, price: number, originalPrice?: number },
  legacyMember: ListMemberPriceDisplay | undefined,
  showOriginal = false,
) {
  const zone = useZonePrices()
  const memberDisplay = legacyMember || zone.memberDisplayOf(item.id, item.price)
  return resolveListZonePrice({
    salesPrice: item.price,
    originalPrice: item.originalPrice,
    showOriginal,
    memberDisplay,
    clearanceDisplay: zone.clearanceDisplayOf(item.id, item.price),
  })
}

beforeEach(() => {
  resetZonePriceCache()
  mocks.getMemberSpuPrices.mockReset()
  mocks.getClearanceSpuPrices.mockReset()
  mocks.getMemberSpuPrices.mockResolvedValue(memberVO())
  mocks.getClearanceSpuPrices.mockResolvedValue(clearanceVO([]))
})

describe('装修楼层卡片专区价展示口径', () => {
  it('非会员 Tab 也展示出清价：主价=捡漏价、划线=当前售价、角标=捡漏价', async () => {
    mocks.getClearanceSpuPrices.mockResolvedValue(clearanceVO([clearanceItem()]))
    const zone = useZonePrices()
    await zone.loadForSpuIds(['spu-1'])

    const display = cardDisplay({ id: 'spu-1', price: 39.9, originalPrice: 59.9 }, true)

    expect(display.badgeKind).toBe('clearance')
    expect(display.mainPrice).toBe(33.9)
    expect(display.strikePrice).toBe(39.9)
    expect(display.guideText).toBeNull()
  })

  it('会员资格达标：主价切会员价、划线取当前售价、角标=会员价', async () => {
    const zone = useZonePrices()
    await zone.loadForSpuIds(['spu-1'])

    const display = cardDisplay({ id: 'spu-1', price: 39.9, originalPrice: 59.9 }, true)

    expect(display.badgeKind).toBe('member')
    expect(display.mainPrice).toBe(35.91)
    expect(display.strikePrice).toBe(39.9)
    expect(display.guideText).toBeNull()
  })

  it('未登录（资格未知）：主价不动，只给「会员专享 ¥x」引导', async () => {
    mocks.getMemberSpuPrices.mockResolvedValue(memberVO({ eligible: null, userLevelId: null, userLevelName: null }))
    const zone = useZonePrices()
    await zone.loadForSpuIds(['spu-1'])

    const display = cardDisplay({ id: 'spu-1', price: 39.9, originalPrice: 59.9 })

    expect(display.badgeKind).toBe('')
    expect(display.mainPrice).toBe(39.9)
    expect(display.guideText).toBe('会员专享 ¥35.91')
  })

  it('会员价与出清价取低：出清价更低时走捡漏价', async () => {
    mocks.getMemberSpuPrices.mockResolvedValue(memberVO({
      items: [
        { memberPrice: 36.9, minLevelId: 'level-1', minLevelName: '新客会员', originalPrice: 39.9, skuId: 'sku-1', spuId: 'spu-1' },
      ],
    }))
    mocks.getClearanceSpuPrices.mockResolvedValue(clearanceVO([clearanceItem({ clearancePrice: 33.9 })]))
    const zone = useZonePrices()
    await zone.loadForSpuIds(['spu-1'])

    const display = cardDisplay({ id: 'spu-1', price: 39.9 })

    expect(display.badgeKind).toBe('clearance')
    expect(display.mainPrice).toBe(33.9)
  })

  it('会员价更低时走会员价；同价时会员价优先（与结算同一口径）', async () => {
    mocks.getMemberSpuPrices.mockResolvedValue(memberVO({
      items: [
        { memberPrice: 33.9, minLevelId: 'level-1', minLevelName: '新客会员', originalPrice: 39.9, skuId: 'sku-1', spuId: 'spu-1' },
      ],
    }))
    mocks.getClearanceSpuPrices.mockResolvedValue(clearanceVO([clearanceItem({ clearancePrice: 35.9 })]))
    const zone = useZonePrices()
    await zone.loadForSpuIds(['spu-1'])

    const cheaperMember = cardDisplay({ id: 'spu-1', price: 39.9 })
    expect(cheaperMember.badgeKind).toBe('member')
    expect(cheaperMember.mainPrice).toBe(33.9)

    // 同价：出清价不再是严格更低 → 归属会员价
    mocks.getClearanceSpuPrices.mockResolvedValue(clearanceVO([clearanceItem({ clearancePrice: 33.9 })]))
    resetZonePriceCache()
    mocks.getMemberSpuPrices.mockResolvedValue(memberVO({
      items: [
        { memberPrice: 33.9, minLevelId: 'level-1', minLevelName: '新客会员', originalPrice: 39.9, skuId: 'sku-1', spuId: 'spu-1' },
      ],
    }))
    await zone.loadForSpuIds(['spu-1'])

    const tied = cardDisplay({ id: 'spu-1', price: 39.9 })
    expect(tied.badgeKind).toBe('member')
    expect(tied.mainPrice).toBe(33.9)
  })

  it('会员专区 Tab 只补出清价：不重复请求会员价，出清价仍能展示', async () => {
    mocks.getClearanceSpuPrices.mockResolvedValue(clearanceVO([clearanceItem()]))
    const zone = useZonePrices()
    await zone.loadForSpuIds(['spu-1'], { member: false })

    expect(mocks.getMemberSpuPrices).not.toHaveBeenCalled()
    expect(mocks.getClearanceSpuPrices).toHaveBeenCalledTimes(1)
    expect(cardDisplay({ id: 'spu-1', price: 39.9 }).badgeKind).toBe('clearance')
  })

  it('显示原价开关关闭：只隐藏划线，会员价主价与角标照常展示', async () => {
    const zone = useZonePrices()
    await zone.loadForSpuIds(['spu-1'])

    const hidden = cardDisplay({ id: 'spu-1', price: 39.9 }, false)
    // 主价/角标不受「显示原价」开关影响：专区价是成交价，必须展示
    expect(hidden.badgeKind).toBe('member')
    expect(hidden.mainPrice).toBe(35.91)
    expect(hidden.strikePrice).toBeNull()

    const shown = cardDisplay({ id: 'spu-1', price: 39.9 }, true)
    expect(shown.strikePrice).toBe(39.9)
  })

  it('普通价兜底：未命中身份价时保持原价，原价高于售价且开了显示原价才划线', async () => {
    mocks.getMemberSpuPrices.mockResolvedValue(null)
    mocks.getClearanceSpuPrices.mockResolvedValue(clearanceVO([]))
    const zone = useZonePrices()
    await zone.loadForSpuIds(['spu-2'])

    const display = cardDisplay({ id: 'spu-2', price: 19.9, originalPrice: 29.9 }, true)
    expect(display.badgeKind).toBe('')
    expect(display.mainPrice).toBe(19.9)
    expect(display.strikePrice).toBe(29.9)
    expect(display.guideText).toBeNull()
  })

  it('会员专区 Tab 用数据层补好的会员价，与出清价仍取低；不再重复请求会员价', async () => {
    mocks.getClearanceSpuPrices.mockResolvedValue(clearanceVO([clearanceItem()]))
    const zone = useZonePrices()
    // 会员专区 Tab 由数据层补会员价，组件只补出清价
    await zone.loadForSpuIds(['spu-1'], { member: false })
    expect(mocks.getMemberSpuPrices).not.toHaveBeenCalled()

    const legacyMember: ListMemberPriceDisplay = {
      badgeText: '会员价',
      guideText: null,
      matched: true,
      memberPrice: 35.91,
      minLevelName: '新客会员',
      showMemberPrice: true,
      strikePrice: 39.9,
    }
    // 出清价 33.9 < 会员价 35.91 → 捡漏价胜出（与低价捡漏/结算同口径）
    const clearanceWins = tabsCardDisplay({ id: 'spu-1', price: 39.9 }, legacyMember, true)
    expect(clearanceWins.badgeKind).toBe('clearance')
    expect(clearanceWins.mainPrice).toBe(33.9)

    // 无出清价时回到数据层会员价
    mocks.getClearanceSpuPrices.mockResolvedValue(clearanceVO([]))
    resetZonePriceCache()
    await zone.loadForSpuIds(['spu-1'], { member: false })
    const memberWins = tabsCardDisplay({ id: 'spu-1', price: 39.9 }, legacyMember, true)
    expect(memberWins.badgeKind).toBe('member')
    expect(memberWins.mainPrice).toBe(35.91)
    expect(memberWins.strikePrice).toBe(39.9)
  })
})

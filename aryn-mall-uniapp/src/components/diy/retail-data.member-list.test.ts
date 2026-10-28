import type { GoodsTabConfig } from './retail-types'

import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * 会员专区 Tab 列表会员价补充的取数行为守门（F3 §8.4 列表侧）。
 *
 * 行为契约（产品刻意耦合）：只有 `memberBanner.enabled === true` 的 Tab 才会按
 * 本页去重 spuIds 批量补会员价——横幅承诺「本区享会员价」，卡片必须有会员价，
 * 两者同开同关；其它 Tab 一律不发请求、不加字段。接口失败必须静默降级为原价。
 */

const mocks = vi.hoisted(() => ({
  getActivityById: vi.fn(),
  getActivityPage: vi.fn(),
  getById: vi.fn(),
  getByIds: vi.fn(),
  getCurrentNewbieFloor: vi.fn(),
  getCurrentShop: vi.fn(),
  getDiscountActivities: vi.fn(),
  getMemberActivityGoods: vi.fn(),
  getMemberSpuPrices: vi.fn(),
  getPage: vi.fn(),
  getSeckillSessions: vi.fn(),
  getSessionGoods: vi.fn(),
}))

vi.mock('@/api/product/spu', () => ({
  getById: mocks.getById,
  getByIds: mocks.getByIds,
  getPage: mocks.getPage,
}))
vi.mock('@/api/promotion', () => ({
  getCurrentNewbieFloor: mocks.getCurrentNewbieFloor,
  getDiscountActivities: mocks.getDiscountActivities,
  getMemberActivityGoods: mocks.getMemberActivityGoods,
  getMemberSpuPrices: mocks.getMemberSpuPrices,
  getSeckillSessions: mocks.getSeckillSessions,
  getSessionGoods: mocks.getSessionGoods,
}))
vi.mock('@/api/promotion/groupBuyActivity', () => ({
  getActivityById: mocks.getActivityById,
  getActivityPage: mocks.getActivityPage,
}))
vi.mock('@/api/upms/tenant', () => ({
  getCurrentShop: mocks.getCurrentShop,
}))

const { loadGoodsTabPage, MEMBER_LIST_PRICE_SPU_LIMIT } = await import('./retail-data')

/** 造一条列表接口的 SPU（只带 normalizeRetailGoods 需要的字段） */
function spu(id: string, salesPrice: number, extra: Record<string, unknown> = {}) {
  return {
    id,
    name: `商品${id}`,
    originalPrice: Number((salesPrice * 1.3).toFixed(2)),
    salesVolume: 1,
    spuUrls: [`${id}.jpg`],
    stock: 10,
    salesPrice,
    ...extra,
  }
}

function tab(overrides: Partial<GoodsTabConfig> = {}): GoodsTabConfig {
  return {
    dataSource: { mode: 'automatic', sort: 'create_time', targetIds: [] },
    id: 'member',
    memberBanner: { enabled: true },
    title: '会员专区',
    ...overrides,
  }
}

/** 造一份「达标且规格价与卡片价一致」的会员价响应 */
function memberVO(overrides: Record<string, unknown> = {}) {
  return {
    eligible: true,
    items: [
      { memberPrice: 23.92, minLevelId: 'l1', minLevelName: '金卡会员', originalPrice: 29.9, skuId: 'sku-1', spuId: 'spu-1' },
    ],
    userLevelId: 'l1',
    userLevelName: '金卡会员',
    ...overrides,
  }
}

beforeEach(() => {
  vi.clearAllMocks()
  mocks.getPage.mockResolvedValue({
    records: [spu('spu-1', 29.9), spu('spu-2', 9.9)],
    total: 2,
  })
  mocks.getByIds.mockResolvedValue([])
  mocks.getMemberSpuPrices.mockResolvedValue(memberVO())
})

describe('会员专区 Tab 列表补价：请求门控', () => {
  it('enabled=true：按本页去重 spuIds 一次批量补价，并合并展示结果', async () => {
    const result = await loadGoodsTabPage(tab(), 1, 10)

    expect(mocks.getMemberSpuPrices).toHaveBeenCalledTimes(1)
    expect(mocks.getMemberSpuPrices).toHaveBeenCalledWith(['spu-1', 'spu-2'])

    const [first, second] = result.list
    expect(first.memberPriceDisplay).toMatchObject({
      badgeText: '会员价',
      guideText: null,
      matched: true,
      memberPrice: 23.92,
      showMemberPrice: true,
      strikePrice: 29.9,
    })
    // spu-2 没有会员价 item → 完全不动（不写字段）
    expect(second.memberPriceDisplay).toBeUndefined()
    expect(result.manual).toBe(false)
  })

  it('未开 memberBanner（缺省 / enabled=false）：不发请求、不加字段', async () => {
    const withoutBanner = await loadGoodsTabPage(tab({ memberBanner: undefined }), 1, 10)
    expect(mocks.getMemberSpuPrices).not.toHaveBeenCalled()
    expect(withoutBanner.list.every(item => item.memberPriceDisplay === undefined)).toBe(true)

    const disabled = await loadGoodsTabPage(tab({ memberBanner: { enabled: false } }), 1, 10)
    expect(mocks.getMemberSpuPrices).not.toHaveBeenCalled()
    expect(disabled.list.every(item => item.memberPriceDisplay === undefined)).toBe(true)
  })

  it('本页无商品：不发请求', async () => {
    mocks.getPage.mockResolvedValue({ records: [], total: 0 })

    const result = await loadGoodsTabPage(tab(), 1, 10)

    expect(result.list).toEqual([])
    expect(mocks.getMemberSpuPrices).not.toHaveBeenCalled()
  })

  it('去重后一次最多补充 MEMBER_LIST_PRICE_SPU_LIMIT 个 SPU', async () => {
    const records = Array.from({ length: MEMBER_LIST_PRICE_SPU_LIMIT + 1 }, (_, index) => spu(`spu-${index + 1}`, 9.9))
    // 同一 SPU 重复出现不应重复请求
    records.push(spu('spu-1', 9.9))
    mocks.getPage.mockResolvedValue({ records, total: records.length })

    await loadGoodsTabPage(tab(), 1, records.length)

    expect(mocks.getMemberSpuPrices).toHaveBeenCalledTimes(1)
    const requested = mocks.getMemberSpuPrices.mock.calls[0][0] as string[]
    expect(requested).toHaveLength(MEMBER_LIST_PRICE_SPU_LIMIT)
    expect(requested[0]).toBe('spu-1')
    expect(new Set(requested).size).toBe(requested.length)
  })
})

describe('会员专区 Tab 列表补价：失败静默', () => {
  it('接口抛错：列表照常返回原价，不向上抛错', async () => {
    mocks.getMemberSpuPrices.mockRejectedValue(new Error('mock network error'))

    const result = await loadGoodsTabPage(tab(), 1, 10)

    expect(result.list).toHaveLength(2)
    expect(result.list.every(item => item.memberPriceDisplay === undefined)).toBe(true)
    expect(result.total).toBe(2)
  })

  it('接口返回 null（无命中）：列表照常返回原价', async () => {
    mocks.getMemberSpuPrices.mockResolvedValue(null)

    const result = await loadGoodsTabPage(tab(), 1, 10)

    expect(result.list).toHaveLength(2)
    expect(result.list[0].memberPriceDisplay).toBeUndefined()
  })
})

describe('会员专区 Tab 列表补价：展示口径', () => {
  it('未登录（eligible=null）：卡片只带引导文案，不切主价', async () => {
    mocks.getMemberSpuPrices.mockResolvedValue(memberVO({ eligible: null, userLevelId: null, userLevelName: null }))

    const [first] = (await loadGoodsTabPage(tab(), 1, 10)).list

    expect(first.memberPriceDisplay).toMatchObject({
      badgeText: '',
      guideText: '会员专享 ¥23.92',
      showMemberPrice: false,
      strikePrice: null,
    })
  })

  it('已登录但不达标（eligible=false）：同样只加引导', async () => {
    mocks.getMemberSpuPrices.mockResolvedValue(memberVO({ eligible: false }))

    const [first] = (await loadGoodsTabPage(tab(), 1, 10)).list

    expect(first.memberPriceDisplay?.showMemberPrice).toBe(false)
    expect(first.memberPriceDisplay?.guideText).toBe('会员专享 ¥23.92')
  })

  it('会员价配在更贵规格（规格价 ≠ 卡片价）：卡片完全不动', async () => {
    mocks.getMemberSpuPrices.mockResolvedValue(memberVO({
      items: [
        { memberPrice: 30, minLevelId: 'l1', minLevelName: '金卡会员', originalPrice: 39.9, skuId: 'sku-9', spuId: 'spu-1' },
      ],
    }))

    const [first] = (await loadGoodsTabPage(tab(), 1, 10)).list

    expect(first.memberPriceDisplay).toBeUndefined()
  })

  it('manual 手选 Tab 同样按手选顺序补价', async () => {
    mocks.getByIds.mockResolvedValue([spu('spu-1', 29.9)])

    const result = await loadGoodsTabPage(
      tab({ dataSource: { mode: 'manual', sort: 'sales', targetIds: ['spu-1'] } }),
      1,
      10,
    )

    expect(result.manual).toBe(true)
    expect(mocks.getMemberSpuPrices).toHaveBeenCalledWith(['spu-1'])
    expect(result.list[0].memberPriceDisplay?.showMemberPrice).toBe(true)
  })

  it('manual 手选为空：直接返回空列表，不发会员价请求', async () => {
    const result = await loadGoodsTabPage(
      tab({ dataSource: { mode: 'manual', sort: 'sales', targetIds: [] } }),
      1,
      10,
    )

    expect(result).toEqual({ list: [], manual: true, total: 0 })
    expect(mocks.getMemberSpuPrices).not.toHaveBeenCalled()
  })
})

describe('会员专区 Tab 列表补价：会员折扣率展示取优', () => {
  it('userDiscountRate 透传：主价 = min(会员价, 卡片价×折扣率)，划线仍是卡片价', async () => {
    // 会员价 25、卡片价 29.9、折扣率 0.8 → 取优 round2(29.9×0.8)=23.92 < 25
    mocks.getMemberSpuPrices.mockResolvedValue(memberVO({
      items: [
        { memberPrice: 25, minLevelId: 'l1', minLevelName: '金卡会员', originalPrice: 29.9, skuId: 'sku-1', spuId: 'spu-1' },
      ],
      userDiscountRate: 0.8,
    }))

    const [first] = (await loadGoodsTabPage(tab(), 1, 10)).list

    expect(first.memberPriceDisplay).toMatchObject({
      badgeText: '会员价',
      memberPrice: 23.92,
      showMemberPrice: true,
      strikePrice: 29.9,
    })
  })

  it('会员折扣率为 1（无折扣）时维持会员价', async () => {
    mocks.getMemberSpuPrices.mockResolvedValue(memberVO({ userDiscountRate: 1 }))

    const [first] = (await loadGoodsTabPage(tab(), 1, 10)).list

    expect(first.memberPriceDisplay?.memberPrice).toBe(23.92)
  })

  it('未达标引导价不取优（仍用活动会员价）', async () => {
    mocks.getMemberSpuPrices.mockResolvedValue(memberVO({
      eligible: false,
      items: [
        { memberPrice: 25, minLevelId: 'l1', minLevelName: '金卡会员', originalPrice: 29.9, skuId: 'sku-1', spuId: 'spu-1' },
      ],
      userDiscountRate: 0.8,
    }))

    const [first] = (await loadGoodsTabPage(tab(), 1, 10)).list

    expect(first.memberPriceDisplay?.showMemberPrice).toBe(false)
    expect(first.memberPriceDisplay?.guideText).toBe('会员专享 ¥25')
  })
})

import type { GoodsTabConfig } from './retail-types'

import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * 会员专区 Tab「按会员价活动自动」数据源的取数行为守门。
 *
 * 契约：`tab.dataSource.mode='activity'` 时，货盘与顺序完全由会员价活动决定——
 * 先取活动商品 spuIds（空/无活动 → 空态，不报错），再按活动顺序拉商品卡；
 * 活动失效/接口失败静默降级为空态；memberBanner 开启时仍沿用同一套列表会员价补价。
 * 其它数据源模式一律不调用该活动接口。
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

const { loadGoodsTabPage } = await import('./retail-data')

/** 造一条商品接口的 SPU（只带 normalizeRetailGoods 需要的字段） */
function spu(id: string, salesPrice = 29.9, extra: Record<string, unknown> = {}) {
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
    dataSource: { mode: 'activity', activityId: '', targetIds: [] },
    id: 'member',
    memberBanner: { enabled: true },
    title: '会员专区',
    ...overrides,
  }
}

/** 造一份「进行中会员价活动」的货盘响应（spuIds 即活动配置顺序） */
function activityVO(overrides: Record<string, unknown> = {}) {
  return {
    activityId: 'act-member-1',
    activityName: '会员专享价',
    endTime: '2026-12-31 23:59:59',
    spuIds: ['spu-2', 'spu-1'],
    ...overrides,
  }
}

/** 造一份列表会员价响应（默认命中 spu-1 且达标） */
function memberVO(overrides: Record<string, unknown> = {}) {
  return {
    eligible: true,
    items: [
      { memberPrice: 23.92, minLevelId: 'l1', minLevelName: '金卡会员', originalPrice: 29.9, skuId: 'sku-1', spuId: 'spu-1' },
    ],
    userDiscountRate: null,
    userLevelId: 'l1',
    userLevelName: '金卡会员',
    ...overrides,
  }
}

beforeEach(() => {
  vi.clearAllMocks()
  mocks.getMemberActivityGoods.mockResolvedValue(activityVO())
  // 商品接口故意「乱序」返回，验证 C 端按活动顺序回排
  mocks.getByIds.mockResolvedValue([spu('spu-1'), spu('spu-2')])
  mocks.getMemberSpuPrices.mockResolvedValue(memberVO())
})

describe('activity 数据源：货盘取数与顺序', () => {
  it('无 activityId 时自动取当前进行中活动，并按活动 spuIds 顺序回排', async () => {
    const result = await loadGoodsTabPage(tab(), 1, 10)

    // 空 activityId 仍会调用接口（由后端按「进行中活动」自动选择）
    expect(mocks.getMemberActivityGoods).toHaveBeenCalledTimes(1)
    expect(mocks.getMemberActivityGoods).toHaveBeenCalledWith('')
    // 商品按活动顺序拉取，不按接口返回顺序
    expect(mocks.getByIds).toHaveBeenCalledWith(['spu-2', 'spu-1'])
    expect(result.list.map(item => item.id)).toEqual(['spu-2', 'spu-1'])
    expect(result.manual).toBe(true)
    // total = 实际条数（一次性取全，不分页）
    expect(result.total).toBe(2)
  })

  it('指定 activityId 时透传给接口', async () => {
    await loadGoodsTabPage(tab({ dataSource: { mode: 'activity', activityId: 'act-9', targetIds: [] } }), 1, 10)

    expect(mocks.getMemberActivityGoods).toHaveBeenCalledWith('act-9')
  })

  it('按展示上限截断，total = 截断后的实际条数', async () => {
    mocks.getMemberActivityGoods.mockResolvedValue(activityVO({ spuIds: ['spu-1', 'spu-2', 'spu-3'] }))
    mocks.getByIds.mockResolvedValue([spu('spu-1'), spu('spu-2'), spu('spu-3')])

    const result = await loadGoodsTabPage(tab(), 1, 2)

    // 商品一次取全（活动货盘 ≤50），展示层按每页条数截断
    expect(mocks.getByIds).toHaveBeenCalledWith(['spu-1', 'spu-2', 'spu-3'])
    expect(result.list.map(item => item.id)).toEqual(['spu-1', 'spu-2'])
    expect(result.total).toBe(2)
    expect(result.manual).toBe(true)
  })

  it('活动里已下架的商品被商品接口过滤后，其余商品仍按活动顺序展示', async () => {
    mocks.getByIds.mockResolvedValue([spu('spu-2')])

    const result = await loadGoodsTabPage(tab(), 1, 10)

    expect(result.list.map(item => item.id)).toEqual(['spu-2'])
    expect(result.total).toBe(1)
  })
})

describe('activity 数据源：空态与失败静默', () => {
  it('无活动（data:null）→ 空列表空态，不拉商品、不报错', async () => {
    mocks.getMemberActivityGoods.mockResolvedValue(null)

    const result = await loadGoodsTabPage(tab(), 1, 10)

    expect(result).toEqual({ list: [], manual: true, total: 0 })
    expect(mocks.getByIds).not.toHaveBeenCalled()
  })

  it('活动无商品（spuIds 为空）→ 空列表空态，不拉商品', async () => {
    mocks.getMemberActivityGoods.mockResolvedValue(activityVO({ spuIds: [] }))

    const result = await loadGoodsTabPage(tab(), 1, 10)

    expect(result).toEqual({ list: [], manual: true, total: 0 })
    expect(mocks.getByIds).not.toHaveBeenCalled()
  })

  it('活动接口失败：静默返回空态，不向上抛错', async () => {
    mocks.getMemberActivityGoods.mockRejectedValue(new Error('mock activity api error'))

    const result = await loadGoodsTabPage(tab(), 1, 10)

    expect(result).toEqual({ list: [], manual: true, total: 0 })
    expect(mocks.getByIds).not.toHaveBeenCalled()
  })

  it('spuIds 里的脏值（空串/空白）被过滤，不请求空商品', async () => {
    mocks.getMemberActivityGoods.mockResolvedValue(activityVO({ spuIds: ['', '  ', 'spu-2'] }))

    await loadGoodsTabPage(tab(), 1, 10)

    expect(mocks.getByIds).toHaveBeenCalledWith(['spu-2'])
  })
})

describe('activity 数据源：与列表会员价补价的衔接', () => {
  it('memberBanner 开启：按活动 spuIds 补会员价并合并展示结果', async () => {
    const result = await loadGoodsTabPage(tab(), 1, 10)

    expect(mocks.getMemberSpuPrices).toHaveBeenCalledWith(['spu-2', 'spu-1'])
    const item = result.list.find(entry => entry.id === 'spu-1')
    expect(item?.memberPriceDisplay).toMatchObject({
      badgeText: '会员价',
      memberPrice: 23.92,
      showMemberPrice: true,
      strikePrice: 29.9,
    })
    // 活动页没有会员价的商品保持原价展示
    expect(result.list.find(entry => entry.id === 'spu-2')?.memberPriceDisplay).toBeUndefined()
  })

  it('memberBanner 未开启：不补会员价（与手选/自动 Tab 同一门控）', async () => {
    const result = await loadGoodsTabPage(tab({ memberBanner: undefined }), 1, 10)

    expect(mocks.getMemberSpuPrices).not.toHaveBeenCalled()
    expect(result.list.every(item => item.memberPriceDisplay === undefined)).toBe(true)
  })
})

describe('非 activity 数据源：不调用活动货盘接口', () => {
  it('automatic 模式只走商品分页，不调 getMemberActivityGoods', async () => {
    mocks.getPage.mockResolvedValue({ records: [spu('spu-1')], total: 1 })

    const result = await loadGoodsTabPage(
      tab({ dataSource: { mode: 'automatic', sort: 'sales', targetIds: [] }, memberBanner: undefined }),
      1,
      10,
    )

    expect(result.manual).toBe(false)
    expect(mocks.getMemberActivityGoods).not.toHaveBeenCalled()
  })

  it('manual 模式只按手选 ID 取数，不调 getMemberActivityGoods', async () => {
    mocks.getByIds.mockResolvedValue([spu('spu-1')])

    const result = await loadGoodsTabPage(
      tab({ dataSource: { mode: 'manual', targetIds: ['spu-1'] }, memberBanner: undefined }),
      1,
      10,
    )

    expect(result.manual).toBe(true)
    expect(mocks.getMemberActivityGoods).not.toHaveBeenCalled()
  })
})

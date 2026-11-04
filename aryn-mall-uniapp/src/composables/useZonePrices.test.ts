import type { AppClearancePriceItem, AppClearancePriceVO } from '@/utils/clearance-price'
import type { AppMemberPriceVO } from '@/api/promotion'

import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * 列表专区价批量取数（会员价 + 出清价）的行为守门。
 *
 * 关键契约：
 * - 一次 load 只按本页 SPU 各发一次请求，缓存命中的 SPU 不重复请求；
 * - 会员价按资格三态展示（达标切主价、未达标给引导）；
 * - 出清价与会员价取低、同价会员优先；到期/倒挂忽略；
 * - 接口失败静默回落原价，不影响列表渲染。
 */

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

const { resetZonePriceCache, useZonePrices } = await import('./useZonePrices')

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
  mocks.getMemberSpuPrices.mockReset()
  mocks.getClearanceSpuPrices.mockReset()
  mocks.getMemberSpuPrices.mockResolvedValue(memberVO())
  mocks.getClearanceSpuPrices.mockResolvedValue({ items: [] })
  mocks.getClearanceSpuPrices.mockResolvedValue(clearanceVO([]))
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
})

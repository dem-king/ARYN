import type { LadderPriceItem } from '@/api/promotion/ladder-price'

import { describe, expect, it } from 'vitest'

import { ladderTiersOf, nextLadderTier, nextPurchasableLadderTier, resolveLadderUnitPrice } from './ladder-price'

it('rounds the next tier to a purchasable quantity and checks stock', () => {
  const tiers = [{ minQty: 6, unitPrice: 4 }, { minQty: 8, unitPrice: 3 }]
  expect(nextPurchasableLadderTier(tiers, 5, 2, 3, 9)).toEqual({ needQty: 3, unitPrice: 3 })
  expect(nextPurchasableLadderTier(tiers, 5, 2, 3, 7)).toBeNull()
  expect(nextPurchasableLadderTier(tiers, 4, 2, 3, 9)).toBeNull()
})

/** 阶梯活动：10 件 9 元、50 件 8 元、100 件 7 元 */
function ladderItem(overrides: Partial<LadderPriceItem> = {}): LadderPriceItem {
  return {
    activityId: 'act-ladder',
    activityName: '整箱阶梯价',
    skuId: 'sku-1',
    ladders: [
      { minQty: 10, unitPrice: 9 },
      { minQty: 50, unitPrice: 8 },
      { minQty: 100, unitPrice: 7 },
    ],
    ...overrides,
  }
}

describe('ladderTiersOf 按 SKU 取档位', () => {
  it('命中 SKU 返回清洗后的升序档位；未命中/空入参返回空数组', () => {
    const tiers = ladderTiersOf([ladderItem()], 'sku-1')
    expect(tiers.map(tier => tier.minQty)).toEqual([10, 50, 100])
    expect(ladderTiersOf([ladderItem()], 'sku-x')).toEqual([])
    expect(ladderTiersOf(null, 'sku-1')).toEqual([])
    expect(ladderTiersOf([ladderItem()], '')).toEqual([])
  })

  it('乱序/脏档位防御：非正数与非数值剔除，其余按 minQty 升序', () => {
    const tiers = ladderTiersOf([ladderItem({
      ladders: [
        { minQty: 50, unitPrice: 8 },
        { minQty: 0, unitPrice: 1 },
        { minQty: 10, unitPrice: 9 },
        { minQty: 20, unitPrice: Number.NaN },
        { minQty: 30, unitPrice: -1 },
      ],
    })], 'sku-1')
    expect(tiers).toEqual([{ minQty: 10, unitPrice: 9 }, { minQty: 50, unitPrice: 8 }])
  })

  it('同 SKU 多活动：取档位更优（最低单价）的一份', () => {
    const tiers = ladderTiersOf([
      ladderItem(),
      ladderItem({ activityId: 'act-better', ladders: [{ minQty: 10, unitPrice: 6 }] }),
    ], 'sku-1')
    expect(tiers).toEqual([{ minQty: 10, unitPrice: 6 }])
  })
})

describe('resolveLadderUnitPrice 按当前数量取档', () => {
  it('未达首档不享阶梯价；达到档位取「不超过件数的最高档」', () => {
    const tiers = ladderTiersOf([ladderItem()], 'sku-1')
    expect(resolveLadderUnitPrice(tiers, 1)).toBeNull()
    expect(resolveLadderUnitPrice(tiers, 9)).toBeNull()
    expect(resolveLadderUnitPrice(tiers, 10)).toBe(9)
    expect(resolveLadderUnitPrice(tiers, 49)).toBe(9)
    expect(resolveLadderUnitPrice(tiers, 50)).toBe(8)
    expect(resolveLadderUnitPrice(tiers, 999)).toBe(7)
  })

  it('数量非法（0/负数/非数值/小数向下取整）与空档位的防御', () => {
    const tiers = ladderTiersOf([ladderItem()], 'sku-1')
    expect(resolveLadderUnitPrice(tiers, 0)).toBeNull()
    expect(resolveLadderUnitPrice(tiers, -5)).toBeNull()
    expect(resolveLadderUnitPrice(tiers, 'abc')).toBeNull()
    expect(resolveLadderUnitPrice(tiers, 10.9)).toBe(9)
    expect(resolveLadderUnitPrice([], 100)).toBeNull()
    expect(resolveLadderUnitPrice(null, 100)).toBeNull()
  })
})

describe('nextLadderTier 结算/商详阶梯进度提示', () => {
  it('给出「再买 N 件降至 X」的最近下一档；已达最高档返回 null', () => {
    const tiers = ladderTiersOf([ladderItem()], 'sku-1')
    expect(nextLadderTier(tiers, 1)).toEqual({ needQty: 9, unitPrice: 9 })
    expect(nextLadderTier(tiers, 10)).toEqual({ needQty: 40, unitPrice: 8 })
    expect(nextLadderTier(tiers, 60)).toEqual({ needQty: 40, unitPrice: 7 })
    expect(nextLadderTier(tiers, 100)).toBeNull()
    expect(nextLadderTier(tiers, 0)).toBeNull()
    expect(nextLadderTier([], 1)).toBeNull()
  })
})

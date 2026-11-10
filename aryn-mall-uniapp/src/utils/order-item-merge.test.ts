import { describe, expect, it } from 'vitest'

import { annotateNewbieSplitRows, mergeNewbieSplitRows } from './order-item-merge'

interface Row {
  skuId?: string
  spuName?: string
  buyQuantity?: number
  totalPrice?: number
  paymentPrice?: number
  originalTotalPrice?: number
  activityDiscountPrice?: number
  contributorUserId?: string
  contributorName?: string
  giftFlag?: string
  activityType?: string | null
  pointsStackable?: boolean | null
}

/** 新人价优惠行（后端 applyPromotionLadder 拆行后的首件行） */
function newbieRow(overrides: Partial<Row> = {}): Row {
  return {
    skuId: 'sku-1',
    spuName: '内酯豆腐400g',
    buyQuantity: 1,
    totalPrice: 1.9,
    paymentPrice: 1.9,
    originalTotalPrice: 5.9,
    activityDiscountPrice: 4,
    activityType: 'newbie',
    pointsStackable: false,
    ...overrides,
  }
}

/** 拆行后的原价行（超出件，不挂活动标签） */
function originalRow(overrides: Partial<Row> = {}): Row {
  return {
    skuId: 'sku-1',
    spuName: '内酯豆腐400g',
    buyQuantity: 2,
    totalPrice: 11.8,
    paymentPrice: 11.8,
    originalTotalPrice: 11.8,
    activityDiscountPrice: 0,
    activityType: null,
    ...overrides,
  }
}

describe('mergeNewbieSplitRows', () => {
  it('无新人价标签行时原样返回（普通订单零变化）', () => {
    const rows = [originalRow(), originalRow({ skuId: 'sku-2', buyQuantity: 1 })]
    expect(mergeNewbieSplitRows(rows)).toEqual(rows)
  })

  it('拆行组（优惠行+原价行）合并为单行：数量/金额累加、标签保留、打提示标记', () => {
    const merged = mergeNewbieSplitRows([newbieRow(), originalRow()])
    expect(merged).toHaveLength(1)
    const row = merged[0] as Row & { newbieSplitHint?: boolean }
    expect(row.activityType).toBe('newbie')
    expect(row.buyQuantity).toBe(3)
    expect(row.totalPrice).toBe(13.7)
    expect(row.paymentPrice).toBe(13.7)
    expect(row.originalTotalPrice).toBe(17.7)
    expect(row.activityDiscountPrice).toBe(4)
    expect(row.newbieSplitHint).toBe(true)
    expect(row.pointsStackable).toBe(false)
  })

  it('纯优惠单行（数量未超限、未拆行）不合并、不打提示标记', () => {
    const rows = [newbieRow({ buyQuantity: 1 })]
    const merged = mergeNewbieSplitRows(rows) as Array<Row & { newbieSplitHint?: boolean }>
    expect(merged).toHaveLength(1)
    expect(merged[0].newbieSplitHint).toBeUndefined()
  })

  it('不同归属人（共享购物车按成员拆行）不跨成员合并', () => {
    const merged = mergeNewbieSplitRows([
      newbieRow({ contributorUserId: 'user-a' }),
      originalRow({ contributorUserId: 'user-b', buyQuantity: 1, totalPrice: 5.9 }),
    ])
    expect(merged).toHaveLength(2)
  })

  it('赠品行（giftFlag=1）永不参与合并', () => {
    const merged = mergeNewbieSplitRows([
      newbieRow(),
      originalRow({ giftFlag: '1', buyQuantity: 1, totalPrice: 0, paymentPrice: 0 }),
    ])
    expect(merged).toHaveLength(2)
  })

  it('同 SKU 纯原价多行（跨靠港滞留行）不合并', () => {
    const merged = mergeNewbieSplitRows([originalRow(), originalRow({ buyQuantity: 1, totalPrice: 5.9 })])
    expect(merged).toHaveLength(2)
  })

  it('null/undefined/空列表安全返回', () => {
    expect(mergeNewbieSplitRows(null)).toEqual([])
    expect(mergeNewbieSplitRows(undefined)).toEqual([])
    expect(mergeNewbieSplitRows([])).toEqual([])
  })

  it('多商品混合：新人价行与其原价行合并，其它商品行位置不变', () => {
    const merged = mergeNewbieSplitRows([
      newbieRow({ skuId: 'sku-a' }),
      originalRow({ skuId: 'sku-b', buyQuantity: 1, totalPrice: 10 }),
      newbieRow({ skuId: 'sku-c', totalPrice: 2, paymentPrice: 2 }),
      originalRow({ skuId: 'sku-c', buyQuantity: 1, totalPrice: 6, paymentPrice: 6 }),
    ])
    expect(merged).toHaveLength(3)
    // sku-a 只有优惠行（未拆行）：不合并、无提示
    expect((merged[0] as Row & { newbieSplitHint?: boolean }).newbieSplitHint).toBeUndefined()
    expect((merged[1] as Row).skuId).toBe('sku-b')
    // sku-c 是拆行组：合并为单行并打提示
    expect((merged[2] as Row & { newbieSplitHint?: boolean }).newbieSplitHint).toBe(true)
    expect((merged[2] as Row & { newbieSplitHint?: boolean }).buyQuantity).toBe(2)
    expect((merged[2] as Row).totalPrice).toBe(8)
  })
})

describe('annotateNewbieSplitRows', () => {
  it('拆行组保留逐行（售后入口不孤儿化），首件行/超出价行分别打标', () => {
    const rows = [newbieRow(), originalRow()]
    const annotated = annotateNewbieSplitRows(rows) as Array<Row & { newbieSplitHint?: boolean, newbieRemainderHint?: boolean }>
    expect(annotated).toHaveLength(2)
    expect(annotated[0].newbieSplitHint).toBe(true)
    expect(annotated[0].newbieRemainderHint).toBeUndefined()
    expect(annotated[1].newbieRemainderHint).toBe(true)
    expect(annotated[1].newbieSplitHint).toBeUndefined()
    // 不修改原数组（纯函数）
    expect(rows[0]).not.toHaveProperty('newbieSplitHint')
  })

  it('非拆行订单原样返回同一引用', () => {
    const rows = [originalRow(), originalRow({ skuId: 'sku-2' })]
    expect(annotateNewbieSplitRows(rows)).toBe(rows)
  })

  it('纯优惠单行与跨成员/赠品行不打标', () => {
    const rows = [
      newbieRow({ buyQuantity: 1 }),
      newbieRow({ contributorUserId: 'user-a' }),
      originalRow({ contributorUserId: 'user-b' }),
    ]
    const annotated = annotateNewbieSplitRows(rows) as Array<Row & { newbieSplitHint?: boolean, newbieRemainderHint?: boolean }>
    expect(annotated.every(row => !row.newbieSplitHint && !row.newbieRemainderHint)).toBe(true)
  })

  it('null/undefined/空列表安全返回', () => {
    expect(annotateNewbieSplitRows(null)).toEqual([])
    expect(annotateNewbieSplitRows(undefined)).toEqual([])
    expect(annotateNewbieSplitRows([])).toEqual([])
  })
})

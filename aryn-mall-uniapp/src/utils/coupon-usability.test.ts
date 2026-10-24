import { describe, expect, it } from 'vitest'

import { getCouponUnavailableInfo, isCouponStackable } from './coupon-usability'

/** 造一张券（useRange：1 全场 2 指定商品；threshold 门槛） */
function coupon(options: {
  threshold?: number
  useRange?: string
  couponGoodsList?: Array<{ spuId: string }>
}) {
  return {
    id: 'coupon-1',
    couponInfo: {
      threshold: options.threshold ?? 0,
      useRange: options.useRange ?? '1',
    },
    couponGoodsList: options.couponGoodsList ?? [],
  }
}

function item(options: {
  spuId: string
  totalPrice: number
  couponStackable?: boolean | null
}) {
  return {
    spuId: options.spuId,
    totalPrice: options.totalPrice,
    couponStackable: options.couponStackable,
  }
}

describe('coupon-usability（叠加规则口径）', () => {
  it('普通商品：门槛满足即可用，老结算接口不下发 couponStackable 也不受影响', () => {
    const info = getCouponUnavailableInfo(
      coupon({ threshold: 100 }),
      [item({ spuId: 'spu-1', totalPrice: 100 })],
      100,
    )
    expect(info.unavailable).toBe(false)
  })

  it('门槛按可叠金额判断：秒杀行不计入门槛基数时给出「还差」文案', () => {
    const items = [
      item({ spuId: 'spu-1', totalPrice: 20, couponStackable: false }),
      item({ spuId: 'spu-2', totalPrice: 80 }),
    ]
    const info = getCouponUnavailableInfo(coupon({ threshold: 100 }), items, 80)
    expect(info.unavailable).toBe(true)
    expect(info.reason).toBe('还差 ￥20.00 可用该券')
  })

  it('全部明细不允许叠券（纯秒杀单）：任何券都不可用并给出活动原因', () => {
    const items = [item({ spuId: 'spu-1', totalPrice: 20, couponStackable: false })]
    const info = getCouponUnavailableInfo(coupon({ threshold: 0 }), items, 0)
    expect(info.unavailable).toBe(true)
    expect(info.reason).toBe('活动商品不支持使用优惠券')
  })

  it('指定商品券：券 SPU ∩ 允许叠券明细为空时不可用', () => {
    const items = [
      item({ spuId: 'spu-1', totalPrice: 100, couponStackable: false }),
      item({ spuId: 'spu-2', totalPrice: 50 }),
    ]
    // 券只指定 spu-1（秒杀行）：命中商品存在但全部不可叠 → 活动原因
    const blockedByActivity = getCouponUnavailableInfo(
      coupon({ useRange: '2', couponGoodsList: [{ spuId: 'spu-1' }] }),
      items,
      50,
    )
    expect(blockedByActivity.unavailable).toBe(true)
    expect(blockedByActivity.reason).toBe('活动商品不支持使用优惠券')

    // 券指定的商品本单根本没有 → 原有「本单无可用商品」口径
    const noMatch = getCouponUnavailableInfo(
      coupon({ useRange: '2', couponGoodsList: [{ spuId: 'spu-9' }] }),
      items,
      50,
    )
    expect(noMatch.unavailable).toBe(true)
    expect(noMatch.reason).toContain('本单无可用商品')
  })

  it('指定商品券：命中可叠商品且满足门槛即可用', () => {
    const items = [
      item({ spuId: 'spu-1', totalPrice: 20, couponStackable: false }),
      item({ spuId: 'spu-2', totalPrice: 120 }),
    ]
    const info = getCouponUnavailableInfo(
      coupon({ threshold: 100, useRange: '2', couponGoodsList: [{ spuId: 'spu-2' }] }),
      items,
      null,
    )
    expect(info.unavailable).toBe(false)
  })

  it('isCouponStackable：缺省视为普通商品（可叠），仅显式 false 为禁止', () => {
    expect(isCouponStackable(item({ spuId: 's', totalPrice: 1 }))).toBe(true)
    expect(isCouponStackable(item({ spuId: 's', totalPrice: 1, couponStackable: null }))).toBe(true)
    expect(isCouponStackable(item({ spuId: 's', totalPrice: 1, couponStackable: true }))).toBe(true)
    expect(isCouponStackable(item({ spuId: 's', totalPrice: 1, couponStackable: false }))).toBe(false)
  })
})

import { describe, expect, it } from 'vitest'

import { estimateCouponDiscount, getCouponUnavailableInfo, isCouponStackable, pickBestCoupon } from './coupon-usability'

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


/** 造一张券记录（含 couponInfo 的面额/折扣/类型，页面拿到的 couponUser 结构） */
function couponRecord(options: {
  id: string
  amount?: number
  discount?: number
  couponType?: string
  threshold?: number
  validatTime?: string
}) {
  return {
    id: options.id,
    validatTime: options.validatTime ?? '2026-11-15 20:00:00',
    couponInfo: {
      couponType: options.couponType ?? '1',
      amount: options.amount ?? null,
      discount: options.discount ?? null,
      threshold: options.threshold ?? 0,
      useRange: '1',
    },
    couponGoodsList: [],
  }
}

/** 固定「当前时间」：2026-11-01 12:00（Asia/Shanghai 无时区语义，仅做日期差） */
const NOW = new Date(2026, 10, 1, 12, 0, 0)

describe('estimateCouponDiscount（自动选券排序口径）', () => {
  it('满减券取面额', () => {
    expect(estimateCouponDiscount(couponRecord({ id: 'a', amount: 40, threshold: 199 }), 227.2)).toBe(40)
  })

  it('折扣券按可叠金额折算：9 折 × 200 = 20', () => {
    expect(
      estimateCouponDiscount(
        couponRecord({ id: 'b', couponType: '2', discount: 9, threshold: 99 }),
        200,
      ),
    ).toBe(20)
  })

  it('折扣非法（10 折及以上）或基数缺失时不参与排序', () => {
    expect(
      estimateCouponDiscount(couponRecord({ id: 'c', couponType: '2', discount: 10 }), 200),
    ).toBe(0)
    expect(estimateCouponDiscount(couponRecord({ id: 'd', amount: 5 }), null)).toBe(0)
  })
})

describe('pickBestCoupon（确认页自动选最优券）', () => {
  const items = [item({ spuId: 'spu-1', totalPrice: 227.2 })]

  it('多张可用时选抵扣金额最大的（满199减40 优于满99减15/无门槛5元）', () => {
    const best = pickBestCoupon(
      [
        couponRecord({ id: 'full-40', amount: 40, threshold: 199 }),
        couponRecord({ id: 'full-15', amount: 15, threshold: 99 }),
        couponRecord({ id: 'no-threshold', amount: 5 }),
      ],
      items,
      227.2,
      NOW,
    )
    expect(best?.id).toBe('full-40')
  })

  it('门槛不足的券不参与：基数 50 时只能选无门槛 5 元券', () => {
    const smallItems = [item({ spuId: 'spu-1', totalPrice: 50 })]
    const best = pickBestCoupon(
      [
        couponRecord({ id: 'full-40', amount: 40, threshold: 199 }),
        couponRecord({ id: 'full-15', amount: 15, threshold: 99 }),
        couponRecord({ id: 'no-threshold', amount: 5 }),
      ],
      smallItems,
      50,
      NOW,
    )
    expect(best?.id).toBe('no-threshold')
  })

  it('全部明细禁止叠券（纯秒杀单）时返回 null', () => {
    const blockedItems = [item({ spuId: 'spu-1', totalPrice: 227.2, couponStackable: false })]
    expect(
      pickBestCoupon(
        [couponRecord({ id: 'no-threshold', amount: 5 })],
        blockedItems,
        0,
        NOW,
      ),
    ).toBeNull()
  })

  it('状态未使用但有效期已过的券不自动选', () => {
    expect(
      pickBestCoupon(
        [couponRecord({ id: 'expired', amount: 40, threshold: 199, validatTime: '2026-10-31 20:00:00' })],
        items,
        227.2,
        NOW,
      ),
    ).toBeNull()
  })

  it('抵扣金额相同时优先快到期的券', () => {
    const best = pickBestCoupon(
      [
        couponRecord({ id: 'later', amount: 15, validatTime: '2026-12-31 20:00:00' }),
        couponRecord({ id: 'sooner', amount: 15, validatTime: '2026-11-08 20:00:00' }),
      ],
      items,
      227.2,
      NOW,
    )
    expect(best?.id).toBe('sooner')
  })

  it('折扣券按折算金额参与比较（9 折 200 元 > 满减 15 元）', () => {
    const best = pickBestCoupon(
      [
        couponRecord({ id: 'discount', couponType: '2', discount: 9, threshold: 99 }),
        couponRecord({ id: 'full-15', amount: 15, threshold: 99 }),
      ],
      [item({ spuId: 'spu-1', totalPrice: 200 })],
      200,
      NOW,
    )
    expect(best?.id).toBe('discount')
  })

  it('无券 / 空列表返回 null', () => {
    expect(pickBestCoupon([], items, 227.2, NOW)).toBeNull()
    expect(pickBestCoupon(null, items, 227.2, NOW)).toBeNull()
  })
})

import { describe, expect, it } from 'vitest'
import { normalizeCouponLadderTiers, normalizeLadderShowNum } from './coupon-ladder-source'

/**
 * 档位取数的守门测试。
 *
 * 阶梯上的「满 X 减 Y」直接来自券面，任何一条过滤/排序写错，
 * 用户看到的档位顺序或金额就是错的——这类错误在画布上完全看不出来。
 */

const amountCoupon = (id: string, threshold: number, amount: number) => ({
  amount,
  couponType: '1',
  id,
  threshold,
})

describe('normalizeCouponLadderTiers', () => {
  it('按门槛升序排列，越买越省', () => {
    const tiers = normalizeCouponLadderTiers(
      [
        amountCoupon('c', 299, 50),
        amountCoupon('a', 99, 10),
        amountCoupon('b', 199, 30),
      ],
      3,
    )
    expect(tiers.map((tier) => tier.threshold)).toEqual(['99', '199', '299'])
    expect(tiers.map((tier) => tier.amount)).toEqual(['10', '30', '50'])
  })

  it('过滤折扣券与无门槛券', () => {
    const tiers = normalizeCouponLadderTiers(
      [
        amountCoupon('a', 99, 10),
        { amount: 0, couponType: '1', id: 'b', threshold: 0 },
        { couponType: '2', id: 'c', threshold: 199 },
        { amount: 5, couponType: '1', id: 'd', threshold: 0 },
      ],
      3,
    )
    expect(tiers.map((tier) => tier.couponId)).toEqual(['a'])
  })

  it('同门槛时面额大的排前面', () => {
    const tiers = normalizeCouponLadderTiers(
      [amountCoupon('small', 199, 10), amountCoupon('big', 199, 30)],
      2,
    )
    expect(tiers.map((tier) => tier.couponId)).toEqual(['big', 'small'])
  })

  it('按 showNum 截断，且 showNum 兜底到 2-4', () => {
    const coupons = [
      amountCoupon('a', 99, 10),
      amountCoupon('b', 199, 30),
      amountCoupon('c', 299, 50),
      amountCoupon('d', 399, 80),
    ]
    expect(normalizeCouponLadderTiers(coupons, 2)).toHaveLength(2)
    expect(normalizeCouponLadderTiers(coupons, 9)).toHaveLength(4)
    expect(normalizeCouponLadderTiers(coupons, 1)).toHaveLength(2)
  })

  it('重复 ID 只保留一次，缺 ID 的直接丢弃', () => {
    const tiers = normalizeCouponLadderTiers(
      [amountCoupon('a', 99, 10), amountCoupon('a', 99, 10), { amount: 5, couponType: '1', threshold: 50 }],
      3,
    )
    expect(tiers).toHaveLength(1)
  })

  it('去掉金额尾零，空列表返回空数组', () => {
    const tiers = normalizeCouponLadderTiers([amountCoupon('a', 99.0, 10.5)], 3)
    expect(tiers[0]?.threshold).toBe('99')
    expect(tiers[0]?.amount).toBe('10.5')
    expect(normalizeCouponLadderTiers([], 3)).toEqual([])
    expect(normalizeCouponLadderTiers(null, 3)).toEqual([])
    expect(normalizeCouponLadderTiers(undefined, 3)).toEqual([])
  })
})

describe('normalizeLadderShowNum', () => {
  it('非法值与越界值都收敛到 2-4', () => {
    expect(normalizeLadderShowNum(undefined)).toBe(3)
    expect(normalizeLadderShowNum('abc')).toBe(3)
    expect(normalizeLadderShowNum(0)).toBe(2)
    expect(normalizeLadderShowNum(10)).toBe(4)
    expect(normalizeLadderShowNum('3')).toBe(3)
  })
})

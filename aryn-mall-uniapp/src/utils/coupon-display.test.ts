import { describe, expect, it } from 'vitest'
import {
  couponScopeText,
  couponStatusMeta,
  couponSubtitle,
  couponThresholdText,
  couponValueParts,
  couponValueText,
  daysUntil,
  formatCouponDeadline,
  formatCouponExpiry,
} from './coupon-display'

const NOW = new Date(2026, 8, 24, 10, 30) // 2026-09-24 10:30 本地时间

describe('券金额展示', () => {
  it('满减券拆成 ¥ + 数值，不带单位', () => {
    expect(couponValueParts({ couponType: '1', amount: 5 })).toEqual({
      symbol: '¥',
      value: '5',
      unit: '',
      sub: '满减券',
    })
  })

  it('折扣券拆成数值 + 折，不带货币符号', () => {
    expect(couponValueParts({ couponType: '2', discount: 8 })).toEqual({
      symbol: '',
      value: '8',
      unit: '折',
      sub: '折扣券',
    })
  })

  it('小数金额去掉无意义的零，不显示 5.00', () => {
    expect(couponValueText({ couponType: '1', amount: 5.0 })).toBe('¥5')
    expect(couponValueText({ couponType: '1', amount: '5.50' })).toBe('¥5.5')
  })

  it('券类型缺失按满减券处理，与后端默认值一致', () => {
    expect(couponValueParts({ amount: 3 }).symbol).toBe('¥')
    expect(couponValueParts(null).symbol).toBe('¥')
  })

  it('金额缺失显示 ¥0 而不是 NaN', () => {
    expect(couponValueText({ couponType: '1' })).toBe('¥0')
    expect(couponValueText({ couponType: '2' })).toBe('0折')
  })
})

describe('券门槛与范围文案', () => {
  it('有门槛拼接金额，无门槛不显示「满0元」', () => {
    expect(couponThresholdText({ threshold: 100 })).toBe('满100元可用')
    expect(couponThresholdText({ threshold: 0 })).toBe('无门槛')
    expect(couponThresholdText({})).toBe('无门槛')
  })

  it('适用范围按 useRange 判定', () => {
    expect(couponScopeText({ useRange: '1' })).toBe('全部商品可用')
    expect(couponScopeText({ useRange: '2' })).toBe('部分商品可用')
  })

  it('副标题优先用后端使用说明，为空才回落适用范围', () => {
    expect(couponSubtitle({ useDescription: '仅限生鲜', useRange: '2' })).toBe('仅限生鲜')
    expect(couponSubtitle({ useDescription: '  ', useRange: '2' })).toBe('部分商品可用')
    expect(couponSubtitle({ useRange: '1' })).toBe('全部商品可用')
  })
})

describe('券有效期文案', () => {
  it('当天到期显示「今天过期」', () => {
    expect(formatCouponExpiry('2026-09-24 23:59:59', NOW)).toBe('今天过期')
  })

  it('次日到期显示「明天过期」', () => {
    expect(formatCouponExpiry('2026-09-25 00:00:00', NOW)).toBe('明天过期')
  })

  it('一周内显示剩余天数', () => {
    expect(formatCouponExpiry('2026-09-27 12:00:00', NOW)).toBe('还剩 3 天')
  })

  it('超过一周显示具体日期，避免「还剩 87 天」这种无意义精度', () => {
    expect(formatCouponExpiry('2026-10-30 12:00:00', NOW)).toBe('10-30 过期')
  })

  it('已过期的时间返回「已过期」而非负数天数', () => {
    expect(formatCouponExpiry('2026-09-20 12:00:00', NOW)).toBe('已过期')
  })

  it('按自然日比较，与当天具体时刻无关', () => {
    // 同一天但时刻已过，仍算「今天过期」而不是「已过期」
    expect(daysUntil('2026-09-24 00:01:00', NOW)).toBe(0)
    expect(daysUntil('2026-09-25 00:00:00', NOW)).toBe(1)
  })

  it('空值与无法解析的值返回空串，由调用方隐藏状态带', () => {
    expect(formatCouponExpiry('', NOW)).toBe('')
    expect(formatCouponExpiry(null, NOW)).toBe('')
    expect(formatCouponExpiry('不是时间', NOW)).toBe('')
    expect(daysUntil(undefined, NOW)).toBeNull()
  })
})

describe('领券截止文案', () => {
  it('用「截止」措辞，避免用户误以为券马上作废', () => {
    expect(formatCouponDeadline('2026-09-24 23:59:59', NOW)).toBe('今天截止')
    expect(formatCouponDeadline('2026-09-25 12:00:00', NOW)).toBe('明天截止')
    expect(formatCouponDeadline('2026-09-27 12:00:00', NOW)).toBe('还剩 3 天')
    expect(formatCouponDeadline('2026-10-30 12:00:00', NOW)).toBe('10-30 截止')
    expect(formatCouponDeadline('2026-09-20 12:00:00', NOW)).toBe('已截止')
  })

  it('无截止时间返回空串', () => {
    expect(formatCouponDeadline(null, NOW)).toBe('')
  })
})

describe('领券记录状态', () => {
  it('按后端状态码映射中文与色调', () => {
    expect(couponStatusMeta('0')).toEqual({ label: '待使用', tone: 'active' })
    expect(couponStatusMeta('1')).toEqual({ label: '已使用', tone: 'used' })
    expect(couponStatusMeta('2')).toEqual({ label: '已过期', tone: 'expired' })
    expect(couponStatusMeta('3')).toEqual({ label: '冻结中', tone: 'frozen' })
  })

  it('未知状态归到禁用色调而不是红色可点样式', () => {
    expect(couponStatusMeta('9')).toEqual({ label: '未知', tone: 'disabled' })
    expect(couponStatusMeta(undefined).tone).toBe('disabled')
  })
})

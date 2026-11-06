import { describe, expect, it } from 'vitest'
import {
  formatMoney,
  getCouponExchangeStatusClass,
  getCouponExchangeStatusKey,
  hasEnoughCommission,
  resolveCouponPackState,
} from './presentation-core'

/**
 * 券兑换页的纯逻辑守卫。
 *
 * 这里覆盖的都是「错了会真扣钱/真发不出券」的判定：
 * 券包能否点击兑换、可兑换佣金是否足够、兑换记录状态文案。
 */
describe('券包兑换展示逻辑', () => {
  it('下架优先于售罄：status=1 无论库存都不可兑换', () => {
    expect(resolveCouponPackState({ status: '1', stock: 10 })).toBe('off-shelf')
    expect(resolveCouponPackState({ status: '1', stock: 0 })).toBe('off-shelf')
    expect(resolveCouponPackState({ status: '1' })).toBe('off-shelf')
  })

  it('库存为 0 视为售罄，null/undefined 表示不限库存', () => {
    expect(resolveCouponPackState({ status: '0', stock: 0 })).toBe('sold-out')
    expect(resolveCouponPackState({ stock: 3 })).toBe('available')
    expect(resolveCouponPackState({ stock: null })).toBe('available')
    expect(resolveCouponPackState({ status: '0' })).toBe('available')
  })

  it('佣金按分比较：余额刚好相等也算足够', () => {
    expect(hasEnoughCommission(9.99, 9.99)).toBe(true)
    expect(hasEnoughCommission(9.98, 9.99)).toBe(false)
    // 浮点误差：0.1 + 0.2 = 0.30000000000000004，不能因此少给一次兑换机会
    expect(hasEnoughCommission(0.1 + 0.2, 0.3)).toBe(true)
    expect(hasEnoughCommission(99.995, 99.99)).toBe(true)
    expect(hasEnoughCommission(null, 1)).toBe(false)
    expect(hasEnoughCommission(undefined, 1)).toBe(false)
  })

  it('金额格式化保留两位小数，异常值回落 0.00', () => {
    expect(formatMoney(12)).toBe('12.00')
    expect(formatMoney('8.5')).toBe('8.50')
    expect(formatMoney(null)).toBe('0.00')
    expect(formatMoney('abc')).toBe('0.00')
  })

  it('兑换记录状态映射：0 发放中、1 已发放、2 发放失败', () => {
    expect(getCouponExchangeStatusKey('0')).toBe('accountSettings.distribution.exchangeStatus.processing')
    expect(getCouponExchangeStatusKey('1')).toBe('accountSettings.distribution.exchangeStatus.granted')
    expect(getCouponExchangeStatusKey('2')).toBe('accountSettings.distribution.exchangeStatus.failed')
    expect(getCouponExchangeStatusKey('9')).toBe('accountSettings.distribution.exchangeStatus.unknown')

    expect(getCouponExchangeStatusClass('1')).toBe('status-success')
    expect(getCouponExchangeStatusClass('2')).toBe('status-rejected')
    expect(getCouponExchangeStatusClass('0')).toBe('status-pending')
  })
})

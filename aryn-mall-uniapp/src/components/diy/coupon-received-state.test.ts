import { describe, expect, it } from 'vitest'
import { mergeReceivedCouponIds } from './coupon-received-state'

describe('领券组件已领取状态', () => {
  it('接口重新加载时从登录用户数据恢复已领取标记', () => {
    expect(mergeReceivedCouponIds(new Set(), [{ id: 'coupon-1', userReceiveCount: 1 }])).toEqual(new Set(['coupon-1']))
  })

  it('未登录查询没有领取计数时不误标已领取', () => {
    expect(mergeReceivedCouponIds(new Set(), [{ id: 'coupon-1', userReceiveCount: null }])).toEqual(new Set())
  })

  it('服务端数据不覆盖本次页面会话里的成功领取状态', () => {
    expect(mergeReceivedCouponIds(new Set(['coupon-1']), [{ id: 'coupon-2', userReceiveCount: 1 }])).toEqual(new Set(['coupon-1', 'coupon-2']))
  })
})

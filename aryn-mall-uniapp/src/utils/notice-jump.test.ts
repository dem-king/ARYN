import { describe, expect, it } from 'vitest'
import { isSafeCRoute, resolveNoticeJump } from './notice-jump'

/**
 * 站内信跳转解析（C 端）单测。
 *
 * 重点不是「能跳对」而是「不会跳错」：jumpPayload 来自运营配置与后端事件，
 * 一旦解析器放行外链/路径穿越，通知列表就变成开放重定向入口。
 */
describe('isSafeCRoute', () => {
  it('only allows C-end pages under /pages and /sub-pages', () => {
    expect(isSafeCRoute('/pages/home/index')).toBe(true)
    expect(isSafeCRoute('/sub-pages/order/order-detail/index?id=1')).toBe(true)
    expect(isSafeCRoute('/pages/promotion/seckill?from=notice')).toBe(true)
    // 管理端路由不归 C 端跳，必须落空
    expect(isSafeCRoute('/order/detail?id=1')).toBe(false)
    expect(isSafeCRoute('/admin/user/page')).toBe(false)
    expect(isSafeCRoute('/pages.json')).toBe(false)
  })

  it('rejects external / protocol / protocol-relative targets', () => {
    expect(isSafeCRoute('https://evil.example.com/pages/home/index')).toBe(false)
    expect(isSafeCRoute('http://evil.example.com')).toBe(false)
    expect(isSafeCRoute('//evil.example.com/pages/home/index')).toBe(false)
    expect(isSafeCRoute('javascript:alert(1)')).toBe(false)
    expect(isSafeCRoute('data:text/html,<script>alert(1)</script>')).toBe(false)
    expect(isSafeCRoute('/pages/home/index\\@evil.example.com')).toBe(false)
    expect(isSafeCRoute('  ')).toBe(false)
    expect(isSafeCRoute(undefined)).toBe(false)
  })

  it('rejects path traversal, including percent-encoded forms', () => {
    expect(isSafeCRoute('/pages/../admin/index')).toBe(false)
    expect(isSafeCRoute('/pages/%2e%2e/admin/index')).toBe(false)
    expect(isSafeCRoute('/pages/home/index?next=..%2Fadmin')).toBe(false)
    expect(isSafeCRoute('/pages/home/index%zz')).toBe(false)
  })
})

describe('resolveNoticeJump', () => {
  it('passes through a safe internal route from jumpPayload', () => {
    expect(
      resolveNoticeJump({
        jumpPayload: JSON.stringify({
          route: '/sub-pages/order/shared-cart/detail?id=cart-1',
        }),
      }),
    ).toBe('/sub-pages/order/shared-cart/detail?id=cart-1')
  })

  it('accepts the legacy bare-route jumpPayload form', () => {
    expect(
      resolveNoticeJump({ jumpPayload: '/pages/promotion/seckill' }),
    ).toBe('/pages/promotion/seckill')
  })

  it('prefers a safe route over biz mapping', () => {
    expect(
      resolveNoticeJump({
        jumpPayload: JSON.stringify({
          route: '/pages/promotion/seckill',
          bizType: 'ORDER',
          bizId: 'order-1',
        }),
      }),
    ).toBe('/pages/promotion/seckill')
  })

  it('never returns an unsafe jumpPayload route', () => {
    expect(
      resolveNoticeJump({
        jumpPayload: JSON.stringify({ route: 'https://evil.example.com/x' }),
      }),
    ).toBeUndefined()
    expect(
      resolveNoticeJump({
        jumpPayload: JSON.stringify({ route: '/order/detail?id=1' }),
      }),
    ).toBeUndefined()
  })

  it('maps bizType to the C-end business detail pages', () => {
    expect(
      resolveNoticeJump({
        jumpPayload: JSON.stringify({ bizType: 'ORDER', bizId: 'order-1' }),
      }),
    ).toBe('/sub-pages/order/order-detail/index?id=order-1')

    expect(
      resolveNoticeJump({
        jumpPayload: JSON.stringify({ bizType: 'REFUND', bizId: 'refund-1' }),
      }),
    ).toBe('/sub-pages/order/order-refunds/refunds-detail/index?id=refund-1')

    expect(
      resolveNoticeJump({
        jumpPayload: JSON.stringify({ bizType: 'PRODUCT', bizId: 'spu-1' }),
      }),
    ).toBe('/sub-pages/product/goods-detail/index?id=spu-1')

    // 优惠券到账：落到「我的优惠券」页（couponUser id 不是页面参数）
    expect(
      resolveNoticeJump({
        jumpPayload: JSON.stringify({ bizType: 'COUPON', bizId: 'coupon-user-1' }),
      }),
    ).toBe('/sub-pages/promotion/coupon/coupon-user/index')
  })

  it('encodes business ids instead of splicing raw text into the url', () => {
    expect(
      resolveNoticeJump({
        jumpPayload: JSON.stringify({ bizType: 'ORDER', bizId: 'order 1&from=evil' }),
      }),
    ).toBe('/sub-pages/order/order-detail/index?id=order%201%26from%3Devil')
  })

  it('falls back to jumpType when the payload only carries bizId', () => {
    expect(
      resolveNoticeJump({
        jumpType: 'ORDER',
        jumpPayload: JSON.stringify({ bizId: 'order-2' }),
      }),
    ).toBe('/sub-pages/order/order-detail/index?id=order-2')
  })

  it('keeps working when jumpType is the history BIZ_DETAIL marker', () => {
    expect(
      resolveNoticeJump({
        jumpType: 'BIZ_DETAIL',
        jumpPayload: JSON.stringify({ bizType: 'REFUND', bizId: 'refund-2' }),
      }),
    ).toBe('/sub-pages/order/order-refunds/refunds-detail/index?id=refund-2')
  })

  it('maps cardPayload ids to the matching detail page', () => {
    expect(resolveNoticeJump({ cardPayload: JSON.stringify({ orderId: 'order-3' }) }))
      .toBe('/sub-pages/order/order-detail/index?id=order-3')
    expect(resolveNoticeJump({ cardPayload: JSON.stringify({ refundId: 'refund-3' }) }))
      .toBe('/sub-pages/order/order-refunds/refunds-detail/index?id=refund-3')
    expect(
      resolveNoticeJump({
        cardPayload: JSON.stringify({ bizType: 'PRODUCT', bizId: 'spu-2' }),
      }),
    ).toBe('/sub-pages/product/goods-detail/index?id=spu-2')
  })

  it('treats cardPayload.orderId of a refund source as the refund id', () => {
    expect(
      resolveNoticeJump({
        cardPayload: JSON.stringify({ orderId: 'refund-4' }),
        sourceType: 'ORDER_REFUND_APPLY',
      }),
    ).toBe('/sub-pages/order/order-refunds/refunds-detail/index?id=refund-4')
  })

  it('returns undefined when nothing can be safely mapped', () => {
    expect(resolveNoticeJump({})).toBeUndefined()
    expect(resolveNoticeJump({ jumpType: 'NONE' })).toBeUndefined()
    expect(
      resolveNoticeJump({ jumpPayload: JSON.stringify({ url: 'https://evil.example.com' }) }),
    ).toBeUndefined()
    expect(resolveNoticeJump({ jumpPayload: '{bad json' })).toBeUndefined()
    // 有业务类型但没有任何 ID：不猜目标
    expect(
      resolveNoticeJump({ jumpPayload: JSON.stringify({ bizType: 'ORDER' }) }),
    ).toBeUndefined()
    // 未知业务类型不臆造路由
    expect(
      resolveNoticeJump({
        jumpPayload: JSON.stringify({ bizType: 'VESSEL_BIND_APPLY', bizId: 'a-1' }),
      }),
    ).toBeUndefined()
  })
})

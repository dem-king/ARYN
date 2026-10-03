import type { SharedCart } from '@/api/order/sharedCart'

import { describe, expect, it } from 'vitest'

import {
  CART_STATUS_CLOSED,
  CART_STATUS_COLLECTING,
  CART_STATUS_COMPLETED,
  CART_STATUS_DRAFT,
  CART_STATUS_SUBMITTED,
  CART_STATUS_WAITING_CONFIRM,
  cartActionLabel,
  cartDeadlineView,
  cartStatusLabel,
  cartStatusTheme,
  groupSharedCarts,
  isCartCollecting,
  isCartExpired,
  isCartReadonly,
  MEMBER_ROLE_CONFIRMATOR,
  MEMBER_ROLE_MEMBER,
  MEMBER_ROLE_OWNER,
  memberRoleLabel,
  pickActiveCart,
  sharedCartLocationLabel,
} from './shared-cart'

describe('cartStatusLabel', () => {
  it('covers all five backend statuses', () => {
    expect(cartStatusLabel(CART_STATUS_DRAFT)).toBe('草稿')
    expect(cartStatusLabel(CART_STATUS_COLLECTING)).toBe('收集中')
    expect(cartStatusLabel(CART_STATUS_WAITING_CONFIRM)).toBe('待确认')
    expect(cartStatusLabel(CART_STATUS_SUBMITTED)).toBe('已提交')
    expect(cartStatusLabel(CART_STATUS_CLOSED)).toBe('已关闭')
  })

  it('does not silently fall back to a concrete status for unknown values', () => {
    expect(cartStatusLabel('9')).toBe('未知状态')
    expect(cartStatusLabel('')).toBe('')
    expect(cartStatusLabel(null)).toBe('')
  })
})

describe('cartStatusTheme', () => {
  it('returns a theme for every known status', () => {
    for (const status of [
      CART_STATUS_DRAFT,
      CART_STATUS_COLLECTING,
      CART_STATUS_WAITING_CONFIRM,
      CART_STATUS_SUBMITTED,
      CART_STATUS_CLOSED,
    ]) {
      const theme = cartStatusTheme(status)
      expect(theme.bg).toMatch(/^#/)
      expect(theme.text).toMatch(/^#/)
    }
  })

  it('falls back to a neutral theme for unknown status', () => {
    expect(cartStatusTheme('9')).toEqual({ bg: '#F1EFE8', text: '#5F5E5A' })
    expect(cartStatusTheme(undefined)).toEqual({ bg: '#F1EFE8', text: '#5F5E5A' })
  })
})

describe('cart editability', () => {
  it('only treats collecting carts as editable', () => {
    expect(isCartCollecting(CART_STATUS_COLLECTING)).toBe(true)
    expect(isCartCollecting(CART_STATUS_DRAFT)).toBe(false)
    expect(isCartCollecting(CART_STATUS_WAITING_CONFIRM)).toBe(false)
    expect(isCartCollecting(CART_STATUS_SUBMITTED)).toBe(false)
    expect(isCartCollecting(CART_STATUS_CLOSED)).toBe(false)
  })

  it('treats submitted and closed carts as readonly', () => {
    expect(isCartReadonly(CART_STATUS_SUBMITTED)).toBe(true)
    expect(isCartReadonly(CART_STATUS_CLOSED)).toBe(true)
    expect(isCartReadonly(CART_STATUS_COLLECTING)).toBe(false)
  })
})

describe('memberRoleLabel', () => {
  it('covers all three member roles', () => {
    expect(memberRoleLabel(MEMBER_ROLE_OWNER)).toBe('发起人')
    expect(memberRoleLabel(MEMBER_ROLE_MEMBER)).toBe('成员')
    expect(memberRoleLabel(MEMBER_ROLE_CONFIRMATOR)).toBe('确认人')
  })

  it('returns empty for missing role', () => {
    expect(memberRoleLabel('')).toBe('')
    expect(memberRoleLabel(null)).toBe('')
  })
})

/** 构造最小可用的购物车对象，只填测试关心的字段 */
function makeCart(overrides: Partial<SharedCart> = {}): SharedCart {
  return {
    id: 'cart-1',
    cartNo: 'SC2104215337',
    vesselId: 'vessel-1',
    vesselName: '悦航2号',
    vesselCallId: 'call-1',
    portName: '上海港',
    berth: '123',
    ownerUserId: 'u1',
    confirmerUserId: 'u1',
    status: CART_STATUS_COLLECTING,
    ...overrides,
  }
}

describe('pickActiveCart', () => {
  it('accepts a collecting cart the viewer can edit', () => {
    const cart = makeCart()
    expect(pickActiveCart({ cart, itemCount: 0, memberCount: 1, totalAmount: 0, previewItems: [], previewTruncated: false })).toBe(cart)
  })

  it('rejects missing cart / non-collecting status / revoked edit right', () => {
    const base = { itemCount: 0, memberCount: 1, totalAmount: 0, previewItems: [], previewTruncated: false }
    expect(pickActiveCart(null)).toBeNull()
    expect(pickActiveCart({ ...base, cart: null })).toBeNull()
    expect(pickActiveCart({ ...base, cart: makeCart({ status: CART_STATUS_WAITING_CONFIRM }) })).toBeNull()
    expect(pickActiveCart({ ...base, cart: makeCart({ status: CART_STATUS_CLOSED }) })).toBeNull()
    expect(pickActiveCart({ ...base, cart: makeCart({ viewerCanEdit: false }) })).toBeNull()
  })
})

describe('sharedCartLocationLabel', () => {
  it('joins port and berth, falling back to vessel name', () => {
    expect(sharedCartLocationLabel(makeCart())).toBe('上海港 123')
    expect(sharedCartLocationLabel(makeCart({ berth: '' }))).toBe('上海港')
    expect(sharedCartLocationLabel(makeCart({ portName: '', berth: '' }))).toBe('悦航2号')
  })

  it('returns empty for missing cart', () => {
    expect(sharedCartLocationLabel(null)).toBe('')
    expect(sharedCartLocationLabel(undefined)).toBe('')
  })
})

/** 以当前时刻为基准构造 `HH:mm:ss` 形式的过期时间，避免测试依赖真实时钟 */
function at(now: Date, offsetMs: number) {
  const date = new Date(now.getTime() + offsetMs)
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} `
    + `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

const NOW = new Date(2026, 8, 29, 12, 0, 0)

describe('isCartExpired', () => {
  it('only applies to collecting carts (a submitted cart is not "expired")', () => {
    const expired = at(NOW, -60_000)
    expect(isCartExpired(makeCart({ expiresAt: expired }), NOW)).toBe(true)
    expect(isCartExpired(makeCart({ status: CART_STATUS_SUBMITTED, expiresAt: expired }), NOW)).toBe(false)
    expect(isCartExpired(makeCart({ status: CART_STATUS_CLOSED, expiresAt: expired }), NOW)).toBe(false)
  })

  it('treats an unparseable or missing deadline as not expired', () => {
    // 本地解析失败不该把仍在收集的单误标成已过期——服务端才是判定的权威
    expect(isCartExpired(makeCart({ expiresAt: undefined }), NOW)).toBe(false)
    expect(isCartExpired(makeCart({ expiresAt: '不是时间' }), NOW)).toBe(false)
    expect(isCartExpired(makeCart({ expiresAt: at(NOW, 60_000) }), NOW)).toBe(false)
  })
})

describe('cartDeadlineView', () => {
  it('renders a countdown for collecting carts and flags urgency under an hour', () => {
    const view = cartDeadlineView(makeCart({ expiresAt: at(NOW, 8 * 60_000) }), NOW)
    expect(view?.text).toBe('还剩 8 分')
    expect(view?.urgent).toBe(true)
  })

  it('does not flag a comfortable deadline as urgent', () => {
    const view = cartDeadlineView(makeCart({ expiresAt: at(NOW, 5 * 3600_000) }), NOW)
    expect(view?.text).toBe('还剩 5 小时')
    expect(view?.urgent).toBe(false)
  })

  it('reports an expired window instead of a zero countdown', () => {
    const view = cartDeadlineView(makeCart({ expiresAt: at(NOW, -60_000) }), NOW)
    expect(view?.expired).toBe(true)
    expect(view?.text).toBe('已过截止时间')
  })

  it('gives nothing for finished carts or missing deadlines', () => {
    // 已提交的单再显示倒计时会让人以为还能加购
    expect(cartDeadlineView(makeCart({ status: CART_STATUS_SUBMITTED, expiresAt: at(NOW, 3600_000) }), NOW)).toBeNull()
    expect(cartDeadlineView(makeCart({ expiresAt: undefined }), NOW)).toBeNull()
  })
})

describe('groupSharedCarts', () => {
  it('splits carts into in-progress and history, in that order', () => {
    const sections = groupSharedCarts([
      makeCart({ id: 'h1', status: CART_STATUS_SUBMITTED }),
      makeCart({ id: 'a1', status: CART_STATUS_COLLECTING, expiresAt: at(NOW, 3600_000) }),
      makeCart({ id: 'h2', status: CART_STATUS_CLOSED }),
    ], NOW)

    expect(sections.map(s => s.key)).toEqual(['active', 'history'])
    expect(sections[0].carts.map(c => c.id)).toEqual(['a1'])
    expect(sections[1].carts.map(c => c.id)).toEqual(['h1', 'h2'])
  })

  it('omits empty sections entirely rather than rendering an empty shell', () => {
    const sections = groupSharedCarts([makeCart({ id: 'a1' })], NOW)
    expect(sections).toHaveLength(1)
    expect(sections[0].key).toBe('active')
    expect(groupSharedCarts([], NOW)).toEqual([])
  })

  it('treats draft as in-progress, not history', () => {
    const sections = groupSharedCarts([makeCart({ id: 'd1', status: CART_STATUS_DRAFT })], NOW)
    expect(sections[0].key).toBe('active')
  })

  it('keeps a waiting-confirm cart in progress rather than inventing a section', () => {
    // 后端 status=3 目前没有写入路径；即使出现也不能把它归到历史里丢掉，
    // 否则一张没收满的单会从「进行中」里消失。
    const sections = groupSharedCarts([makeCart({ id: 'w1', status: CART_STATUS_WAITING_CONFIRM })], NOW)
    expect(sections.map(s => s.key)).toEqual(['history'])
    expect(sections[0].carts.map(c => c.id)).toEqual(['w1'])
  })

  it('sorts active carts by deadline so the most urgent floats up', () => {
    const sections = groupSharedCarts([
      makeCart({ id: 'later', expiresAt: at(NOW, 10 * 3600_000) }),
      makeCart({ id: 'sooner', expiresAt: at(NOW, 30 * 60_000) }),
    ], NOW)
    expect(sections[0].carts.map(c => c.id)).toEqual(['sooner', 'later'])
  })

  it('pushes expired carts below still-actionable ones', () => {
    // 过期的单服务端会兜底关单，但此刻界面上应让用户先看到还能动的
    const sections = groupSharedCarts([
      makeCart({ id: 'expired', expiresAt: at(NOW, -3600_000) }),
      makeCart({ id: 'live', expiresAt: at(NOW, 3600_000) }),
    ], NOW)
    expect(sections[0].carts.map(c => c.id)).toEqual(['live', 'expired'])
  })

  it('keeps completed carts in history, labelled as delivered', () => {
    const sections = groupSharedCarts([makeCart({ id: 'c1', status: CART_STATUS_COMPLETED })], NOW)
    expect(sections[0].key).toBe('history')
    expect(cartStatusLabel(sections[0].carts[0].status)).toBe('本次采购已送达')
  })
})

describe('cartActionLabel', () => {
  it('asks the confirmer to plan and a plain member to add goods', () => {
    expect(cartActionLabel(makeCart({ viewerCanConfirm: true, viewerCanEdit: true }), NOW)).toBe('排计划')
    expect(cartActionLabel(makeCart({ viewerCanConfirm: false, viewerCanEdit: true }), NOW)).toBe('去加货')
  })

  it('points at the order once one exists, whatever the rights say', () => {
    // 已生成订单的单，点进去要看的是一次真实的采购结果，而不是继续排计划
    expect(cartActionLabel(
      makeCart({ status: CART_STATUS_SUBMITTED, submitOrderId: 'order-1', viewerCanConfirm: true }),
      NOW,
    )).toBe('查看订单')
  })

  it('falls back to a neutral verb when the viewer can do nothing', () => {
    expect(cartActionLabel(makeCart({ viewerCanConfirm: false, viewerCanEdit: false }), NOW)).toBe('查看')
    expect(cartActionLabel(makeCart({ status: CART_STATUS_CLOSED }), NOW)).toBe('查看')
  })

  it('does not promise editing on an expired window', () => {
    // 过期单服务端已不可写，写「去加货」是骗点击
    expect(cartActionLabel(
      makeCart({ expiresAt: at(NOW, -60_000), viewerCanEdit: true, viewerCanConfirm: false }),
      NOW,
    )).toBe('查看')
  })
})

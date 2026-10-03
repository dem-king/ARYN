import { describe, expect, it } from 'vitest'
import { tabBar } from '@/pages.json'
import { CART_BADGE_LIMIT, CART_TAB_PATH, formatCartBadgeText, resolveCartTabIndex } from '@/utils/cart-badge'

describe('购物车 tabBar 角标（纯函数）', () => {
  it('从真实 pages.json 推导购物车 tab 下标，与线上 tabBar 同源', () => {
    // 线上 tab 顺序：首页 / 分类 / 购物车 / 我的
    const list = tabBar?.list ?? []
    expect(resolveCartTabIndex(list)).toBe(2)
    expect(list[2]?.pagePath).toBe(CART_TAB_PATH)
  })

  it('配置缺了购物车 tab 时返回 -1（整体停用，而不是打到错误 tab 上）', () => {
    expect(resolveCartTabIndex([{ pagePath: 'pages/home/index' }, { pagePath: 'pages/user/user-center/index' }])).toBe(-1)
    expect(resolveCartTabIndex([])).toBe(-1)
  })

  it('角标文案：0/负数/非法值无角标，正常数原样，超过上限收敛为 99+', () => {
    expect(formatCartBadgeText(0)).toBeNull()
    expect(formatCartBadgeText(-3)).toBeNull()
    expect(formatCartBadgeText(Number.NaN)).toBeNull()
    expect(formatCartBadgeText(1)).toBe('1')
    expect(formatCartBadgeText(12)).toBe('12')
    expect(formatCartBadgeText(CART_BADGE_LIMIT)).toBe('99')
    expect(formatCartBadgeText(CART_BADGE_LIMIT + 1)).toBe('99+')
    expect(formatCartBadgeText(5200)).toBe('99+')
  })
})

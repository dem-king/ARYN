import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * 购物车「会员专享价」契约守门（F3 §8.4）。
 *
 * 与限时折扣/新人价同款风险：接口接了、工具函数也有单测，但购物车行价与合计
 * 不同源、优先级别写反、划线价对不上，用户就会看到「行价会员价、合计原价」。
 * 这里把「整页一次批量拉价」「行价/划线价/合计同源」「会员价 > 新人价 > 折扣」
 * 绑成契约，避免后续重构再让购物车与会员价断线。
 */
const projectRoot = fileURLToPath(new URL('../../../../', import.meta.url))

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

const CART_PAGE = 'src/pages/user/shopping-cart/index.vue'

/** 截取页面源码里一个函数的实现：到下一个顶层 `function` / `async function` 为止 */
function functionSource(page: string, name: string) {
  const start = page.indexOf(`function ${name}(`)
  if (start < 0)
    return ''
  const ends = [
    page.indexOf('\nfunction ', start + 1),
    page.indexOf('\nasync function ', start + 1),
  ].filter(index => index > start)
  return page.slice(start, ends.length ? Math.min(...ends) : page.length)
}

describe('shopping cart member price contract', () => {
  it('loads member prices in one batch per cart list load', () => {
    const cart = source(CART_PAGE)
    // 共享层：批量接口 + 按 SKU 精确解析 + 成交单价兜底
    expect(cart).toContain('getMemberSkuPrices')
    expect(cart).toContain('resolveMemberPriceDisplay')
    expect(cart).toContain('memberPriceUnitPrice')
    expect(cart).toContain('emptyMemberPriceDisplay')
    // 整页只允许一处调用：逐行请求会把购物车首屏放大成 N 个请求
    expect(cart.match(/getMemberSkuPrices\(/g) || []).toHaveLength(1)

    const loader = functionSource(cart, 'loadMemberPrices')
    expect(loader).toContain('getMemberSkuPrices(skuIds)')
    // 同一 SKU 可能有多行，查询前必须去重
    expect(loader).toContain('new Set')
    // 失败静默回落原价，不能冒泡到购物车列表与结算主流程
    expect(loader).toContain('catch')
    expect(loader).toContain('memberDisplays.value = {}')
    // 价格晚于首屏返回：回来时必须重算合计
    expect(loader).toContain('computePrice()')

    // 与新人价/折扣价同一时机：列表加载/刷新/增删行都会走 getCartPage
    const listLoader = functionSource(cart, 'getCartPage')
    expect(listLoader).toContain('void loadMemberPrices()')
    expect(listLoader).toContain('void loadNewbiePrices()')
    expect(listLoader).toContain('void loadDiscountPrices()')
  })

  it('keeps member > newbie > discount priority and totals on the same unit price', () => {
    const cart = source(CART_PAGE)

    const unit = functionSource(cart, 'cartUnitPrice')
    const memberAt = unit.indexOf('showMemberPrice')
    const newbieAt = unit.indexOf('showNewbiePrice')
    const discountAt = unit.indexOf('discountUnitPrice')
    expect(memberAt).toBeGreaterThan(-1)
    expect(newbieAt).toBeGreaterThan(memberAt)
    expect(discountAt).toBeGreaterThan(newbieAt)
    expect(unit).toContain('memberPriceUnitPrice(fallback')

    // 合计与行价同源：不再直接读 salesPrice，否则行价会员价、合计原价
    const compute = functionSource(cart, 'computePrice')
    expect(compute).toContain('cartUnitPrice(val)')
    expect(compute).not.toContain('goodsSku.salesPrice')
  })

  it('renders the member price as main price with badge, guide and strike-through price', () => {
    const cart = source(CART_PAGE)
    // 行主价必须走与合计同源的 cartUnitPrice
    expect(cart).toContain(':text="cartUnitPrice(goods)"')
    // 达标角标（固定「会员价」文案）优先于新人价/折扣角标
    expect(cart).toContain('memberDisplayOf(goods).showMemberPrice')
    expect(cart).toContain('cart.memberBadge')
    expect(cart).toContain('member-badge')
    // 未达标/未登录透出「会员专享 ¥x」引导，不改主价
    expect(cart).toContain('memberGuideOf(goods)')
    expect(cart).toContain('class="member-guide"')
    expect(cart).toContain('function memberGuideOf(')

    // 划线价取会员价基准价（display.originalPrice），与主价同一份判定结果
    const strike = functionSource(cart, 'strikePriceOf')
    const memberStrikeAt = strike.indexOf('memberDisplayOf(row)')
    const newbieStrikeAt = strike.indexOf('newbieDisplayOf(row)')
    expect(memberStrikeAt).toBeGreaterThan(-1)
    expect(newbieStrikeAt).toBeGreaterThan(memberStrikeAt)
    expect(strike).toContain('member.memberPrice')
  })
})

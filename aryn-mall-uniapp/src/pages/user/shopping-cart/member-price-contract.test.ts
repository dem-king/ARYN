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
    // 行主价必须走与合计同源的 cartUnitPrice（price-text 组件只负责排版拆分）
    expect(cart).toContain(':value="cartUnitPrice(goods)"')
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

/**
 * 购物车「出清价（捡漏价）」口径契约。
 *
 * 真实缺陷（审计 §2.2 P1）：行价 `cartUnitPrice` 已按「出清 vs 会员取低」收敛，
 * 但角标与划线价仍先走会员分支——同一 SKU 同时命中会员+出清且出清更低时，
 * 主价是捡漏价、角标却标「会员价」、划线基准也错。加上「捡漏价」角标词条
 * 与到期提示，让购物车与卡片/会员专区对同一个成交价给出同一套解释。
 */
describe('shopping cart clearance price priority contract', () => {
  const CART_ZH = 'src/locales/modules/cart.zh-CN.ts'
  const CART_EN = 'src/locales/modules/cart.en-US.ts'

  it('keeps clearance ahead of member in the row unit price', () => {
    const unit = functionSource(source(CART_PAGE), 'cartUnitPrice')
    // 出清价胜出时直接返回出清价（不再允许会员价/新人价/折扣覆盖）
    const clearanceAt = unit.indexOf('clearanceWins(row)')
    const memberAt = unit.indexOf('member.showMemberPrice')
    expect(clearanceAt).toBeGreaterThan(-1)
    expect(memberAt).toBeGreaterThan(clearanceAt)
    expect(unit).toContain('clearance.clearancePrice')
  })

  it('badge follows the same clearance-first branch as the main price', () => {
    const cart = source(CART_PAGE)
    // 角标顺序：捡漏价 → 会员价 → 新人价 → 折扣
    expect(cart).toContain('v-if="clearanceWins(goods)"')
    expect(cart).toContain('t(\'cart.clearanceBadge\')')
    expect(cart).toContain('clearance-badge')

    const clearanceBadgeAt = cart.indexOf('v-if="clearanceWins(goods)"')
    const memberBadgeAt = cart.indexOf('memberDisplayOf(goods).showMemberPrice')
    expect(clearanceBadgeAt).toBeGreaterThan(-1)
    expect(memberBadgeAt).toBeGreaterThan(clearanceBadgeAt)
  })

  it('strike-through price uses the SKU current price when clearance wins', () => {
    const strike = functionSource(source(CART_PAGE), 'strikePriceOf')
    // 出清分支必须排在会员分支之前，否则划线价取会员价基准
    const clearanceAt = strike.indexOf('clearanceWins(row)')
    const memberAt = strike.indexOf('memberDisplayOf(row)')
    expect(clearanceAt).toBeGreaterThan(-1)
    expect(memberAt).toBeGreaterThan(clearanceAt)
    // 划线基准=接口下发的 SKU 当前售价（无则回落行内售价），与主价同一份判定
    expect(strike).toContain('clearanceDisplayOf(row).originalPrice')
    expect(strike).toContain('sku.salesPrice')
  })

  it('suppresses the members-only guide once clearance price wins', () => {
    const guide = functionSource(source(CART_PAGE), 'memberGuideOf')
    // 主价已是捡漏价时再引导「会员专享 ¥x」会让用户以为价格还没生效
    expect(guide).toContain('clearanceWins(row)')
    expect(guide).toMatch(/clearanceWins\(row\)\)\s*\n\s*return ''/)
  })

  it('renders a clearance expiry hint only when clearance wins and expire time exists', () => {
    const cart = source(CART_PAGE)
    const hint = functionSource(cart, 'clearanceExpireHintOf')
    expect(hint).toContain('clearanceWins(row)')
    expect(hint).toContain('clearanceDisplayOf(row).expireTime')
    expect(hint).toContain('formatClearanceExpireDate')
    expect(cart).toContain('clearanceExpireHintOf(goods)')
    expect(cart).toContain('clearance-expire')
    // iOS 不能直接 new Date('yyyy-MM-dd HH:mm:ss')：必须走手工解析，否则到期日不渲染
    expect(cart).toContain('parseLocalDateTimeTimestamp')
    expect(functionSource(cart, 'formatClearanceExpireDate')).toContain('parseLocalDateTimeTimestamp')
  })

  it('ships the clearance badge and expiry hint copy in both locales', () => {
    const zh = source(CART_ZH)
    const en = source(CART_EN)
    expect(zh).toMatch(/clearanceBadge:\s*'捡漏价'/)
    expect(zh).toMatch(/clearanceExpireHint:\s*'.*\{date\}.*'/)
    expect(en).toMatch(/clearanceBadge:\s*'Clearance Price'/)
    expect(en).toMatch(/clearanceExpireHint:\s*'.*\{date\}.*'/)
  })
})

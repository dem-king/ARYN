import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * 会员价（F3 §8.4）「接口 → 商详价格区」一致性契约守门。
 *
 * 会员价的典型漏网方式与折扣价/新人价相同：接口与工具函数都对了，但页面没调用、
 * 优先级排错（会员价压不过新人价）、或者只改了主价忘了划线/角标/引导条，
 * 用户就会看到「会员专区标会员价、商详还是原价 / 页面标会员价、加购弹层按原价」。
 * 类型检查发现不了这类跨文件断线，只能按源码结构把接线钉住。
 */
const projectRoot = fileURLToPath(new URL('../../../../', import.meta.url))

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

const DETAIL_PAGE = 'src/sub-pages/product/goods-detail/index.vue'
const GOODS_INFO = 'src/sub-pages/product/goods-detail/components/GoodsInfo.vue'
const PROMOTION_API = 'src/api/promotion.ts'

describe('member price API contract', () => {
  it('exposes an optional-login batch endpoint without hardcoding /boot', () => {
    const api = source(PROMOTION_API)
    const fn = api.slice(api.indexOf('export function getMemberSkuPrices'), api.length)

    // 微服务域首段由请求封装统一改写成 /boot，页面/API 层禁止硬编码
    expect(fn).toContain('/promotion/app/member/prices')
    expect(fn).toContain('skuIds')
    expect(fn).not.toContain('/boot')
    // 必须带登录态：后端据此判定用户等级是否达标（未登录返回 eligible=null）
    expect(fn).not.toContain('skipToken')
    expect(api).toContain('AppMemberPriceVO')
    for (const field of ['memberPrice', 'minLevelId', 'minLevelName', 'userLevelId', 'eligible'])
      expect(api).toContain(field)
    // 空入参不请求（避免无意义的 ?skuIds= 请求）
    expect(fn).toContain('Promise.resolve<AppMemberPriceVO | null>(null)')
  })

  it('never branches on VITE_OPEN_BOOT in the price integration', () => {
    for (const file of [DETAIL_PAGE, GOODS_INFO]) {
      const text = source(file)
      expect(text).not.toContain('VITE_OPEN_BOOT')
      expect(text).not.toContain('/boot')
    }
  })
})

describe('goods detail member price contract', () => {
  it('loads member prices in one batch per SPU, silently', () => {
    const page = source(DETAIL_PAGE)
    expect(page).toContain('getMemberSkuPrices')

    const loader = page.slice(
      page.indexOf('async function loadMemberPrices('),
      page.indexOf('/**\n * 当前聚焦的 SKU'),
    )
    // 一次带上本 SPU 全部 SKU，禁止逐 SKU 请求
    expect(loader).toContain('(state.goodsSpu?.goodsSkus || [])')
    expect(loader).toContain('getMemberSkuPrices(skuIds)')
    // 失败静默：会员价只是价格标注，接口异常不影响加购/下单主流程
    expect(loader).toContain('catch')
    expect(loader).toContain('state.memberPriceVO = null')

    // 与秒杀/新人价/折扣价同时机触发，不互相阻塞
    const getSpu = page.slice(page.indexOf('async function getSpu('), page.indexOf('function getBoundingClientRect('))
    expect(getSpu).toContain('loadSeckillInfo()')
    expect(getSpu).toContain('loadMemberPrices()')
    expect(getSpu).toContain('loadNewbiePrices()')
    expect(getSpu).toContain('loadDiscountPrices()')
    // 未 await：会员价请求不得拖慢商详首屏
    expect(getSpu).not.toContain('await loadMemberPrices()')
  })

  it('keeps seckill > member > newbie > discount priority in the page', () => {
    const page = source(DETAIL_PAGE)

    const memberBlock = page.slice(
      page.indexOf('const memberDisplay = computed('),
      page.indexOf('/**\n * 当前 SKU 的新人价展示'),
    )
    // 秒杀压会员价
    expect(memberBlock).toContain('state.seckillInfo')
    expect(memberBlock).toContain('resolveMemberPriceDisplay(state.memberPriceVO, currentSkuId.value)')

    const newbieBlock = page.slice(
      page.indexOf('const newbieDisplay = computed('),
      page.indexOf('/**\n * 当前 SKU 的限时折扣展示'),
    )
    // 会员价命中后新人价整块关掉
    expect(newbieBlock).toContain('memberDisplay.value.showMemberPrice')

    const discountBlock = page.slice(
      page.indexOf('const discountDisplay = computed('),
      page.indexOf('/**\n * 单规格直加时交给加购确认弹层的成交价'),
    )
    // 秒杀 > 新人价 > 会员价 的短路顺序：任一层命中都不再解释折扣价
    const seckillAt = discountBlock.indexOf('state.seckillInfo')
    const newbieAt = discountBlock.indexOf('newbieDisplay.value.showNewbiePrice')
    const memberAt = discountBlock.indexOf('memberDisplay.value.showMemberPrice')
    expect(seckillAt).toBeGreaterThan(-1)
    expect(newbieAt).toBeGreaterThan(seckillAt)
    expect(memberAt).toBeGreaterThan(newbieAt)
  })

  it('passes the member display down and uses it for the add-confirmation unit price', () => {
    const page = source(DETAIL_PAGE)
    // 父级算好的展示传给价格组件，组件内不再重复判定优先级
    expect(page).toContain(':member-display="memberDisplay"')

    const promotion = page.slice(
      page.indexOf('const quickAddPromotion = computed('),
      page.indexOf('/**\n * 加载当前 SKU 的秒杀信息'),
    )
    // 弹层小计必须用价格区同一个成交价：秒杀 > 会员价 > 新人价 > 折扣
    const memberAt = promotion.indexOf('member.showMemberPrice')
    const newbieAt = promotion.indexOf('display.showNewbiePrice')
    expect(memberAt).toBeGreaterThan(-1)
    expect(newbieAt).toBeGreaterThan(memberAt)
    expect(promotion).toContain('unitPrice: member.memberPrice')
  })

  it('renders member badge, guide strip and strike price in the price area', () => {
    const info = source(GOODS_INFO)
    expect(info).toContain('memberDisplay: MemberPriceDisplay')

    // 主价：秒杀 > 会员价 > 新人价 > 折后价 > 售价
    const main = info.slice(info.indexOf('const mainPrice = computed('), info.indexOf('/**\n * 价格区划线价'))
    const memberAt = main.indexOf('memberDisplay.showMemberPrice')
    const newbieAt = main.indexOf('newbieDisplay.showNewbiePrice')
    expect(memberAt).toBeGreaterThan(-1)
    expect(newbieAt).toBeGreaterThan(memberAt)
    expect(main).toContain('memberDisplay.memberPrice')

    // 划线价：会员价划接口回填的当前售价（与新人价同口径，不改变 original>sales 判定）
    const strike = info.slice(info.indexOf('const strikePrice = computed('), info.indexOf('/** 「省 ¥x」'))
    expect(strike).toContain('memberDisplay.showMemberPrice')
    expect(strike).toContain('memberDisplay.originalPrice')

    // 角标 + 引导条（未达标/未登录时「会员专享 ¥x」）
    expect(info).toContain('product.memberPrice')
    expect(info).toContain('class="member-badge"')
    expect(info).toContain('const memberGuideText = computed(')
    expect(info).toContain('product.memberOnly')
    expect(info).toContain('class="member-strip"')
    expect(info).toContain('{{ memberGuideText }}')
    // 有引导条时标题卡取消上提，避免首行被活动条裁切
    expect(info).toContain('Boolean(memberGuideText.value)')
  })
})

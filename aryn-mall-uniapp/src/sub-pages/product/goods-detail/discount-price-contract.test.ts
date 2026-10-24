import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * 限时折扣价「会场 → 商详 → 加购弹层」一致性契约守门。
 *
 * 背景（用户反馈的真实缺陷）：折扣会场卡片标「8 折 ¥23.92」，点卡片进商详价格区
 * 仍是 ¥29.90、快捷加购确认弹层小计也是 ¥29.90 —— 折扣价只存在于会场页。
 *
 * 漏网机制：会场页用的是活动接口自带的 discountPrice，商详/加购只认识 seckill/newbie
 * 两类活动；`getGoodsDiscountInfo`（单 SKU，带登录校验）虽然早就存在，但页面从没调用。
 * 这类「页面各写各的价格来源」的漂移类型检查发现不了，只能按源码结构钉住：
 * 批量接口、优先级顺序、价格区/角标/说明条、加购弹层成交价四处缺一不可。
 */
const projectRoot = fileURLToPath(new URL('../../../../', import.meta.url))

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

const DETAIL_PAGE = 'src/sub-pages/product/goods-detail/index.vue'
const GOODS_INFO = 'src/sub-pages/product/goods-detail/components/GoodsInfo.vue'
const QUICK_CART_BUTTON = 'src/components/quick-cart-button/index.vue'
const DISCOUNT_PAGE = 'src/pages/promotion/discount.vue'

describe('limited discount price consistency contract', () => {
  it('detail page loads the batch discount price once per SPU', () => {
    const page = source(DETAIL_PAGE)
    // 免登批量接口：一次带上本 SPU 全部 SKU，禁止逐 SKU 请求
    expect(page).toContain('getGoodsDiscountPrices')
    expect(page).toContain('loadDiscountPrices()')
    const loader = page.slice(
      page.indexOf('async function loadDiscountPrices('),
      page.indexOf('/**\n * 当前聚焦的 SKU'),
    )
    expect(loader).toContain('(state.goodsSpu?.goodsSkus || [])')
    expect(loader).toContain('getGoodsDiscountPrices(skuIds)')
    // 失败静默：折扣价只是价格标注，接口异常不影响加购/下单主流程
    expect(loader).toContain('catch')

    // 与秒杀/新人价同时机触发，不互相阻塞
    const getSpu = page.slice(page.indexOf('async function getSpu('), page.indexOf('function getBoundingClientRect('))
    expect(getSpu).toContain('loadSeckillInfo()')
    expect(getSpu).toContain('loadNewbiePrices()')
    expect(getSpu).toContain('loadDiscountPrices()')
  })

  it('detail page keeps seckill > newbie > discount priority', () => {
    const page = source(DETAIL_PAGE)
    const display = page.slice(
      page.indexOf('const discountDisplay = computed('),
      page.indexOf('/**\n * 单规格直加时交给加购确认弹层的成交价'),
    )
    // 上层活动命中时折扣展示必须关掉：同一时刻只解释一个成交价
    const seckillAt = display.indexOf('state.seckillInfo')
    const newbieAt = display.indexOf('newbieDisplay.value.showNewbiePrice')
    expect(seckillAt).toBeGreaterThan(-1)
    expect(newbieAt).toBeGreaterThan(seckillAt)
    expect(display).toContain('resolveDiscountPriceDisplay(state.discountPrices, currentSkuId.value)')
  })

  it('detail price area renders discount main price, badge, strike price and strip', () => {
    const page = source(DETAIL_PAGE)
    // 父级算好的展示传给价格组件，组件内不再重复判定优先级
    expect(page).toContain(':discount-display="discountDisplay"')

    const info = source(GOODS_INFO)
    expect(info).toContain('discountDisplay: DiscountPriceDisplay')
    // 主价：秒杀 > 新人价 > 限时折扣 > 售价
    const main = info.slice(info.indexOf('const mainPrice = computed('), info.indexOf('/**\n * 价格区划线价'))
    expect(main).toContain('discountDisplay.showDiscountPrice')
    expect(main).toContain('discountDisplay.discountPrice')
    // 「省￥x」按主价与划线价差计算，折扣场景自然生效
    expect(info).toContain('省￥{{ savingAmount }}')
    // 角标文案走共享层 formatDiscountValue 口径（8折/9.5折/减¥5/一口价）
    expect(info).toContain('discountDisplay.badgeText')
    expect(info).toContain('class="discount-badge"')
    // 说明条：解释折后价从哪来（活动名）
    expect(info).toContain('class="discount-strip"')
    expect(info).toContain('discountDisplay.activityName')
  })

  it('detail add-confirmation uses the discount price as the unit price', () => {
    const page = source(DETAIL_PAGE)
    const promotion = page.slice(
      page.indexOf('const quickAddPromotion = computed('),
      page.indexOf('/**\n * 加载当前 SKU 的秒杀信息'),
    )
    // 折扣是最后一层兜底：秒杀、新人价都不命中时才标折后价
    const newbieAt = promotion.indexOf('display.showNewbiePrice')
    const discountAt = promotion.indexOf('discount.showDiscountPrice')
    expect(discountAt).toBeGreaterThan(newbieAt)
    expect(promotion).toContain('unitPrice: discount.discountPrice')
    expect(promotion).toContain('限时折扣')
  })

  it('discount hall passes the activity price into the quick-add confirmation', () => {
    const page = source(DISCOUNT_PAGE)
    expect(page).toContain('function quickAddPromotionOf(')
    expect(page).toContain(':promotion="quickAddPromotionOf(goods)"')

    const hall = page.slice(
      page.indexOf('function quickAddPromotionOf('),
      page.indexOf('function goGoodsDetail('),
    )
    // 卡片标的是 discountPrice，弹层小计必须用同一价格
    expect(hall).toContain('goods?.discountPrice')
    expect(hall).toContain('unitPrice: price')
    expect(hall).toContain('限时折扣')

    // QuickCartButton 必须把活动价透传给 useQuickCart，否则弹层回落回 salesPrice
    const button = source(QUICK_CART_BUTTON)
    expect(button).toContain('promotion?: QuickAddPromotion | null')
    expect(button).toContain('promotion: props.promotion')
  })
})

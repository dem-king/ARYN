import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * 商品列表 / 收藏 / 足迹 / 推荐位 / 快捷商详 / 快捷加购弹层的「专区价」接线契约。
 *
 * 这类问题的典型漏网方式：页面模板还在渲染 `item.salesPrice`，而共享层
 * `useZonePrices#cardPriceOf` 已经接好——类型检查与既有单测都不会发现，
 * 用户在分类页看到会员价、在收藏页又看到原价。这里用源码结构守住每个入口。
 *
 * 另一条线是「先渲染、后补价」：`await zone.loadForSpuIds(...)` 会让列表首屏
 * 被价格接口阻塞（弱网下白屏），所以必须用 `void` 触发、由 revision 驱动重算。
 */
const projectRoot = fileURLToPath(new URL('../../../', import.meta.url))

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

/** 去掉注释，避免「注释里提到某某」被当成真实接线（契约只认代码） */
function stripComments(code: string) {
  return code
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/\/\/[^\n]*/g, '')
}

const GOODS_LIST_PANEL = 'src/components/goods-list-panel/index.vue'
const WATERFALL = 'src/components/waterfall-goods/index.vue'
const COLLECT = 'src/sub-pages/user/collect/index.vue'
const FOOTPRINT = 'src/sub-pages/user/footprint/index.vue'
const DETAIL_SHEET = 'src/components/goods-detail-sheet/index.vue'
const QUICK_CART_BUTTON = 'src/components/quick-cart-button/index.vue'
const USE_QUICK_CART = 'src/composables/useQuickCart.ts'
const QUICK_CART = 'src/utils/quick-cart.ts'

describe('p3 近N天最低价上卡（四张卡统一，主价旁描边小标）', () => {
  const TABS = 'src/components/diy/diy-goods-tabs/index.vue'
  const DIY_GOODS = 'src/components/diy/diy-goods/index.vue'

  it('card-tags 下发的 priceTagText 直渲染（服务端成文，前端零门槛/取整逻辑）', () => {
    for (const file of [GOODS_LIST_PANEL, WATERFALL, DIY_GOODS, TABS]) {
      const code = stripComments(source(file))
      // list-panel/waterfall 用 cardPrice 访问器，diy 两卡用 cardDisplay，均从共享层取值
      expect(code, file).toMatch(/\w+\(item\)\.priceTagText/)
      expect(code, file).toContain('price-tag')
      // 前端不做门槛/取整：不得出现自行拼「近14天」文案
      expect(code, file).not.toMatch(/近14天最低价/)
    }
  })

  it('priceTagText 由 useZonePrices 管线透传（cardTagOf 带 priceTagText）', () => {
    const code = stripComments(source('src/composables/useZonePrices.ts'))
    expect(code).toContain('priceTagText: tags?.priceTagText')
    expect(code).toContain('typeof item?.priceTagText === \'string\'')
  })

  it('降级契约：resolveListZonePrice 空结果 priceTagText=null，卡片不渲染新元素', () => {
    const code = stripComments(source('src/utils/zone-price.ts'))
    expect(code).toContain('priceTagText: null')
    expect(code).toContain('sanitizeProofText(input.priceTagText)')
  })
})

describe('goods-list-panel 专区价接线（分类 / 商品列表 / 搜索）', () => {
  it('按本页 SPU 异步补价（void，不阻塞列表首屏）', () => {
    const code = stripComments(source(GOODS_LIST_PANEL))
    expect(code).toContain('useZonePrices')
    expect(code).toContain('void zone.loadForSpuIds(')
    expect(code).not.toContain('await zone.loadForSpuIds(')
  })

  it('卡片主价走共享层 cardPriceOf，角标走 zonePriceBadgeText', () => {
    const code = stripComments(source(GOODS_LIST_PANEL))
    expect(code).toContain('zone.cardPriceOf(item)')
    expect(code).toContain('zonePriceBadgeText(cardPrice(item).badgeKind)')
    // 主价不再直接渲染接口的普通售价（否则会员价/捡漏价永远出不来）
    expect(code).not.toContain('{{ item.salesPrice }}')
  })

  it('快捷加购透传 skuId→专区成交价，卡片价与确认弹层一致', () => {
    const code = stripComments(source(GOODS_LIST_PANEL))
    expect(code).toContain(':zone-prices="zone.skuZonePricesOf(item.id)"')
  })
})

describe('waterfall-goods 推荐位专区价接线', () => {
  it('按本页 SPU 异步补价并渲染主价/角标/划线/引导', () => {
    const code = stripComments(source(WATERFALL))
    expect(code).toContain('useZonePrices')
    expect(code).toContain('void zone.loadForSpuIds(')
    expect(code).toContain('zone.cardPriceOf(item)')
    expect(code).toContain('zonePriceBadgeText(cardPrice(item).badgeKind)')
    expect(code).not.toContain('await zone.loadForSpuIds(')
  })

  it('快捷加购透传专区价', () => {
    const code = stripComments(source(WATERFALL))
    expect(code).toContain(':zone-prices="zone.skuZonePricesOf(item.id)"')
  })
})

describe('收藏 / 足迹专区价接线', () => {
  it('收藏：列表加载后按 spuId 批量补价，卡片价走 cardPriceOf', () => {
    const code = stripComments(source(COLLECT))
    expect(code).toContain('useZonePrices')
    expect(code).toContain('void zone.loadForSpuIds(')
    expect(code).toContain('zone.cardPriceOf(')
    expect(code).toContain('zonePriceBadgeText(')
    // 返回的 spu 普通售价不能再直接当主价渲染
    expect(code).not.toContain('splitPrice(spu?.salesPrice)')
  })

  it('足迹：列表加载后按 spuId 批量补价，卡片价走 cardPriceOf', () => {
    const code = stripComments(source(FOOTPRINT))
    expect(code).toContain('useZonePrices')
    expect(code).toContain('void zone.loadForSpuIds(')
    expect(code).toContain('zone.cardPriceOf(')
    expect(code).toContain('zonePriceBadgeText(')
    expect(code).not.toContain('splitPrice(item?.salesPrice)')
  })
})

describe('goods-detail-sheet 快捷商详专区价接线', () => {
  it('快捷商详价格条展示专区价，商品加载后异步补价', () => {
    const code = stripComments(source(DETAIL_SHEET))
    expect(code).toContain('useZonePrices')
    expect(code).toContain('void zone.loadForSpuIds(')
    expect(code).toContain('zone.cardPriceOf(')
    expect(code).toContain('zonePriceBadgeText(zonePriceCard.badgeKind)')
  })

  it('多规格弹层用专区成交价覆盖 SKU 展示价，加购透传专区价', () => {
    const code = stripComments(source(DETAIL_SHEET))
    expect(code).toContain('zone.skuZonePricesOf(')
    expect(code).toContain('resolveZoneSkuPrice(')
    expect(code).toContain('minZoneSkuPrice(')
    // 弹层必须换成覆盖后的数据源，不能再直接传原始 goodsSpu
    expect(code).toContain('skuGoods.value = skuLocalData.value')
    expect(code).toContain('zonePrices: zone.skuZonePricesOf(')
  })
})

describe('quick-cart-button / useQuickCart 专区价契约', () => {
  it('quick-cart-button 新增可选 zonePrices prop，不传时行为不变', () => {
    const code = stripComments(source(QUICK_CART_BUTTON))
    expect(code).toContain('zonePrices?: QuickAddZonePrices | null')
    // 可选 + 默认 null：商详/购物车等既有调用方不传即保持原行为
    expect(code).toMatch(/zonePrices:\s*null/)
  })

  it('规格弹层按 SKU 覆盖展示价并透传给确认弹层', () => {
    const code = stripComments(source(QUICK_CART_BUTTON))
    expect(code).toContain('resolveZoneSkuPrice(props.zonePrices')
    expect(code).toContain('minZoneSkuPrice(props.zonePrices)')
    expect(code).toContain('zonePrices: props.zonePrices')
  })

  it('useQuickCart 在活动价不适用时用专区价当确认弹层单价', () => {
    const code = stripComments(source(USE_QUICK_CART))
    expect(code).toContain('zonePrices?: QuickAddZonePrices | null')
    expect(code).toContain('isQuickAddPromotionApplicable(info, options.promotion)')
    expect(code).toContain('resolveQuickAddZonePrice(info, options.zonePrices)')
  })

  it('专区价纯函数守住「SKU 匹配 + 必须低于普通售价」', () => {
    const code = stripComments(source(QUICK_CART))
    expect(code).toContain('export function resolveQuickAddZonePrice(')
    expect(code).toContain('export function resolveZoneSkuPrice(')
    expect(code).toContain('export function minZoneSkuPrice(')
    // 专区价不得高于普通售价：否则宁可按普通价展示
    expect(code).toContain('entry.price >= sales')
  })
})

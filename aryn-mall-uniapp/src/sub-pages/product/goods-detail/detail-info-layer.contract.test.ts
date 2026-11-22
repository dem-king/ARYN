import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * 商详信息层结构守门。
 *
 * 首屏信息去重、优惠与档案弹层、详情折叠都靠结构约束：
 * 防止后续迭代把多档活动、长参数表和通用场景话术重新铺回主页面。
 */
const projectRoot = fileURLToPath(new URL('../../../../', import.meta.url))

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

const DETAIL_PAGE = 'src/sub-pages/product/goods-detail/index.vue'
const PROMOTION_HINTS = 'src/sub-pages/product/goods-detail/components/GoodsPromotionHints.vue'

describe('detail info layer contract', () => {
  it('keeps one compact archive entry whenever basic params or a deep profile exist', () => {
    const page = source(DETAIL_PAGE)
    const params = source('src/sub-pages/product/goods-detail/components/GoodsParamCard.vue')
    expect(page).toContain('const showGoodsArchive = computed(() => parameterRows.value.length > 0 || showGoodsProfile.value)')
    expect(page).toContain('v-if="promotionHints || showGoodsArchive"')
    expect(page).not.toContain('MIN_PARAM_ROWS')
    expect(page).not.toContain('showParameterCard')
    expect(params).toContain('defineEmits<{ openProfile: [] }>()')
    expect(params).not.toContain("emit('choose'")
    expect(params).not.toContain('MAX_VISIBLE_PARAM_ROWS')
  })

  it('measures anchors on tab click and refuses to move the nav highlight when measurement fails', () => {
    const page = source(DETAIL_PAGE)
    // 点击时现量锚点，避免评价异步加载晚于首次量测时「评价 tab 点了没反应」
    expect(page).toContain('measureTabAnchor(index, selector)')
    expect(page).toContain('window.scrollTo')
    // 量不到时仍然不改高亮，避免把用户带到错误位置
    expect(page).toContain('if (!item?.top)')
    expect(page).not.toContain('if (index > 0 && !item?.top)')
    expect(page).toContain('state.tabActive = index')
  })

  it('shows one offer summary and moves complete groups and tiers into the bottom sheet', () => {
    const hints = source(PROMOTION_HINTS)
    expect(hints).toContain('groupGiftOffers')
    expect(hints).toContain('formatHintAmount')
    expect(hints).toContain('offer-entry__summary')
    expect(hints).toContain('<wd-popup')
    // 完整满赠/满减分组必须位于弹层之后，主页面只保留摘要入口。
    expect(hints.indexOf('<wd-popup')).toBeLessThan(hints.indexOf('v-for="group in giftGroups"'))
    expect(hints.indexOf('<wd-popup')).toBeLessThan(hints.indexOf('v-for="group in volumeGroups"'))
    // toFixed(2) 会把 ¥3000 写成 ¥3000.00，金额口径统一走 goods-hints
    expect(hints).not.toContain('toFixed(2)')
  })

  it('renders product-level service promises inside the info card, not a separate card', () => {
    const page = source(DETAIL_PAGE)
    const info = source('src/sub-pages/product/goods-detail/components/GoodsInfo.vue')
    // 承诺行并回商品信息卡：不再单独挂保障卡，避免同一承诺出现两次
    expect(page).not.toContain('GoodsAssurance')
    expect(info).toContain('resolveServiceTags')
    expect(info).toContain('goods-title-card__promises')
  })

  it('removes duplicated scenario cards and limits long selling copy to two lines', () => {
    const page = source(DETAIL_PAGE)
    const info = source('src/sub-pages/product/goods-detail/components/GoodsInfo.vue')
    expect(page).not.toContain('GoodsUseScenarios')
    expect(page).not.toContain('resolveUseScenarios')
    expect(info).toContain('max-height: 50px')
    expect(info).toContain('.wot-theme-dark .goods-title-card__promises')
  })

  it('orders offers before parameters so the price block is followed by decision support', () => {
    const page = source(DETAIL_PAGE)
    expect(page.indexOf('<GoodsPromotionHints')).toBeLessThan(page.indexOf('<GoodsParamCard'))
  })

  it('never exposes an immediate-order button on the detail page', () => {
    const page = source(DETAIL_PAGE)
    const footer = source('src/sub-pages/product/goods-detail/components/GoodsFooter.vue')
    // 底栏只有加入购物车；SKU 弹层固定 mode=2（只显示购物车）
    expect(page).toContain('const skuMode = ref(2)')
    expect(footer).not.toContain('buyNow')
  })

  it('estimates the gift gap locally instead of pretending to know the order basket', () => {
    const page = source(DETAIL_PAGE)
    const hints = source(PROMOTION_HINTS)
    expect(page).toContain(':quantity="currentQuantity"')
    expect(page).toContain(':unit-price="currentSalesPrice"')
    expect(hints).toContain('resolveGiftProgress')
    expect(hints).toContain('tierCount')
  })

  it('keeps purchase facts visible and combines basic params with the deep product profile in one sheet', () => {
    const page = source(DETAIL_PAGE)
    const info = source('src/sub-pages/product/goods-detail/components/GoodsInfo.vue')
    const params = source('src/sub-pages/product/goods-detail/components/GoodsParamCard.vue')
    const profile = source('src/sub-pages/product/goods-detail/components/GoodsProfileSheet.vue')
    expect(page).toContain(':purchase-tags="purchaseTags"')
    expect(page).toContain(':show-profile="showGoodsProfile"')
    expect(page).toContain('<GoodsProfileSheet v-model="profileVisible" :rows="parameterRows" :profile="goodsProfile"')
    // 图集只留页码；商品档案不再浮在图片上，也不重复首屏规格/产地/储存值。
    expect(info).toContain('props.purchaseTags')
    expect(info).not.toContain('goods-swiper__profile')
    expect(params).toContain('archive-entry')
    expect(params).toContain("emit('openProfile')")
    expect(profile).toContain('rows?: GoodsParamRow[]')
    expect(profile).toContain('profile?: GoodsProfile | null')
    expect(profile).toContain('v-if="show && hasContent"')
    expect(profile).toContain('v-for="row in rows"')
    expect(profile).toContain('profile?.nutrition')
    expect(profile.indexOf('v-for="row in rows"')).toBeLessThan(profile.indexOf('profile?.ingredients'))
  })

  it('makes structured detail media-first, folds text-only sections and hides an empty detail header', () => {
    const page = source(DETAIL_PAGE)
    const sections = source('src/sub-pages/product/goods-detail/components/GoodsDetailSections.vue')
    expect(page).toContain('<GoodsDetailSections')
    expect(page).toContain('v-if="detailSections.length || state.goodsSpu.description"')
    expect(page).toContain('@resize="updateTabListPositions"')
    expect(page.indexOf('<GoodsDetailSections')).toBeLessThan(page.indexOf('<mp-html v-if="state.goodsSpu.description"'))
    // 有媒体时图片先于标题；纯文字正文只有点击展开后才渲染。
    expect(sections.indexOf('class="detail-story__image"')).toBeLessThan(sections.indexOf('class="detail-story__heading"'))
    expect(sections).toContain('v-if="section.content && isOpen(section, index)"')
    expect(sections).toContain('expanded.value[key] = !expanded.value[key]')
    expect(sections.match(/emit\('resize'\)/g)?.length).toBeGreaterThanOrEqual(2)
  })

  it('keeps the reputation bubble inside the gallery instead of fixed over the page', () => {
    const info = source('src/sub-pages/product/goods-detail/components/GoodsInfo.vue')
    expect(info).not.toContain('position: fixed')
    expect(info).toMatch(/&__bubble[\s\S]*position: absolute/)
  })
})

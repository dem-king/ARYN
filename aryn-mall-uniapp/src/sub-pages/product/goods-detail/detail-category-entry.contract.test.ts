import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * 商详「评价 → 去逛逛」回流的契约守门。
 *
 * 这类改动的回归方式很隐蔽：分类入口被挪到详情/推荐之后、跳转退回 switchTab
 * 导致先闪回首页、评价标签只改样式却没把 rateType 传给列表页——页面看起来都对，
 * 但用户点下去不是想要的结果。因此按源码结构断言关键接线。
 */
const projectRoot = fileURLToPath(new URL('../../../../', import.meta.url))

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

const DETAIL_PAGE = 'src/sub-pages/product/goods-detail/index.vue'
const COMMENT = 'src/sub-pages/product/goods-detail/components/GoodsComment.vue'
const CATEGORY_ENTRY = 'src/sub-pages/product/goods-detail/components/GoodsCategoryEntry.vue'
const APPRAISE_PAGE = 'src/sub-pages/product/goods-appraise/index.vue'

describe('goods detail review and category entry', () => {
  it('mounts the category entry below the review card', () => {
    const page = source(DETAIL_PAGE)
    const reviewIndex = page.indexOf('<GoodsComment')
    const entryIndex = page.indexOf('<GoodsCategoryEntry')
    expect(reviewIndex).toBeGreaterThan(-1)
    expect(entryIndex).toBeGreaterThan(reviewIndex)
  })

  it('pushes a stack category page so the detail can be returned to', () => {
    const entry = source(CATEGORY_ENTRY)
    expect(entry).toContain('setPendingLocate')
    expect(entry).toContain('uni.navigateTo')
    expect(entry).toContain('/sub-pages/product/category-browse/index')
    expect(entry).toContain('categoryFirstId')
    expect(entry).toContain('categorySecondId')
  })

  it('browse page reuses the category browser with a back arrow, tab page does not', () => {
    const browse = source('src/sub-pages/product/category-browse/index.vue')
    expect(browse).toContain('<CategoryBrowser show-back')
    const browser = source('src/components/category-browser/index.vue')
    expect(browser).toContain(':left-arrow="props.showBack"')
    const tabPage = source('src/pages/product/category/index.vue')
    expect(tabPage).toContain('<CategoryBrowser />')
  })

  it('keeps the review summary, full-list entry and empty state', () => {
    const comment = source(COMMENT)
    expect(comment).toContain('好评率')
    expect(comment).toContain('positiveRateText')
    expect(comment).toContain('还没有评价')
    expect(comment).toContain('查看全部')
  })

  it('applies the rate type passed from the review chips', () => {
    const page = source(APPRAISE_PAGE)
    expect(page).toContain('options?.rateType')
    expect(page).toContain('allowedRateTypes')
  })
})

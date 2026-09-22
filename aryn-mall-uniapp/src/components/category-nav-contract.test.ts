import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

const projectRoot = fileURLToPath(new URL('../../', import.meta.url))

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

/**
 * 分类页结构守门。
 *
 * 这类问题的典型漏网方式：改版后旧的锚点量测逻辑残留（既没人调用又占用维护成本）、
 * 浮层 z-index 低于 H5 tabBar 导致被切掉、或 categoryId 历史入参被改名导致
 * 装修位/优惠券跳转失效——三者都不会被类型检查发现。
 */
describe('category page structure', () => {
  const page = 'src/pages/product/category/index.vue'

  it('组合搜索导航栏、一级图标条与全部分类浮层', () => {
    const pageSource = source(page)
    expect(pageSource).toContain('<hr-search-navbar')
    expect(pageSource).toContain('<category-icon-strip')
    expect(pageSource).toContain('<category-all-sheet')
    expect(pageSource).toContain('<goods-list-panel')
  })

  it('删除锚点定位量测逻辑（改版后左右是筛选关系）', () => {
    const pageSource = source(page)
    expect(pageSource).not.toContain('measureCategoryBoxes')
    expect(pageSource).not.toContain('handleChange')
    expect(pageSource).not.toContain('itemScrollTop')
    expect(pageSource).not.toContain('getRect')
  })

  it('不再使用 wd-sidebar（与参考图的二级 rail 表现不一致）', () => {
    const pageSource = source(page)
    expect(pageSource).not.toContain('wd-sidebar')
    expect(pageSource).toContain('category-rail')
  })

  it('浮层懒挂载，不打开时不产生节点', () => {
    expect(source(page)).toMatch(/<category-all-sheet\s+v-if="sheetVisible"/)
  })

  it('浮层 z-index 高于 H5 tabBar(998) 与 SKU 弹层(990)、低于搜索导航栏(1000)', () => {
    const sheet = source('src/components/category-all-sheet/index.vue')
    // 只看样式块，避免命中注释里提到的数字
    const styleBlock = sheet.slice(sheet.indexOf('<style'))
    const match = styleBlock.match(/z-index:\s*(\d+)/)
    expect(match).toBeTruthy()
    const z = Number(match?.[1])
    // 低于导航栏 → 导航栏不被压暗（参考图搜索框仍是纯白、角标仍是满饱和红）
    expect(z).toBeLessThan(1000)
    // 高于 tabBar / SKU 弹层 → 遮罩能盖住它们，页面其余部分保持压暗
    expect(z).toBeGreaterThan(998)
    expect(z).toBeGreaterThan(990)
  })

  it('浮层顶边从导航栏下沿开始，不遮挡搜索框', () => {
    const pageSource = source(page)
    const sheet = source('src/components/category-all-sheet/index.vue')
    // 页面把导航栏真实占位高度透传给面板，避免按平台手算高度而漂移
    expect(pageSource).toContain('sheetTopOffset')
    expect(pageSource).toContain(':top-offset="sheetTopOffset"')
    expect(sheet).toContain('topOffset')
    // 导航栏组件需暴露该高度，作为唯一事实来源
    const navbar = source('src/components/hr-search-navbar/index.vue')
    expect(navbar).toContain('defineExpose({ placeholderHeight })')
  })

  it('浮层底边不贴屏幕底，下方露出压暗的页面内容', () => {
    const sheet = source('src/components/category-all-sheet/index.vue')
    const styleBlock = sheet.slice(sheet.indexOf('<style'))
    // 参考图面板底边在约 86.5% 屏高；写死 bottom:0 就等于铺满全屏
    expect(styleBlock).toMatch(/bottom:\s*13\.5%/)
    expect(styleBlock).toContain('border-bottom-left-radius')
  })

  it('未配图的类目渲染首字色块兜底，不渲染空白圆', () => {
    for (const component of [
      'src/components/category-icon-strip/index.vue',
      'src/components/category-all-sheet/index.vue',
    ]) {
      const src = source(component)
      expect(src).toContain('resolveCategoryFallback')
      // categoryPic 为空时兜底
      expect(src).toMatch(/v-if="showFallback\(item, index\)"/)
      // 已填图但 URL 不可达时也要兜底，否则渲染空白圆
      expect(src).toContain('@error="handleImageError(index)"')
    }
  })

  it('一级过滤传 categoryFirstId（后端 selectApiPage 已支持该字段）', () => {
    expect(source('src/components/goods-list-panel/index.vue')).toContain('categoryFirstId')
  })

  it('访客不请求购物车数量（分类页在白名单内，401 会把访客踢到登录页）', () => {
    const pageSource = source(page)
    expect(pageSource).toContain('authStore.isLoggedIn')
    // 守卫必须紧贴调用，避免退化成「判断了但照样请求」
    expect(pageSource).toContain('if (authStore.isLoggedIn)\n    shoppingCartStore.fetchCartCount()')
  })

  it('页面高度不按平台手算导航栏，避免与 hr-search-navbar 等高占位冲突', () => {
    const pageSource = source(page)
    // 旧写法按平台分别减去 44px/84px，换成 hr-search-navbar 后必然错位
    expect(pageSource).not.toMatch(/100vh\s*-\s*var\(--window-top\)\s*-\s*44px/)
    expect(pageSource).toContain('--window-bottom')
  })
})

describe('goods list entry compatibility', () => {
  it('保留 categoryId 作为二级类目的历史别名', () => {
    const listSource = source('src/sub-pages/product/goods-list/index.vue')
    // 装修链接 link-resolver 与优惠券/购物车跳转仍在用 ?categoryId=二级ID
    expect(listSource).toContain('options?.categorySecondId')
    expect(listSource).toContain('options?.categoryId')

    const resolver = source('src/components/diy/link-resolver.ts')
    expect(resolver).toContain('categoryId=')
  })

  it('商品列表页复用统一商品流面板，不再自建查询逻辑', () => {
    const listSource = source('src/sub-pages/product/goods-list/index.vue')
    expect(listSource).toContain('<goods-list-panel')
    expect(listSource).not.toContain('@query="queryList"')
  })
})

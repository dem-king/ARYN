import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * 业务页顶栏磨砂玻璃接线守门。
 *
 * 与装修页的沉浸式导航共用一套材质与状态机，差异只在「谁开开关」：
 *   · 装修页由页面配置 scrollEffect 驱动（DiyPage 下传）；
 *   · 其余业务页调用 useFrostedNavbar()，由独立的 provide 开关启用磨砂白。
 *
 * 两类页面的公共约束：
 * 1. 状态只从 useDiyNavbarScroll 的「顶部 ↔ 已滚动」布尔来，一次滚动最多翻转两次；
 * 2. 小程序端页面滚动必须在宿主 SFC 里写字面量 onPageScroll（编译期按源码
 *    字符串生成滚动位标记），内部列表滚动则把事件绑到 z-paging / scroll-view；
 * 3. 毛玻璃必须带 -webkit- 前缀（iOS 18 以下只认前缀写法）。
 */
const projectRoot = fileURLToPath(new URL('../../../', import.meta.url))

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

const FROSTED = 'src/composables/useFrostedNavbar.ts'
const SCROLL = 'src/composables/useDiyNavbarScroll.ts'
const NAVBAR = 'src/components/hr-navbar/index.vue'
const SEARCH_NAVBAR = 'src/components/hr-search-navbar/index.vue'
const GOODS_NAVBAR = 'src/sub-pages/product/goods-detail/components/GoodsNavbar.vue'
const CATEGORY = 'src/components/category-browser/index.vue'
const STRIP = 'src/components/category-icon-strip/index.vue'
const APP = 'src/App.vue'
const NAVBAR_SPACE = 'src/components/hr-navbar-space/index.vue'

describe('useFrostedNavbar：复用沉浸式导航状态 + 独立开关', () => {
  it('复用同一套滚动状态机，并单独 provide 磨砂开关', () => {
    const code = source(FROSTED)
    expect(code).toContain('useDiyNavbarScroll()')
    expect(code).toContain('provide(FROSTED_NAVBAR_KEY, true)')
    expect(code).toContain('handlePageScroll')
    expect(code).toContain('scrolled')
  })

  it('滚动事件归一化兼容页面与内部列表两种形态', () => {
    const code = source(SCROLL)
    expect(code).toContain('event?.scrollTop')
    expect(code).toContain('event?.detail?.scrollTop')
  })
})

describe('导航组件：磨砂材质自动套用', () => {
  it('hr-navbar 注入开关后回落到磨砂白常量，显式配置仍优先', () => {
    const code = source(NAVBAR)
    expect(code).toContain('inject(FROSTED_NAVBAR_KEY, false)')
    expect(code).toContain('FROSTED_SCROLL_EFFECT')
    expect(code).toContain('FROSTED_SCROLL_SURFACE')
    // 装修页下传的配置必须仍然优先（mode=none 的页面不能被自动接管）
    expect(code).toContain('props.scrollEffect')
    expect(code).toContain('props.scrollSurface')
  })

  it('搜索导航栏滚动后切半透明白 + 毛玻璃 + 细线', () => {
    const code = source(SEARCH_NAVBAR)
    expect(code).toContain('inject(FROSTED_NAVBAR_KEY, false)')
    expect(code).toContain('hr-search-navbar--frosted')
    expect(code).toContain('rgba(255, 255, 255, 0.86)')
    expect(code).toContain('-WEBKIT-backdrop-filter')
    expect(code).toContain('blur(18px) saturate(160%)')
  })

  it('搜索输入框默认保持浅灰实心胶囊，滚动磨砂后融进整条顶栏', () => {
    const code = source(SEARCH_NAVBAR)
    expect(code).toContain('search-navbar-field')
    expect(code).toMatch(/\.search-navbar-field[\s\S]*?background-color: #f5f6f8/)
    expect(code).toMatch(/\.search-navbar-field[\s\S]*?border: 1px solid #ececf0/)
    expect(code).toContain('search-navbar-field--blended')
    // 只有「页面滚动」才融合；浮层展开的 forceFrosted 保留胶囊轮廓
    expect(code).toContain('frosted.value && pageScrolled?.value === true')
    expect(code).toMatch(
      /search-navbar-field--blended[\s\S]*?background-color: transparent/,
    )
  })

  it('商详顶栏滚动到位后切磨砂玻璃，tabs 同步一步显示', () => {
    const code = source(GOODS_NAVBAR)
    expect(code).toContain('goods-detail-navbar--frosted')
    expect(code).toContain('rgba(255, 255, 255, 0.86)')
    expect(code).toContain('-WEBKIT-backdrop-filter')
    expect(code).toContain('scrollStatus ? 1 : rootOpacity')
  })
})

describe('分类页头部覆盖层：搜索栏 + 图标条压在商品流之上', () => {
  const code = source(CATEGORY)

  it('头部固定定位在搜索栏下沿，滚动一步切磨砂', () => {
    expect(code).toContain('--category-nav-height')
    expect(code).toContain('--category-strip-height')
    expect(code).toContain('category-header--frosted')
    expect(code).toContain('useFrostedNavbar()')
  })

  it('右栏商品流上探到页面顶部，滚动占位用头部真实高度', () => {
    expect(code).toContain('--category-header-height')
    expect(code).toContain('categoryHeaderHeight')
    // 头部高度必须实测回填（文案换行/语言切换会改变图标条高度）
    expect(code).toContain('measureIconStripHeight')
  })

  it('图标条磨砂态撤掉自身白底，毛玻璃才透得出来', () => {
    const strip = source(STRIP)
    expect(strip).toContain('icon-strip--frosted')
    expect(strip).toContain('background: transparent')
  })

  it('吸顶排序条与头部共用同一套磨砂材质，接缝处不断层', () => {
    // 分类页排序条吸顶后紧贴搜索栏 + 图标条下沿，纯白底会在接缝处露出一条材质分界
    const sortBar = source('src/components/goods-list-panel/index.vue')
    expect(sortBar).toMatch(/\.sort-bar[\s\S]*?rgba\(255, 255, 255, 0\.86\)/)
    expect(sortBar).toMatch(/\.sort-bar[\s\S]*?-WEBKIT-backdrop-filter/)
    expect(sortBar).toMatch(/\.sort-bar[\s\S]*?backdrop-filter: blur\(18px\) saturate\(160%\)/)
  })
})

describe('列表页整屏滚动占位', () => {
  it('占位高度取运行时实测状态栏，不再用恒为 25px 的 CSS 变量', () => {
    const space = source(NAVBAR_SPACE)
    // 小程序端 uni 注入 page{--status-bar-height:25px} 是写死的，与设备无关：
    // 刘海屏真实 44~59px，按 25px 占位短 20~35px，首屏卡片会顶到导航栏标题上。
    expect(space).toContain('uni.getWindowInfo()')
    expect(space).toContain('resolveNavbarSpaceHeight')
    expect(space).not.toContain('--status-bar-height')
    // 全局样式里也不许再出现 CSS 变量占位（旧实现就写在这里）
    expect(source(APP)).not.toContain('--status-bar-height')
  })
})

describe('宿主页必须把真实滚动源接到 handlePageScroll', () => {
  /** 页面滚动页：字面量注册（小程序编译期按页面源码生成滚动位标记） */
  const PAGE_SCROLL_PAGES = [
    'src/pages/user/user-center/index.vue',
    'src/pages/user/shopping-cart/index.vue',
    'src/sub-pages/order/order-detail/index.vue',
    'src/sub-pages/message/notice/index.vue',
    'src/sub-pages/user/distribution/index.vue',
  ]
  /** 内部列表滚动页：z-paging 的 @scroll 驱动（11 个，与需求文档的页数一致） */
  const INNER_SCROLL_PAGES = [
    'src/sub-pages/product/goods-appraise/index.vue',
    'src/sub-pages/user/collect/index.vue',
    'src/sub-pages/user/footprint/index.vue',
    'src/sub-pages/order/order-refunds/refunds-list/index.vue',
    'src/sub-pages/user/appraise/index.vue',
    'src/sub-pages/user/address/index.vue',
    'src/sub-pages/user/member/points-record.vue',
    'src/sub-pages/user/member/balance-record.vue',
    'src/sub-pages/promotion/coupon/coupon-list/index.vue',
    'src/sub-pages/promotion/group-buy/group-buy-list/index.vue',
    'src/sub-pages/promotion/group-buy/group-buy-record/index.vue',
  ]

  it('页面滚动页注册了页面级 onPageScroll 并接入 handlePageScroll', () => {
    for (const path of PAGE_SCROLL_PAGES) {
      const code = source(path)
      expect(code, `${path} 缺少页面级 onPageScroll(`).toContain('onPageScroll(')
      expect(code, `${path} 未接入 handlePageScroll`).toContain('handlePageScroll')
      expect(code, `${path} 应调用 useFrostedNavbar()`).toContain('useFrostedNavbar()')
    }
  })

  it('内部列表滚动页把 @scroll 绑到 handlePageScroll，并留出顶栏滚动占位', () => {
    for (const path of INNER_SCROLL_PAGES) {
      const code = source(path)
      expect(code, `${path} 缺少 @scroll="handlePageScroll"`).toContain(
        '@scroll="handlePageScroll"',
      )
      expect(code, `${path} 缺少顶栏等高占位`).toContain('<hr-navbar-space />')
      expect(code, `${path} 应调用 useFrostedNavbar()`).toContain('useFrostedNavbar()')
    }
  })

  it('装修页仍走 useDiyNavbarScroll，不被业务页开关接管', () => {
    for (const path of ['src/pages/home/index.vue', 'src/sub-pages/promotion/diy-page/index.vue']) {
      const code = source(path)
      expect(code).toContain('useDiyNavbarScroll()')
      expect(code).not.toContain('useFrostedNavbar')
    }
  })
})

import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * 沉浸式导航栏（装修页滚动渐显）C 端接线守门。
 *
 * 这类缺陷的漏网方式（详见 docs/50-需求文档/2026-10-27-小程序沉浸式导航栏/README.md）：
 *
 * 1. 微信小程序的页面选项由 `@dcloudio/uni-mp-vite` 的 runtimeHooks 插件对
 *    **页面 SFC 源文本**做字符串匹配生成 `__runtimeHooks` 位标记。把
 *    `onPageScroll` 只包在 composable 里调用，H5 一切正常，小程序端却完全
 *    收不到滚动事件 —— 类型检查、单测、浏览器预览全绿，只有真机滚动时
 *    导航栏不动。宿主页必须保留字面量 `onPageScroll(handlePageScroll)`。
 *
 * 2. 滚动值若经过 `DiyPage` 当 prop 下钻，滚动时整棵装修楼层树都会重渲染，
 *    低端机直接掉帧；正确做法是 composable `provide` + `hr-navbar` `inject`，
 *    只让导航子树响应。
 *
 * 3. 状态栏前景色若 `useDecorationPage` 与 `hr-navbar` 各写一份，fade 模式
 *    下两处会互相覆盖（导航刚翻转成黑，页面 watch 又写回静态白）。
 */
const projectRoot = fileURLToPath(new URL('../../../', import.meta.url))

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

const HOME_PAGE = 'src/pages/home/index.vue'
const DIY_LANDING_PAGE = 'src/sub-pages/promotion/diy-page/index.vue'
const DIY_RENDERER = 'src/components/diy/index.vue'
const NAVBAR = 'src/components/hr-navbar/index.vue'
const NAVBAR_SCROLL = 'src/composables/useDiyNavbarScroll.ts'
const DECORATION_PAGE = 'src/composables/useDecorationPage.ts'
const GOODS_DETAIL = 'src/sub-pages/product/goods-detail/index.vue'
const GOODS_TABS = 'src/components/diy/diy-goods-tabs/index.vue'

describe('装修页滚动接线契约', () => {
  it('两个整页装修宿主页都写了 onPageScroll(handlePageScroll) 字面量', () => {
    for (const path of [HOME_PAGE, DIY_LANDING_PAGE]) {
      const code = source(path)
      expect(code, `${path} 缺少字面量 onPageScroll(handlePageScroll)，小程序端收不到滚动`)
        .toContain('onPageScroll(handlePageScroll)')
      expect(code, `${path} 必须从 @dcloudio/uni-app 显式导入 onPageScroll`)
        .toMatch(/import \{[^}]*\bonPageScroll\b[^}]*\} from '@dcloudio\/uni-app'/)
      expect(code, `${path} 应调用 useDiyNavbarScroll()`).toContain('useDiyNavbarScroll()')
    }
  })

  it('滚动值以 provide 暴露、8px 量化，并用 onShow 补同步', () => {
    const code = source(NAVBAR_SCROLL)
    expect(code).toContain('provide(DIY_NAVBAR_SCROLL_KEY')
    expect(code).toContain('shallowRef')
    // 不量化会让导航每帧重渲染；8px 量化后 140px 渐显全程最多约 18 次更新
    expect(code).toMatch(/SCROLL_QUANTUM = 8/)
    // Tab 切回 / 页面返回不产生滚动事件，必须用 viewport scrollOffset 补一次
    expect(code).toContain('selectViewport')
    expect(code).toContain('onShow')
  })
})

describe('装修渲染器不消费滚动状态', () => {
  it('不 inject 滚动 ref、不把 scrollTop 当 prop 往下钻', () => {
    const code = source(DIY_RENDERER)
    // 只判断真实取值/注入动作：注释里提到 scrollTop 是说明为什么不该消费它
    expect(code).not.toContain('scrollTop.value')
    expect(code).not.toMatch(/\binject\(/)
    expect(code).not.toContain('DIY_NAVBAR_SCROLL_KEY')
    expect(code).not.toContain('useDiyNavbarScroll')
  })

  it('只把静态 scrollEffect / scrollSurface 下传给 hr-navbar', () => {
    const code = source(DIY_RENDERER)
    expect(code).toContain('resolveNavigationScrollSurface')
    expect(code).toContain(':scroll-effect="navigationScrollEffect"')
    expect(code).toContain(':scroll-surface="navigationScrollSurface"')
  })
})

describe('hr-navbar 滚动渐显渲染契约', () => {
  it('消费共享解析函数与注入的滚动 ref', () => {
    const code = source(NAVBAR)
    expect(code).toContain('resolveNavigationScrollAppearance')
    expect(code).toContain('inject(DIY_NAVBAR_SCROLL_KEY, null)')
    expect(code).toContain('fadeEnabled = computed')
    // 旧渲染契约不能丢：placeholder / fixed / 标题常驻插槽 / backgroundImage 字面量
    expect(code).toContain('placeholder')
    expect(code).toContain('fixed')
    expect(code).toContain('backgroundImage')
    expect(code).toContain('<template #title>')
    // fade 模式把 wd-navbar 根设透明，底色全部交给 fixed 背景层
    expect(code).toContain('background:transparent;')
  })

  it('背景层在导航之下且不挡点击，毛玻璃带 -webkit- 前缀', () => {
    const code = source(NAVBAR)
    expect(code).toMatch(/v-if="fadeEnabled"[\s\S]*?hr-navbar-bg/)
    expect(code).toMatch(/\.hr-navbar-bg \{[\s\S]*?z-index: 499/)
    expect(code).toMatch(/\.hr-navbar-bg \{[\s\S]*?pointer-events: none/)
    // iOS 18 以下只认 -webkit-backdrop-filter；不支持时仍是不透明度 .86 的底色层
    expect(code).toContain('-webkit-backdrop-filter')
    // 高度 = 状态栏 + 44px，与 wd-navbar 占位同口径
    expect(code).toContain('statusBarHeight + 44')
  })

  it('状态栏前景色去重后调用系统 API，且异常静默', () => {
    const code = source(NAVBAR)
    expect(code).toContain('uni.setNavigationBarColor')
    expect(code).toContain('lastFrontColor')
    expect(code).toContain('try {')
    expect(code).toContain('statusBarFrontColor')
  })
})

describe('状态栏归属不冲突', () => {
  it('useDecorationPage 在 fade 模式下让出 frontColor，非 fade 用解析后外观', () => {
    const code = source(DECORATION_PAGE)
    expect(code).toContain('resolveNavigationAppearance')
    expect(code).toContain('resolveEffectivePageTheme')
    expect(code).toMatch(/mode === 'fade'/)
    // 守卫必须在 setNavigationBarColor 之前 return，才能避免两处 watch 互相覆盖
    const guard = code.indexOf('navbarOwnsStatusBar.value')
    const setColor = code.indexOf('uni.setNavigationBarColor')
    expect(guard).toBeGreaterThan(-1)
    expect(setColor).toBeGreaterThan(guard)
    expect(code.slice(guard, setColor)).toContain('return')
    // 非 fade 的 frontColor 必须来自解析后的外观，而不是原始 textColor
    expect(code).toContain('normalizeNavigationTextColor(navigation.textColor)')
  })
})

describe('内嵌装修不渲染导航', () => {
  it('商详 DiyPage 带 embedded（不渲染装修导航、不撑满 100vh）', () => {
    const code = source(GOODS_DETAIL)
    const tag = code.match(/<DiyPage[\s\S]*?\/>/)?.[0] ?? ''
    expect(tag).toContain('embedded')
    expect(tag).toContain(':page-content-data="decorationContent"')
  })
})

describe('吸顶 Tab 栏与顶栏共用磨砂材质', () => {
  it('DiyPage 把沉浸式导航的目标底色材质下发给吸顶 Tab 栏', () => {
    const diy = source(DIY_RENDERER)
    // 只在整页 DIY + 导航可见 + 开了滚动渐显时生效
    expect(diy).toContain('immersiveNavEnabled')
    expect(diy).toContain("navigation.scrollEffect?.mode === 'fade'")
    expect(diy).toContain(':immersive-nav="immersiveNavEnabled"')
    // 材质令牌：目标底色（含 alpha）、模糊值、细线色，与 hr-navbar 同口径
    expect(diy).toContain("'--diy-nav-surface-bg'")
    expect(diy).toContain("'--diy-nav-surface-blur'")
    expect(diy).toContain("'--diy-nav-hairline'")
    expect(diy).toContain('toSurfaceCssColor')
  })

  it('吸顶 Tab 栏消费材质令牌；未下发时回落原白底（内嵌页不受影响）', () => {
    const tabs = source(GOODS_TABS)
    // 仅吸顶模式 + 沉浸式导航同时成立才加 class，普通 Tab 栏保持原样式
    expect(tabs).toContain("'goods-tabs__bar--immersive': immersiveNav && showData.stickyTabs")
    expect(tabs).toContain('var(--diy-nav-surface-bg, #fff)')
    expect(tabs).toContain('var(--diy-nav-surface-blur, none)')
    expect(tabs).toContain('var(--diy-nav-hairline, #f2f3f5)')
    // 与顶栏一致的 iOS 前缀兜底
    expect(tabs).toContain('-webkit-backdrop-filter')
  })
})

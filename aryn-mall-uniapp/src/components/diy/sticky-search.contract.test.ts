import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * 搜索框「滚动常驻」契约守门。
 *
 * 这个功能的漏网方式（对照 docs/50-需求文档/2026-10-27-搜索框滚动常驻/README.md）：
 *
 * 1. **静置外观回归**：常驻层是页面级固定元素，一旦在 scrollTop=0 就画出底色，
 *    首屏「搜索框直接坐在区块渐变上」的设计稿观感就被导航底色糊掉了；材质必须
 *    初始完全透明、随滚动「成形」。占位实例必须只保留布局高度（visibility:hidden），
 *    否则同屏会出现两条搜索框。
 *
 * 2. **滚动性能回归**：滚动值只能由常驻层组件自己 inject 消费，绝不能经过
 *    DiyPage 下钻——否则滚动时整棵装修楼层重渲染，低端机直接掉帧
 *    （与沉浸式导航栏同一条纪律，见 navbar-scroll.contract.test.ts）。
 *
 * 3. **材质分家**：常驻栏必须复用 hr-navbar 的滚动渐显解析与
 *    --diy-nav-surface-* 变量，否则滚动后「导航栏磨砂白、搜索栏纯白」会露出
 *    一条材质分界。
 *
 * 4. **位置约束**：只有「首个渲染区块的第一个组件」才能静态算出常驻顶边；
 *    其它位置必须安静回退为随页滚动，不能把搜索框错误地钉到导航栏下方。
 */
const projectRoot = fileURLToPath(new URL('../../../', import.meta.url))

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

const DIY_RENDERER = 'src/components/diy/index.vue'
const SEARCH_BAR = 'src/components/diy/diy-search-bar/index.vue'
const NAVBAR_SCROLL = 'src/composables/useDiyNavbarScroll.ts'
const NAVBAR = 'src/components/hr-navbar/index.vue'

describe('装修渲染器：常驻搜索框的识别与页面级渲染', () => {
  const code = source(DIY_RENDERER)

  it('只认「首个渲染区块的第一个组件 + sticky=true」，其它位置安静回退', () => {
    expect(code).toContain('const stickySearch = computed')
    expect(code).toContain('props.embedded')
    expect(code).toContain('sections.value[0]')
    expect(code).toContain("component.type !== 'search-bar'")
    expect(code).toContain('showData.sticky !== true')
    // 横滑区块里的搜索框不在页面顶端，不能常驻
    expect(code).toContain('first.style.horizontalScroll')
  })

  it('页面级渲染 pinned 实例，并把导航外观/滚动材质静态下传', () => {
    expect(code).toContain('v-if="stickySearch"')
    expect(code).toContain('pinned')
    expect(code).toContain(':pinned-top="stickySearch.top"')
    expect(code).toContain(':pinned-inset-x="stickySearch.insetX"')
    expect(code).toContain(':nav-appearance="navigationAppearance"')
    expect(code).toContain(':scroll-effect="navigationScrollEffect"')
    expect(code).toContain(':scroll-surface="navigationScrollSurface"')
  })

  it('区块内原实例退化为占位，高度由它保留（不能出现两条搜索框）', () => {
    expect(code).toContain(':sticky-placeholder="stickySearchId === item.id"')
    expect(code).toContain('const stickySearchId = computed')
  })

  it('DiyPage 不消费滚动状态（scrollTop 只允许在叶子组件里 inject）', () => {
    expect(code).not.toContain('DIY_NAVBAR_SCROLL_KEY')
    expect(code).not.toMatch(/\binject\(/)
    expect(code).not.toContain('scrollTop.value')
  })

  it('吸顶基准线让出常驻搜索栏：Tab 栏 / 吸顶区块停在搜索框下沿', () => {
    expect(code).toContain('const stickyOffsetTop = computed')
    expect(code).toContain("'--diy-sticky-top': `${stickyOffsetTop.value}px`")
    expect(code).toContain('style.top = `${stickyOffsetTop.value}px`')
    // 占位高度公式必须包含行高与搜索框自身外边距/内边距，否则吸顶栏会被盖住
    expect(code).toContain('SEARCH_BAR_ROW_HEIGHT')
    expect(code).toContain('occupiedHeight')
  })
})

describe('常驻搜索栏接管头部下沿', () => {
  it('导航栏底边细线交给搜索栏渲染，一整块头部不被切成两段', () => {
    // 导航栏磨砂白 + 自己的 1px 细线 + 搜索栏磨砂白，会在头部中间露一条缝；
    // 常驻搜索栏出现时由 DiyPage 关掉导航栏细线，改由搜索栏底边承担分界。
    expect(source(DIY_RENDERER)).toContain(':hairline="!stickySearch"')
    const navbar = source(NAVBAR)
    expect(navbar).toContain('hairline?: boolean')
    expect(navbar).toContain('v-if="hairline"')
  })
})

describe('搜索栏组件：常驻层材质与静置零变化', () => {
  const code = source(SEARCH_BAR)

  it('只由常驻层自己 inject 页面滚动值，并复用导航滚动渐显解析', () => {
    expect(code).toContain('inject(DIY_NAVBAR_RAW_SCROLL_KEY, null)')
    expect(code).toContain('inject(DIY_NAVBAR_SCROLL_KEY, null)')
    expect(code).toContain('resolveNavigationScrollAppearance')
    expect(code).toContain('materialScrollTop.value')
  })

  it('材质跟随逐帧原始滚动值、不加 CSS 时间过渡（用户反馈「比导航栏慢一步」的根因）', () => {
    // 8px 量化值 + transition: opacity .18s 会让大面积色块比导航栏晚 ~180ms 到位；
    // 材质层必须直接消费页面逐帧原始值，且不得再挂时间过渡。
    expect(source(NAVBAR_SCROLL)).toContain('DIY_NAVBAR_RAW_SCROLL_KEY')
    expect(source(NAVBAR_SCROLL)).toContain(
      'provide(DIY_NAVBAR_RAW_SCROLL_KEY, rawScrollTop)',
    )
    expect(code).toContain('pageRawScrollTop?.value ?? pageScrollTop?.value ?? 0')
    expect(code).not.toMatch(/transition:\s*opacity/)
    // 起步「成形」距离收紧到一个量化步，避免起步阶段比导航栏慢半拍
    expect(code).toContain('MATERIALIZE_DISTANCE = 8')
  })

  it('静置完全透明、随滚动「成形」，避免首屏被导航底色糊住', () => {
    expect(code).toContain('MATERIALIZE_DISTANCE')
    expect(code).toContain('materialize')
    // 材质两层不透明度 = 渐显进度 × 成形进度
    expect(code).toContain('material.value.baseOpacity * materialize.value')
    expect(code).toContain('material.value.surfaceOpacity * materialize.value')
  })

  it('材质与导航栏同源：复用 CSS 变量 + 毛玻璃双前缀', () => {
    expect(code).toContain('--diy-nav-surface-bg')
    expect(code).toContain('--diy-nav-hairline')
    expect(code).toContain('search-bar-material__base')
    expect(code).toContain('search-bar-material__surface')
    expect(code).toContain('search-bar-material__hairline')
    expect(code).toContain('-webkit-backdrop-filter')
  })

  it('pinned 用页面级 fixed 且压在导航栏之下（z-index < 500）', () => {
    expect(code).toContain("'position': 'fixed'")
    expect(code).toContain("'z-index': '480'")
    expect(code).toContain('pinnedTop')
    expect(code).toContain('pinnedInsetX')
  })

  it('占位实例不可见不可点、只保留高度；普通实例样式原样返回', () => {
    expect(code).toContain('props.stickyPlaceholder')
    expect(code).toContain("'visibility': 'hidden'")
    expect(code).toContain("'pointer-events': 'none'")
    expect(code).toContain('if (!props.pinned)')
    // 行内容必须叠在材质层之上，否则材质会盖住搜索条与铃铛
    expect(code).toContain('position: relative')
    expect(code).toContain('z-index: 1')
  })

  it('图标色静置沿用区块底色、材质出现后跟随导航文字色翻转', () => {
    expect(code).toContain('materialized')
    expect(code).toContain('return material.value.textColor')
    expect(code).toContain('pickReadableIconColor(ambientBackground.value')
  })
})

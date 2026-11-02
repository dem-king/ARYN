import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

import { GLASS_TAB_ITEMS, resolveGlassTabKey } from './tabs'

/**
 * 底部 tabBar 玻璃材质守门。
 *
 * 平台差异（必须写清，避免后人「统一」成一套后一边失效）：
 *  · H5：uni-app 内置 tabBar 是真实 DOM，直接全局 CSS 加磨砂材质，布局零改动；
 *  · 微信小程序：原生 tabBar 只支持纯色、无模糊能力，只能隐藏后自绘玻璃底栏。
 *
 * 两端共用同一组材质数值（92% 白 + blur 18px）与同一套 tab 配置（tabs.ts）。
 *
 * 小程序还有一份原生首帧实现（src/custom-tab-bar/）：tab 切换是换页，新页面
 * 的 Vue 没启动前自绘底栏还不存在，首次切 tab 会看到底栏空一段；原生组件由
 * 框架随页面首帧渲染，等 Vue 版挂载后立即隐藏并接管。两份视觉必须逐值对齐。
 */
const projectRoot = fileURLToPath(new URL('../../../', import.meta.url))

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

const APP = 'src/App.vue'
const BAR = 'src/components/glass-tabbar/index.vue'
const LAYOUT = 'src/layouts/tabbar.vue'
const CATEGORY = 'src/components/category-browser/index.vue'
const CATEGORY_PAGE = 'src/pages/product/category/index.vue'
const CART = 'src/pages/user/shopping-cart/index.vue'

describe('tab 配置与选中态解析', () => {
  it('四个 tab 与 pages.json 的 tabBar.list 一一对应', () => {
    expect(GLASS_TAB_ITEMS.map(item => item.route)).toEqual([
      'pages/home/index',
      'pages/product/category/index',
      'pages/user/shopping-cart/index',
      'pages/user/user-center/index',
    ])
    for (const item of GLASS_TAB_ITEMS) {
      expect(item.path).toBe(`/${item.route}`)
      expect(item.labelKey.startsWith('tabbar.')).toBe(true)
      expect(item.icon).toContain('/static/tabbar/')
    }
  })

  it('路由 → 高亮 tab：容忍前导/尾随斜杠，未知路由回落首页', () => {
    expect(resolveGlassTabKey('pages/user/shopping-cart/index')).toBe('cart')
    expect(resolveGlassTabKey('/pages/user/user-center/index/')).toBe('user')
    expect(resolveGlassTabKey('pages/product/category/index')).toBe('category')
    expect(resolveGlassTabKey('pages/unknown/index')).toBe('home')
  })
})

describe('H5：内置 tabBar 直接加磨砂材质', () => {
  const code = source(APP)

  it('H5 端点内容材质，且做成悬浮胶囊（居中留边 / 圆角 / 浮起阴影）', () => {
    expect(code).toContain('/* #ifdef H5 */')
    expect(code).toContain('uni-tabbar .uni-tabbar')
    expect(code).toContain('background-color: rgba(255, 255, 255, 0.86) !important')
    expect(code).toContain('backdrop-filter: blur(18px) saturate(160%) !important')
    // iOS 18 以下只认前缀写法
    expect(code).toContain('-WEBKIT-backdrop-filter')
    // 悬浮胶囊：居中 + 左右留边 + 贴底 16px + 高 52px + 26px 圆角
    expect(code).toContain('width: calc(100% - 40px) !important')
    expect(code).toContain('bottom: 16px !important')
    expect(code).toContain('height: 52px !important')
    expect(code).toContain('border-radius: 26px !important')
    expect(code).toContain('box-shadow: 0 8px 24px')
    // 原生 tab 项按 50px 定高、图标 24px / 文案 10px，统一收到胶囊比例
    expect(code).toMatch(/uni-tabbar__item[\s\S]*?height: 52px !important/)
    expect(code).toContain('width: 26px !important')
    expect(code).toContain('font-size: 11px !important')
    // 原生 0.5px 分隔线与磨砂材质叠在一起会变成深灰线，必须隐藏
    expect(code).toContain('.uni-tabbar-border')
    expect(code).toContain('display: none !important')
  })

  it('深色模式跟随系统，不把夜间 tabBar 强制刷白', () => {
    expect(code).toMatch(/prefers-color-scheme: dark[\s\S]*?uni-tabbar/)
  })
})

describe('小程序：自绘玻璃底栏', () => {
  const code = source(BAR)

  it('材质与顶部导航同源：86% 白 + blur 18px', () => {
    expect(code).toContain('rgba(255, 255, 255, 0.86)')
    expect(code).toContain('-WEBKIT-backdrop-filter: blur(18px) saturate(160%)')
    expect(code).toContain('blur(18px) saturate(160%)')
  })

  it('层级与原生 tabBar 对齐：页面弹层之下、全部分类浮层之上', () => {
    expect(code).toContain('z-index: 998')
  })

  it('悬浮胶囊：居中、左右留边、贴底 16px、高 52px、圆角 26px、浮起阴影', () => {
    expect(code).toContain('left: 50%')
    expect(code).toContain('transform: translateX(-50%)')
    expect(code).toContain('width: calc(100% - 40px)')
    expect(code).toContain('max-width: 480px')
    expect(code).toContain('bottom: 16px')
    expect(code).toContain('height: 52px')
    expect(code).toContain('border-radius: 26px')
    expect(code).toContain('box-shadow: 0 8px 24px')
  })

  it('挂载后接管原生首帧占位栏（custom 模式下 hideTabBar 不生效）', () => {
    // 老基础库不支持 custom 时仍靠 hideTabBar 兜底
    expect(code).toContain('uni.hideTabBar')
    expect(code).toContain('getTabBar')
    expect(code).toMatch(/bar\.setData\(\{ hidden: true \}\)/)
    expect(code).toContain('onMounted')
    // 页面首次 onShow 可能早于挂载，必须先等 vueMounted，否则会提前隐藏占位栏
    expect(code).toMatch(/onShow\(\(\) => \{[\s\S]*?vueMounted[\s\S]*?takeOverNativeTabBar\(\)/)
    expect(code).toContain('uni.switchTab')
  })

  it('按路由找本页的底栏实例，切页瞬间不会误伤新页面', () => {
    expect(code).toMatch(/pages\.find\(/)
    expect(code).toContain('item.route === expected')
  })

  it('把主题色/深色/角标/文案写给下一个页面的原生首帧', () => {
    expect(code).toContain('__glassTabBarSeed')
    expect(code).toContain('primaryColor')
    expect(code).toContain('dark')
    expect(code).toContain('cartBadge.value')
    expect(code).toContain('labels')
  })

  it('购物车角标直接读 store（原生角标 API 在隐藏的 tabBar 上无效）', () => {
    expect(code).toContain('shoppingCartStore.cartCount')
    expect(code).toContain('formatCartBadgeText')
  })

  it('选中态用选中图标 + 主题色文案', () => {
    expect(code).toContain('activeIcon')
    expect(code).toContain('--wot-color-theme-primary')
  })
})

describe('tab 页布局只在微信小程序渲染自绘底栏', () => {
  const code = source(LAYOUT)

  it('MP-WEIXIN 条件编译包住 GlassTabBar，H5 继续用原生 tabBar', () => {
    expect(code).toMatch(/<!-- #ifdef MP-WEIXIN -->\s*<GlassTabBar[\s\S]*?<!-- #endif -->/)
    // 不能无条件渲染：H5 会与原生 tabBar 叠两条底栏
    expect(code).not.toMatch(/TEMP-H5-PREVIEW/)
  })

  it('活动 tab 由当前页面路由解析，onShow 兜底', () => {
    expect(code).toContain('resolveGlassTabKey')
    expect(code).toContain('getCurrentPages')
    expect(code).toMatch(/onShow\(/)
  })
})

describe('小程序自定义 tabBar：从根上不渲染原生底栏', () => {
  it('pages.config 无条件写入 custom（按平台条件写会被 H5 覆盖）', () => {
    const code = source('pages.config.ts')
    // 微信 schema 字段：custom=true 时小程序从根上不渲染原生 tabBar
    expect(code).toMatch(/const customTabBar = \{ custom: true \}/)
    // 必须无条件写进 tabBar。src/pages.json 是 H5 / 小程序 / App 共用的同一份生成文件，
    // 按编译平台条件写入时，任意一次 H5 启动或构建都会把它重写成「不带 custom」；
    // 正在跑的 mp-weixin dev 监听到 pages.json 变化后重新生成的 app.json 随即丢掉
    // custom，微信开发者工具就退回原生底栏（2026-10-29 实际回归过）。
    expect(code).not.toContain('UNI_PLATFORM')
    expect(code).not.toContain('useCustomTabBar')
    expect(code).toMatch(/tabBar:\s*\{[\s\S]*?\.\.\.customTabBar/)
  })

  it('提供合法的原生 custom-tab-bar 组件（首帧兜底）', () => {
    expect(source('src/custom-tab-bar/index.json')).toContain('"component": true')
    for (const file of ['index.js', 'index.wxml', 'index.wxss']) {
      expect(source(`src/custom-tab-bar/${file}`).length, `缺少 ${file}`).toBeGreaterThan(0)
    }
  })
})

describe('小程序原生首帧底栏：页面 Vue 启动前兜住空窗', () => {
  const NATIVE_JS = 'src/custom-tab-bar/index.js'
  const NATIVE_WXML = 'src/custom-tab-bar/index.wxml'
  const NATIVE_WXSS = 'src/custom-tab-bar/index.wxss'

  it('tab 清单与 tabs.ts 一一对应（路径与两套图标）', () => {
    const code = source(NATIVE_JS)
    for (const item of GLASS_TAB_ITEMS) {
      expect(code, `${item.key} 缺少 path`).toContain(`'${item.path}'`)
      expect(code, `${item.key} 缺少 icon`).toContain(`'${item.icon}'`)
      expect(code, `${item.key} 缺少 activeIcon`).toContain(`'${item.activeIcon}'`)
    }
  })

  it('选中项按真实路由解析：attached 时页面栈为空，先中性再下一拍点亮', () => {
    const code = source(NATIVE_JS)
    expect(code).toContain('getCurrentPages')
    expect(code).toContain('selectedIndex')
    expect(code).toMatch(/selected: -1/)
    expect(code).toMatch(/setTimeout\(function \(\) \{\s*self\.applyState\(\)/)
    // attached 阶段页面栈为空时不能亮错 tab
    expect(code).toMatch(/if \(!route\) \{\s*return -1/)
  })

  it('文案/主题色/深色/角标取 Vue 版写入的 seed，缺省回落本地语言', () => {
    const code = source(NATIVE_JS)
    expect(code).toContain('__glassTabBarSeed')
    expect(code).toContain('ARYN_LOCALE')
    expect(code).toContain('primaryColor')
    expect(code).toContain('badge')
    expect(code).toContain('systemDark')
  })

  it('视觉与 Vue 版逐值一致（胶囊几何 + 材质 + 字号 + 角标）', () => {
    const wxss = source(NATIVE_WXSS)
    for (const rule of [
      'left: 50%',
      'transform: translateX(-50%)',
      'width: calc(100% - 40px)',
      'max-width: 480px',
      'bottom: 16px',
      'height: 52px',
      'border-radius: 26px',
      'background-color: rgba(255, 255, 255, 0.86)',
      'backdrop-filter: blur(18px) saturate(160%)',
      'box-shadow: 0 8px 24px',
      'width: 26px',
      'font-size: 11px',
      'min-width: 15px',
    ]) {
      expect(wxss, `原生底栏缺少 ${rule}`).toContain(rule)
    }
    const wxml = source(NATIVE_WXML)
    expect(wxml).toContain('item.activeIcon')
    expect(wxml).toContain('glass-tabbar__badge')
  })

  it('切换走 wx.switchTab；隐藏由 Vue 侧 setData({ hidden: true }) 控制', () => {
    expect(source(NATIVE_JS)).toContain('wx.switchTab')
    expect(source(NATIVE_JS)).toContain('hidden: false')
    expect(source(NATIVE_WXML)).toContain('wx:if="{{!hidden}}"')
  })
})

describe('tab 页为悬浮底栏让位（H5 与小程序同口径）', () => {
  it('分类页 tab 宿主让出一条底栏占位，压栈页不让', () => {
    const code = source(CATEGORY)
    expect(code).toContain('withTabBar')
    expect(code).toContain('category-page--with-tabbar')
    expect(code).toMatch(/category-page--with-tabbar[\s\S]*?height: calc\(100vh - var\(--app-tabbar-height/)
    expect(source(CATEGORY_PAGE)).toContain('with-tab-bar')
  })

  it('购物车结算条与滚动占位按底栏占位上移', () => {
    const code = source(CART)
    expect(code).toMatch(/bottom: var\(--app-tabbar-height/)
    expect(code).toMatch(/cart-footer-holder[\s\S]*?var\(--app-tabbar-height/)
  })

  it('全局变量定义 68px（52 胶囊 + 16 底边距），tab 页按它让位', () => {
    const code = source(APP)
    expect(code).toContain('--app-tabbar-height: 68px')
  })

  it('首页与个人中心的滚动流补等高底部占位（两端一致）', () => {
    for (const path of ['src/pages/home/index.vue', 'src/pages/user/user-center/index.vue']) {
      const code = source(path)
      expect(code, `${path} 缺少底部占位`).toContain('tabbar-bottom-space')
    }
  })
})

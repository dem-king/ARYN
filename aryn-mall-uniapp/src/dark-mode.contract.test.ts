import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * 深色模式接线守门。
 *
 * 架构（三层，缺一不可，见 docs 与 mallThemeStore 注释）：
 *  · 原生外壳：manifest darkmode + theme.json 自动切换窗口/状态栏文字；
 *  · wot 组件层：useMallThemeSync 读系统主题 → ConfigProvider :theme="mode"
 *    → 根节点挂 .wot-theme-dark，wot 组件深色态生效；
 *  · 业务层：根节点同一时刻追加 UnoCSS 的 .dark 类（激活模板里 dark:* 工具类，
 *    编译产物选择器是 `.dark .dark_xxx`），全局语义变量 --app-* 在
 *    .wot-theme-dark 块下发深色值，业务页硬编码色全部替换为变量引用。
 *
 * 本测试守住三处最容易被「顺手改坏」的接缝。
 */
const projectRoot = fileURLToPath(new URL('../', import.meta.url))

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

const APP_KU = 'src/App.ku.vue'
const APP = 'src/App.vue'
const UNO = 'uno.config.ts'

describe('根节点 .dark 类接线（App.ku.vue）', () => {
  const code = source(APP_KU)

  it('深色时 custom-class 追加 dark，与 wot-theme-dark 同节点', () => {
    expect(code).toContain('rootCustomClass')
    expect(code).toMatch(/mode === 'dark' \? 'page-wrapper dark' : 'page-wrapper'/)
    // ConfigProvider 必须使用动态绑定，静态字符串会让 dark 永远挂不上
    expect(code).toContain(':custom-class="rootCustomClass"')
  })

  it('根节点仍然是 page-wrapper（App.vue 的深色页面壳依赖它）', () => {
    expect(code).toContain('page-wrapper')
  })
})

describe('全局语义变量层（App.vue）', () => {
  const code = source(APP)

  it('浅色变量定义在 page 根，覆盖卡片/分区/边线/三级文字', () => {
    for (const variable of [
      '--app-bg',
      '--app-card',
      '--app-card-2',
      '--app-card-pressed',
      '--app-border',
      '--app-text',
      '--app-text-2',
      '--app-text-3',
      '--app-mask',
      '--app-skeleton',
      '--app-skeleton-band',
    ]) {
      expect(code, `缺少浅色变量 ${variable}`).toContain(`${variable}:`)
    }
  })

  it('深色变量挂在 .wot-theme-dark，色板与 wot 组件内置值同灰阶', () => {
    expect(code).toContain('.wot-theme-dark {')
    expect(code).toMatch(/\.wot-theme-dark\s*\{[\s\S]*?--app-bg: #131313/)
    expect(code).toMatch(/\.wot-theme-dark\s*\{[\s\S]*?--app-card: #1b1b1b/)
  })

  it('page 根背景有深色媒体查询分支（盖过 !important 灰底，仅小程序）', () => {
    expect(code).toMatch(/#ifdef MP-WEIXIN[\s\S]*?@media \(prefers-color-scheme: dark\)[\s\S]*?background-color: #131313 !important[\s\S]*?#endif/)
  })

  it('深色页面壳引用语义变量而非写死色值', () => {
    expect(code).toMatch(/\.wot-theme-dark\.page-wrapper\s*\{[\s\S]*?background: var\(--app-bg/)
  })
})

describe('bg-white 编译期语义化（uno.config.ts）', () => {
  const code = source(UNO)

  it('bg-white 改写为卡片表面变量（90+ 处工具类的深色兜底）', () => {
    expect(code).toMatch(/\['bg-white',\s*\{\s*'background-color':\s*'var\(--app-card, #ffffff\)'/)
  })
})

describe('深色模式跟随系统的取数链路', () => {
  const sync = source('src/composables/useMallThemeSync.ts')
  const store = source('src/store/mallThemeStore.ts')

  it('从 getAppBaseInfo/onThemeChange 读系统深浅写入 store.mode', () => {
    expect(sync).toContain('uni.getAppBaseInfo()')
    expect(sync).toContain('uni.onThemeChange')
    expect(store).toMatch(/mode: 'light' as MallThemeMode/)
  })

  it('ConfigProvider 的 theme 绑定 store.mode（wot 组件层开关）', () => {
    expect(source(APP_KU)).toContain(':theme="mallThemeStore.mode"')
  })

  it('业务页磨砂滚动玻璃深色时切深色面（53 页顶栏，不把夜间玻璃刷白）', () => {
    const schema = source('src/components/diy/schema/navigation-scroll.ts')
    expect(schema).toMatch(/FROSTED_SCROLL_SURFACE_DARK[\s\S]*?color: '#1A1A1A'[\s\S]*?textColor: '#FFFFFF'/)
    const navbar = source('src/components/hr-navbar/index.vue')
    // 装修页显式下传的 surface 优先；业务页磨砂兜底按系统深浅二选一
    expect(navbar).toMatch(/props\.scrollSurface\s*\?\?[\s\S]*?mallThemeStore\.mode === 'dark'[\s\S]*?FROSTED_SCROLL_SURFACE_DARK[\s\S]*?FROSTED_SCROLL_SURFACE/)
  })

  it('--wot-color-theme-background 在深色下取值侧切深色（inline 变量无法被 class 覆盖）', () => {
    expect(store).toContain('MALL_THEME_DARK_BACKGROUND')
    expect(store).toMatch(/state\.mode === 'dark'\s*\?\s*MALL_THEME_DARK_BACKGROUND\s*:\s*state\.pageBackgroundColor/)
  })
})

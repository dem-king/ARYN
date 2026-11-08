import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'
import { beforeAll, describe, expect, it } from 'vitest'

/**
 * 折叠屏（微信小程序）适配契约守门。
 *
 * 背景见 docs/50-需求文档/2026-11-04-微信小程序折叠屏适配调研/折叠屏适配改造方案.md：
 * rpx 以「窗口宽 = 750rpx」为基准，折叠屏展开到 ~700px 时所有 rpx 尺寸等比放大 ~1.8 倍，
 * 所以宽屏版式必须是「MP-WEIXIN 条件编译 + @media (min-width: 600px) + 纯 px/token」。
 *
 * 这个测试守的是三类最容易在后续迭代里悄悄退化的接缝（不是像素级快照）：
 * 1. 构建开关：app.json 根级 resizable 一旦丢失，展开态就回到「手机宽居中、两侧留白」；
 * 2. 断点纪律：宽屏段漏进 H5/App（条件编译被删）、或宽屏段里混进 rpx（放大根因复现）；
 * 3. 地标页结构：商详双栏/分类 rail/列表 3 列/瀑布 3 列的关键声明与锚点 id 被挪走。
 *
 * 断言只认「结构/关键声明」，用宽松正则容忍空格与实现微调；真机三态（A3/A4）与构建产物
 * （dist 下 app.json/wxss）不在这里守，由构建验证与真机回归覆盖。
 */

const projectRoot = fileURLToPath(new URL('../', import.meta.url))

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

const APP = 'src/App.vue'
const DIY = 'src/components/diy/index.vue'
const CATEGORY_BROWSER = 'src/components/category-browser/index.vue'
const GOODS_LIST_PANEL = 'src/components/goods-list-panel/index.vue'
const WATERFALL = 'src/components/waterfall-goods/index.vue'
const GOODS_INFO = 'src/sub-pages/product/goods-detail/components/GoodsInfo.vue'
const DETAIL_PAGE = 'src/sub-pages/product/goods-detail/index.vue'
const GLASS_TABBAR = 'src/components/glass-tabbar/index.vue'
const GOODS_FOOTER = 'src/sub-pages/product/goods-detail/components/GoodsFooter.vue'

/** 方案第三节列出的 7 个宽屏改造目标：每个都必须自带 ≥1 个 600px 宽屏段。 */
const WIDE_TARGET_FILES = [
  APP,
  DIY,
  CATEGORY_BROWSER,
  GOODS_LIST_PANEL,
  WATERFALL,
  GOODS_INFO,
  DETAIL_PAGE,
]

/** 底部两条栏（P0-3）：方案明确「不改代码」，只守「已全 px 限宽」这个现状不被 rpx 化。 */
const BOTTOM_BARS = [GLASS_TABBAR, GOODS_FOOTER]

// ---------------------------------------------------------------------------
// 宽屏段扫描器：读 SFC 源码 → 找出所有 @media (min-width: 600px) 块及其条件编译深度
// ---------------------------------------------------------------------------

interface WideBlock {
  /** 从 @media 起、按花括号配平截到的完整片段（含 @media 行与两侧括号） */
  content: string
  /** 片段起点之前，其外层 MP-WEIXIN 条件编译的嵌套深度（>0 表示仅小程序生效） */
  ifdefDepth: number
}

/**
 * 取 SFC 全部 <style> 块内容。
 *
 * 只扫样式区：宽屏断点只允许出现在 <style> 里，而 <script>/<template> 里的
 * `// #ifdef MP`（其它平台段）会干扰条件编译深度计算。
 */
function styleContent(code: string): string {
  const styles: string[] = []
  const styleRe = /<style[^>]*>([\s\S]*?)<\/style>/gi
  let match: RegExpExecArray | null
  while ((match = styleRe.exec(code)) !== null) {
    styles.push(match[1])
  }
  return styles.join('\n')
}

/**
 * 计算 offset 处的 MP-WEIXIN 条件编译深度。
 *
 * 用栈而不是简单加减：同一文件里还有 `#ifdef H5` / `#ifdef MP` 等其它平台段，
 * 简单计数会被它们的 #endif 算错深度，进而让「宽屏段是否在 MP-WEIXIN 内」失真。
 */
function ifdefDepthAt(css: string, offset: number): number {
  const tokenRe = /#ifdef\s+([\w-]+)|#ifndef\s+([\w-]+)|#endif/g
  const stack: Array<{ platform: string; negated: boolean }> = []
  let token: RegExpExecArray | null
  while ((token = tokenRe.exec(css)) !== null) {
    if (token.index >= offset) {
      break
    }
    if (token[0] === '#endif') {
      stack.pop()
      continue
    }
    stack.push({
      platform: token[1] ?? token[2] ?? '',
      negated: token[2] !== undefined,
    })
  }
  // 只有「ifdef MP-WEIXIN」或「ifndef <非 MP-WEIXIN 平台>」才让小程序端命中该段
  return stack.filter(
    entry =>
      (entry.negated && entry.platform !== 'MP-WEIXIN')
      || (!entry.negated && entry.platform === 'MP-WEIXIN'),
  ).length
}

/**
 * 找出一个文件全部 @media (min-width: 600px) 块。
 *
 * 从 @media 行起按花括号配平截取（跳过注释与字符串里的花括号），这样实现里
 * 嵌套 SCSS 选择器、注释里出现大括号都不会把块的边界算错。容忍
 * `@media screen and (...)` 这类前缀写法。
 */
function findWideBlocks(code: string): WideBlock[] {
  const css = styleContent(code)
  const blocks: WideBlock[] = []
  const mediaRe = /@media\b[^{]*?\(\s*min-width\s*:\s*600px\s*\)/gi
  let match: RegExpExecArray | null
  while ((match = mediaRe.exec(css)) !== null) {
    const start = match.index
    let cursor = start
    let depth = 0
    let opened = false
    while (cursor < css.length) {
      const char = css[cursor]
      if (char === '/' && css[cursor + 1] === '*') {
        const end = css.indexOf('*/', cursor + 2)
        cursor = end === -1 ? css.length : end + 2
        continue
      }
      // SCSS 行注释：`http://` 之类由「前一个字符是冒号」放行
      if (char === '/' && css[cursor + 1] === '/' && css[cursor - 1] !== ':') {
        const end = css.indexOf('\n', cursor + 2)
        cursor = end === -1 ? css.length : end + 1
        continue
      }
      if (char === '"' || char === '\'') {
        const quote = char
        cursor += 1
        while (cursor < css.length) {
          if (css[cursor] === '\\') {
            cursor += 2
            continue
          }
          if (css[cursor] === quote) {
            cursor += 1
            break
          }
          cursor += 1
        }
        continue
      }
      if (char === '{') {
        depth += 1
        opened = true
      }
      else if (char === '}') {
        depth -= 1
        if (opened && depth <= 0) {
          cursor += 1
          break
        }
      }
      cursor += 1
    }
    blocks.push({ content: css.slice(start, cursor), ifdefDepth: ifdefDepthAt(css, start) })
    mediaRe.lastIndex = cursor
  }
  return blocks
}

/** 去掉注释：注释里写「这里不要用 rpx」「--wide-gutter: 24px 是这个意思」不算声明。 */
function stripComments(css: string): string {
  return css
    .replace(/\/\*[\s\S]*?\*\//g, ' ')
    .replace(/(^|[^:])\/\/[^\n]*/g, '$1')
}

/** 只取「在 MP-WEIXIN 条件编译内」的宽屏段，拼成一份 CSS 供正向断言使用。 */
function mpWideCss(code: string): string {
  return findWideBlocks(code)
    .filter(block => block.ifdefDepth > 0)
    .map(block => stripComments(block.content))
    .join('\n')
}

// ---------------------------------------------------------------------------
// A. 构建开关（P0-1 / 验收 A1 的源码侧）
// ---------------------------------------------------------------------------

interface ManifestIdentity {
  name: string
  wxAppId: string
  resizable?: boolean
}

interface ManifestMpWeixin {
  resizable?: boolean
  appid?: string
}

interface ManifestFactory {
  createOriginalManifest: () => { 'mp-weixin': ManifestMpWeixin }
  createManifest: (identity: ManifestIdentity) => { 'mp-weixin': ManifestMpWeixin }
  resolveMpResizable: (envValue?: string) => boolean
}

let factory: ManifestFactory

beforeAll(async () => {
  // 工厂是构建期 .mjs 纯模块（不读 env、不 import uni 插件），可直接动态加载；
  // 用 .mjs 而不是重写一份预期值，保证测试与 manifest.config.ts / 租户构建同源。
  const factoryUrl = pathToFileURL(resolve(projectRoot, 'build', 'manifest-factory.mjs')).href
  factory = (await import(factoryUrl)) as ManifestFactory
})

describe('构建开关：app.json 根级 resizable（A1 源码侧）', () => {
  it('createOriginalManifest 的 mp-weixin 默认 resizable: true（展开态占满窗口）', () => {
    // 丢了这个默认值，构建产物 app.json 就没有 resizable，微信按手机宽度居中并留白
    expect(factory.createOriginalManifest()['mp-weixin'].resizable).toBe(true)
  })

  it('createManifest 默认开启；identity.resizable=false 时关闭（构建期回滚）', () => {
    const on = factory.createManifest({ name: '契约商城', wxAppId: 'wx0a8242ea59f3e6b4' })
    expect(on['mp-weixin'].resizable).toBe(true)
    expect(on['mp-weixin'].appid).toBe('wx0a8242ea59f3e6b4')

    const off = factory.createManifest({
      name: '契约商城',
      wxAppId: 'wx0a8242ea59f3e6b4',
      resizable: false,
    })
    expect(off['mp-weixin'].resizable).toBe(false)

    // 工厂每次返回独立对象：回滚构建不能污染同一进程里先前的默认构建
    expect(on['mp-weixin'].resizable).toBe(true)
  })

  it('resolveMpResizable 只认字面量 "false"，其它值一律视为开启（防误配静默关城）', () => {
    // 回滚开关必须只在显式 MP_RESIZABLE=false 时生效：'0'/'no' 之类误配若被当成关闭，
    // 线上会静默退化成「手机宽居中」，排查成本极高
    expect(factory.resolveMpResizable('false')).toBe(false)
    expect(factory.resolveMpResizable(undefined)).toBe(true)
    expect(factory.resolveMpResizable('0')).toBe(true)
  })

  it('三条构建路径都接上 MP_RESIZABLE 回滚开关（manifest 源 / 默认 dev / 租户构建）', () => {
    // 漏接任意一条：对应构建方式就失去回滚能力，或预写 manifest 与 vite 插件产物不一致
    for (const file of ['manifest.config.ts', 'scripts/run-uni.mjs', 'scripts/build-tenant.mjs']) {
      const code = source(file)
      expect(code, `${file} 未读取 MP_RESIZABLE 回滚开关`).toContain('MP_RESIZABLE')
      expect(code, `${file} 未复用 resolveMpResizable（不要各写一套空串/false 判断）`).toContain(
        'resolveMpResizable',
      )
    }
  })
})

// ---------------------------------------------------------------------------
// B. 条件编译与断点纪律（A2 / A5 源码侧）
// ---------------------------------------------------------------------------

describe('条件编译与断点纪律（A2/A5 源码侧）', () => {
  it('7 个改造目标文件都有 @media (min-width: 600px) 宽屏段', () => {
    // 一次报全缺哪些文件，避免逐个失败逐轮排查
    const missing = WIDE_TARGET_FILES.filter(file => findWideBlocks(source(file)).length === 0)
    expect(missing, `以下文件缺少 @media (min-width: 600px) 宽屏段：${missing.join(', ')}`).toEqual(
      [],
    )
  })

  it('每个宽屏段都在 #ifdef MP-WEIXIN 条件编译内（H5/App 不得命中）', () => {
    // 条件编译被删/漏包的直接后果：H5 也吃宽屏 px 规则 → 手机浏览器与 App 视觉回归；
    // 这是方案「仅 mp-weixin」边界，删一行注释就能破
    const violations: string[] = []
    for (const file of WIDE_TARGET_FILES) {
      findWideBlocks(source(file)).forEach((block, index) => {
        if (block.ifdefDepth <= 0) {
          violations.push(`${file} 第 ${index + 1} 个宽屏段：${block.content.slice(0, 120)}`)
        }
      })
    }
    expect(violations, `以下宽屏段不在 #ifdef MP-WEIXIN 内：\n${violations.join('\n')}`).toEqual([])
  })

  it('宽屏段内不出现 rpx/upx（展开态等比放大 1.8 倍正是要修的根因）', () => {
    // 宽屏段里混入一个 rpx，在 700px 窗口就放大到 1.8 倍，平板密度版式立刻失衡
    const violations: string[] = []
    for (const file of WIDE_TARGET_FILES) {
      findWideBlocks(source(file)).forEach((block, index) => {
        if (/\b\d+(?:\.\d+)?\s*(?:rpx|upx)\b/i.test(stripComments(block.content))) {
          violations.push(`${file} 第 ${index + 1} 个宽屏段`)
        }
      })
    }
    expect(violations, `以下宽屏段出现 rpx/upx：${violations.join(', ')}`).toEqual([])
  })
})

// ---------------------------------------------------------------------------
// C. 全局宽屏 token（P0-2 / A2）
// ---------------------------------------------------------------------------

describe('全局宽屏 token（P0-2 / A2）', () => {
  it('App.vue 在 MP 宽屏段声明全部 --wide-* 变量（各页宽屏规则唯一取值来源）', () => {
    // 缺任意一个：各页面的 var(--wide-*, 兜底值) 会大面积走兜底，改 token 等于失效
    const css = mpWideCss(source(APP))
    const tokens: Record<string, string> = {
      '--wide-gutter': '24px',
      '--wide-pad': '16px',
      '--wide-nav': '44px',
      '--wide-radius': '12px',
      '--wide-card-font': '13px',
      '--wide-price-font': '15px',
      '--wide-body-font': '14px',
      '--wide-meta-font': '12px',
    }
    for (const [name, value] of Object.entries(tokens)) {
      expect(css, `App.vue 宽屏段缺少 ${name}: ${value}`).toMatch(
        new RegExp(`${name}\\s*:\\s*${value}`),
      )
    }
  })
})

// ---------------------------------------------------------------------------
// D. 商详首屏双栏（P1-1 / A6）
// ---------------------------------------------------------------------------

describe('商详首屏双栏（P1-1 / A6）', () => {
  const goodsInfo = source(GOODS_INFO)
  const detailPage = source(DETAIL_PAGE)
  const css = mpWideCss(goodsInfo)

  it('GoodsInfo 新增 gallery/body 两个包裹层，scroll-pic 仍挂在最外层', () => {
    // scroll-pic 被挪到子节点/改名 → 页面 tab 锚点 getScrollPicRect 与首屏定位全部失效
    const picIndex = goodsInfo.search(/<view[^>]*id="scroll-pic"/)
    expect(picIndex, 'GoodsInfo 根节点丢失 id="scroll-pic"').toBeGreaterThan(-1)
    const galleryIndex = goodsInfo.search(/class="[^"]*\bgoods-gallery\b[^"]*"/)
    const bodyIndex = goodsInfo.search(/class="[^"]*\bgoods-body\b[^"]*"/)
    expect(galleryIndex, 'GoodsInfo 缺少 goods-gallery 包裹层').toBeGreaterThan(-1)
    expect(bodyIndex, 'GoodsInfo 缺少 goods-body 包裹层').toBeGreaterThan(-1)
    // 两个包裹层必须是 scroll-pic 的后代（双栏是首屏内部布局，不是把根节点拆开）
    expect(galleryIndex).toBeGreaterThan(picIndex)
    expect(bodyIndex).toBeGreaterThan(picIndex)
  })

  it('宽屏 grid 两栏：左列 46% + 右栏 minmax(300px,1fr)，间距用 --wide-gutter', () => {
    // 验收 A6：左图列 42–48%、右栏 ≥300px、折痕安全区 ≥24px；
    // 用 rpx 拼两栏正是方案明确禁止的写法
    expect(css, '宽屏段缺少 grid-template-columns').toMatch(/grid-template-columns\s*:/)
    expect(css, '两栏比例不是 46% + minmax(300px,1fr)').toMatch(
      /grid-template-columns\s*:[^;]*(?:46%[^;]*minmax\(\s*300px|minmax\(\s*300px[^;]*46%)/,
    )
    expect(css, '两栏间距未引用 --wide-gutter').toMatch(/var\(\s*--wide-gutter\b/)
  })

  it('左图列 sticky 吸顶 + var(--wide-*) 间距（无 rpx 已在 B 项守门）', () => {
    expect(css, '图集列缺少 position: sticky（滚首屏时左图不吸顶）').toMatch(
      /position\s*:\s*sticky/,
    )
  })

  it('商详页面壳锚点未变（scroll-description / scroll-like / GoodsInfo 挂载）', () => {
    // 首屏以下保持单栏是方案前提：整页重排会动到导航栏 tab 锚点与滚动定位
    expect(detailPage, '丢失评价锚点 id="scroll-description"').toContain('id="scroll-description"')
    expect(detailPage, '丢失推荐锚点 id="scroll-like"').toContain('id="scroll-like"')
    expect(detailPage, '首屏未挂载 <GoodsInfo>').toContain('<GoodsInfo')
  })
})

// ---------------------------------------------------------------------------
// E. 分类 / 列表 / 瀑布（P1-2 + P1-3 / A7 + A8）
// ---------------------------------------------------------------------------

describe('分类页 rail + 列表 / 瀑布（A7/A8）', () => {
  it('分类页宽屏 rail 固定 96px，商品流骨架保持单列', () => {
    // rail 若继续用 rpx，展开态会放大到 ~170px，右侧商品区被挤得放不下图左信息右的行卡
    const css = mpWideCss(source(CATEGORY_BROWSER))
    expect(css, '分类 rail 未固定 width: 96px').toMatch(/width\s*:\s*96px/)
    // 分类页固定单列：宽屏骨架不得被重排成多列（同权规则后写者胜会悄悄盖掉单列）
    expect(css, '分类页宽屏骨架未保持单列').toMatch(/grid-template-columns\s*:\s*minmax\(0,\s*1fr\)/)
  })

  it('列表页宽屏 3 列 + 卡片标题/价格用平板密度 token', () => {
    // A8：3 列且标题 13px、价格 15px；字号若仍走 rpx，展开态标题会 26px+ 直接挤爆卡片
    const code = source(GOODS_LIST_PANEL)
    const css = mpWideCss(code)
    // 3 列改由 useResponsiveColumns 控制（不再用 CSS grid 轨道写死）
    expect(code, '列表未接入宽屏 3 列').toContain('useResponsiveColumns(600, 2, 3)')
    expect(css, '卡片标题未引用 --wide-card-font').toMatch(/var\(\s*--wide-card-font\b/)
    expect(css, '价格未引用 --wide-price-font').toMatch(/var\(\s*--wide-price-font\b/)
  })

  it('瀑布流宽屏 3 列 + 模板语义 class', () => {
    // 与列表页同参数：3 列、间距 24px；列数由 useResponsiveColumns 控制
    const waterfall = source(WATERFALL)
    const css = mpWideCss(waterfall)
    expect(waterfall, '瀑布流未接入宽屏 3 列').toContain('useResponsiveColumns(600, 2, 3)')
    expect(css, '瀑布流宽屏段未引用 --wide-gutter').toMatch(/var\(\s*--wide-gutter\b/)
    expect(waterfall, '模板缺少 waterfall-grid 语义 class').toMatch(
      /class="[^"]*\bwaterfall-grid\b[^"]*"/,
    )
    expect(waterfall, '模板缺少 waterfall-card 语义 class').toMatch(
      /class="[^"]*\bwaterfall-card\b[^"]*"/,
    )
  })
})

// ---------------------------------------------------------------------------
// F. 底部两条栏不随 rpx 放大（P0-3 / A3：方案明确不改代码，只守门）
// ---------------------------------------------------------------------------

describe('底部两条栏不随 rpx 放大（P0-3 / A3 回归守门）', () => {
  it('glass-tabbar 与 GoodsFooter 保持纯 px 限宽居中（禁止新增 rpx）', () => {
    // 方案 P0-3 判定「TabBar 限宽已完成、不再改」：这里守住现状——一旦有人给这两条
    // 栏加 rpx（或改成百分比全宽），700px 展开态胶囊就会被拉长、脱离 480px 限宽
    for (const file of BOTTOM_BARS) {
      const code = source(file)
      expect(code, `${file} 出现 rpx/upx（底部栏必须全 px）`).not.toMatch(
        /\b\d+(?:\.\d+)?\s*(?:rpx|upx)\b/i,
      )
      expect(code, `${file} 丢失 max-width: 480px 限宽`).toMatch(/max-width\s*:\s*480px/)
      expect(code, `${file} 丢失 width: calc(100% - 40px)`).toMatch(
        /width\s*:\s*calc\(\s*100%\s*-\s*40px\s*\)/,
      )
      expect(code, `${file} 丢失水平居中 left: 50%`).toMatch(/left\s*:\s*50%/)
      expect(code, `${file} 丢失 transform: translateX(-50%)`).toMatch(
        /transform\s*:\s*translateX\(\s*-50%\s*\)/,
      )
      expect(code, `${file} 丢失 height: 52px（与 --app-tabbar-height 同口径）`).toMatch(
        /height\s*:\s*52px/,
      )
    }
  })
})

// ---------------------------------------------------------------------------
// G. 装修背景兜底（P0-4 / A4）
// ---------------------------------------------------------------------------

describe('装修背景宽屏兜底（P0-4 / A4）', () => {
  it('diy 宽屏段把 100% auto 改成 cover，并 !important 覆盖区块行内背景尺寸', () => {
    // 页面级 .diy-page 是 class 规则可直接覆盖；区块背景的 100% auto 是 sectionStyle()
    // 写进元素 style 的行内值，普通规则赢不了行内样式，漏了 !important 就等于没覆盖
    const css = mpWideCss(source(DIY))
    expect(css, 'diy 宽屏段未把背景改成 background-size: cover').toMatch(
      /background-size\s*:\s*cover/,
    )
    expect(css, 'diy 宽屏段缺少 .diy-section 行内背景覆盖').toMatch(/\.diy-section\b/)
    expect(css, 'diy 区块背景覆盖缺少 !important（压不过行内 100% auto）').toMatch(
      /background-size\s*:\s*cover\s*!important/,
    )
  })
})

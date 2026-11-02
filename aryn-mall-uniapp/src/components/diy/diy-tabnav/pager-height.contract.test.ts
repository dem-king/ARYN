import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

import { describe, expect, it } from 'vitest'

/**
 * 金刚区（tab-nav 分页模式）高度跟随的性能契约 —— 真机整页掉帧回归守卫。
 *
 * 真实缺陷：金刚区每页行数不同（例：22 个入口 = 3 行 + 2 行），翻页时容器要
 * 收缩/展开到当前页行数，且必须**跟着手指同步变化**。曾经绑定 `@transition`
 * 在逻辑层逐帧插值高度：该事件每秒 60~120 次，每次 setData 改高度都会让金刚区
 * 下方**所有楼层**重新排版，30Hz 限频也仍会打满主线程，真机表现为滑动掉帧、
 * 收缩明显滞后于手指（「不够丝滑」）。
 *
 * 现行口径：
 *   · 小程序端：`@transition` 绑到 WXS 视图层事件，由 WXS `selectComponent` +
 *     `setStyle` 逐帧写高度 —— 不经过 setData，收缩/展开与翻页位移严格同步；
 *     高度公式只在 JS 端算一次，经 `data-heights` 下发给 WXS 做线性插值。
 *   · 非小程序端（H5/App）：`tabnavWxs` 在模板里是一个 JS 垫片，回落到
 *     `onLivePagerTransition`（30Hz 提交 + 40ms 线性 CSS 过渡补间）。
 *   · 两端都只在 `@animationfinish`（滑停后）结算页码，不拿 `@change` 改高度。
 *
 * 该契约同时守两个真机踩过的坑（WXS 基础库限制）：
 *   1. WXS 没有 `String` 构造器（也没有 `JSON`），只能用 `(x || '')` 兜底；
 *   2. WXS 不支持单行 `{ return }` 写法，会被 WXML 编译器判为 Unexpected token。
 */

const projectRoot = fileURLToPath(new URL('../../../../', import.meta.url))
const COMPONENT = 'src/components/diy/diy-tabnav/index.vue'

function source() {
  return readFileSync(resolve(projectRoot, COMPONENT), 'utf8')
}

/** 取某个具名函数体（到下一个顶层 `}` 为止），用于断言事件处理器里没有布局写入 */
function functionBody(code: string, name: string) {
  const start = code.indexOf(`function ${name}`)
  expect(start, `${COMPONENT} 缺少 ${name}`).toBeGreaterThan(-1)
  const end = code.indexOf('\n}', start)
  expect(end, `${COMPONENT} 的 ${name} 未闭合`).toBeGreaterThan(start)
  return code.slice(start, end)
}

/** 取出 WXS 视图层脚本块内容（`<script module="tabnavWxs" lang="wxs">`），并剥掉注释 */
function wxsBlock(code: string) {
  const start = code.indexOf('<script module="tabnavWxs" lang="wxs">')
  expect(start, `${COMPONENT} 缺少 WXS 视图层脚本块`).toBeGreaterThan(-1)
  const end = code.indexOf('</script>', start)
  return code
    .slice(start, end)
    .split('\n')
    .filter(line => !line.trim().startsWith('//'))
    .join('\n')
}

describe('金刚区分页高度跟随：手势期间与翻页位移同步', () => {
  it('小程序端由 WXS 在视图层逐帧写高度（setStyle，不经过 setData）', () => {
    const code = source()
    const wxs = wxsBlock(code)
    // 事件绑定：模板里统一写 tabnavWxs，小程序端编译成 bindtransition="{{tabnavWxs.onTransition}}"
    expect(code).toContain('@transition="tabnavWxs.onTransition"')
    expect(wxs).toContain('ownerInstance.selectComponent')
    expect(wxs).toContain('.setStyle(')
    // 禁止把逐帧事件打回逻辑层：callMethod 会重新引入 setData 往返与延迟
    expect(wxs).not.toContain('callMethod')
    // WXS 基础库里没有 String/JSON 构造器（真机踩过：加了就整块失效）
    expect(wxs).not.toMatch(/\bString\(/)
    expect(wxs).not.toMatch(/\bJSON\./)
    // WXS 不支持单行 `{ return }`（WXML 编译器报 Unexpected token '}'）
    expect(wxs).not.toMatch(/\{[ \t]*return[ \t]*\}/)
  })

  it('每页高度只由 JS 公式算一次，经 data-* 下发给 WXS 做线性插值', () => {
    const code = source()
    expect(code).toContain('const pagerHeights = computed(() =>')
    expect(code).toContain(':data-heights="pagerHeightsAttr"')
    expect(code).toContain(':data-current="current"')
    expect(code).toContain(':data-width="pagerWidth"')
    // 高度公式仍按「当前页实际行数 × (行高 + 8px) + 指示点预留」派生
    expect(code).toContain('const actualRows = computed(() => rowsOfPage(current.value))')
    expect(code).toContain('Math.min(pageRows.value, Math.ceil(page.length / showNum.value))')
    expect(code).toContain('rowHeight.value + 8')
  })

  it('滑停后只结算页码，不在事件里直接写高度', () => {
    const code = source()
    expect(code).toContain('@animationfinish="onPagerAnimationFinish"')
    const body = functionBody(code, 'onPagerAnimationFinish')
    expect(body).toContain('current.value = next')
    expect(body).not.toContain('pagerHeight')
  })

  it('非小程序端保留 JS 实时插值 + CSS 过渡（H5/App 无跨线程限制）', () => {
    const code = source()
    expect(code).toContain('const tabnavWxs = { onTransition: onLivePagerTransition }')
    expect(code).toContain('liveHeight')
    // 小程序端 wxss 不能带高度过渡（会变成追赶拖影），过渡必须包在条件编译里
    expect(code).toContain('/* #ifndef MP-WEIXIN */')
    expect(code).toContain('transition: height 240ms cubic-bezier(0.34, 1.35, 0.64, 1)')
  })
})

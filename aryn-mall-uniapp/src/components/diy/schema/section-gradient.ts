import type { SectionBackgroundGradient } from './types'

/*
 * 区块背景渐变解析（C 端实机渲染）。
 *
 * 与管理端 `page-designer/schema/section-style.ts` 同源：两端是两套独立实现，
 * 这里用 SECTION-GRADIENT 标记圈出纯函数体，`preview-parity.test.ts` 会去掉
 * 空白与分号后逐字比对，任何一端改了色值口径或方向白名单都会被测试挡住。
 * 修改这里必须同步改管理端。
 */

/* SECTION-GRADIENT-BEGIN */
const SECTION_GRADIENT_DIRECTIONS = new Set([
  '135deg',
  '225deg',
  'to bottom',
  'to right',
])

/** 只接受合法色值，避免任意字符串进入内联样式；方向同样走白名单 */
function normalizeSectionGradientColor(value: unknown): string {
  if (typeof value !== 'string') return ''
  const color = value.trim()
  if (!color) return ''
  const legal
    = /^(?:#(?:[0-9a-f]{3,4}|[0-9a-f]{6}|[0-9a-f]{8})|rgba?\(\s*[\d.%]+\s*(?:,\s*[\d.%]+\s*){2,3}\)|transparent)$/i
  return legal.test(color) ? color : ''
}

/** 方向白名单归一：非法值统一回落 to right，供渐变预览与导航栏跟随共用 */
function normalizeSectionGradientDirection(value: unknown): string {
  return typeof value === 'string'
    && SECTION_GRADIENT_DIRECTIONS.has(value.trim())
    ? value.trim()
    : 'to right'
}

/** 区块背景渐变 → CSS linear-gradient；起止色都为空时返回 undefined（回退纯色） */
export function buildSectionGradient(
  gradient?: null | SectionBackgroundGradient,
): string | undefined {
  if (!gradient) return undefined
  const start = normalizeSectionGradientColor(gradient.startColor)
  const end = normalizeSectionGradientColor(gradient.endColor)
  const from = start || end
  if (!from) return undefined
  const to = end || start
  const direction = normalizeSectionGradientDirection(gradient.direction)
  return `linear-gradient(${direction}, ${from}, ${to})`
}

/**
 * 区块背景的「顶边起色」：渐变取起始色（只填一端时取那一端），供导航栏
 * 「跟随首个区块」取色；没有渐变时返回空串，由纯色/主题色兜底。
 */
export function getSectionGradientAnchorColor(
  gradient?: null | SectionBackgroundGradient,
): string {
  if (!gradient) return ''
  const start = normalizeSectionGradientColor(gradient.startColor)
  const end = normalizeSectionGradientColor(gradient.endColor)
  return start || end
}

/** 左右方向（to right）的渐变可以整段复用到导航栏，接缝两侧逐列同色 */
export function isHorizontalSectionGradient(
  gradient?: null | SectionBackgroundGradient,
): boolean {
  return (
    !!gradient
    && normalizeSectionGradientDirection(gradient.direction) === 'to right'
  )
}
/* SECTION-GRADIENT-END */

import type { EffectivePageTheme } from './effective-theme'
import type { NavigationAppearance } from './navigation-style'
import type { NavigationScrollEffect, PageSettings } from './types'

import { pickReadableNavigationTextColor } from './navigation-style'

/* NAVIGATION-SCROLL-BEGIN */
/** 滚动渐显默认距离（px）：约两个导航栏高度 */
export const DEFAULT_NAVIGATION_SCROLL_DISTANCE = 140
export const MIN_NAVIGATION_SCROLL_DISTANCE = 40
export const MAX_NAVIGATION_SCROLL_DISTANCE = 400
/** 磨砂白目标态不透明度：有毛玻璃 / 无毛玻璃兜底 */
export const FROSTED_SURFACE_ALPHA = 0.86
export const FROSTED_FALLBACK_ALPHA = 0.97
/** 浅色目标底色开始出现底部细线的进度 */
export const NAVIGATION_HAIRLINE_PROGRESS = 0.55

/**
 * 导航目标底色外观（静态部分）。
 *
 * 只随页面配置 / 主题变化，不随滚动变化：C 端、编辑画布、草稿预览先解析出
 * 这一层，再在滚动时用 resolveNavigationScrollAppearance() 按进度合成。
 */
export interface NavigationSurfaceAppearance {
  /** 目标底色（不含 alpha，用于亮度判断与合成） */
  color: string
  /** 目标态不透明度：磨砂白 0.86 / 无毛玻璃兜底 0.97 / 其他 1 */
  alpha: number
  /** 是否启用毛玻璃（仅磨砂白目标态） */
  blur: boolean
  /** 目标态文字色（主题色目标态取主题文字色，其余按对比度取黑/白） */
  textColor: string
}

/** 滚动进度合成后的导航外观（动态部分） */
export interface NavigationScrollAppearance {
  /** 0–1 渐显进度 */
  progress: number
  /** 起始外观层不透明度（1-progress） */
  baseOpacity: number
  /** 目标外观层不透明度（progress） */
  surfaceOpacity: number
  /** 目标底色 */
  surfaceColor: string
  /** 目标底色不透明度 */
  surfaceAlpha: number
  /** 是否启用毛玻璃 */
  blur: boolean
  /** 当前进度下的标题 / 图标色 */
  textColor: string
  /** 状态栏前景色（微信仅支持这两个值） */
  statusBarFrontColor: '#000000' | '#ffffff'
  /** 浅色目标底色的底部细线不透明度 */
  hairlineOpacity: number
}

function isScrollRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function clampScrollNumber(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max)
}

/** 只解析 hex / rgb(a) 的 sRGB 通道；非法值返回 null，由调用方回落 */
function parseScrollColor(value: string): [number, number, number] | null {
  const color = value.trim()
  const hex = /^#([0-9a-f]{3}|[0-9a-f]{6})$/i.exec(color)
  if (hex) {
    const raw = hex[1] ?? ''
    const base =
      raw.length === 3 ? [...raw].map((char) => char + char).join('') : raw
    const red = Number.parseInt(base.slice(0, 2), 16)
    const green = Number.parseInt(base.slice(2, 4), 16)
    const blue = Number.parseInt(base.slice(4, 6), 16)
    return Number.isFinite(red) &&
      Number.isFinite(green) &&
      Number.isFinite(blue)
      ? [red, green, blue]
      : null
  }
  const rgb = /^rgba?\(\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)/i.exec(color)
  if (!rgb) return null
  const channels = [Number(rgb[1]), Number(rgb[2]), Number(rgb[3])]
  if (
    channels.some(
      (channel) => !Number.isFinite(channel) || channel < 0 || channel > 255,
    )
  )
    return null
  return channels as [number, number, number]
}

/** 两色 sRGB 线性混合：progress=0 取 from，progress=1 取 to */
function mixScrollColor(from: string, to: string, progress: number): string {
  const start = parseScrollColor(from) ?? [255, 255, 255]
  const end = parseScrollColor(to) ?? [255, 255, 255]
  const channels = start.map((channel, index) =>
    Math.round(channel + ((end[index] ?? channel) - channel) * progress),
  )
  return `rgb(${channels[0]}, ${channels[1]}, ${channels[2]})`
}

function scrollLuminance(color: string): number {
  const rgb = parseScrollColor(color)
  if (!rgb) return 1
  return (rgb[0] * 0.299 + rgb[1] * 0.587 + rgb[2] * 0.114) / 255
}

function smoothstep(progress: number): number {
  const value = clampScrollNumber(progress, 0, 1)
  return value * value * (3 - 2 * value)
}

/** 可读文字色 → 状态栏合法值（#222222 视为黑，其余非白也按黑） */
function toStatusBarFrontColor(color: string): '#000000' | '#ffffff' {
  return color.toLowerCase() === '#ffffff' ? '#ffffff' : '#000000'
}

/**
 * 滚动渐显配置归一化：缺省 / 脏值 → 合法对象。
 *
 * 迁移器与设置面板共用；缺省 mode='none' 表示保持现状（存量页面零变化）。
 * 不抛错：小程序端不能因为一条脏数据白屏。
 */
export function normalizeNavigationScrollEffect(
  value: unknown,
): NavigationScrollEffect {
  const record = isScrollRecord(value) ? value : {}
  const rawDistance =
    typeof record.distance === 'number' && Number.isFinite(record.distance)
      ? record.distance
      : DEFAULT_NAVIGATION_SCROLL_DISTANCE
  const surfaceColor =
    typeof record.surfaceColor === 'string' &&
    parseScrollColor(record.surfaceColor)
      ? record.surfaceColor
      : ''
  return {
    mode: record.mode === 'fade' ? 'fade' : 'none',
    surface:
      record.surface === 'theme' || record.surface === 'custom'
        ? record.surface
        : 'frosted',
    surfaceColor,
    distance: Math.round(
      clampScrollNumber(
        rawDistance,
        MIN_NAVIGATION_SCROLL_DISTANCE,
        MAX_NAVIGATION_SCROLL_DISTANCE,
      ),
    ),
    blur: record.blur !== false,
  }
}

/**
 * 静态部分：目标底色外观（只随页面配置 / 主题变化）。
 *
 * 规则：
 *   · mode !== 'fade' —— 原样返回起始外观（与旧渲染完全一致）；
 *   · surface='theme' —— 取商城主题导航色 / 主题文字色；
 *   · surface='custom' —— 取自定义色，文字按对比度自动取黑/白；
 *   · surface='frosted'（缺省）—— 磨砂白，文字按白色对比度取深色。
 */
export function resolveNavigationScrollSurface(
  page: PageSettings,
  theme: EffectivePageTheme,
  base: NavigationAppearance,
): NavigationSurfaceAppearance {
  const effect = normalizeNavigationScrollEffect(page.navigation.scrollEffect)
  if (effect.mode !== 'fade') {
    return {
      color: base.backgroundColor,
      alpha: 1,
      blur: false,
      textColor: base.textColor,
    }
  }
  if (effect.surface === 'theme') {
    return {
      color: theme.navigationBackgroundColor,
      alpha: 1,
      blur: false,
      textColor: theme.navigationTextColor,
    }
  }
  // 自定义色非法/缺失时回落磨砂白（normalize 已把非法色清成空串）
  if (effect.surface === 'custom' && effect.surfaceColor) {
    return {
      color: effect.surfaceColor,
      alpha: 1,
      blur: false,
      textColor: pickReadableNavigationTextColor(
        effect.surfaceColor,
        base.textColor,
      ),
    }
  }
  return {
    color: '#FFFFFF',
    alpha: effect.blur ? FROSTED_SURFACE_ALPHA : FROSTED_FALLBACK_ALPHA,
    blur: effect.blur,
    textColor: pickReadableNavigationTextColor('#FFFFFF', base.textColor),
  }
}

/**
 * 动态部分：滚动进度 → 导航外观（纯函数，三端共用）。
 *
 * 进度 = smoothstep(clamp(scrollTop / distance, 0, 1))；文字与状态栏颜色按
 * 「起始色 → 目标色」的合成背景亮度取黑/白（阈值 0.6，与现有导航解析同口径）。
 */
export function resolveNavigationScrollAppearance(
  base: NavigationAppearance,
  surface: NavigationSurfaceAppearance,
  effect: NavigationScrollEffect | undefined,
  scrollTop: number,
): NavigationScrollAppearance {
  const normalized = normalizeNavigationScrollEffect(effect)
  if (normalized.mode !== 'fade') {
    return {
      progress: 0,
      baseOpacity: 1,
      surfaceOpacity: 0,
      surfaceColor: surface.color,
      surfaceAlpha: surface.alpha,
      blur: false,
      textColor: base.textColor,
      statusBarFrontColor: toStatusBarFrontColor(
        pickReadableNavigationTextColor(base.backgroundColor, base.textColor),
      ),
      hairlineOpacity: 0,
    }
  }
  const progress = smoothstep(Math.max(0, scrollTop) / normalized.distance)
  const composite = mixScrollColor(
    base.backgroundColor,
    surface.color,
    progress,
  )
  const textColor = pickReadableNavigationTextColor(composite, base.textColor)
  const lightSurface = scrollLuminance(surface.color) > 0.75
  return {
    progress,
    baseOpacity: 1 - progress,
    surfaceOpacity: progress,
    surfaceColor: surface.color,
    surfaceAlpha: surface.alpha,
    blur: surface.blur,
    textColor,
    statusBarFrontColor: toStatusBarFrontColor(textColor),
    hairlineOpacity: lightSurface
      ? clampScrollNumber(
          (progress - NAVIGATION_HAIRLINE_PROGRESS) /
            (1 - NAVIGATION_HAIRLINE_PROGRESS),
          0,
          1,
        )
      : 0,
  }
}
/* NAVIGATION-SCROLL-END */

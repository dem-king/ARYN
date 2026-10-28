/**
 * 类目图标兜底（纯函数，便于单测）。
 *
 * 背景：`goods_category.category_pic` 在存量数据里大面积为空
 * （商超 12 个一级 + 船舶物料 1 个一级都没有图），而分类页新版式的骨架
 * 就是圆形图标。这里为「没有配图」的类目生成一个可辨识的首字色块，
 * 保证首屏不会是一片空白圆；运营在管理后台补图后自动切换为真实图片。
 */

import { translate } from '@/locales'

/**
 * 兜底色板：浅底 + 同色系深字。
 *
 * 原先的白字压在中等明度色块上，缺图时像一块突兀的彩色头像；
 * 改成低饱和浅底、深色首字后，和实拍图标混排更接近主流的「类目字母块」。
 */
const CATEGORY_FALLBACK_PALETTES = [
  { bgColor: '#FFF1E8', color: '#C2410C' }, // 暖橙
  { bgColor: '#EAF6EC', color: '#15803D' }, // 苔绿
  { bgColor: '#EAF2FF', color: '#1D4ED8' }, // 灰蓝
  { bgColor: '#F5EEFF', color: '#7E22CE' }, // 雾紫
  { bgColor: '#FFF7E6', color: '#B45309' }, // 麦黄
  { bgColor: '#E8F6F6', color: '#0F766E' }, // 青灰
  { bgColor: '#FDEEF2', color: '#BE185D' }, // 藕粉
  { bgColor: '#F1F5E9', color: '#4D7C0F' }, // 橄榄
] as const

/** 只导出背景色，保持既有引用与单测的稳定校验口径 */
export const CATEGORY_FALLBACK_COLORS = CATEGORY_FALLBACK_PALETTES.map(palette => palette.bgColor)

export interface CategoryIconFallback {
  /** 色块上显示的文字（类目名首字） */
  text: string
  /** 色块背景色 */
  bgColor: string
  /** 文字色：与浅底同色系，保证对比度 */
  color: string
}

/**
 * 计算兜底图标的展示内容。
 *
 * 用类目 id 而不是名称做哈希：类目重命名后色块颜色不应该跳变，
 * 而 id 稳定。名称只用于取首字。
 */
export function resolveCategoryFallback(
  name: string | null | undefined,
  id: string | null | undefined,
): CategoryIconFallback {
  const trimmed = (name ?? '').trim()
  // 取首字：emoji 等代理对用 Array.from 才不会截断成半个字符
  const chars = Array.from(trimmed)
  const text = chars[0] ?? translate('shared.categoryIcon.fallbackText')
  const palette = CATEGORY_FALLBACK_PALETTES[hashIndex(id ?? trimmed)]
  return {
    text,
    bgColor: palette.bgColor,
    color: palette.color,
  }
}

/** 稳定的字符串哈希 → 色板下标 */
function hashIndex(value: string): number {
  let hash = 0
  for (let i = 0; i < value.length; i += 1) {
    hash = (hash * 31 + value.charCodeAt(i)) | 0
  }
  return Math.abs(hash) % CATEGORY_FALLBACK_PALETTES.length
}

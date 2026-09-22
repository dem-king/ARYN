/**
 * 类目图标兜底（纯函数，便于单测）。
 *
 * 背景：`goods_category.category_pic` 在存量数据里大面积为空
 * （商超 12 个一级 + 船舶物料 1 个一级都没有图），而分类页新版式的骨架
 * 就是圆形图标。这里为「没有配图」的类目生成一个可辨识的首字色块，
 * 保证首屏不会是一片空白圆；运营在管理后台补图后自动切换为真实图片。
 */

/** 兜底色板：低饱和、明度接近，避免同屏出现刺眼的高饱和色块 */
export const CATEGORY_FALLBACK_COLORS = [
  '#7BA88F', // 苔绿
  '#8FA6C4', // 灰蓝
  '#C49A86', // 陶土
  '#9C93C4', // 雾紫
  '#C4A96E', // 麦黄
  '#86B4B8', // 青灰
  '#C08C9A', // 藕粉
  '#93A97D', // 橄榄
] as const

export interface CategoryIconFallback {
  /** 色块上显示的文字（类目名首字） */
  text: string
  /** 色块背景色 */
  bgColor: string
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
  const text = chars[0] ?? '类'
  return {
    text,
    bgColor: CATEGORY_FALLBACK_COLORS[hashIndex(id ?? trimmed)],
  }
}

/** 稳定的字符串哈希 → 色板下标 */
function hashIndex(value: string): number {
  let hash = 0
  for (let i = 0; i < value.length; i += 1) {
    hash = (hash * 31 + value.charCodeAt(i)) | 0
  }
  return Math.abs(hash) % CATEGORY_FALLBACK_COLORS.length
}

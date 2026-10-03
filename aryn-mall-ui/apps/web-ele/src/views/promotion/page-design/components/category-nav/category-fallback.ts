/**
 * 分类首字色块兜底（编辑器预览用）。
 *
 * 本文件是小程序端 `aryn-mall-uniapp/src/utils/category-icon.ts` 的镜像实现：
 * 色板、哈希算法必须与 C 端逐字一致，否则同一分类在编辑器预览与小程序实机
 * 上会渲出不同的颜色（两端的 resolveCategoryFallback 按 id 哈希取色）。
 * 修改任一端时必须同步另一端。
 */

export const CATEGORY_FALLBACK_COLORS = [
  '#7BA88F', // 苔绿
  '#8FA6C4', // 灰蓝
  '#C49A86', // 陶土
  '#9C93C4', // 雾紫
  '#C4A96E', // 麦黄
  '#86B4B8', // 青灰
  '#C08C9A', // 藕粉
  '#93A97D', // 橄榄
] as const;

export interface CategoryIconFallback {
  /** 色块上显示的文字（类目名首字） */
  text: string;
  /** 色块背景色 */
  bgColor: string;
}

export function resolveCategoryFallback(
  name: null | string | undefined,
  id: null | string | undefined,
): CategoryIconFallback {
  const trimmed = (name ?? '').trim();
  const chars = [...trimmed];
  const text = chars[0] ?? '类';
  return {
    text,
    bgColor:
      CATEGORY_FALLBACK_COLORS[hashIndex(id ?? trimmed)] ??
      CATEGORY_FALLBACK_COLORS[0],
  };
}

/** 稳定的字符串哈希 → 色板下标 */
function hashIndex(value: string): number {
  let hash = 0;
  for (let i = 0; i < value.length; i += 1) {
    // 逐位镜像 C 端算法：换成 Math.trunc/codePointAt 会得到不同哈希值，
    // 同一分类在预览与实机将渲出不同颜色，故保留 `| 0` 与 charCodeAt
    // eslint-disable-next-line unicorn/prefer-math-trunc, unicorn/prefer-code-point
    hash = (hash * 31 + value.charCodeAt(i)) | 0;
  }
  return Math.abs(hash) % CATEGORY_FALLBACK_COLORS.length;
}

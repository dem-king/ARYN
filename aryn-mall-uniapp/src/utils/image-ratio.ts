/**
 * 商品卡图片占位比例（padding-top 百分比 = 高 / 宽）。
 *
 * 后端列表接口会回填主图像素宽高：有值时按原图比例锁住图片区高度，
 * 图片加载前后卡片高度不变（瀑布流不跳动）；缺失或非法时回落 1:1，
 * 由 aspectFit 完整展示图片。极端长图限制在 60%~180%，避免单卡过高。
 */
export function imageAspectPadding(width?: unknown, height?: unknown): string {
  const w = Number(width)
  const h = Number(height)
  if (!Number.isFinite(w) || !Number.isFinite(h) || w <= 0 || h <= 0) {
    return '100%'
  }
  const percent = Math.min(180, Math.max(60, (h / w) * 100))
  return `${Number(percent.toFixed(2))}%`
}

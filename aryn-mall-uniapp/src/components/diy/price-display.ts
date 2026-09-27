/**
 * 商品卡片的「划线原价」显示判定。
 *
 * 各零售楼层都只渲染现价，原价（`goods_spu.original_price`）是可选信息：
 * 存量商品大量未填原价（值为 0），直接渲染会划出一个「￥0」。
 * 因此口径与商详页（GoodsInfo.vue）保持一致：**原价严格大于现价时才显示**。
 *
 * 相等也不显示 —— 无折扣的划线价没有信息量，只会让卡片更挤。
 */
export function shouldShowOriginalPrice(
  price: unknown,
  originalPrice: unknown,
): boolean {
  const current = Number(price)
  const original = Number(originalPrice)
  if (!Number.isFinite(current) || !Number.isFinite(original))
    return false
  return original > current
}

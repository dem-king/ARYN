/**
 * 商品卡片的「划线原价」显示判定。
 *
 * 与小程序端 `aryn-mall-uniapp/src/components/diy/price-display.ts` 保持同一口径：
 * 各零售楼层只渲染现价，原价（`goods_spu.original_price`）是可选信息，
 * 存量商品大量未填原价（值为 0），直接渲染会划出一个「￥0」。
 * 因此**原价严格大于售价时才显示**，相等也不显示（无折扣的划线没有信息量）。
 *
 * 两端各有一份实现（管理端预览与小程序是两套独立渲染），
 * 判定口径必须一致，否则运营在编辑器里看到的效果与实机不符。
 */
export function shouldShowOriginalPrice(item: {
  originalPrice?: unknown;
  price?: unknown;
  salesPrice?: unknown;
}): boolean {
  const current = Number(item.salesPrice ?? item.price);
  const original = Number(item.originalPrice);
  if (!Number.isFinite(current) || !Number.isFinite(original)) return false;
  return original > current;
}

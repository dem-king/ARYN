/**
 * 商品卡片的「库存提示」显示口径。
 *
 * 与小程序端 `aryn-mall-uniapp/src/utils/stock-display.ts` 保持同一口径：
 * 绝对库存与累计销量不外露（运营敏感数据，自营即时零售竞品也不展示），
 * 只在售罄 / 低库存（≤10）时给出稀缺提示，充足或未知时返回空串整块隐藏。
 *
 * 两端各有一份实现（管理端预览与小程序是两套独立渲染），
 * 判定口径必须一致，否则运营在编辑器里看到的效果与实机不符。
 */
export const STOCK_LOW_THRESHOLD = 10;

export function stockHintText(
  stock: null | number | string | undefined,
): string {
  if (stock === null || stock === undefined || stock === '') return '';
  const value = Number(stock);
  if (!Number.isFinite(value) || value < 0) return '';
  if (value <= 0) return '已售罄';
  if (value <= STOCK_LOW_THRESHOLD) return `仅剩 ${value} 件`;
  return '';
}

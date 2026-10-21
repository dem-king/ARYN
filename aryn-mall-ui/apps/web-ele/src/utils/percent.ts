/**
 * 统计接口的比率统一返回 0-1 小数，展示层在这里统一换算，避免各页面重复乘 100。
 */
export function toPercent(value?: null | number): number {
  const numeric = Number(value);
  return Number.isFinite(numeric) ? numeric * 100 : 0;
}

export function formatPercent(value?: null | number, decimals = 2): string {
  return `${toPercent(value).toFixed(decimals)}%`;
}

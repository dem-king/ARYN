const numberFormatter = new Intl.NumberFormat('zh-CN');

export function formatNumber(value: number): string {
  return numberFormatter.format(Number(value) || 0);
}

export function formatCompactNumber(value: number): string {
  const numeric = Number(value) || 0;
  if (Math.abs(numeric) >= 100_000_000) {
    return `${(numeric / 100_000_000).toFixed(1)}亿`;
  }
  if (Math.abs(numeric) >= 10_000) {
    return `${(numeric / 10_000).toFixed(1)}万`;
  }
  return numberFormatter.format(numeric);
}

export function formatCurrency(value: number): string {
  return `¥${formatNumber(value)}`;
}

export function formatAxisCurrency(value: number): string {
  return `¥${formatCompactNumber(value)}`;
}

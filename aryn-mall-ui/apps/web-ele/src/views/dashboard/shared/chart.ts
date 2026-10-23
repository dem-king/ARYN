const numberFormatter = new Intl.NumberFormat('zh-CN');

/**
 * 看板统一图表调色板
 *
 * 色值与 Element Plus 默认主题语义色保持一致，Batch 3 之前散落在各图表组件中，
 * 这里集中管理，保证同一语义在不同看板里始终使用同一色值（渲染结果不变）。
 */
export const CHART_PALETTE = {
  /** 主色：成交额、PV、新增用户等主序列 */
  primary: '#409EFF',
  /** 成功色：订单数、UV、成交用户等正向序列 */
  success: '#67C23A',
  /** 警告色：支付买家数、渠道占比等提示序列 */
  warning: '#E6A23C',
  /** 危险色：退款、库存预警等负向序列 */
  danger: '#F56C6C',
  /** 中性信息色：其他/未知分类 */
  info: '#909399',
  /** 扩展色：RFM 分层、渠道贡献等多分类场景 */
  purple: '#8E44AD',
  /** 扩展色：RFM 分层等多分类场景 */
  teal: '#16A085',
  /** 扩展色：RFM 分层等多分类场景 */
  gray: '#7F8C8D',
} as const;

/** 多系列图表按序取色的序列色板（饼图、柱状图、RFM 分层等） */
export const CHART_SERIES_COLORS = [
  CHART_PALETTE.primary,
  CHART_PALETTE.success,
  CHART_PALETTE.warning,
  CHART_PALETTE.danger,
  CHART_PALETTE.info,
  CHART_PALETTE.purple,
  CHART_PALETTE.teal,
  CHART_PALETTE.gray,
] as const;

const HEX_COLOR_PATTERN = /^#(?:[\dA-F]{3}|[\dA-F]{6})$/i;
const RGB_COLOR_PATTERN = /^rgba?\(([^)]*)\)$/i;

function expandHexColor(hex: string): [number, number, number] | null {
  const value = hex.slice(1);
  if (value.length !== 3 && value.length !== 6) {
    return null;
  }
  const full =
    value.length === 3
      ? [...value].map((char) => `${char}${char}`).join('')
      : value;
  return [
    Number.parseInt(full.slice(0, 2), 16),
    Number.parseInt(full.slice(2, 4), 16),
    Number.parseInt(full.slice(4, 6), 16),
  ];
}

/**
 * 将调色板色值转为带透明度的 rgba 字符串。
 * 传入已是 rgb/rgba 的颜色时只替换透明度，便于复用同一色板做渐变。
 */
export function withAlpha(color: string, alpha: number): string {
  const trimmed = color.trim();
  const rgbMatch = RGB_COLOR_PATTERN.exec(trimmed);
  if (rgbMatch) {
    const [red, green, blue] = (rgbMatch[1] ?? '')
      .split(',')
      .map((item) => item.trim());
    return `rgba(${red}, ${green}, ${blue}, ${alpha})`;
  }
  const rgb = HEX_COLOR_PATTERN.test(trimmed) ? expandHexColor(trimmed) : null;
  if (!rgb) {
    return trimmed;
  }
  return `rgba(${rgb[0]}, ${rgb[1]}, ${rgb[2]}, ${alpha})`;
}

export interface ChartAreaGradient {
  colorStops: Array<{ color: string; offset: number }>;
  type: 'linear';
  x: number;
  x2: number;
  y: number;
  y2: number;
}

/**
 * 折线图面积渐变：默认自上而下由半透明过渡到全透明。
 * 统一由 `withAlpha` 生成色值，避免各组件重复手写 rgba。
 */
export function areaGradient(
  color: string,
  fromOpacity = 0.5,
  toOpacity = 0,
): ChartAreaGradient {
  return {
    colorStops: [
      { color: withAlpha(color, fromOpacity), offset: 0 },
      { color: withAlpha(color, toOpacity), offset: 1 },
    ],
    type: 'linear',
    x: 0,
    x2: 0,
    y: 0,
    y2: 1,
  };
}

/**
 * 读取 CSS 变量（ECharts canvas 无法解析 `var(--x)`，必须取具体色值）。
 * 在非浏览器环境（SSR / 单测）返回兜底色。
 */
export function readCssVar(name: string, fallback: string): string {
  if (
    typeof document === 'undefined' ||
    typeof getComputedStyle === 'undefined'
  ) {
    return fallback;
  }
  const value = getComputedStyle(document.documentElement)
    .getPropertyValue(name)
    .trim();
  return value || fallback;
}

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

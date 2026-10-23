import { describe, expect, it } from 'vitest';

import {
  areaGradient,
  CHART_PALETTE,
  CHART_SERIES_COLORS,
  formatAxisCurrency,
  formatCompactNumber,
  formatCurrency,
  formatNumber,
  readCssVar,
  withAlpha,
} from './chart';

describe('chart formatters', () => {
  it('formats numbers with thousands separators', () => {
    expect(formatNumber(1234)).toBe('1,234');
  });

  it('compacts large numbers for axis labels', () => {
    expect(formatCompactNumber(12_000)).toBe('1.2万');
    expect(formatCompactNumber(120_000_000)).toBe('1.2亿');
  });

  it('formats currency for tooltips and axes', () => {
    expect(formatCurrency(1234)).toBe('¥1,234');
    expect(formatAxisCurrency(12_000)).toBe('¥1.2万');
  });
});

describe('chart palette', () => {
  it('keeps the Element Plus semantic color values', () => {
    expect(CHART_PALETTE.primary).toBe('#409EFF');
    expect(CHART_PALETTE.success).toBe('#67C23A');
    expect(CHART_PALETTE.warning).toBe('#E6A23C');
    expect(CHART_PALETTE.danger).toBe('#F56C6C');
    expect(CHART_PALETTE.info).toBe('#909399');
  });

  it('exposes an ordered series palette for multi-series charts', () => {
    expect(CHART_SERIES_COLORS).toHaveLength(8);
    expect(CHART_SERIES_COLORS[0]).toBe(CHART_PALETTE.primary);
    expect(CHART_SERIES_COLORS[7]).toBe(CHART_PALETTE.gray);
  });
});

describe('withAlpha', () => {
  it('converts 6-digit hex colors to rgba', () => {
    expect(withAlpha(CHART_PALETTE.primary, 0.5)).toBe(
      'rgba(64, 158, 255, 0.5)',
    );
    expect(withAlpha(CHART_PALETTE.primary, 0)).toBe('rgba(64, 158, 255, 0)');
  });

  it('expands 3-digit hex colors', () => {
    expect(withAlpha('#fff', 0.2)).toBe('rgba(255, 255, 255, 0.2)');
  });

  it('replaces the alpha channel of rgb/rgba input', () => {
    expect(withAlpha('rgba(64,158,255, 0.9)', 0.5)).toBe(
      'rgba(64, 158, 255, 0.5)',
    );
    expect(withAlpha('rgb(103, 194, 58)', 0.3)).toBe('rgba(103, 194, 58, 0.3)');
  });

  it('returns unsupported color formats unchanged', () => {
    expect(withAlpha('var(--el-color-primary)', 0.5)).toBe(
      'var(--el-color-primary)',
    );
  });
});

describe('areaGradient', () => {
  it('builds a vertical gradient from half transparent to transparent', () => {
    expect(areaGradient(CHART_PALETTE.primary)).toEqual({
      colorStops: [
        { color: 'rgba(64, 158, 255, 0.5)', offset: 0 },
        { color: 'rgba(64, 158, 255, 0)', offset: 1 },
      ],
      type: 'linear',
      x: 0,
      x2: 0,
      y: 0,
      y2: 1,
    });
  });

  it('supports custom opacity for strong/weak fills', () => {
    const gradient = areaGradient(CHART_PALETTE.success, 0.3, 0.05);
    expect(gradient.colorStops[0]?.color).toBe('rgba(103, 194, 58, 0.3)');
    expect(gradient.colorStops[1]?.color).toBe('rgba(103, 194, 58, 0.05)');
  });
});

describe('readCssVar', () => {
  it('reads design tokens from the document root', () => {
    document.documentElement.style.setProperty(
      '--dashboard-test-color',
      '#123456',
    );
    expect(readCssVar('--dashboard-test-color', '#ffffff')).toBe('#123456');
    document.documentElement.style.removeProperty('--dashboard-test-color');
  });

  it('falls back when the token is missing', () => {
    expect(readCssVar('--dashboard-missing-color', '#909399')).toBe('#909399');
  });
});

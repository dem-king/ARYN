import { describe, expect, it } from 'vitest';

import {
  formatAxisCurrency,
  formatCompactNumber,
  formatCurrency,
  formatNumber,
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

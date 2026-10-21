import { describe, expect, it } from 'vitest';

import { formatPercent, toPercent } from './percent';

describe('toPercent', () => {
  it('converts backend ratios to percentages', () => {
    expect(toPercent(0.1234)).toBeCloseTo(12.34, 6);
    expect(toPercent(1)).toBe(100);
    expect(toPercent(0)).toBe(0);
  });

  it('falls back to zero for empty or invalid values', () => {
    expect(toPercent(null)).toBe(0);
    expect(toPercent(undefined)).toBe(0);
    expect(toPercent(Number.NaN)).toBe(0);
  });
});

describe('formatPercent', () => {
  it('formats ratios with percent suffix and decimals', () => {
    expect(formatPercent(0.1234)).toBe('12.34%');
    expect(formatPercent(0.5, 1)).toBe('50.0%');
    expect(formatPercent(null)).toBe('0.00%');
  });
});

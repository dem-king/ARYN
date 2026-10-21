import { describe, expect, it } from 'vitest';

import { calculateDelta } from './compare';

describe('calculateDelta', () => {
  it('calculates percentage change against the previous value', () => {
    expect(calculateDelta(120, 100)).toBeCloseTo(20, 6);
    expect(calculateDelta(80, 100)).toBeCloseTo(-20, 6);
  });

  it('returns zero when both periods are zero', () => {
    expect(calculateDelta(0, 0)).toBe(0);
  });

  it('returns null when comparison is not meaningful', () => {
    expect(calculateDelta(10, 0)).toBeNull();
    expect(calculateDelta(10, null)).toBeNull();
    expect(calculateDelta(null, 10)).toBeNull();
  });
});

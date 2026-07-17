import { describe, expect, it } from 'vitest';

import {
  buildPreviewUrl,
  formatRemainingTime,
  getRemainingSeconds,
} from './preview-utils';

describe('preview utilities', () => {
  it('builds an absolute preview URL and safely encodes the opaque token', () => {
    expect(
      buildPreviewUrl('token/with + symbols', 'https://mall.example.com'),
    ).toBe(
      'https://mall.example.com/#/page-preview/token%2Fwith%20%2B%20symbols',
    );
  });

  it('rounds remaining time up and never returns a negative value', () => {
    expect(getRemainingSeconds(10_001, 9000)).toBe(2);
    expect(getRemainingSeconds(9000, 10_001)).toBe(0);
  });

  it('formats the countdown as minutes and seconds', () => {
    expect(formatRemainingTime(600)).toBe('10:00');
    expect(formatRemainingTime(65)).toBe('01:05');
    expect(formatRemainingTime(0)).toBe('00:00');
  });
});

import { describe, expect, it } from 'vitest';

import { canActivate, testIsFresh } from './ai-config-state';

describe('aI version lifecycle', () => {
  it('tests are bound to exact version and expire', () => {
    const test = {
      version: 'a',
      modelId: 'm',
      passed: true,
      testedAt: '2026-11-15T00:00:00Z',
      status: 'PASSED',
    };
    expect(testIsFresh(test, 'a', Date.parse('2026-11-15T01:00:00Z'))).toBe(
      true,
    );
    expect(testIsFresh(test, 'b', Date.parse('2026-11-15T01:00:00Z'))).toBe(
      false,
    );
    expect(testIsFresh(test, 'a', Date.parse('2026-11-16T01:00:00Z'))).toBe(
      false,
    );
  });
  it('dirty drafts and missing model tests cannot activate', () => {
    expect(canActivate(true, 'a', ['m'], [])).toBe(false);
    expect(canActivate(false, 'a', ['m'], [])).toBe(false);
    expect(canActivate(false, null, [], [])).toBe(false);
  });
});

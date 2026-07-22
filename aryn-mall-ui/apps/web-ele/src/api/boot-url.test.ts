import { describe, expect, it } from 'vitest';

import { parseOpenBoot, rewriteBootUrl } from './boot-url';

describe('boot URL rewriting', () => {
  it('removes only the service prefix and preserves query/hash suffixes', () => {
    expect(rewriteBootUrl('/upms/sysuser/page?current=1#users', true)).toBe(
      '/boot/sysuser/page?current=1#users',
    );
  });

  it('leaves URLs unchanged when Boot mode is disabled or already applied', () => {
    expect(rewriteBootUrl('/upms/sysuser/page', false)).toBe(
      '/upms/sysuser/page',
    );
    expect(rewriteBootUrl('/boot/sysuser/page', true)).toBe(
      '/boot/sysuser/page',
    );
  });

  it('parses the environment flag without throwing on missing values', () => {
    expect(parseOpenBoot('true')).toBe(true);
    expect(parseOpenBoot('TRUE')).toBe(true);
    expect(parseOpenBoot(undefined)).toBe(false);
    expect(parseOpenBoot('invalid')).toBe(false);
  });
});

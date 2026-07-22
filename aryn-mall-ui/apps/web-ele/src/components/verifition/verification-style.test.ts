import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

import { describe, expect, it } from 'vitest';

const verificationComponent = resolve(import.meta.dirname, 'index.vue');

describe('verification component styles', () => {
  it('scopes wrapper styles while reaching dynamic captcha children', () => {
    const source = readFileSync(verificationComponent, 'utf8');

    expect(source).toContain('<style scoped>');
    expect(source).toContain(':deep(.verify-img-panel)');
    expect(source).toContain(':deep(.verify-bar-area)');
    expect(source).toContain(
      ':deep(.verify-bar-area .verify-move-block .verify-sub-block)',
    );
  });
});

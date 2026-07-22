import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import process from 'node:process';

import { describe, expect, it } from 'vitest';

const appRoot = resolve(process.cwd(), 'apps/web-ele');

describe('reviewed component contracts', () => {
  it('checks actual undefined values in DictTag', () => {
    const source = readFileSync(
      resolve(appRoot, 'src/components/dict-tag/index.vue'),
      'utf8',
    );

    expect(source).not.toContain("props.value !== 'undefined'");
    expect(source).toContain('props.value === undefined');
  });

  it('keeps verification styles scoped to the component', () => {
    const source = readFileSync(
      resolve(appRoot, 'src/components/verifition/index.vue'),
      'utf8',
    );

    expect(source).toContain('<style scoped>');
  });
});

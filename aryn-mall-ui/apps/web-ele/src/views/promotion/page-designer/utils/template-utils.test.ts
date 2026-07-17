import { describe, expect, it } from 'vitest';

import { allLegacyComponentsV2 } from '../fixtures/all-components-v2';
import { cloneTemplateDocument } from './template-utils';

describe('cloneTemplateDocument', () => {
  it('regenerates every component id without mutating the template', () => {
    const originalIds = allLegacyComponentsV2.components.map(({ id }) => id);
    let sequence = 0;

    const cloned = cloneTemplateDocument(
      allLegacyComponentsV2,
      () => `new-${(sequence += 1)}`,
    );

    expect(cloned.components.map(({ id }) => id)).not.toEqual(originalIds);
    expect(new Set(cloned.components.map(({ id }) => id)).size).toBe(
      cloned.components.length,
    );
    expect(allLegacyComponentsV2.components.map(({ id }) => id)).toEqual(
      originalIds,
    );
  });
});

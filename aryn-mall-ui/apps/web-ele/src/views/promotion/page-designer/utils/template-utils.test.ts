import { describe, expect, it } from 'vitest';

import { allLegacyComponentsV2 } from '../fixtures/all-components-v2';
import { cloneTemplateDocument } from './template-utils';

describe('cloneTemplateDocument', () => {
  it('regenerates every component id without mutating the template', () => {
    const originalIds = allLegacyComponentsV2.sections
      .flatMap((section) => section.components)
      .map(({ id }) => id);
    let sequence = 0;

    const cloned = cloneTemplateDocument(
      allLegacyComponentsV2,
      () => `new-${(sequence += 1)}`,
    );

    const clonedIds = cloned.sections.flatMap((section) =>
      section.components.map(({ id }) => id),
    );
    expect(clonedIds).not.toEqual(originalIds);
    expect(new Set(clonedIds).size).toBe(clonedIds.length);
    expect(
      allLegacyComponentsV2.sections.flatMap((section) => section.components),
    ).toHaveLength(clonedIds.length);
    expect(
      allLegacyComponentsV2.sections
        .flatMap((section) => section.components)
        .map(({ id }) => id),
    ).toEqual(originalIds);
  });
});

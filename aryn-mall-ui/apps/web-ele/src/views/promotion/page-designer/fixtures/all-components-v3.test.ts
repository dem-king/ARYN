import { describe, expect, it } from 'vitest';

import { migratePageContent } from '../schema/migrate';
import { toV3Document } from '../schema/v3';
import { allComponentsV2 } from './all-components-v2';

describe('all-components v3 contract fixture', () => {
  it('round-trips every registered component through the v3 contract', () => {
    const v3Document = toV3Document(allComponentsV2);

    expect(v3Document.schemaVersion).toBe(3);
    expect(v3Document.sections).toHaveLength(
      allComponentsV2.sections.length,
    );
    expect(v3Document.sections[0]?.components).toHaveLength(17);

    const restored = migratePageContent(structuredClone(v3Document));

    expect(restored.sections).toEqual(allComponentsV2.sections);
    expect(restored.page).toEqual(allComponentsV2.page);
  });

  it('keeps the v3 document serializable without data loss', () => {
    const serialized = JSON.parse(JSON.stringify(toV3Document(allComponentsV2)));
    const restored = migratePageContent(serialized);

    const restoredIds = restored.sections.flatMap((section) =>
      section.components.map(({ id }) => id),
    );
    expect(restoredIds).toEqual(
      allComponentsV2.sections.flatMap((section) =>
        section.components.map(({ id }) => id),
      ),
    );
    expect(restored.sections[0]?.style).toBeDefined();
  });
});

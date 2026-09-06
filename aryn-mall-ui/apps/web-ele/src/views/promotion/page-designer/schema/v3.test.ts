import { describe, expect, it } from 'vitest';

import { createDefaultDecorationDocument } from './defaults';
import { migratePageContent } from './migrate';
import { toV3Document } from './v3';

describe('toV3Document', () => {
  it('produces the v3 contract from the sectioned editor document', () => {
    const document = createDefaultDecorationDocument();

    const v3Document = toV3Document(document);

    expect(v3Document.schemaVersion).toBe(3);
    expect(v3Document.sections).toHaveLength(1);
    expect(v3Document.themeRef).toBeUndefined();
    const restored = migratePageContent(structuredClone(v3Document));
    expect(restored.sections).toEqual(document.sections);
  });

  it('carries the theme ref when provided', () => {
    const document = createDefaultDecorationDocument();

    const v3Document = toV3Document(document, { themeRef: 'theme-9' });

    expect(v3Document.themeRef).toBe('theme-9');
    const restored = migratePageContent(structuredClone(v3Document));
    expect(restored.sections).toEqual(document.sections);
  });
});

import { describe, expect, it } from 'vitest';

import { createDefaultPageSettings } from './defaults';
import { migratePageContent } from './migrate';

describe('migratePageContent', () => {
  it('migrates legacy formData components to versioned props', () => {
    const result = migratePageContent({
      components: [
        {
          formData: { text: '欢迎光临' },
          id: 'notice-1',
          title: '公告',
          type: 'notice',
        },
      ],
    });

    expect(result).toEqual({
      components: [
        {
          id: 'notice-1',
          props: { text: '欢迎光临' },
          type: 'notice',
          version: 1,
        },
      ],
      page: createDefaultPageSettings(),
      schemaVersion: 2,
    });
  });

  it('adds default page settings to an empty legacy document', () => {
    expect(migratePageContent({ components: [] })).toEqual({
      components: [],
      page: createDefaultPageSettings(),
      schemaVersion: 2,
    });
  });

  it('normalizes legacy camel-case component type aliases', () => {
    const result = migratePageContent({
      components: [
        {
          formData: { imageList: [] },
          id: 'image-ad-1',
          type: 'imageAd',
        },
      ],
    });

    expect(result.components[0]).toEqual({
      id: 'image-ad-1',
      props: { imageList: [] },
      type: 'image-ad',
      version: 1,
    });
  });

  it('does not mutate an existing v2 document', () => {
    const source = {
      components: [
        {
          id: 'search-1',
          props: { placeholder: '搜索商品' },
          type: 'search-bar',
          version: 1,
        },
      ],
      page: createDefaultPageSettings(),
      schemaVersion: 2 as const,
    };
    const snapshot = structuredClone(source);

    const result = migratePageContent(source);

    expect(result).toEqual(snapshot);
    expect(result).not.toBe(source);
    expect(source).toEqual(snapshot);
  });

  it('preserves unknown component types for round-trip editing', () => {
    const result = migratePageContent({
      components: [
        {
          formData: { custom: true },
          id: 'future-1',
          title: '未来组件',
          type: 'future-component',
        },
      ],
    });

    expect(result.components[0]).toEqual({
      id: 'future-1',
      props: { custom: true },
      type: 'future-component',
      version: 1,
    });
  });
});

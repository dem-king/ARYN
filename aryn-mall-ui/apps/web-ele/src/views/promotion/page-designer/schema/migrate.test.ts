import { describe, expect, it } from 'vitest';

import { createDefaultPageSettings } from './defaults';
import { migratePageContent } from './migrate';

describe('migratePageContent', () => {
  it('migrates legacy formData components into the default section', () => {
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

    expect(result.schemaVersion).toBe(3);
    expect(result.page).toEqual(createDefaultPageSettings());
    expect(result.sections).toHaveLength(1);
    expect(result.sections[0]?.id).toBe('section-root');
    expect(result.sections[0]?.components).toEqual([
      {
        id: 'notice-1',
        props: { text: '欢迎光临' },
        type: 'notice',
        version: 1,
      },
    ]);
    expect(result.sections[0]?.style.condition).toBe('always');
  });

  it('adds default page settings and section to an empty legacy document', () => {
    expect(migratePageContent({ components: [] })).toEqual({
      page: createDefaultPageSettings(),
      schemaVersion: 3,
      sections: [
        {
          components: [],
          id: 'section-root',
          style: {
            backgroundColor: '',
            backgroundImage: '',
            condition: 'always',
            horizontalScroll: false,
            paddingY: 0,
            sticky: false,
          },
          type: 'default',
        },
      ],
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

    expect(result.sections[0]?.components[0]).toEqual({
      id: 'image-ad-1',
      props: { imageList: [] },
      type: 'image-ad',
      version: 1,
    });
  });

  it('keeps v3 sections, styles and drops malformed entries', () => {
    const result = migratePageContent({
      page: { backgroundColor: '#ffffff' },
      schemaVersion: 3,
      sections: [
        {
          components: [
            { id: 'gap-1', props: { height: 10 }, type: 'gap', version: 1 },
          ],
          id: 'section-root',
          name: '头部',
          style: { condition: 'login', paddingY: 12, sticky: true },
          type: 'default',
        },
        {
          components: [
            { id: 'c2', props: {}, type: 'notice', version: 1 },
            { broken: true },
          ],
          id: 'section-2',
          type: 'default',
        },
        { noId: true },
      ],
    });

    expect(result.sections).toHaveLength(2);
    expect(result.sections[0]?.name).toBe('头部');
    expect(result.sections[0]?.style).toEqual({
      backgroundColor: '',
      backgroundImage: '',
      condition: 'login',
      horizontalScroll: false,
      paddingY: 12,
      sticky: true,
    });
    expect(result.sections[1]?.components.map(({ id }) => id)).toEqual(['c2']);
    expect(result.page.backgroundColor).toBe('#ffffff');
  });

  it('falls back to the default section when v3 sections are malformed', () => {
    const result = migratePageContent({
      schemaVersion: 3,
      sections: 'broken',
    });

    expect(result.sections).toHaveLength(1);
    expect(result.sections[0]?.id).toBe('section-root');
    expect(result.sections[0]?.components).toEqual([]);
  });

  it('does not mutate an existing v3 document', () => {
    const source = {
      page: createDefaultPageSettings(),
      schemaVersion: 3 as const,
      sections: [
        {
          components: [
            {
              id: 'search-1',
              props: { placeholder: '搜索商品' },
              type: 'search-bar',
              version: 1,
            },
          ],
          id: 'section-root',
          style: {
            backgroundColor: '',
            backgroundImage: '',
            condition: 'always' as const,
            horizontalScroll: false,
            paddingY: 0,
            sticky: false,
          },
          type: 'default',
        },
      ],
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

    expect(result.sections[0]?.components[0]).toEqual({
      id: 'future-1',
      props: { custom: true },
      type: 'future-component',
      version: 1,
    });
  });
});

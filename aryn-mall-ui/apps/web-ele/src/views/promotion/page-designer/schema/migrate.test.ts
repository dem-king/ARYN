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
            marginX: 0,
            marginY: 0,
            paddingX: 0,
            paddingY: 0,
            radius: 0,
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
      marginX: 0,
      marginY: 0,
      paddingX: 0,
      paddingY: 12,
      radius: 0,
      sticky: true,
    });
    expect(result.sections[1]?.components.map(({ id }) => id)).toEqual(['c2']);
    expect(result.page.backgroundColor).toBe('#ffffff');
  });

  it('preserves a SectionConditionGroup and sanitizes unknown rules', () => {
    const result = migratePageContent({
      schemaVersion: 3,
      sections: [
        {
          components: [],
          id: 'section-root',
          style: {
            condition: {
              logic: 'or',
              rules: [
                { type: 'memberLevel', memberLevelIds: ['lv1', 'lv2'] },
                { type: 'userTag', userTagIds: ['tag-a'] },
                { type: 'timeRange', startTime: '09:00', endTime: '21:00' },
                { type: 'future-rule', foo: 1 },
                { type: 'memberLevel', memberLevelIds: 'bad' },
              ],
            },
          },
          type: 'default',
        },
      ],
    });

    expect(result.sections[0]?.style.condition).toEqual({
      logic: 'or',
      rules: [
        { type: 'memberLevel', memberLevelIds: ['lv1', 'lv2'] },
        { type: 'userTag', userTagIds: ['tag-a'] },
        { type: 'timeRange', startTime: '09:00', endTime: '21:00' },
        { type: 'memberLevel', memberLevelIds: [] },
      ],
    });
  });

  it('normalizes unknown condition values to always', () => {
    const result = migratePageContent({
      schemaVersion: 3,
      sections: [
        {
          components: [],
          id: 'section-root',
          style: { condition: 'vip' },
          type: 'default',
        },
        {
          components: [],
          id: 'section-2',
          style: { condition: { logic: 'and' } },
          type: 'default',
        },
      ],
    });

    expect(result.sections[0]?.style.condition).toBe('always');
    expect(result.sections[1]?.style.condition).toBe('always');
  });

  it('keeps section box styles and clamps illegal length values', () => {
    const result = migratePageContent({
      schemaVersion: 3,
      sections: [
        {
          components: [],
          id: 'section-card',
          style: {
            marginX: 12,
            marginY: 12,
            paddingX: 12,
            paddingY: 12,
            radius: 16,
          },
          type: 'default',
        },
        {
          components: [],
          id: 'section-dirty',
          style: {
            marginX: -8,
            marginY: 'large',
            paddingX: 999,
            paddingY: Number.NaN,
            radius: null,
          },
          type: 'default',
        },
      ],
    });

    expect(result.sections[0]?.style).toMatchObject({
      marginX: 12,
      marginY: 12,
      paddingX: 12,
      paddingY: 12,
      radius: 16,
    });
    // 非法值收敛为 0，超大值封顶 200，避免脏数据把内容挤出屏幕
    expect(result.sections[1]?.style).toMatchObject({
      marginX: 0,
      marginY: 0,
      paddingX: 200,
      paddingY: 0,
      radius: 0,
    });
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
            marginX: 0,
            marginY: 0,
            paddingX: 0,
            paddingY: 0,
            radius: 0,
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

import { isReactive, reactive, readonly } from 'vue';

import { describe, expect, it } from 'vitest';

import { cloneDesignerValue, isSameDesignerValue } from './clone';

describe('cloneDesignerValue', () => {
  it('clones reactive and readonly schema values into independent plain data', () => {
    const source = readonly(
      reactive({
        components: [{ id: 'notice-1', props: reactive({ content: '公告' }) }],
        page: reactive({ backgroundColor: '#ffffff' }),
      }),
    );

    const cloned = cloneDesignerValue(source) as {
      components: Array<{ id: string; props: { content: string } }>;
      page: { backgroundColor: string };
    };

    expect(cloned).toEqual(source);
    expect(isReactive(cloned)).toBe(false);
    expect(isReactive(cloned.components[0]?.props)).toBe(false);
    cloned.page.backgroundColor = '#000000';
    expect(source.page.backgroundColor).toBe('#ffffff');
  });

  it('compares reactive schema values by JSON content', () => {
    const first = reactive({ nested: { enabled: true }, title: '首页' });
    const same = readonly({ nested: { enabled: true }, title: '首页' });
    const changed = reactive({ nested: { enabled: false }, title: '首页' });

    expect(isSameDesignerValue(first, same)).toBe(true);
    expect(isSameDesignerValue(first, changed)).toBe(false);
  });
});

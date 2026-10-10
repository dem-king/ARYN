import type { PageSettings } from './types';

import { describe, expect, it } from 'vitest';

import { resolveEffectivePageTheme } from './effective-theme';

function makePage(overrides?: Partial<PageSettings>): PageSettings {
  return {
    backgroundColor: '#1543e8',
    backgroundImage: '',
    enablePullDownRefresh: true,
    navigation: {
      backgroundColor: '#1543e8',
      textColor: '#ffffff',
      title: '',
      visible: true,
    },
    share: { description: '', imageUrl: '', title: '' },
    ...overrides,
  };
}

const mallTheme = {
  navigationColor: '#FF5500',
  navigationTextColor: '#ffffff',
  pageBackgroundColor: '#FFF3EC',
};

describe('resolveEffectivePageTheme', () => {
  it('未引用主题的页面跟随商城默认主题：页面底色与导航配色被覆盖', () => {
    const effective = resolveEffectivePageTheme(makePage(), mallTheme);
    expect(effective.backgroundColor).toBe('#FFF3EC');
    expect(effective.navigationBackgroundColor).toBe('#FF5500');
    expect(effective.navigationTextColor).toBe('#ffffff');
  });

  it('页面引用过主题（theme=undefined）时保持页面自身颜色：快照已由 migrate 合并', () => {
    const effective = resolveEffectivePageTheme(makePage(), undefined);
    expect(effective.backgroundColor).toBe('#1543e8');
    expect(effective.navigationBackgroundColor).toBe('#1543e8');
    expect(effective.navigationTextColor).toBe('#ffffff');
  });

  it('主题字段为空串时逐字段回落页面自存值，不做整体回退', () => {
    const effective = resolveEffectivePageTheme(makePage(), {
      navigationColor: '',
      navigationTextColor: '#222222',
      pageBackgroundColor: '',
    });
    expect(effective.backgroundColor).toBe('#1543e8');
    expect(effective.navigationBackgroundColor).toBe('#1543e8');
    expect(effective.navigationTextColor).toBe('#222222');
  });
});

import { describe, expect, it } from 'vitest';

import {
  mergeTenantTemplates,
  targetPageTypeFor,
} from './template-create-utils';

describe('targetPageTypeFor', () => {
  it('落位首页与通用模板到微页面', () => {
    expect(targetPageTypeFor('1')).toBe('0');
    expect(targetPageTypeFor('2')).toBe('0');
    expect(targetPageTypeFor(undefined)).toBe('0');
  });

  it('分类页/个人中心页模板原样落位', () => {
    expect(targetPageTypeFor('3')).toBe('3');
    expect(targetPageTypeFor('4')).toBe('4');
  });

  it('微页面模板原样落位', () => {
    expect(targetPageTypeFor('0')).toBe('0');
  });
});

describe('mergeTenantTemplates', () => {
  const t = (id: string, pageType: '0' | '1' | '2' | '3' | '4', sort = 0) => ({
    id,
    pageType,
    schemaVersion: 3,
    sort,
    systemFlag: '0' as const,
    templateContent: {},
    templateName: id,
    templateType: '0' as const,
  });

  it('跨页型查询去重（通用模板在多个响应中重复出现）', () => {
    const merged = mergeTenantTemplates([
      [t('a', '2'), t('b', '0')],
      [t('a', '2'), t('c', '3')],
      [t('a', '2'), t('d', '4')],
    ]);
    expect(merged.map((item) => item.id)).toEqual(['b', 'c', 'd', 'a']);
  });

  it('同页型内按 sort 稳定排序', () => {
    const merged = mergeTenantTemplates([
      [t('x', '3', 20), t('y', '3', 10)],
      [t('z', '0', 99)],
    ]);
    expect(merged.map((item) => item.id)).toEqual(['z', 'y', 'x']);
  });
});

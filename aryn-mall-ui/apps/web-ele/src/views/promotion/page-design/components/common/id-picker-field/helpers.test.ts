import { describe, expect, it } from 'vitest';

import { invalidItem, mergePickedIds, missingIds } from './helpers';

describe('装修手选字段：ID 合并与回填', () => {
  it('追加去重并保留「已有顺序 + 新选顺序」', () => {
    expect(mergePickedIds(['a', 'b'], ['b', 'c', 'a', 'd'])).toEqual([
      'a',
      'b',
      'c',
      'd',
    ]);
  });

  it('过滤空值与重复项后再按上限截断', () => {
    expect(mergePickedIds(['a'], ['', 'b', 'c'], 2)).toEqual(['a', 'b']);
    expect(mergePickedIds([], ['a', 'b', 'c'], 0)).toEqual(['a', 'b', 'c']);
  });

  it('missingIds 只返回本地还没有展示信息的 ID', () => {
    expect(missingIds(['1', '2', '3', ''], new Set(['2']))).toEqual(['1', '3']);
  });

  it('查不到的 ID 兜底为可删除的已失效行', () => {
    const item = invalidItem('999');
    expect(item.invalid).toBe(true);
    expect(item.title).toContain('999');
  });
});

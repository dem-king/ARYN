import { describe, expect, it } from 'vitest';

import { pickerKindOfType, TARGET_LINK_TYPES } from './link-types';

describe('链接类型 → 选择器映射', () => {
  it('商品/优惠券/微页面/活动都有对应的实体选择器', () => {
    expect(pickerKindOfType('goods')).toBe('goods');
    expect(pickerKindOfType('coupon')).toBe('coupon');
    expect(pickerKindOfType('page')).toBe('page');
    expect(pickerKindOfType('activity')).toBe('groupbuy-activity');
  });

  it('分类走分类树、无实体选择的类型返回 null', () => {
    for (const type of [
      'category',
      'custom',
      'mini-program',
      'customer-service',
    ] as const) {
      expect(pickerKindOfType(type)).toBeNull();
    }
  });

  it('目标类型集合与 C 端需要 targetId 的类型一致', () => {
    expect([...TARGET_LINK_TYPES].sort()).toEqual([
      'activity',
      'category',
      'coupon',
      'goods',
      'page',
    ]);
  });
});

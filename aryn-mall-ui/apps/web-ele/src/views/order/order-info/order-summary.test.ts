import { describe, expect, it } from 'vitest';

import { buildOrderSummary, MAX_THUMBNAILS } from './order-summary';

/**
 * 订单列表折叠态汇总口径测试：
 * 折叠时只展示首件 + 最多 4 张缩略图 + 汇总，展开后走分类分组全量渲染。
 */
describe('order list summary', () => {
  const item = (
    spuName: string,
    buyQuantity: number,
    categoryName?: string,
  ) => ({
    spuName,
    buyQuantity,
    categoryName: categoryName ?? null,
  });

  it('空订单返回 null，单件商品不需要折叠开关', () => {
    expect(buildOrderSummary([])).toBeNull();
    expect(buildOrderSummary(undefined as any)).toBeNull();

    const single = buildOrderSummary([item('矿泉水', 2, '饮品/水')]);
    expect(single?.collapsible).toBe(false);
    expect(single?.firstItem.spuName).toBe('矿泉水');
    expect(single?.totalQuantity).toBe(2);
    expect(single?.thumbnails).toHaveLength(0);
  });

  it('多件订单汇总总件数、分类数，缩略图取首件之后的商品', () => {
    const summary = buildOrderSummary([
      item('达克宁软膏', 4, '个护清洁/个护健康'),
      item('高露洁牙膏', 6, '个护清洁/个护健康'),
      item('冷冻带鱼段', 3, '海鲜水产/海鲜水产'),
    ]);
    expect(summary?.collapsible).toBe(true);
    expect(summary?.firstItem.spuName).toBe('达克宁软膏');
    expect(summary?.totalQuantity).toBe(13);
    expect(summary?.categoryCount).toBe(2);
    expect(summary?.thumbnails.map((i) => i.spuName)).toEqual([
      '高露洁牙膏',
      '冷冻带鱼段',
    ]);
    expect(summary?.hiddenCount).toBe(0);
  });

  it('缩略图最多 4 张，其余计入 hiddenCount 以 +N 收口', () => {
    const items = Array.from({ length: 9 }, (_, i) => item(`商品${i}`, 1));
    const summary = buildOrderSummary(items);
    expect(summary?.thumbnails).toHaveLength(MAX_THUMBNAILS);
    expect(summary?.hiddenCount).toBe(4);
    expect(summary?.totalQuantity).toBe(9);
  });

  it('buyQuantity 缺失或非法时不污染总件数', () => {
    const summary = buildOrderSummary([
      { spuName: 'A', buyQuantity: '3' },
      { spuName: 'B' },
      { spuName: 'C', buyQuantity: null },
    ]);
    expect(summary?.totalQuantity).toBe(3);
  });
});

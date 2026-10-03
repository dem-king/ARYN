import { describe, expect, it } from 'vitest';

import {
  buildGroupedDisplayItems,
  groupOrderItems,
  UNCATEGORIZED,
} from './category-group';

/**
 * 订单商品分类分组的共享口径测试：
 * 详情页合并单元格与列表页分组标题条、后端导出 Excel 必须同序同组。
 */
describe('order category group', () => {
  const water = { spuName: '矿泉水', categoryName: '饮品/水' };
  const cola = { spuName: '可乐', categoryName: '饮品/碳酸' };
  const chips = { spuName: '薯片', categoryName: '零食/膨化' };
  const tissue = { spuName: '纸巾', categoryName: null };

  it('组间按拼音序、未分类固定排最后、组内保持原始顺序', () => {
    // 故意乱序：未分类插入中间
    const groups = groupOrderItems([chips, tissue, cola, water]);
    expect(groups.map((g) => g.key)).toEqual([
      '零食/膨化',
      '饮品/水',
      '饮品/碳酸',
      UNCATEGORIZED,
    ]);
    // 未分类兜底标记
    const last = groups.at(-1);
    expect(last).toMatchObject({ isUncategorized: true });
    // 组内顺序保持：传入是什么顺序组内就是什么顺序
    const drink = groups.find((g) => g.key === '饮品/碳酸');
    expect(drink?.items).toEqual([cola]);
  });

  it('组内多商品时保持原始顺序（共享采购按人拆行语义）', () => {
    const a = { spuName: 'A人-水', categoryName: '饮品/水' };
    const b = { spuName: 'B人-水', categoryName: '饮品/水' };
    const groups = groupOrderItems([a, b, cola]);
    expect(groups).toHaveLength(2);
    expect(groups[0]?.items.map((i) => i.spuName)).toEqual([
      'A人-水',
      'B人-水',
    ]);
  });

  it('display 行标注组首行与组大小供合并单元格使用', () => {
    const rows = buildGroupedDisplayItems([cola, water, chips, tissue]);
    // 行序 = 分组序（拼音序：零食 < 饮品/水 < 饮品/碳酸）
    expect(rows.map((r) => r.categoryKey)).toEqual([
      '零食/膨化',
      '饮品/水',
      '饮品/碳酸',
      UNCATEGORIZED,
    ]);
    // 每组首行
    expect(rows[0]).toMatchObject({
      isFirstOfGroup: true,
      groupSize: 1,
      categoryLabel: '零食/膨化',
    });
    expect(rows[3]).toMatchObject({
      isFirstOfGroup: true,
      groupSize: 1,
      isUncategorized: true,
    });
  });

  it('同组多行只有首行标记为组首，其余行用于合并隐藏', () => {
    const water2 = { spuName: '矿泉水2', categoryName: '饮品/水' };
    const rows = buildGroupedDisplayItems([water, water2, cola]);
    expect(rows.map((r) => r.categoryKey)).toEqual([
      '饮品/水',
      '饮品/水',
      '饮品/碳酸',
    ]);
    expect(rows[0]?.isFirstOfGroup).toBe(true);
    expect(rows[0]?.groupSize).toBe(2);
    expect(rows[1]).toMatchObject({ isFirstOfGroup: false, groupSize: 2 });
  });

  it('空列表返回空结果', () => {
    expect(groupOrderItems([])).toEqual([]);
    expect(buildGroupedDisplayItems(undefined as any)).toEqual([]);
  });
});

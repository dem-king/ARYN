import { describe, expect, it } from 'vitest';

import {
  NOTICE_TYPE_META,
  noticeTypeLabelOf,
  noticeTypeMetaOf,
} from './notice-type-meta';

/**
 * 运营反馈「取消订单和新订单图标颜色一样」的回归守卫：
 * 同一 ORDER 分类下，新订单与已取消必须是不同的图标/颜色/标签。
 */
describe('noticeTypeMetaOf', () => {
  it('gives new and cancelled orders distinct visuals', () => {
    const created = noticeTypeMetaOf({
      category: 'ORDER',
      sourceType: 'ORDER_CREATE',
      title: '新订单待处理',
    });
    const cancelled = noticeTypeMetaOf({
      category: 'ORDER',
      sourceType: 'ORDER_CANCEL',
      title: '买家取消订单',
    });

    expect(created.label).toBe('新订单');
    expect(cancelled.label).toBe('已取消');
    expect(created.color).not.toBe(cancelled.color);
    expect(created.icon).not.toBe(cancelled.icon);
  });

  it('labels refund, COD, vessel and system notices', () => {
    expect(noticeTypeMetaOf({ sourceType: 'ORDER_REFUND_APPLY' }).label).toBe(
      '退款审核',
    );
    expect(noticeTypeMetaOf({ sourceType: 'COD_PAY_REMIND' }).label).toBe(
      '待收款',
    );
    expect(noticeTypeMetaOf({ category: 'VESSEL_BIND_TODO' }).label).toBe(
      '绑定申请',
    );
    expect(noticeTypeMetaOf({ category: 'SYSTEM' }).label).toBe('系统');
  });

  it('covers every notice type with a colour, icon and label', () => {
    for (const [type, meta] of Object.entries(NOTICE_TYPE_META)) {
      expect(meta.color, `${type} 缺少颜色`).toMatch(/^#/);
      expect(meta.icon, `${type} 缺少图标`).toBeDefined();
      expect(meta.label, `${type} 缺少标签`).toBeTruthy();
      expect(noticeTypeLabelOf(type as keyof typeof NOTICE_TYPE_META)).toBe(
        meta.label,
      );
    }
  });
});

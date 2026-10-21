import { describe, expect, it } from 'vitest';

import {
  formatNoticeDateTime,
  formatNoticeTime,
  noticeCategoryOf,
  noticeTypeOf,
  parseNoticeTime,
} from '../notice';

describe('noticeCategoryOf', () => {
  it('treats conversation reminders separately from notices', () => {
    expect(noticeCategoryOf({ kind: 'conversation' })).toBe('conversation');
  });

  it('splits refund out of the ORDER category by source type', () => {
    expect(
      noticeCategoryOf({
        category: 'ORDER',
        sourceType: 'ORDER_REFUND_APPLY',
      }),
    ).toBe('refund');
  });

  it('maps order sources, including COD reminders', () => {
    expect(noticeCategoryOf({ sourceType: 'ORDER_CREATE' })).toBe('order');
    expect(noticeCategoryOf({ sourceType: 'COD_PAY_REMIND' })).toBe('order');
    expect(noticeCategoryOf({ category: 'ORDER' })).toBe('order');
  });

  it('maps vessel bind reminders by source or category', () => {
    expect(
      noticeCategoryOf({
        category: 'VESSEL_BIND_TODO',
        sourceType: 'VESSEL_BIND_APPLY_TODO',
      }),
    ).toBe('vessel');
    expect(noticeCategoryOf({ category: 'VESSEL_BIND_TODO' })).toBe('vessel');
  });

  it('falls back to system for plain notices', () => {
    expect(noticeCategoryOf({ category: 'SYSTEM' })).toBe('system');
    expect(noticeCategoryOf({})).toBe('system');
  });
});

describe('noticeTypeOf', () => {
  it('splits new and cancelled orders into different types', () => {
    expect(noticeTypeOf({ sourceType: 'ORDER_CREATE' })).toBe('order-created');
    expect(noticeTypeOf({ sourceType: 'ORDER_CANCEL' })).toBe(
      'order-cancelled',
    );
  });

  it('maps refund, COD, vessel and delivery sources', () => {
    expect(noticeTypeOf({ sourceType: 'ORDER_REFUND_APPLY' })).toBe(
      'order-refund',
    );
    expect(noticeTypeOf({ sourceType: 'COD_PAY_REMIND' })).toBe(
      'cod-pay-remind',
    );
    expect(noticeTypeOf({ sourceType: 'VESSEL_BIND_APPLY_TODO' })).toBe(
      'vessel-bind',
    );
    expect(noticeTypeOf({ sourceType: 'ORDER_DELIVERY_TASK' })).toBe(
      'delivery-task',
    );
  });

  it('keeps conversation reminders separate and defaults to system', () => {
    expect(noticeTypeOf({ kind: 'conversation' })).toBe('conversation');
    expect(noticeTypeOf({ category: 'SYSTEM' })).toBe('system');
    expect(noticeTypeOf({})).toBe('system');
  });

  it('falls back to title keywords when sourceType is missing', () => {
    expect(noticeTypeOf({ title: '买家取消订单' })).toBe('order-cancelled');
    expect(noticeTypeOf({ title: '订单超时自动取消' })).toBe('order-cancelled');
    expect(noticeTypeOf({ title: '新的退款申请待审核' })).toBe('order-refund');
    expect(noticeTypeOf({ title: '货到付款订单收款提醒' })).toBe(
      'cod-pay-remind',
    );
    expect(noticeTypeOf({ title: '新的船舶绑定申请' })).toBe('vessel-bind');
    expect(noticeTypeOf({ title: '新订单待处理' })).toBe('order-created');
    expect(noticeTypeOf({ title: '系统维护通知' })).toBe('system');
  });
});

describe('parseNoticeTime', () => {
  it('parses the backend yyyy-MM-dd HH:mm:ss format', () => {
    const time = parseNoticeTime('2026-10-18 17:21:01');
    expect(time).toBeTypeOf('number');
    const date = new Date(time ?? 0);
    expect(date.getFullYear()).toBe(2026);
    expect(date.getMonth()).toBe(9);
    expect(date.getDate()).toBe(18);
    expect(date.getHours()).toBe(17);
    expect(date.getMinutes()).toBe(21);
  });

  it('accepts ISO-style T separator and missing seconds', () => {
    expect(parseNoticeTime('2026-10-18T17:21')).toBe(
      parseNoticeTime('2026-10-18 17:21:00'),
    );
  });

  it('returns undefined for empty or malformed values', () => {
    expect(parseNoticeTime(undefined)).toBeUndefined();
    expect(parseNoticeTime('')).toBeUndefined();
    expect(parseNoticeTime('昨天')).toBeUndefined();
  });
});

describe('formatNoticeTime', () => {
  const now = new Date(2026, 9, 18, 18, 0).getTime();

  it('shows month-day and clock within the current year', () => {
    expect(formatNoticeTime('2026-10-18 17:21:01', now)).toBe('10-18 17:21');
  });

  it('shows the full date across years', () => {
    expect(formatNoticeTime('2025-12-31 23:59:59', now)).toBe('2025-12-31');
  });

  it('falls back to the raw text when it cannot parse', () => {
    expect(formatNoticeTime('刚刚', now)).toBe('刚刚');
    expect(formatNoticeTime(undefined, now)).toBe('');
  });
});

describe('formatNoticeDateTime', () => {
  it('keeps date and clock for inbox rows', () => {
    expect(formatNoticeDateTime('2026-10-18 17:21:01')).toBe(
      '2026-10-18 17:21',
    );
  });

  it('falls back to raw text when malformed', () => {
    expect(formatNoticeDateTime('invalid')).toBe('invalid');
  });
});

import type { NoticeInboxItem } from '#/api/message/types';

import { describe, expect, it } from 'vitest';

import {
  filterInbox,
  INBOX_FILTER_KEYS,
  inboxCardMeta,
  inboxCategoryOf,
  inboxFilterCounts,
  inboxStats,
  matchInboxSearch,
  parseInboxCardPayload,
  searchInbox,
} from './inbox-filter';

function notice(overrides: Partial<NoticeInboxItem> = {}): NoticeInboxItem {
  return {
    category: 'SYSTEM',
    content: '系统维护通知正文',
    messageId: 'message-1',
    priority: 'NORMAL',
    publishTime: '2026-10-18 17:21:01',
    readStatus: '0',
    receivedTime: '2026-10-18 17:21:02',
    recipientRecordId: 'record-1',
    title: '系统维护通知',
    ...overrides,
  };
}

/** 订单通知：未读，cardPayload 带订单标题与金额。 */
const orderNotice = notice({
  cardPayload:
    '{"bizType":"ORDER","bizId":"1001","title":"订单 NO-1","amount":"88.00"}',
  category: 'ORDER',
  content: '收到新订单 NO-1，请及时处理。',
  messageId: 'order-1',
  readStatus: '0',
  recipientRecordId: 'record-order',
  sourceType: 'ORDER_CREATE',
  summary: '订单 NO-1 金额 88.00 元',
  title: '新订单待处理',
});

/** 退款通知：已读，category 同为 ORDER，只能靠 sourceType 区分。 */
const refundNotice = notice({
  cardPayload:
    '{"bizType":"REFUND","bizId":"9001","title":"退款单 R-9","amount":"36.50"}',
  category: 'ORDER',
  content: '买家对订单 SO-1 提交退款申请，待审核。',
  messageId: 'refund-1',
  readStatus: '1',
  recipientRecordId: 'record-refund',
  sourceType: 'ORDER_REFUND_APPLY',
  summary: '退款单 R-9 申请退款 36.50 元',
  title: '新的退款申请待审核',
});

/** 船舶通知：已读。 */
const vesselNotice = notice({
  category: 'VESSEL_BIND_TODO',
  messageId: 'vessel-1',
  readStatus: '1',
  recipientRecordId: 'record-vessel',
  sourceType: 'VESSEL_BIND_APPLY_TODO',
  title: '船舶绑定申请',
});

/** 系统通知：未读，无业务卡片。 */
const systemNotice = notice({
  category: 'SYSTEM',
  content: '平台将于今晚 23:00 升级，期间下单可能延迟。',
  messageId: 'system-1',
  readStatus: '0',
  recipientRecordId: 'record-system',
  title: '平台升级通知',
});

const records = [orderNotice, refundNotice, vesselNotice, systemNotice];

describe('inboxCategoryOf', () => {
  it('splits refund and order by source type, not by the shared ORDER category', () => {
    expect(inboxCategoryOf(orderNotice)).toBe('order');
    expect(inboxCategoryOf(refundNotice)).toBe('refund');
  });

  it('maps vessel reminders and falls back to system', () => {
    expect(inboxCategoryOf(vesselNotice)).toBe('vessel');
    expect(inboxCategoryOf(systemNotice)).toBe('system');
    expect(inboxCategoryOf({ category: 'UNKNOWN' })).toBe('system');
  });
});

describe('parseInboxCardPayload', () => {
  it('parses the object convention', () => {
    expect(parseInboxCardPayload('{"orderId":"1"}')).toMatchObject({
      orderId: '1',
    });
  });

  it('returns undefined for empty, malformed, array and scalar payloads', () => {
    expect(parseInboxCardPayload(undefined)).toBeUndefined();
    expect(parseInboxCardPayload('   ')).toBeUndefined();
    expect(parseInboxCardPayload('not-json')).toBeUndefined();
    expect(parseInboxCardPayload('[1,2]')).toBeUndefined();
    expect(parseInboxCardPayload('"text"')).toBeUndefined();
    expect(parseInboxCardPayload('null')).toBeUndefined();
  });
});

describe('inboxCardMeta', () => {
  it('reads the title and amount chips', () => {
    expect(inboxCardMeta(orderNotice)).toEqual({
      amount: '88.00',
      title: '订单 NO-1',
    });
  });

  it('normalizes numeric amounts and ignores malformed payloads', () => {
    expect(
      inboxCardMeta({ cardPayload: '{"amount":36.5,"title":" 退款单 R-9 "}' }),
    ).toEqual({ amount: '36.5', title: '退款单 R-9' });
    expect(inboxCardMeta({ cardPayload: 'not-json' })).toEqual({
      amount: undefined,
      title: undefined,
    });
  });
});

describe('searchInbox / matchInboxSearch', () => {
  it('returns everything for an empty query', () => {
    expect(searchInbox(records, '')).toHaveLength(4);
    expect(searchInbox(records, '   ')).toHaveLength(4);
    expect(matchInboxSearch(orderNotice)).toBe(true);
  });

  it('matches title, summary and content case-insensitively', () => {
    expect(
      searchInbox(records, '退款申请').map((item) => item.messageId),
    ).toEqual(['refund-1']);
    expect(searchInbox(records, 'R-9').map((item) => item.messageId)).toEqual([
      'refund-1',
    ]);
    expect(searchInbox(records, '升级')).toHaveLength(1);
  });

  it('matches card payload text such as amount and business keys', () => {
    expect(searchInbox(records, '88.00').map((item) => item.messageId)).toEqual(
      ['order-1'],
    );
    expect(searchInbox(records, '9001').map((item) => item.messageId)).toEqual([
      'refund-1',
    ]);
  });

  it('requires every whitespace-separated keyword to match the same notice', () => {
    // 只有退款通知同时包含「订单」和「退款」两个词
    expect(
      searchInbox(records, '订单 退款').map((item) => item.messageId),
    ).toEqual(['refund-1']);
    expect(searchInbox(records, '订单 不存在的词')).toHaveLength(0);
  });
});

describe('filterInbox', () => {
  it('keeps all records for the default filter', () => {
    expect(filterInbox(records, 'all')).toHaveLength(4);
  });

  it('filters unread records only', () => {
    expect(
      filterInbox(records, 'unread').map((item) => item.messageId),
    ).toEqual(['order-1', 'system-1']);
  });

  it('filters by business category', () => {
    expect(filterInbox(records, 'order').map((item) => item.messageId)).toEqual(
      ['order-1'],
    );
    expect(
      filterInbox(records, 'refund').map((item) => item.messageId),
    ).toEqual(['refund-1']);
    expect(
      filterInbox(records, 'vessel').map((item) => item.messageId),
    ).toEqual(['vessel-1']);
    expect(
      filterInbox(records, 'system').map((item) => item.messageId),
    ).toEqual(['system-1']);
  });
});

describe('inboxStats / inboxFilterCounts', () => {
  it('counts totals, unread and per-category distribution', () => {
    expect(inboxStats(records)).toEqual({
      byCategory: { order: 1, refund: 1, system: 1, vessel: 1 },
      total: 4,
      unread: 2,
    });
  });

  it('counts tabs on top of the search result only', () => {
    const searched = searchInbox(records, '订单');
    expect(inboxFilterCounts(searched)).toMatchObject({
      all: 2,
      order: 1,
      refund: 1,
      unread: 1,
    });
    expect(
      inboxFilterCounts(searchInbox(records, '没有任何匹配')),
    ).toMatchObject({
      all: 0,
      refund: 0,
      unread: 0,
    });
  });

  it('exposes tabs in the display order', () => {
    expect([...INBOX_FILTER_KEYS]).toEqual([
      'all',
      'unread',
      'order',
      'refund',
      'system',
      'vessel',
    ]);
  });
});

/**
 * 工作人员收件箱的纯前端筛选/搜索/统计。
 *
 * 收件箱是游标分页（cursor）加载的：筛选、搜索和计数都只作用于**已加载**的
 * records，不能替用户“猜”服务端还有没有匹配项；「加载更早通知」仍是唯一的
 * 扩量入口。把这些口径收敛成纯函数，页面只负责渲染，也便于单测钉住行为。
 */
import type { NoticeInboxItem } from '#/api/message/types';

import { noticeCategoryOf } from '@vben/utils';

/** 收件箱分类页签：客服会话不进收件箱，只保留四类站内信。 */
export const INBOX_CATEGORY_KEYS = [
  'order',
  'refund',
  'system',
  'vessel',
] as const;

export type InboxCategoryKey = (typeof INBOX_CATEGORY_KEYS)[number];

/** 工具栏页签：全部 / 未读 / 四个业务分类。 */
export type InboxFilterKey = 'all' | 'unread' | InboxCategoryKey;

export const INBOX_FILTER_KEYS = [
  'all',
  'unread',
  ...INBOX_CATEGORY_KEYS,
] as const satisfies readonly InboxFilterKey[];

/** 卡片载荷里与列表展示相关的字段（订单/退款通知带标题与金额）。 */
export interface InboxCardMeta {
  amount?: string;
  title?: string;
}

/** 把 JSON 里的标量读数统一成字符串：金额后端可能下发字符串或数字。 */
function readText(value: unknown): string | undefined {
  if (typeof value === 'string') {
    const text = value.trim();
    return text === '' ? undefined : text;
  }
  if (typeof value === 'number' && Number.isFinite(value)) {
    return String(value);
  }
  return undefined;
}

/**
 * 容错解析 cardPayload。
 *
 * 历史数据里这个字段的键名并不统一（orderId/refundId/bizType…），也可能
 * 是空串、非法 JSON 甚至数组；解析失败只影响卡片上的辅助信息，不能让整页报错。
 */
export function parseInboxCardPayload(
  raw?: null | string,
): Record<string, unknown> | undefined {
  const text = raw?.trim();
  if (!text) {
    return undefined;
  }
  try {
    const value: unknown = JSON.parse(text);
    return value && typeof value === 'object' && !Array.isArray(value)
      ? (value as Record<string, unknown>)
      : undefined;
  } catch {
    return undefined;
  }
}

/** 订单/退款卡片在列表里展示的标题与金额。 */
export function inboxCardMeta(
  item: Pick<NoticeInboxItem, 'cardPayload'>,
): InboxCardMeta {
  const card = parseInboxCardPayload(item.cardPayload);
  if (!card) {
    return {};
  }
  return {
    amount: readText(card.amount),
    title: readText(card.title),
  };
}

/** 收敛成收件箱页签分类；`noticeCategoryOf` 的会话分支在收件箱里不存在，兜底系统。 */
export function inboxCategoryOf(
  item: Pick<NoticeInboxItem, 'category' | 'sourceType'>,
): InboxCategoryKey {
  const category = noticeCategoryOf(item);
  return category === 'conversation' ? 'system' : category;
}

/**
 * 搜索索引：标题/摘要/正文/发送人 + cardPayload 里的所有文案。
 *
 * cardPayload 原文也拼进去：金额、订单号这类内容即使键名陌生也能被搜到。
 */
export function inboxSearchText(item: NoticeInboxItem): string {
  const parts = [
    item.title,
    item.summary ?? '',
    item.content,
    item.senderName ?? '',
    item.cardPayload ?? '',
  ];
  const card = parseInboxCardPayload(item.cardPayload);
  if (card) {
    for (const value of Object.values(card)) {
      const text = readText(value);
      if (text) {
        parts.push(text);
      }
    }
  }
  return parts.join('\n').toLowerCase();
}

/**
 * 搜索匹配：空格分词后 AND 匹配。
 *
 * 用分词而不是整串 `includes`，是为了让「订单 退款」这类多关键词输入符合直觉。
 */
export function matchInboxSearch(
  item: NoticeInboxItem,
  query?: string,
): boolean {
  const keywords = (query ?? '')
    .trim()
    .toLowerCase()
    .split(/\s+/)
    .filter(Boolean);
  if (keywords.length === 0) {
    return true;
  }
  const haystack = inboxSearchText(item);
  return keywords.every((keyword) => haystack.includes(keyword));
}

/** 只做搜索（不叠加分类/未读），供页签计数与最终筛选共用。 */
export function searchInbox(
  records: NoticeInboxItem[],
  query?: string,
): NoticeInboxItem[] {
  return records.filter((item) => matchInboxSearch(item, query));
}

/** 按页签筛选已加载的通知。 */
export function filterInbox(
  records: NoticeInboxItem[],
  filter: InboxFilterKey,
): NoticeInboxItem[] {
  if (filter === 'all') {
    return records;
  }
  if (filter === 'unread') {
    return records.filter((item) => item.readStatus === '0');
  }
  return records.filter((item) => inboxCategoryOf(item) === filter);
}

export interface InboxStats {
  /** 各分类的已加载条数 */
  byCategory: Record<InboxCategoryKey, number>;
  total: number;
  unread: number;
}

/** 已加载记录的总量/未读/分类分布。 */
export function inboxStats(records: NoticeInboxItem[]): InboxStats {
  const byCategory: Record<InboxCategoryKey, number> = {
    order: 0,
    refund: 0,
    system: 0,
    vessel: 0,
  };
  let unread = 0;
  for (const item of records) {
    byCategory[inboxCategoryOf(item)] += 1;
    if (item.readStatus === '0') {
      unread += 1;
    }
  }
  return { byCategory, total: records.length, unread };
}

/**
 * 页签计数基于**搜索后**的记录（只搜未筛）。
 *
 * 这样切换分类时页签数字不会跳变，用户也能直接看出「当前搜索下哪类还有结果」。
 */
export function inboxFilterCounts(
  records: NoticeInboxItem[],
): Record<InboxFilterKey, number> {
  const stats = inboxStats(records);
  return { all: stats.total, unread: stats.unread, ...stats.byCategory };
}

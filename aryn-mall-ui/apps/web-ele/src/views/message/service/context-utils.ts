import type { ViewChatMessage } from '../message-state';

import type { MemberContext, OrderContextItem } from '#/api/message/context';
import type { ChatMessageType } from '#/api/message/types';

import {
  chatCardLink,
  isStructuredMessage,
  parseMessageCard,
} from '../message-state';

/** 会话内可索引的结构化卡片。 */
export interface ConversationCardItem {
  createTime?: string;
  id: string;
  image?: string;
  link?: string;
  seqNo?: number;
  summary?: string;
  title: string;
  type: ChatMessageType;
}

/** 卡片类型兜底标题：payload 缺 title 时不让索引出现空白行。 */
const CARD_TYPE_LABEL: Partial<Record<ChatMessageType, string>> = {
  IMAGE: '图片',
  NOTICE_CARD: '通知',
  ORDER_CARD: '订单',
  PRODUCT_CARD: '商品',
  REFUND_CARD: '退款',
};

/** 卡片去重键：同一商品/订单/退款/通知在会话里反复发送时只保留最新一条。 */
function cardDedupeKey(
  message: ViewChatMessage,
  card: Record<string, unknown>,
): string {
  if (message.messageType === 'IMAGE') {
    return `IMAGE:${String(card.url ?? message.id)}`;
  }
  const businessId =
    card.productId ?? card.orderId ?? card.refundId ?? card.noticeId;
  return businessId === undefined
    ? `${message.messageType}:${message.id}`
    : `${message.messageType}:${String(businessId)}`;
}

/**
 * 从已加载消息里提取「卡片消息索引」。
 *
 * 纯函数：不依赖组件/请求，单测覆盖坏 payload、类型过滤、去重与排序。
 * 返回按发送时间倒序（最新在前），最多保留 20 条，避免长会话把右栏撑爆。
 */
export function extractConversationCards(
  messages: ViewChatMessage[],
): ConversationCardItem[] {
  const cards = new Map<string, ConversationCardItem>();
  for (const message of messages) {
    if (!isStructuredMessage(message)) continue;
    const card = parseMessageCard(message.payload) as Record<string, unknown>;
    const key = cardDedupeKey(message, card);
    const title =
      typeof card.title === 'string' && card.title.trim()
        ? card.title.trim()
        : (CARD_TYPE_LABEL[message.messageType] ?? '卡片');
    cards.set(key, {
      createTime: message.createTime,
      id: key,
      image:
        typeof card.image === 'string' && card.image.trim()
          ? card.image.trim()
          : undefined,
      link: chatCardLink(message.messageType, card) || undefined,
      seqNo: message.seqNo,
      summary:
        typeof card.summary === 'string' && card.summary.trim()
          ? card.summary.trim()
          : undefined,
      title,
      type: message.messageType,
    });
  }
  return [...cards.values()]
    .sort((first, second) => (second.seqNo ?? 0) - (first.seqNo ?? 0))
    .slice(0, 20);
}

/* -------------------------------------------------------------------------- */
/*  会员 / 最近订单上下文（P2-A 契约 §8）：纯函数抽离，便于单测与降级兜底         */
/* -------------------------------------------------------------------------- */

/** 最近订单展示条数上限（契约 §8.2 后端最多 3 条，前端再兜底截断）。 */
export const RECENT_ORDER_LIMIT = 3;

/** 配送方式码到中文文案（契约 §2.2：1 普通快递 / 2 上门自提）。 */
const DELIVERY_WAY_LABEL: Record<string, string> = {
  '1': '普通快递',
  '2': '上门自提',
};

/** 订单状态码到 ElTag type（视觉辅助；文案始终以 statusName/status 为准）。 */
const ORDER_STATUS_TONE: Record<
  string,
  'danger' | 'info' | 'primary' | 'success' | 'warning'
> = {
  '1': 'warning',
  '11': 'info',
  '2': 'primary',
  '3': 'primary',
  '4': 'success',
  '5': 'danger',
};

type OrderStatusTone = 'danger' | 'info' | 'primary' | 'success' | 'warning';

/**
 * 支付金额展示：`¥88.80`。
 *
 * 契约 §8.2：`payAmount` 为 null 时前端不展示金额（返回空串，模板据此隐藏）；
 * 字符串形态（兼容后端 decimal 的两种序列化）先转数字，非法值同样隐藏。
 */
export function formatOrderAmount(payAmount?: null | number | string): string {
  if (payAmount === null || payAmount === undefined) return '';
  const amount =
    typeof payAmount === 'number' ? payAmount : Number(payAmount.trim());
  if (typeof payAmount === 'string' && payAmount.trim() === '') return '';
  if (!Number.isFinite(amount)) return '';
  return `¥${amount.toFixed(2)}`;
}

/**
 * 订单状态文案：优先 `statusName`，缺失回退状态码 `status`（契约 §2.2）。
 *
 * 未知状态码时后端 `statusName` 为 null，这里回退展示状态码避免空白；
 * 两者都缺失返回空串，模板不渲染状态标签。
 */
export function orderStatusLabel(
  status?: null | string,
  statusName?: null | string,
): string {
  return statusName?.trim() || status?.trim() || '';
}

/** 订单状态对应的标签色调；未知状态回退 info（中性色）。 */
export function orderStatusTone(status?: null | string): OrderStatusTone {
  return ORDER_STATUS_TONE[status?.trim() ?? ''] ?? 'info';
}

/** 配送方式文案：1→普通快递、2→上门自提；其它/缺失返回空串（不展示）。 */
export function formatDeliveryWay(deliveryWay?: null | string): string {
  return DELIVERY_WAY_LABEL[deliveryWay?.trim() ?? ''] ?? '';
}

/**
 * 订单详情路由（管理端既有约定 `/order/detail?id=...`，见 notice-link.ts 与
 * router/routes/modules/dashboard.ts）。
 *
 * `chatCardLink` 只处理 PRODUCT_CARD，订单卡片无既有链接规则，因此订单行
 * 采用详情路由约定；订单 ID 缺失/空白时返回空串，调用方不渲染点击行为。
 */
export function orderDetailLink(orderId?: null | string): string {
  const id = orderId?.trim();
  if (!id) return '';
  return `/order/detail?id=${encodeURIComponent(id)}`;
}

/** 下单时间转时间戳；缺失/非法返回 -Infinity（排序时放最后）。 */
function orderCreateTimeValue(createTime?: null | string): number {
  const value = createTime?.trim();
  if (!value) return Number.NEGATIVE_INFINITY;
  const normalized = value.includes('T') ? value : value.replace(' ', 'T');
  const time = new Date(normalized).getTime();
  return Number.isNaN(time) ? Number.NEGATIVE_INFINITY : time;
}

/**
 * 规范化最近订单列表：过滤坏数据、按下单时间倒序、截断到上限。
 *
 * 后端已按 `create_time DESC` 排序，这里仍做一次防御性排序（缺时间/坏时间
 * 稳定排到最后），保证前端兜底截断时总取最近的 N 条；最多 3 条避免撑爆右栏。
 */
export function normalizeRecentOrders(
  orders: null | OrderContextItem[] | undefined,
  limit = RECENT_ORDER_LIMIT,
): OrderContextItem[] {
  if (!Array.isArray(orders)) return [];
  const normalized = orders.filter((item): item is OrderContextItem =>
    Boolean(item && typeof item.orderId === 'string' && item.orderId.trim()),
  );
  const sorted = [...normalized].sort((first, second) => {
    const firstTime = orderCreateTimeValue(first.createTime);
    const secondTime = orderCreateTimeValue(second.createTime);
    if (firstTime === secondTime) return 0;
    return secondTime - firstTime;
  });
  return sorted.slice(0, Math.max(0, limit));
}

/**
 * 会员昵称展示：`name` → `会员 {id}` → `会员`。
 *
 * 契约 §8.2 中原会话标题快照可能缺失，面板必须给出可读名称而不是空白；
 * 会员 ID 由模板单独展示，因此这里只负责昵称主文案。
 */
export function memberDisplayName(member?: MemberContext | null): string {
  const name = member?.name?.trim();
  if (name) return name;
  const id = member?.id?.trim();
  return id ? `会员 ${id}` : '会员';
}

/**
 * 会员头像缺失时展示的首字：优先昵称首字，其次会员 ID 首字，最后「会」。
 *
 * 字符串展开按码点迭代，emoji/代理对不会截成半个字符。
 */
export function memberInitial(member?: MemberContext | null): string {
  const source = member?.name?.trim() || member?.id?.trim() || '会员';
  return [...source][0] ?? '会';
}

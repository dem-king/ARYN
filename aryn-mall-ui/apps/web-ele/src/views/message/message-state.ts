import type { ChatMessage } from '#/api/message/types';

export type MessageSendState = 'failed' | 'sending' | 'sent';

export type ViewChatMessage = ChatMessage & {
  sendState?: MessageSendState;
};

/** 结构化消息（图片/业务卡片）负载的公共形态，字段由 C 端发送方写入。 */
export interface StructuredCard {
  [key: string]: unknown;
  image?: string;
  summary?: string;
  title?: string;
}

/** 结构化消息类型：这些消息不走纯文本渲染，交给卡片组件展示。 */
export const STRUCTURED_MESSAGE_TYPES = new Set([
  'IMAGE',
  'NOTICE_CARD',
  'ORDER_CARD',
  'PRODUCT_CARD',
  'REFUND_CARD',
]);

export function isStructuredMessage(message: ViewChatMessage) {
  return STRUCTURED_MESSAGE_TYPES.has(message.messageType);
}

/** 解析结构化消息负载；非法 JSON、数组与标量一律回退空对象。 */
export function parseMessageCard(payload?: string): StructuredCard {
  if (!payload) return {};
  try {
    const value: unknown = JSON.parse(payload);
    return value && typeof value === 'object' && !Array.isArray(value)
      ? (value as StructuredCard)
      : {};
  } catch {
    return {};
  }
}

/** 卡片在管理端的跳转：商品卡片直达商品编辑页，其余类型暂无管理端详情页。 */
export function chatCardLink(messageType: string, card: StructuredCard) {
  if (
    messageType === 'PRODUCT_CARD' &&
    typeof card.productId === 'string' &&
    card.productId
  ) {
    return `/spu/form?id=${card.productId}`;
  }
  return '';
}

export function mergeServerMessage(
  messages: ViewChatMessage[],
  serverMessage: ChatMessage,
): ViewChatMessage[] {
  const index = messages.findIndex(
    (item) => item.clientMessageId === serverMessage.clientMessageId,
  );
  const merged = [...messages];
  const next: ViewChatMessage = { ...serverMessage, sendState: 'sent' };
  if (index === -1) {
    merged.push(next);
  } else {
    merged.splice(index, 1, next);
  }
  return merged.sort((first, second) => first.seqNo - second.seqNo);
}

export function latestSequence(messages: Array<Pick<ChatMessage, 'seqNo'>>) {
  let latest = 0;
  for (const item of messages) latest = Math.max(latest, item.seqNo);
  return latest;
}

/**
 * 已落库的最大序号，作为增量补拉的 afterSeq。
 *
 * 本地乐观消息占用最大安全整数且未落库，必须排除，否则补拉永远返回空。
 */
export function latestServerSequence(messages: ViewChatMessage[]) {
  let latest = 0;
  for (const item of messages) {
    if (item.seqNo >= Number.MAX_SAFE_INTEGER) continue;
    if (item.sendState === 'failed' || item.sendState === 'sending') continue;
    latest = Math.max(latest, item.seqNo);
  }
  return latest;
}

/** 合并增量补拉结果，按消息主键去重后再交给 mergeServerMessage 排序。 */
export function mergeCursorMessages(
  messages: ViewChatMessage[],
  incoming: ChatMessage[],
): ViewChatMessage[] {
  const known = new Set(messages.map((item) => item.id));
  let result = messages;
  for (const item of incoming) {
    if (known.has(item.id)) continue;
    known.add(item.id);
    result = mergeServerMessage(result, item);
  }
  return result;
}

export function markMessageFailed(
  messages: ViewChatMessage[],
  clientMessageId: string,
): ViewChatMessage[] {
  return messages.map((item) =>
    item.clientMessageId === clientMessageId
      ? { ...item, sendState: 'failed' }
      : item,
  );
}

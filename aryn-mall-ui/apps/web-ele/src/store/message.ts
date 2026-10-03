import type { NotificationItem } from '@vben/layouts';

import type { ConversationAttention } from '#/api/message/types';

import { computed, onScopeDispose, ref } from 'vue';

import { useAppConfig } from '@vben/hooks';
import { useAccessStore } from '@vben/stores';

import { defineStore } from 'pinia';

import { parseOpenBoot, rewriteBootUrl } from '#/api/boot-url';
import { getConversationAttention } from '#/api/message/conversation';
import {
  getStaffNoticeInbox,
  getStaffNoticeUnreadCount,
  markAllStaffNoticesRead,
  markStaffNoticeRead,
} from '#/api/message/notice';

export interface MessagePushSignal {
  conversationId?: string;
  eventType: string;
  messageId?: string;
  seqNo?: number;
}

/** 会话相关的推送事件，收到后需要按会话补拉。 */
const CONVERSATION_EVENT_TYPES = new Set([
  'CONVERSATION_MESSAGE',
  'CONVERSATION_QUEUED',
]);

/**
 * 解析服务端下发的轻量推送事件。
 *
 * 事件只表示“数据已变化”，正文仍由 HTTP 游标补拉；非 JSON 或缺少事件类型时忽略。
 */
export function parsePushSignal(raw: unknown): MessagePushSignal | undefined {
  if (typeof raw !== 'string' || !raw.startsWith('{')) return undefined;
  let payload: unknown;
  try {
    payload = JSON.parse(raw);
  } catch {
    return undefined;
  }
  if (!payload || typeof payload !== 'object') return undefined;
  const event = payload as Record<string, unknown>;
  if (typeof event.eventType !== 'string') return undefined;
  return {
    conversationId:
      typeof event.conversationId === 'string'
        ? event.conversationId
        : undefined,
    eventType: event.eventType,
    messageId:
      typeof event.messageId === 'string' ? event.messageId : undefined,
    seqNo: typeof event.seqNo === 'number' ? event.seqNo : undefined,
  };
}

export function isConversationPush(signal?: MessagePushSignal) {
  return (
    !!signal?.conversationId && CONVERSATION_EVENT_TYPES.has(signal.eventType)
  );
}

/**
 * 拼装工作人员实时通道地址。
 *
 * 接口前缀（开发与生产的 `/api`）必须保留：去掉后既不被开发代理转发，
 * 也匹配不到生产 nginx 与网关的转发规则。
 */
export function buildStaffWebSocketUrl(options: {
  apiUrl?: string;
  openBoot?: boolean | string;
  origin: string;
  token?: null | string;
}) {
  const base = new URL(options.apiUrl || '/', options.origin);
  base.protocol = base.protocol === 'https:' ? 'wss:' : 'ws:';
  const path =
    rewriteBootUrl('/message/ws/staff', parseOpenBoot(options.openBoot)) ??
    '/message/ws/staff';
  base.pathname = `${base.pathname.replace(/\/+$/, '')}${path}`;
  if (options.token) base.searchParams.set('satoken', options.token);
  return base.toString();
}

export const useMessageStore = defineStore('message', () => {
  const unreadCount = ref(0);
  const notifications = ref<NotificationItem[]>([]);
  const conversationUnread = ref(0);
  const socketState = ref<'closed' | 'connecting' | 'open'>('closed');
  const lastConversationPush = ref<MessagePushSignal>();
  /**
   * 铃铛点击后要定位的会话。
   *
   * 走 store 而不是路由 query：工作台的标签页 key 取自 fullPath，
   * 用 query 会让每个会话都开一个新标签页。
   */
  const pendingConversationId = ref<string>();
  let socket: null | WebSocket = null;
  let reconnectTimer: ReturnType<typeof setTimeout> | undefined;
  let shouldReconnect = false;
  let sessionGeneration = 0;

  const showDot = computed(() => unreadCount.value > 0);

  /** 铃铛角标 = 站内信未读 + 客服会话未读（含共享池待领取）。 */
  const totalUnread = computed(
    () => unreadCount.value + conversationUnread.value,
  );

  /**
   * 会话提醒：未读的本人会话在前，共享池待领取在后。
   *
   * 待领取会话对坐席是不可读的（还没有参与者身份），因此单独标注状态，
   * 点击后进入工作台的“待领取”页签而不是直接打开会话。
   */
  function conversationNotifications(
    attention: ConversationAttention,
  ): NotificationItem[] {
    return attention.conversations.map((item) => {
      const waiting = item.status === 'WAITING';
      const name =
        item.conversationType === 'STAFF_DIRECT'
          ? '工作人员私信'
          : `会员 ${item.customerId || ''}`.trim();
      return {
        avatar: '',
        avatarText: item.conversationType === 'STAFF_DIRECT' ? '内' : '客',
        conversationId: item.id,
        date: item.lastMessageTime || '',
        isRead: !waiting && !item.unreadCount,
        kind: 'conversation',
        message: item.lastMessageSummary || '暂无新消息',
        tag: waiting ? '待领取' : undefined,
        title: name,
        unreadCount: waiting ? 0 : item.unreadCount,
      };
    });
  }

  async function refreshNotifications() {
    const requestGeneration = sessionGeneration;
    const [count, page, attention] = await Promise.all([
      getStaffNoticeUnreadCount(),
      getStaffNoticeInbox({ limit: 6 }),
      // 会话提醒失败不能拖垮站内信：非坐席账号调用该接口没有意义，
      // 这里降级为“没有会话提醒”，避免整个铃铛空白。
      getConversationAttention({ limit: 10 }).catch(() => undefined),
    ]);
    if (requestGeneration !== sessionGeneration) return;
    unreadCount.value = count;
    const notices = page.records.map((item) => ({
      avatar: '/favicon.ico',
      date: item.publishTime || item.receivedTime,
      isRead: item.readStatus === '1',
      kind: 'notice' as const,
      message: item.summary || item.content,
      title: item.title,
      ...({ messageId: item.messageId } as Record<string, string>),
    }));
    conversationUnread.value = attention
      ? attention.unreadConversations + attention.waitingTotal
      : 0;
    notifications.value = attention
      ? [...conversationNotifications(attention), ...notices]
      : notices;
  }

  async function markRead(item: NotificationItem) {
    const messageId = (item as NotificationItem & { messageId?: string })
      .messageId;
    if (!messageId || item.isRead) return;
    const requestGeneration = sessionGeneration;
    await markStaffNoticeRead(messageId);
    if (requestGeneration !== sessionGeneration) return;
    item.isRead = true;
    unreadCount.value = Math.max(0, unreadCount.value - 1);
  }

  /**
   * 全部标记已读只覆盖站内信。
   *
   * 会话未读由工作台里的已读游标驱动，在铃铛里“一键已读”等于假装读过，
   * 会让坐席漏掉消息，因此这里保留会话未读计数。
   */
  async function markAllRead() {
    const requestGeneration = sessionGeneration;
    await markAllStaffNoticesRead();
    if (requestGeneration !== sessionGeneration) return;
    notifications.value.forEach((item) => {
      if (item.kind !== 'conversation') item.isRead = true;
    });
    unreadCount.value = 0;
  }

  function websocketUrl() {
    // 与 HTTP 请求共用同一份运行时配置，避免两者指向不同地址。
    const { apiURL } = useAppConfig(import.meta.env, import.meta.env.PROD);
    return buildStaffWebSocketUrl({
      apiUrl: apiURL,
      openBoot: import.meta.env.VITE_OPEN_BOOT,
      origin: window.location.origin,
      token: useAccessStore().accessToken,
    });
  }

  function connect() {
    if (
      socket?.readyState === WebSocket.OPEN ||
      socketState.value === 'connecting'
    )
      return;
    shouldReconnect = true;
    socketState.value = 'connecting';
    const currentSocket = new WebSocket(websocketUrl());
    socket = currentSocket;
    currentSocket.addEventListener('open', () => {
      if (socket !== currentSocket) return;
      socketState.value = 'open';
    });
    currentSocket.addEventListener('message', (event) => {
      if (socket !== currentSocket) return;
      void refreshNotifications();
      const signal = parsePushSignal((event as MessageEvent).data);
      if (isConversationPush(signal)) lastConversationPush.value = signal;
    });
    currentSocket.addEventListener('close', () => {
      if (socket !== currentSocket) return;
      socket = null;
      socketState.value = 'closed';
      if (shouldReconnect) {
        reconnectTimer = setTimeout(() => {
          reconnectTimer = undefined;
          connect();
        }, 3000);
      }
    });
    currentSocket.addEventListener('error', () => currentSocket.close());
  }

  function disconnect() {
    shouldReconnect = false;
    if (reconnectTimer) {
      clearTimeout(reconnectTimer);
      reconnectTimer = undefined;
    }
    const currentSocket = socket;
    socket = null;
    currentSocket?.close();
    socketState.value = 'closed';
  }

  /** 请求工作台定位到指定会话；工作台消费后调用 clearPendingConversation。 */
  function requestConversation(conversationId: string) {
    pendingConversationId.value = conversationId;
  }

  function clearPendingConversation() {
    pendingConversationId.value = undefined;
  }

  function $reset() {
    sessionGeneration += 1;
    disconnect();
    unreadCount.value = 0;
    conversationUnread.value = 0;
    notifications.value = [];
    lastConversationPush.value = undefined;
    pendingConversationId.value = undefined;
  }

  onScopeDispose(disconnect);

  return {
    $reset,
    clearPendingConversation,
    connect,
    conversationUnread,
    disconnect,
    lastConversationPush,
    markAllRead,
    markRead,
    notifications,
    pendingConversationId,
    refreshNotifications,
    requestConversation,
    showDot,
    socketState,
    totalUnread,
    unreadCount,
  };
});

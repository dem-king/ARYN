import type { NotificationItem } from '@vben/layouts';

import { computed, onScopeDispose, ref } from 'vue';

import { useAppConfig } from '@vben/hooks';
import { useAccessStore } from '@vben/stores';

import { defineStore } from 'pinia';

import { parseOpenBoot, rewriteBootUrl } from '#/api/boot-url';
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
  const socketState = ref<'closed' | 'connecting' | 'open'>('closed');
  const lastConversationPush = ref<MessagePushSignal>();
  let socket: null | WebSocket = null;
  let reconnectTimer: ReturnType<typeof setTimeout> | undefined;
  let shouldReconnect = false;
  let sessionGeneration = 0;

  const showDot = computed(() => unreadCount.value > 0);

  async function refreshNotifications() {
    const requestGeneration = sessionGeneration;
    const [count, page] = await Promise.all([
      getStaffNoticeUnreadCount(),
      getStaffNoticeInbox({ limit: 6 }),
    ]);
    if (requestGeneration !== sessionGeneration) return;
    unreadCount.value = count;
    notifications.value = page.records.map((item) => ({
      avatar: '/favicon.ico',
      date: item.publishTime || item.receivedTime,
      isRead: item.readStatus === '1',
      message: item.summary || item.content,
      title: item.title,
      ...({ messageId: item.messageId } as Record<string, string>),
    }));
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

  async function markAllRead() {
    const requestGeneration = sessionGeneration;
    await markAllStaffNoticesRead();
    if (requestGeneration !== sessionGeneration) return;
    notifications.value.forEach((item) => (item.isRead = true));
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

  function $reset() {
    sessionGeneration += 1;
    disconnect();
    unreadCount.value = 0;
    notifications.value = [];
    lastConversationPush.value = undefined;
  }

  onScopeDispose(disconnect);

  return {
    $reset,
    connect,
    disconnect,
    lastConversationPush,
    markAllRead,
    markRead,
    notifications,
    refreshNotifications,
    showDot,
    socketState,
    unreadCount,
  };
});

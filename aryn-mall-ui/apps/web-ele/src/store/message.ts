import type { NotificationItem } from '@vben/layouts';

import { computed, onScopeDispose, ref } from 'vue';

import { useAccessStore } from '@vben/stores';

import { defineStore } from 'pinia';

import { parseOpenBoot, rewriteBootUrl } from '#/api/boot-url';
import {
  getStaffNoticeInbox,
  getStaffNoticeUnreadCount,
  markAllStaffNoticesRead,
  markStaffNoticeRead,
} from '#/api/message/notice';

export const useMessageStore = defineStore('message', () => {
  const unreadCount = ref(0);
  const notifications = ref<NotificationItem[]>([]);
  const socketState = ref<'closed' | 'connecting' | 'open'>('closed');
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
    const apiBase = new URL(
      import.meta.env.VITE_GLOB_API_URL || '/',
      window.location.origin,
    );
    apiBase.protocol = apiBase.protocol === 'https:' ? 'wss:' : 'ws:';
    const openBoot = parseOpenBoot(import.meta.env.VITE_OPEN_BOOT);
    apiBase.pathname =
      rewriteBootUrl('/message/ws/staff', openBoot) ?? '/message/ws/staff';
    const token = useAccessStore().accessToken;
    if (token) apiBase.searchParams.set('satoken', token);
    return apiBase.toString();
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
    currentSocket.addEventListener('message', () => {
      if (socket !== currentSocket) return;
      void refreshNotifications();
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
  }

  onScopeDispose(disconnect);

  return {
    $reset,
    connect,
    disconnect,
    markAllRead,
    markRead,
    notifications,
    refreshNotifications,
    showDot,
    socketState,
    unreadCount,
  };
});

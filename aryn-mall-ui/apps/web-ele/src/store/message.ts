import type { NotificationItem } from '@vben/layouts';

import { computed, onScopeDispose, ref } from 'vue';

import { useAccessStore } from '@vben/stores';

import { defineStore } from 'pinia';

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

  const showDot = computed(() => unreadCount.value > 0);

  async function refreshNotifications() {
    const [count, page] = await Promise.all([
      getStaffNoticeUnreadCount(),
      getStaffNoticeInbox({ limit: 6 }),
    ]);
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
    await markStaffNoticeRead(messageId);
    item.isRead = true;
    unreadCount.value = Math.max(0, unreadCount.value - 1);
  }

  async function markAllRead() {
    await markAllStaffNoticesRead();
    notifications.value.forEach((item) => (item.isRead = true));
    unreadCount.value = 0;
  }

  function websocketUrl() {
    const apiBase = new URL(
      import.meta.env.VITE_GLOB_API_URL || '/',
      window.location.origin,
    );
    apiBase.protocol = apiBase.protocol === 'https:' ? 'wss:' : 'ws:';
    const openBoot = JSON.parse(import.meta.env.VITE_OPEN_BOOT || 'false');
    apiBase.pathname = openBoot ? '/boot/ws/staff' : '/message/ws/staff';
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
    socketState.value = 'connecting';
    socket = new WebSocket(websocketUrl());
    socket.addEventListener('open', () => {
      socketState.value = 'open';
    });
    socket.addEventListener('message', () => {
      void refreshNotifications();
    });
    socket.addEventListener('close', () => {
      socketState.value = 'closed';
      reconnectTimer = setTimeout(connect, 3000);
    });
    socket.addEventListener('error', () => socket?.close());
  }

  function disconnect() {
    if (reconnectTimer) clearTimeout(reconnectTimer);
    socket?.close();
    socket = null;
    socketState.value = 'closed';
  }

  onScopeDispose(disconnect);

  return {
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

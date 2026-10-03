import type { NotificationItem } from '@vben/layouts';

import { createPinia, setActivePinia } from 'pinia';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { getConversationAttention } from '#/api/message/conversation';
import {
  getStaffNoticeInbox,
  getStaffNoticeUnreadCount,
  markAllStaffNoticesRead,
  markStaffNoticeRead,
} from '#/api/message/notice';

import { useMessageStore } from './message';

vi.mock('#/api/message/notice', () => ({
  getStaffNoticeInbox: vi.fn(),
  getStaffNoticeUnreadCount: vi.fn(),
  markAllStaffNoticesRead: vi.fn(),
  markStaffNoticeRead: vi.fn(),
}));

vi.mock('#/api/message/conversation', () => ({
  getConversationAttention: vi.fn(),
}));

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((promiseResolve) => {
    resolve = promiseResolve;
  });
  return { promise, resolve };
}

describe('message store', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // 真实接口总是返回 Promise；用例未显式桩时补一个空提醒，避免解构时炸掉。
    vi.mocked(getConversationAttention).mockResolvedValue({
      conversations: [],
      unreadConversations: 0,
      waitingTotal: 0,
    });
    setActivePinia(createPinia());
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('resets notification state during logout', () => {
    const store = useMessageStore();
    store.unreadCount = 3;
    store.socketState = 'open';

    expect(() => store.$reset()).not.toThrow();
    expect(store.unreadCount).toBe(0);
    expect(store.notifications).toEqual([]);
    expect(store.socketState).toBe('closed');
  });

  it('ignores notification responses completed after logout', async () => {
    const unreadRequest = deferred<number>();
    const inboxRequest = deferred<{
      hasMore: boolean;
      records: Array<{
        category: string;
        content: string;
        messageId: string;
        priority: string;
        publishTime: string;
        readStatus: '0';
        receivedTime: string;
        recipientRecordId: string;
        title: string;
      }>;
    }>();
    vi.mocked(getStaffNoticeUnreadCount).mockReturnValueOnce(
      unreadRequest.promise,
    );
    vi.mocked(getStaffNoticeInbox).mockReturnValueOnce(inboxRequest.promise);
    const store = useMessageStore();

    const refreshPromise = store.refreshNotifications();
    store.$reset();
    unreadRequest.resolve(4);
    inboxRequest.resolve({
      hasMore: false,
      records: [
        {
          category: 'SYSTEM',
          content: '旧用户通知',
          messageId: 'notice-1',
          priority: 'NORMAL',
          publishTime: '2026-07-22 17:00:00',
          readStatus: '0',
          receivedTime: '2026-07-22 17:00:00',
          recipientRecordId: 'recipient-1',
          title: '旧通知',
        },
      ],
    });
    await refreshPromise;

    expect(store.unreadCount).toBe(0);
    expect(store.notifications).toEqual([]);
  });

  it('ignores a mark-read response completed after logout', async () => {
    const markReadRequest = deferred<unknown>();
    vi.mocked(markStaffNoticeRead).mockReturnValueOnce(markReadRequest.promise);
    const store = useMessageStore();
    const item: NotificationItem & { messageId: string } = {
      avatar: '/favicon.ico',
      date: '2026-07-22 17:00:00',
      isRead: false,
      message: '旧用户通知',
      messageId: 'notice-1',
      title: '旧通知',
    };
    store.unreadCount = 2;

    const markReadPromise = store.markRead(item);
    store.$reset();
    store.unreadCount = 5;
    markReadRequest.resolve(undefined);
    await markReadPromise;

    expect(item.isRead).toBe(false);
    expect(store.unreadCount).toBe(5);
  });

  it('ignores a mark-all-read response completed after logout', async () => {
    const markAllReadRequest = deferred<unknown>();
    vi.mocked(markAllStaffNoticesRead).mockReturnValueOnce(
      markAllReadRequest.promise,
    );
    const store = useMessageStore();
    const currentItem: NotificationItem = {
      avatar: '/favicon.ico',
      date: '2026-07-22 17:10:00',
      isRead: false,
      message: '新用户通知',
      title: '新通知',
    };

    const markAllReadPromise = store.markAllRead();
    store.$reset();
    store.notifications = [currentItem];
    store.unreadCount = 3;
    markAllReadRequest.resolve(undefined);
    await markAllReadPromise;

    expect(currentItem.isRead).toBe(false);
    expect(store.unreadCount).toBe(3);
  });

  it('merges conversation attention into the bell count and list', async () => {
    vi.mocked(getStaffNoticeUnreadCount).mockResolvedValueOnce(2);
    vi.mocked(getStaffNoticeInbox).mockResolvedValueOnce({
      hasMore: false,
      records: [
        {
          category: 'SYSTEM',
          content: '站内信内容',
          messageId: 'notice-1',
          priority: 'NORMAL',
          publishTime: '2026-07-22 17:00:00',
          readStatus: '1',
          receivedTime: '2026-07-22 17:00:00',
          recipientRecordId: 'recipient-1',
          title: '站内信',
        },
      ],
    });
    vi.mocked(getConversationAttention).mockResolvedValueOnce({
      conversations: [
        {
          conversationType: 'CUSTOMER_SERVICE',
          customerId: 'member-9',
          id: 'conversation-1',
          lastMessageSummary: '在吗',
          lastMessageTime: '2026-09-29 18:49:25',
          lastReadSeq: 5,
          lastSeq: 7,
          queueCode: 'DEFAULT',
          status: 'ACTIVE',
          unreadCount: 2,
        },
        {
          conversationType: 'CUSTOMER_SERVICE',
          customerId: 'member-8',
          id: 'conversation-2',
          lastMessageSummary: '排队中',
          lastReadSeq: 0,
          lastSeq: 1,
          queueCode: 'DEFAULT',
          status: 'WAITING',
          unreadCount: 0,
        },
      ],
      unreadConversations: 1,
      waitingTotal: 1,
    });
    const store = useMessageStore();

    await store.refreshNotifications();

    // 角标 = 站内信 2 + 未读会话 1 + 待领取 1
    expect(store.totalUnread).toBe(4);
    expect(store.conversationUnread).toBe(2);
    // 会话在前、站内信在后，且待领取项带状态标记
    expect(store.notifications.map((item) => item.kind)).toEqual([
      'conversation',
      'conversation',
      'notice',
    ]);
    expect(store.notifications[0]).toMatchObject({
      conversationId: 'conversation-1',
      isRead: false,
      unreadCount: 2,
    });
    expect(store.notifications[1]).toMatchObject({
      conversationId: 'conversation-2',
      tag: '待领取',
    });
  });

  it('keeps conversation unread when marking all notices read', async () => {
    vi.mocked(markAllStaffNoticesRead).mockResolvedValueOnce(undefined);
    const store = useMessageStore();
    store.conversationUnread = 3;
    store.unreadCount = 2;
    store.notifications = [
      {
        avatar: '',
        conversationId: 'conversation-1',
        date: '',
        isRead: false,
        kind: 'conversation',
        message: '在吗',
        title: '会员 member-9',
      },
      {
        avatar: '/favicon.ico',
        date: '2026-07-22 17:00:00',
        isRead: false,
        kind: 'notice',
        message: '站内信内容',
        title: '站内信',
      },
    ];

    await store.markAllRead();

    // 一键已读只作用于站内信，会话未读必须保留，否则坐席会漏消息
    expect(store.unreadCount).toBe(0);
    expect(store.conversationUnread).toBe(3);
    expect(store.totalUnread).toBe(3);
    expect(store.notifications[0]?.isRead).toBe(false);
    expect(store.notifications[1]?.isRead).toBe(true);
  });

  it('falls back to notices only when conversation attention fails', async () => {
    vi.mocked(getStaffNoticeUnreadCount).mockResolvedValueOnce(1);
    vi.mocked(getStaffNoticeInbox).mockResolvedValueOnce({
      hasMore: false,
      records: [
        {
          category: 'SYSTEM',
          content: '站内信内容',
          messageId: 'notice-1',
          priority: 'NORMAL',
          publishTime: '2026-07-22 17:00:00',
          readStatus: '0',
          receivedTime: '2026-07-22 17:00:00',
          recipientRecordId: 'recipient-1',
          title: '站内信',
        },
      ],
    });
    vi.mocked(getConversationAttention).mockRejectedValueOnce(
      new Error('no agent'),
    );
    const store = useMessageStore();

    await store.refreshNotifications();

    expect(store.unreadCount).toBe(1);
    expect(store.conversationUnread).toBe(0);
    expect(store.notifications.map((item) => item.kind)).toEqual(['notice']);
  });

  it('does not reconnect after logout resets the store', () => {
    vi.useFakeTimers();
    const sockets: object[] = [];

    class FakeWebSocket {
      static readonly OPEN = 1;
      readonly readyState = 0;
      private readonly listeners = new Map<string, Array<() => void>>();

      constructor() {
        sockets.push(this);
      }

      addEventListener(type: string, listener: () => void) {
        const listeners = this.listeners.get(type) ?? [];
        listeners.push(listener);
        this.listeners.set(type, listeners);
      }

      close() {
        this.listeners.get('close')?.forEach((listener) => listener());
      }
    }

    vi.stubGlobal('WebSocket', FakeWebSocket);
    const store = useMessageStore();
    store.connect();

    expect(() => store.$reset()).not.toThrow();
    vi.advanceTimersByTime(3000);

    expect(sockets).toHaveLength(1);
  });

  it('ignores delayed events from a socket closed during logout', () => {
    vi.useFakeTimers();
    const sockets: object[] = [];

    class FakeWebSocket {
      static readonly OPEN = 1;
      closeCalls = 0;
      readonly readyState = 0;
      private readonly listeners = new Map<string, Array<() => void>>();

      constructor() {
        sockets.push(this);
      }

      addEventListener(type: string, listener: () => void) {
        const listeners = this.listeners.get(type) ?? [];
        listeners.push(listener);
        this.listeners.set(type, listeners);
      }

      close() {
        this.closeCalls += 1;
      }

      emit(type: string) {
        this.listeners.get(type)?.forEach((listener) => listener());
      }
    }

    vi.stubGlobal('WebSocket', FakeWebSocket);
    const store = useMessageStore();
    store.connect();
    const oldSocket = sockets[0] as FakeWebSocket;
    store.$reset();

    store.connect();
    const currentSocket = sockets[1] as FakeWebSocket;
    currentSocket.emit('open');
    oldSocket.emit('close');
    oldSocket.emit('error');
    vi.advanceTimersByTime(3000);

    expect(currentSocket.closeCalls).toBe(0);
    expect(store.socketState).toBe('open');
    expect(sockets).toHaveLength(2);
  });
});

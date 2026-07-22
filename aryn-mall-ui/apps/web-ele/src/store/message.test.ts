import type { NotificationItem } from '@vben/layouts';

import { createPinia, setActivePinia } from 'pinia';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

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

import { useAccessStore } from '@vben/stores';

import { createPinia, setActivePinia } from 'pinia';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { getConversationAttention } from '#/api/message/conversation';
import {
  getStaffNoticeInbox,
  getStaffNoticeUnreadCount,
} from '#/api/message/notice';

import { useMessageStore } from './message';
import { useSessionStore } from './session';

vi.mock('#/api/message/notice', () => ({
  getStaffNoticeInbox: vi.fn(),
  getStaffNoticeUnreadCount: vi.fn(),
  markAllStaffNoticesRead: vi.fn(),
  markStaffNoticeRead: vi.fn(),
}));

vi.mock('#/api/message/conversation', () => ({
  getConversationAttention: vi.fn(),
}));

class FakeWebSocket {
  static readonly OPEN = 1;

  static sockets: FakeWebSocket[] = [];

  closeCalls = 0;

  readonly readyState = 0;
  private readonly listeners = new Map<
    string,
    Array<(event?: { data?: unknown }) => void>
  >();
  constructor() {
    FakeWebSocket.sockets.push(this);
  }

  static reset() {
    FakeWebSocket.sockets = [];
  }

  addEventListener(
    type: string,
    listener: (event?: { data?: unknown }) => void,
  ) {
    const listeners = this.listeners.get(type) ?? [];
    listeners.push(listener);
    this.listeners.set(type, listeners);
  }

  close() {
    this.closeCalls += 1;
  }

  emit(type: string, payload?: unknown) {
    const event = type === 'message' ? { data: payload } : undefined;
    this.listeners.get(type)?.forEach((listener) => listener(event));
  }
}

async function flushVueWatchers() {
  const { nextTick } = await import('vue');
  await nextTick();
}

describe('message store: session invalid boundary (二期契约 §3.2)', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    FakeWebSocket.reset();
    vi.stubGlobal('WebSocket', FakeWebSocket);
    vi.clearAllMocks();
    // 真实接口总是返回 Promise；用例未显式桩时补一个空提醒，避免解构时炸掉。
    vi.mocked(getConversationAttention).mockResolvedValue({
      conversations: [],
      unreadConversations: 0,
      waitingTotal: 0,
    });
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('does not open a websocket when the session is already invalid', async () => {
    const sessionStore = useSessionStore();
    sessionStore.invalidate({ reason: 'BE_REPLACED' });

    const store = useMessageStore();
    store.connect();

    expect(FakeWebSocket.sockets).toHaveLength(0);
    expect(store.socketState).toBe('closed');
  });

  it('disconnects the active websocket once the session becomes invalid', async () => {
    const store = useMessageStore();
    store.connect();
    expect(FakeWebSocket.sockets).toHaveLength(1);

    useSessionStore().invalidate({ reason: 'BE_REPLACED' });
    await flushVueWatchers();

    expect(FakeWebSocket.sockets[0]?.closeCalls).toBe(1);
    expect(store.socketState).toBe('closed');
  });

  it('does not schedule a reconnect after the session became invalid', async () => {
    vi.useFakeTimers();
    const store = useMessageStore();
    store.connect();
    expect(FakeWebSocket.sockets).toHaveLength(1);

    await flushVueWatchers();
    useSessionStore().invalidate({ reason: 'KICK_OUT' });
    // 失效与 close 事件同 tick 发生：无论定时器先排上还是被 disconnect 清掉，
    // 都不能再建立新连接（死 token 每 3 秒重连只会刷 401）
    FakeWebSocket.sockets[0]?.emit('close');
    await flushVueWatchers();

    vi.advanceTimersByTime(10_000);
    expect(FakeWebSocket.sockets).toHaveLength(1);
  });

  it('reconnect guard also blocks the timer path when only invalid state changed', async () => {
    vi.useFakeTimers();
    const store = useMessageStore();
    store.connect();
    await flushVueWatchers();

    useSessionStore().invalidate({ reason: 'KICK_OUT' });
    await flushVueWatchers();
    // 断开后事件晚到也不得复活连接
    FakeWebSocket.sockets[0]?.emit('close');
    vi.advanceTimersByTime(10_000);

    expect(FakeWebSocket.sockets).toHaveLength(1);
    expect(store.socketState).toBe('closed');
  });

  describe('session invalid push (三期契约 §3.1)', () => {
    function emitSessionInvalid(
      socket: FakeWebSocket,
      event: Record<string, unknown>,
    ) {
      socket.emit(
        'message',
        JSON.stringify({ eventType: 'SESSION_INVALID', ...event }),
      );
    }

    it('invalidates session, opens dialog, disconnects and skips unread refresh', () => {
      const store = useMessageStore();
      store.connect();
      const socket = FakeWebSocket.sockets[0] as FakeWebSocket;

      emitSessionInvalid(socket, {
        invalidAt: '2026-10-29T09:12:33+08:00',
        reason: 'KICK_OUT',
        subReason: 'ACCOUNT_DISABLED',
      });

      const sessionStore = useSessionStore();
      const accessStore = useAccessStore();
      expect(sessionStore.invalid).toBe(true);
      expect(sessionStore.reason).toBe('KICK_OUT');
      expect(sessionStore.subReason).toBe('ACCOUNT_DISABLED');
      expect(sessionStore.invalidAt).toBe('2026-10-29T09:12:33+08:00');
      expect(accessStore.loginExpired).toBe(true);
      expect(socket.closeCalls).toBe(1);
      expect(store.socketState).toBe('closed');
      // 失效事件不是普通消息：不再触发未读刷新
      expect(getStaffNoticeUnreadCount).not.toHaveBeenCalled();
    });

    it('keeps BE_REPLACED device info from the pushed event', () => {
      const store = useMessageStore();
      store.connect();
      const socket = FakeWebSocket.sockets[0] as FakeWebSocket;

      emitSessionInvalid(socket, {
        device: { browser: 'Chrome 120', os: 'Windows 10' },
        invalidAt: '2026-10-29T09:12:33+08:00',
        reason: 'BE_REPLACED',
      });

      const sessionStore = useSessionStore();
      expect(sessionStore.reason).toBe('BE_REPLACED');
      expect(sessionStore.device).toEqual({
        browser: 'Chrome 120',
        os: 'Windows 10',
      });
    });

    it('drops unknown subReason (falls back to generic KICK_OUT copy)', () => {
      const store = useMessageStore();
      store.connect();
      const socket = FakeWebSocket.sockets[0] as FakeWebSocket;

      emitSessionInvalid(socket, {
        reason: 'KICK_OUT',
        subReason: 'SOMETHING_NEW',
      });

      const sessionStore = useSessionStore();
      expect(sessionStore.invalid).toBe(true);
      expect(sessionStore.reason).toBe('KICK_OUT');
      expect(sessionStore.subReason).toBeNull();
    });

    it('is idempotent: duplicate pushes keep the first invalidation', () => {
      const store = useMessageStore();
      store.connect();
      const socket = FakeWebSocket.sockets[0] as FakeWebSocket;

      emitSessionInvalid(socket, {
        invalidAt: '2026-10-29T09:12:33+08:00',
        reason: 'KICK_OUT',
      });
      // 断开后同一 socket 的迟到事件直接忽略
      emitSessionInvalid(socket, { reason: 'BE_REPLACED' });

      const sessionStore = useSessionStore();
      expect(sessionStore.invalid).toBe(true);
      expect(sessionStore.reason).toBe('KICK_OUT');
      expect(sessionStore.invalidAt).toBe('2026-10-29T09:12:33+08:00');
      expect(socket.closeCalls).toBe(1);
    });

    it('keeps the original logic for non-session events', async () => {
      vi.mocked(getStaffNoticeUnreadCount).mockResolvedValue(0);
      vi.mocked(getStaffNoticeInbox).mockResolvedValue({
        hasMore: false,
        records: [],
      });
      const store = useMessageStore();
      store.connect();
      const socket = FakeWebSocket.sockets[0] as FakeWebSocket;

      socket.emit(
        'message',
        JSON.stringify({
          conversationId: 'c-1',
          eventType: 'CONVERSATION_MESSAGE',
        }),
      );

      await vi.waitFor(() =>
        expect(getStaffNoticeUnreadCount).toHaveBeenCalledTimes(1),
      );
      expect(store.lastConversationPush?.conversationId).toBe('c-1');
      expect(useSessionStore().invalid).toBe(false);
    });

    it('keeps the original logic for invalid/unknown messages', async () => {
      vi.mocked(getStaffNoticeUnreadCount).mockResolvedValue(0);
      vi.mocked(getStaffNoticeInbox).mockResolvedValue({
        hasMore: false,
        records: [],
      });
      const store = useMessageStore();
      store.connect();
      const socket = FakeWebSocket.sockets[0] as FakeWebSocket;

      socket.emit('message', 'not-json');
      socket.emit('message', JSON.stringify({ eventType: 'SOMETHING_ELSE' }));

      await vi.waitFor(() =>
        expect(getStaffNoticeUnreadCount).toHaveBeenCalledTimes(2),
      );
      expect(useSessionStore().invalid).toBe(false);
    });
  });

  describe('refreshNotifications', () => {
    it('swallows 401 so the unified session-invalid chain owns the hint', async () => {
      const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
      vi.mocked(getStaffNoticeUnreadCount).mockRejectedValue({
        code: 401,
        msg: '登录状态已失效，请重新登录',
      });
      vi.mocked(getStaffNoticeInbox).mockResolvedValue({
        hasMore: false,
        records: [],
      });
      const store = useMessageStore();

      await expect(store.refreshNotifications()).resolves.toBeUndefined();
      expect(warn).not.toHaveBeenCalled();
    });

    it('warns without throwing for non-401 failures', async () => {
      const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
      vi.mocked(getStaffNoticeUnreadCount).mockRejectedValue(
        new Error('network broken'),
      );
      vi.mocked(getStaffNoticeInbox).mockResolvedValue({
        hasMore: false,
        records: [],
      });
      const store = useMessageStore();

      await expect(store.refreshNotifications()).resolves.toBeUndefined();
      expect(warn).toHaveBeenCalledTimes(1);
      expect(String(warn.mock.calls[0]?.[0])).toContain('未读提醒刷新失败');
    });

    it('stays silent for any failure while the session is invalid', async () => {
      const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
      vi.mocked(getStaffNoticeUnreadCount).mockRejectedValue(
        new Error('network broken'),
      );
      useSessionStore().invalidate({ reason: 'BE_REPLACED' });
      const store = useMessageStore();

      await expect(store.refreshNotifications()).resolves.toBeUndefined();
      expect(warn).not.toHaveBeenCalled();
    });
  });
});

import { useAccessStore } from '@vben/stores';

import { createPinia, setActivePinia } from 'pinia';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { useSessionStore } from './session';
import {
  broadcastSessionInvalid,
  broadcastSessionRestored,
  getSessionTabId,
  handleSessionSyncMessage,
  isBroadcastChannelSupported,
  SESSION_SYNC_CHANNEL_NAME,
  SESSION_SYNC_STORAGE_KEY,
  startSessionSync,
  stopSessionSync,
} from './session-sync';

type Listener = (event: { data?: unknown }) => void;

class FakeBroadcastChannel {
  static instances: FakeBroadcastChannel[] = [];

  closed = false;

  readonly name: string;
  posts: unknown[] = [];
  private readonly listeners = new Map<string, Listener[]>();
  constructor(name: string) {
    this.name = name;
    FakeBroadcastChannel.instances.push(this);
  }

  static reset() {
    FakeBroadcastChannel.instances = [];
  }

  addEventListener(type: string, listener: Listener) {
    const listeners = this.listeners.get(type) ?? [];
    listeners.push(listener);
    this.listeners.set(type, listeners);
  }

  close() {
    this.closed = true;
  }

  emit(type: string, data: unknown) {
    this.listeners.get(type)?.forEach((listener) => listener({ data }));
  }

  postMessage(data: unknown) {
    this.posts.push(data);
  }

  removeEventListener(type: string, listener: Listener) {
    this.listeners.set(
      type,
      (this.listeners.get(type) ?? []).filter((item) => item !== listener),
    );
  }
}

const OTHER_TAB = 'other-tab';

function broadcastFromOtherTab(message: Record<string, unknown>) {
  return { ...message, at: Date.now(), from: OTHER_TAB };
}

describe('session sync (multi-tab)', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    FakeBroadcastChannel.reset();
    vi.stubGlobal('BroadcastChannel', FakeBroadcastChannel);
    localStorage.clear();
  });

  afterEach(() => {
    stopSessionSync();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('exposes a stable module-level tab id used to dedupe self messages', () => {
    const tabId = getSessionTabId();
    expect(tabId.length).toBeGreaterThan(0);
    expect(getSessionTabId()).toBe(tabId);
  });

  it('broadcasts session-invalid on channel aryn-session', () => {
    broadcastSessionInvalid({
      device: { browser: 'Chrome 120', os: 'Windows 10' },
      invalidAt: '2026-10-28T21:32:07+08:00',
      reason: 'BE_REPLACED',
      subReason: null,
    });

    const channel = FakeBroadcastChannel.instances[0];
    expect(channel?.name).toBe(SESSION_SYNC_CHANNEL_NAME);
    expect(channel?.posts).toHaveLength(1);
    expect(channel?.posts[0]).toMatchObject({
      from: getSessionTabId(),
      payload: {
        device: { browser: 'Chrome 120', os: 'Windows 10' },
        reason: 'BE_REPLACED',
        subReason: null,
      },
      type: 'session-invalid',
    });
    expect(typeof (channel?.posts[0] as { at: unknown }).at).toBe('number');
  });

  it('broadcasts session-restored', () => {
    broadcastSessionRestored();

    const channel = FakeBroadcastChannel.instances[0];
    expect(channel?.posts[0]).toMatchObject({
      from: getSessionTabId(),
      type: 'session-restored',
    });
  });

  it('ignores malformed messages and its own broadcasts', () => {
    const store = useSessionStore();
    const invalidPayload = {
      payload: { reason: 'KICK_OUT', subReason: 'ACCOUNT_DISABLED' },
      type: 'session-invalid',
    };

    for (const malformed of [
      null,
      undefined,
      'not-json',
      '{}',
      [],
      { type: 'unknown', from: OTHER_TAB },
      { from: OTHER_TAB, type: 'session-invalid' },
      { from: OTHER_TAB, type: 'session-invalid', payload: null },
      { from: '', type: 'session-restored' },
    ]) {
      expect(handleSessionSyncMessage(malformed)).toBe(false);
    }
    expect(store.invalid).toBe(false);

    // 自己发的消息必须忽略（防回环）
    expect(
      handleSessionSyncMessage({
        ...invalidPayload,
        at: Date.now(),
        from: getSessionTabId(),
      }),
    ).toBe(false);
    expect(store.invalid).toBe(false);
  });

  it('applies session-invalid received from another tab (state + dialog, no logout)', () => {
    const sessionStore = useSessionStore();
    const accessStore = useAccessStore();
    vi.spyOn(accessStore, 'setLoginExpired');

    const handled = handleSessionSyncMessage(
      broadcastFromOtherTab({
        payload: {
          detectedAt: 1_700_000_000_000,
          device: { browser: 'Chrome 120' },
          invalidAt: '2026-10-28T21:32:07+08:00',
          reason: 'KICK_OUT',
          subReason: 'ACCOUNT_DISABLED',
        },
        type: 'session-invalid',
      }),
    );

    expect(handled).toBe(true);
    expect(sessionStore.invalid).toBe(true);
    expect(sessionStore.reason).toBe('KICK_OUT');
    expect(sessionStore.subReason).toBe('ACCOUNT_DISABLED');
    expect(sessionStore.device).toEqual({ browser: 'Chrome 120' });
    expect(sessionStore.detectedAt).toBe(1_700_000_000_000);
    expect(accessStore.loginExpired).toBe(true);
    // 多标签同步只落状态 + 弹窗，不重复触发登录过期动作
    expect(accessStore.setLoginExpired).toHaveBeenCalledWith(true);
  });

  it('reloads the page when another tab reports session-restored', () => {
    const reload = vi.fn();
    const location = window.location as unknown as Record<string, unknown>;
    const originalReload = location.reload;
    location.reload = reload;

    try {
      const handled = handleSessionSyncMessage(
        broadcastFromOtherTab({ type: 'session-restored' }),
      );

      expect(handled).toBe(true);
      expect(reload).toHaveBeenCalledTimes(1);
    } finally {
      location.reload = originalReload;
    }
  });

  it('falls back to localStorage storage events when BroadcastChannel is unavailable', () => {
    vi.stubGlobal('BroadcastChannel', undefined);
    expect(isBroadcastChannelSupported()).toBe(false);

    const stop = startSessionSync();

    // 发送：写入约定的 localStorage key（storage 事件会在其它标签页触发）
    broadcastSessionInvalid({ reason: 'TOKEN_EXPIRED' });
    const raw = localStorage.getItem(SESSION_SYNC_STORAGE_KEY);
    expect(raw).toBeTruthy();
    expect(JSON.parse(raw ?? '{}')).toMatchObject({
      type: 'session-invalid',
    });

    // 接收：其它标签页写入 storage 后触发 storage 事件
    window.dispatchEvent(
      new StorageEvent('storage', {
        key: SESSION_SYNC_STORAGE_KEY,
        newValue: JSON.stringify(
          broadcastFromOtherTab({
            payload: { reason: 'TOKEN_EXPIRED' },
            type: 'session-invalid',
          }),
        ),
      }),
    );

    expect(useSessionStore().invalid).toBe(true);
    expect(useSessionStore().reason).toBe('TOKEN_EXPIRED');
    expect(useAccessStore().loginExpired).toBe(true);

    // 其它 key 的 storage 事件不影响状态
    const anotherStore = createPinia();
    setActivePinia(anotherStore);
    window.dispatchEvent(
      new StorageEvent('storage', {
        key: 'unrelated-key',
        newValue: JSON.stringify(
          broadcastFromOtherTab({ type: 'session-restored' }),
        ),
      }),
    );
    expect(useSessionStore().invalid).toBe(false);

    stop();
  });

  it('starts listening on the BroadcastChannel and handles incoming messages', () => {
    const stop = startSessionSync();
    const channel = FakeBroadcastChannel.instances[0];

    channel?.emit(
      'message',
      broadcastFromOtherTab({
        payload: { reason: 'BE_REPLACED' },
        type: 'session-invalid',
      }),
    );

    expect(useSessionStore().invalid).toBe(true);
    expect(useAccessStore().loginExpired).toBe(true);

    stop();
    expect(channel?.closed).toBe(true);
  });

  it('is idempotent when started twice and can start again after stop', () => {
    const firstStop = startSessionSync();
    const secondStop = startSessionSync();

    // 幂等：共用同一个通道实例，不会重复建连
    expect(FakeBroadcastChannel.instances).toHaveLength(1);
    expect(secondStop).toBe(firstStop);

    firstStop();
    expect(FakeBroadcastChannel.instances[0]?.closed).toBe(true);

    startSessionSync();
    expect(FakeBroadcastChannel.instances).toHaveLength(2);
  });

  it('degrades silently when storage is unavailable', () => {
    vi.stubGlobal('BroadcastChannel', undefined);
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('storage disabled');
    });

    expect(() =>
      broadcastSessionInvalid({ reason: 'BE_REPLACED' }),
    ).not.toThrow();
    expect(() => broadcastSessionRestored()).not.toThrow();
  });
});

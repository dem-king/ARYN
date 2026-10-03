import { createPinia, setActivePinia } from 'pinia';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import {
  getSelfAgent,
  heartbeatAgent,
  updateAgentPresence,
} from '#/api/message/agent';

import { useAgentPresenceStore } from './agent-presence';

vi.mock('#/api/message/agent', () => ({
  getSelfAgent: vi.fn(),
  heartbeatAgent: vi.fn(),
  updateAgentPresence: vi.fn(),
}));

function agentFixture(overrides: Partial<Record<string, string>> = {}) {
  return {
    autoAccept: '1',
    currentActiveCount: 0,
    enabled: '1',
    id: '1',
    maxActiveCount: 10,
    presenceStatus: 'ONLINE',
    staffId: '1',
    ...overrides,
  } as any;
}

async function flush() {
  // activate() 内部不等待 refresh 完成，用 0ms 定时器推进微任务队列。
  await vi.advanceTimersByTimeAsync(0);
}

describe('agent presence store', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setActivePinia(createPinia());
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('激活后按服务端状态展示，并以 30 秒间隔续约心跳', async () => {
    vi.mocked(getSelfAgent).mockResolvedValue(agentFixture());
    const store = useAgentPresenceStore();

    store.activate();
    store.activate(); // 并发激活只允许触发一次拉取
    await flush();

    expect(getSelfAgent).toHaveBeenCalledTimes(1);
    expect(store.loaded).toBe(true);
    expect(store.enabled).toBe(true);
    expect(store.presence).toBe('ONLINE');

    await vi.advanceTimersByTimeAsync(30_000);
    expect(heartbeatAgent).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(30_000);
    expect(heartbeatAgent).toHaveBeenCalledTimes(2);
  });

  it('离线状态不发心跳', async () => {
    vi.mocked(getSelfAgent).mockResolvedValue(
      agentFixture({ presenceStatus: 'OFFLINE' }),
    );
    const store = useAgentPresenceStore();
    store.activate();
    await flush();

    await vi.advanceTimersByTimeAsync(90_000);
    expect(heartbeatAgent).not.toHaveBeenCalled();
  });

  it('坐席未启用时按离线展示且不发心跳', async () => {
    vi.mocked(getSelfAgent).mockResolvedValue(agentFixture({ enabled: '0' }));
    const store = useAgentPresenceStore();
    store.activate();
    await flush();

    expect(store.enabled).toBe(false);
    expect(store.presence).toBe('OFFLINE');
    await vi.advanceTimersByTimeAsync(90_000);
    expect(heartbeatAgent).not.toHaveBeenCalled();
  });

  it('坐席未配置时静默回落离线', async () => {
    vi.mocked(getSelfAgent).mockRejectedValue(new Error('客服坐席未配置'));
    const store = useAgentPresenceStore();
    store.activate();
    await flush();

    expect(store.loaded).toBe(true);
    expect(store.presence).toBe('OFFLINE');
  });

  it('心跳失败后用当前状态重写租约（休眠唤醒/Redis 重启恢复）', async () => {
    vi.mocked(getSelfAgent).mockResolvedValue(agentFixture());
    vi.mocked(heartbeatAgent).mockRejectedValueOnce(new Error('客服未上线'));
    vi.mocked(updateAgentPresence).mockResolvedValue(agentFixture());
    const store = useAgentPresenceStore();
    store.activate();
    await flush();

    await vi.advanceTimersByTimeAsync(30_000);
    expect(updateAgentPresence).toHaveBeenCalledWith('ONLINE');
    expect(store.presence).toBe('ONLINE');
  });

  it('重写租约也失败则回落离线，不再继续心跳', async () => {
    vi.mocked(getSelfAgent).mockResolvedValue(agentFixture());
    vi.mocked(heartbeatAgent).mockRejectedValue(new Error('客服未上线'));
    vi.mocked(updateAgentPresence).mockRejectedValue(
      new Error('客服坐席未启用'),
    );
    const store = useAgentPresenceStore();
    store.activate();
    await flush();

    await vi.advanceTimersByTimeAsync(30_000);
    expect(store.presence).toBe('OFFLINE');

    const heartbeats = vi.mocked(heartbeatAgent).mock.calls.length;
    await vi.advanceTimersByTimeAsync(90_000);
    expect(vi.mocked(heartbeatAgent).mock.calls.length).toBe(heartbeats);
  });

  it('切换状态失败时回滚到之前的状态并抛出', async () => {
    vi.mocked(getSelfAgent).mockResolvedValue(agentFixture());
    vi.mocked(updateAgentPresence).mockRejectedValue(
      new Error('客服坐席未启用'),
    );
    const store = useAgentPresenceStore();
    store.activate();
    await flush();

    await expect(store.changePresence('BUSY')).rejects.toThrow();
    expect(store.presence).toBe('ONLINE');
  });

  it('坐席配置保存后同步本地状态，禁用立即回落离线', async () => {
    vi.mocked(getSelfAgent).mockResolvedValue(agentFixture());
    const store = useAgentPresenceStore();
    store.activate();
    await flush();
    expect(store.presence).toBe('ONLINE');

    store.applyAgent(agentFixture({ enabled: '0' }));
    expect(store.enabled).toBe(false);
    expect(store.presence).toBe('OFFLINE');
  });
});

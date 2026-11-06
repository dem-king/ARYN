import { createPinia, setActivePinia } from 'pinia';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import {
  getSelfAgent,
  heartbeatAgent,
  updateAgentPresence,
} from '#/api/message/agent';

import {
  AGENT_PRESENCE_CHANNEL,
  useAgentPresenceStore,
} from './agent-presence';

vi.mock('#/api/message/agent', () => ({
  getSelfAgent: vi.fn(),
  heartbeatAgent: vi.fn(),
  updateAgentPresence: vi.fn(),
}));

/**
 * BroadcastChannel 的测试替身：记录本标签页发出的消息，并允许测试模拟
 * “另一个标签页”推来的消息（调用 emit）。
 */
class FakeBroadcastChannel {
  static instances: FakeBroadcastChannel[] = [];

  closed = false;

  readonly name: string;
  posted: unknown[] = [];
  private listeners = new Set<(event: { data: unknown }) => void>();
  constructor(name: string) {
    this.name = name;
    FakeBroadcastChannel.instances.push(this);
  }

  static latest() {
    return [...FakeBroadcastChannel.instances]
      .reverse()
      .find((item) => item.name === AGENT_PRESENCE_CHANNEL && !item.closed);
  }

  addEventListener(type: string, listener: (event: { data: unknown }) => void) {
    if (type === 'message') this.listeners.add(listener);
  }

  close() {
    this.closed = true;
  }

  emit(data: unknown) {
    for (const listener of this.listeners) listener({ data });
  }

  postMessage(data: unknown) {
    this.posted.push(data);
  }

  removeEventListener(
    type: string,
    listener: (event: { data: unknown }) => void,
  ) {
    if (type === 'message') this.listeners.delete(listener);
  }
}

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

/** 取当前测试里 store 创建的广播通道（不存在直接抛错，避免用非空断言）。 */
function latestChannel() {
  const channel = FakeBroadcastChannel.latest();
  if (!channel) throw new Error('BroadcastChannel 未创建');
  return channel;
}

describe('agent presence store', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setActivePinia(createPinia());
    vi.useFakeTimers();
    FakeBroadcastChannel.instances = [];
    vi.stubGlobal('BroadcastChannel', FakeBroadcastChannel);
  });

  afterEach(() => {
    // 每个用例都会在 document/window 上挂监听，必须重置 store 摘掉，
    // 否则后续用例 dispatch 事件会触发前面所有 store 实例的刷新。
    useAgentPresenceStore().$reset();
    vi.clearAllTimers();
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('激活后按服务端状态展示，并以 30 秒间隔续约心跳', async () => {
    vi.mocked(getSelfAgent).mockResolvedValue(agentFixture());
    const store = useAgentPresenceStore();

    store.activate();
    store.activate(); // 并发激活只允许触发一次拉取
    await flush();

    expect(getSelfAgent).toHaveBeenCalledTimes(1);
    expect(store.loaded).toBe(true);
    expect(store.configured).toBe(true);
    expect(store.enabled).toBe(true);
    expect(store.presence).toBe('ONLINE');
    expect(store.serverPresence).toBe('ONLINE');

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

  it('坐席未启用时展示禁用，但不发心跳', async () => {
    vi.mocked(getSelfAgent).mockResolvedValue(agentFixture({ enabled: '0' }));
    const store = useAgentPresenceStore();
    store.activate();
    await flush();

    expect(store.configured).toBe(true);
    expect(store.enabled).toBe(false);
    expect(store.presence).toBe('OFFLINE');
    await vi.advanceTimersByTimeAsync(90_000);
    expect(heartbeatAgent).not.toHaveBeenCalled();
  });

  it('坐席未配置（self 返回 null）时 configured=false，与接口异常区分', async () => {
    vi.mocked(getSelfAgent).mockResolvedValue(null);
    const store = useAgentPresenceStore();
    store.activate();
    await flush();

    expect(store.loaded).toBe(true);
    expect(store.configured).toBe(false);
    expect(store.enabled).toBe(false);
    expect(store.presence).toBe('OFFLINE');
    expect(store.syncError).toBe(false);
  });

  it('拉取失败时标记同步异常并按离线展示', async () => {
    vi.mocked(getSelfAgent).mockRejectedValue(new Error('网络异常'));
    const store = useAgentPresenceStore();
    store.activate();
    await flush();

    expect(store.loaded).toBe(true);
    expect(store.configured).toBe(false);
    expect(store.syncError).toBe(true);
    expect(store.presence).toBe('OFFLINE');
  });

  it('心跳失败后以服务端确认状态为准（不再盲目重写租约）', async () => {
    vi.mocked(getSelfAgent)
      .mockResolvedValueOnce(agentFixture())
      .mockResolvedValueOnce(agentFixture({ presenceStatus: 'BUSY' }));
    vi.mocked(heartbeatAgent).mockRejectedValue(new Error('客服未上线'));
    vi.mocked(updateAgentPresence).mockResolvedValue(agentFixture());
    const store = useAgentPresenceStore();
    store.activate();
    await flush();

    await vi.advanceTimersByTimeAsync(30_000);

    expect(getSelfAgent).toHaveBeenCalledTimes(2);
    expect(updateAgentPresence).not.toHaveBeenCalled();
    expect(store.presence).toBe('BUSY');
    expect(store.serverPresence).toBe('BUSY');
  });

  it('心跳失败且服务端确认也失败时降级离线，不再继续心跳', async () => {
    vi.mocked(getSelfAgent)
      .mockResolvedValueOnce(agentFixture())
      .mockRejectedValueOnce(new Error('网络异常'));
    vi.mocked(heartbeatAgent).mockRejectedValue(new Error('客服未上线'));
    const store = useAgentPresenceStore();
    store.activate();
    await flush();

    await vi.advanceTimersByTimeAsync(30_000);
    expect(store.presence).toBe('OFFLINE');
    expect(store.serverPresence).toBe('OFFLINE');

    const heartbeats = vi.mocked(heartbeatAgent).mock.calls.length;
    await vi.advanceTimersByTimeAsync(90_000);
    expect(vi.mocked(heartbeatAgent).mock.calls.length).toBe(heartbeats);
  });

  it('切换状态时乐观回显并以服务端返回值校准', async () => {
    vi.mocked(getSelfAgent).mockResolvedValue(agentFixture());
    // 服务端因容量/策略把 BUSY 归一成 PAUSED：以服务端返回为准
    vi.mocked(updateAgentPresence).mockResolvedValue(
      agentFixture({ presenceStatus: 'PAUSED' }),
    );
    const store = useAgentPresenceStore();
    store.activate();
    await flush();

    const pending = store.changePresence('BUSY');
    // 请求未返回前下拉先回显用户选择
    expect(store.presence).toBe('BUSY');
    await pending;

    expect(updateAgentPresence).toHaveBeenCalledWith('BUSY');
    expect(store.presence).toBe('PAUSED');
    expect(store.serverPresence).toBe('PAUSED');
    expect(store.syncing).toBe(false);
  });

  it('切换状态失败时回滚到服务端确认值并抛出', async () => {
    vi.mocked(getSelfAgent).mockResolvedValue(agentFixture());
    vi.mocked(updateAgentPresence).mockRejectedValue(
      new Error('客服坐席未启用'),
    );
    const store = useAgentPresenceStore();
    store.activate();
    await flush();

    await expect(store.changePresence('BUSY')).rejects.toThrow();
    expect(store.presence).toBe('ONLINE');
    expect(store.serverPresence).toBe('ONLINE');
  });

  it('切换状态进行中时后台刷新不覆盖乐观状态', async () => {
    vi.mocked(getSelfAgent).mockResolvedValue(agentFixture());
    const store = useAgentPresenceStore();
    store.activate();
    await flush();

    // 切换请求悬挂未返回，此时后台（如 visibilitychange）刷新拿到旧的服务端快照
    let resolveRefresh: (agent: any) => void = () => {};
    vi.mocked(getSelfAgent).mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveRefresh = resolve;
        }) as any,
    );
    vi.mocked(updateAgentPresence).mockImplementation(
      () => new Promise(() => {}) as any,
    );
    void store.changePresence('BUSY');
    const refreshPromise = store.refresh();
    resolveRefresh(agentFixture({ presenceStatus: 'OFFLINE' }));
    await refreshPromise;

    // 旧快照不能把用户刚点的「忙碌」改回离线
    expect(store.presence).toBe('BUSY');
  });

  it('连续快速切换时旧请求失败不覆盖最新状态', async () => {
    vi.mocked(getSelfAgent).mockResolvedValue(agentFixture());
    let rejectFirst: (error: Error) => void = () => {};
    const firstRequest = new Promise((_resolve, reject) => {
      rejectFirst = reject;
    });
    vi.mocked(updateAgentPresence)
      .mockReturnValueOnce(firstRequest as any)
      .mockResolvedValueOnce(agentFixture({ presenceStatus: 'PAUSED' }));
    const store = useAgentPresenceStore();
    store.activate();
    await flush();

    const first = store.changePresence('BUSY').catch((error) => error);
    const second = store.changePresence('PAUSED');
    await vi.advanceTimersByTimeAsync(0);
    await second;

    rejectFirst(new Error('网络慢导致的旧失败'));
    const firstError = await first;

    expect(firstError).toBeInstanceOf(Error);
    // 旧请求失败不能让下拉回滚掉最新一次切换结果
    expect(store.presence).toBe('PAUSED');
    expect(store.serverPresence).toBe('PAUSED');
  });

  it('未启用坐席时忽略状态切换（不请求接口）', async () => {
    vi.mocked(getSelfAgent).mockResolvedValue(agentFixture({ enabled: '0' }));
    const store = useAgentPresenceStore();
    store.activate();
    await flush();

    await store.changePresence('ONLINE');
    expect(updateAgentPresence).not.toHaveBeenCalled();
    expect(store.presence).toBe('OFFLINE');
  });

  it('通过 BroadcastChannel 接收其它标签页的接待状态', async () => {
    vi.mocked(getSelfAgent).mockResolvedValue(agentFixture());
    const store = useAgentPresenceStore();
    store.activate();
    await flush();

    latestChannel().emit({
      configured: true,
      enabled: true,
      presence: 'BUSY',
      serverPresence: 'BUSY',
      type: 'presence',
    });

    expect(store.presence).toBe('BUSY');
    expect(store.serverPresence).toBe('BUSY');
    expect(store.loaded).toBe(true);
    expect(store.syncError).toBe(false);

    // 非法广播消息直接忽略，不影响本地状态
    latestChannel().emit({ type: 'unknown' });
    expect(store.presence).toBe('BUSY');
  });

  it('切换状态成功后向其它标签页广播', async () => {
    vi.mocked(getSelfAgent).mockResolvedValue(agentFixture());
    vi.mocked(updateAgentPresence).mockResolvedValue(
      agentFixture({ presenceStatus: 'BUSY' }),
    );
    const store = useAgentPresenceStore();
    store.activate();
    await flush();
    await store.changePresence('BUSY');

    expect(latestChannel().posted).toContainEqual({
      autoAccept: true,
      configured: true,
      currentActiveCount: 0,
      enabled: true,
      maxActiveCount: 10,
      presence: 'BUSY',
      serverPresence: 'BUSY',
      type: 'presence',
    });
  });

  it('切回标签页或网络恢复时静默刷新服务端状态', async () => {
    vi.mocked(getSelfAgent).mockResolvedValue(agentFixture());
    const store = useAgentPresenceStore();
    store.activate();
    await flush();
    expect(getSelfAgent).toHaveBeenCalledTimes(1);

    document.dispatchEvent(new Event('visibilitychange'));
    await flush();
    expect(getSelfAgent).toHaveBeenCalledTimes(2);

    window.dispatchEvent(new Event('online'));
    await flush();
    expect(getSelfAgent).toHaveBeenCalledTimes(3);
  });

  it('重置时停止心跳、断开广播并清空状态', async () => {
    vi.mocked(getSelfAgent).mockResolvedValue(agentFixture());
    const store = useAgentPresenceStore();
    store.activate();
    await flush();
    const channel = latestChannel();

    store.$reset();

    expect(store.presence).toBe('OFFLINE');
    expect(store.loaded).toBe(false);
    expect(channel.closed).toBe(true);

    await vi.advanceTimersByTimeAsync(90_000);
    expect(heartbeatAgent).not.toHaveBeenCalled();
  });

  it('坐席配置保存后同步本地状态，禁用立即回落离线并广播', async () => {
    vi.mocked(getSelfAgent).mockResolvedValue(agentFixture());
    const store = useAgentPresenceStore();
    store.activate();
    await flush();
    expect(store.presence).toBe('ONLINE');

    store.applyAgent(agentFixture({ enabled: '0' }));
    expect(store.enabled).toBe(false);
    expect(store.presence).toBe('OFFLINE');
    expect(store.configured).toBe(true);

    // 未配置（null）同样落到“未配置坐席”语义
    store.applyAgent(null);
    expect(store.configured).toBe(false);
    expect(store.enabled).toBe(false);
    expect(store.presence).toBe('OFFLINE');
  });
});

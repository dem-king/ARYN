import type { AgentInfo } from '#/api/message/agent';

import { ref } from 'vue';

import { defineStore } from 'pinia';

import {
  getSelfAgent,
  heartbeatAgent,
  updateAgentPresence,
} from '#/api/message/agent';

export type PresenceStatus = AgentInfo['presenceStatus'];

/** 心跳间隔必须小于服务端 Redis 租约的 90 秒 TTL，否则空闲期会掉线。 */
const HEARTBEAT_INTERVAL = 30_000;

/** 多标签页同步接待状态的广播频道：同一浏览器里所有管理端标签页共用。 */
export const AGENT_PRESENCE_CHANNEL = 'aryn-agent-presence';

const PRESENCE_VALUES = new Set<PresenceStatus>([
  'BUSY',
  'OFFLINE',
  'ONLINE',
  'PAUSED',
]);

/** 广播消息体：只同步展示所需的字段，不传函数/实例。 */
interface PresenceBroadcast {
  configured: boolean;
  enabled: boolean;
  presence: PresenceStatus;
  serverPresence: PresenceStatus;
  type: 'presence';
}

function normalizePresence(value: unknown): PresenceStatus {
  if (
    typeof value === 'string' &&
    PRESENCE_VALUES.has(value as PresenceStatus)
  ) {
    return value as PresenceStatus;
  }
  return 'OFFLINE';
}

/**
 * 客服坐席接待状态（应用级单例）。
 *
 * 在线状态是 Redis 里 90 秒过期的租约，此前心跳挂在工作台页面组件上，
 * 切换菜单即卸载停跳，坐席查个订单 90 秒后就掉线。这里提升到应用层：
 * 登录会话内设置过一次在线，心跳就持续续约，离开工作台不再掉线。
 *
 * 状态语义（产品口径）：
 * - 接待状态只约束「承接新工作」：自动分配、手动领取、普通转交目标都要求 ONLINE；
 * - 已分配给当前坐席的会话，离线/忙碌/暂停仍可正常收发消息；
 * - 下拉是受控更新：先乐观展示，失败回滚到**服务端确认值**（serverPresence），
 *   而不是回滚到上一次本地值，避免连续切换时回滚到过期状态；
 * - 多标签页通过 BroadcastChannel 同步，一个标签页上线/下线，其它标签页立即跟随；
 * - 心跳失败先向服务端确认真实状态，确认失败才降级 OFFLINE。
 */
export const useAgentPresenceStore = defineStore('agent-presence', () => {
  /** 下拉展示值（可能领先于服务端：乐观更新中）。 */
  const presence = ref<PresenceStatus>('OFFLINE');
  /** 服务端确认值：下拉失败时回滚到这里。 */
  const serverPresence = ref<PresenceStatus>('OFFLINE');
  /** 坐席配置是否启用（enabled='1'）。 */
  const enabled = ref(false);
  /** 是否已配置坐席：后端 /agent/self 返回 null 表示未配置。 */
  const configured = ref(false);
  /** 是否已完成首次同步（用于区分“加载中”和“未配置”）。 */
  const loaded = ref(false);
  /** 下拉提交中：用来给下拉框 loading，避免连点。 */
  const syncing = ref(false);
  /** 接待状态同步失败（网络/服务端异常），与“未配置坐席”区分展示。 */
  const syncError = ref(false);

  let heartbeatTimer: ReturnType<typeof setInterval> | undefined;
  let renewing = false;
  let refreshing: null | Promise<void> = null;
  /** 切换代际：连续切换时只允许最新一次请求落状态/回滚。 */
  let changeGeneration = 0;
  /** 正在提交的切换请求数：有切换在途时，后台刷新快照可能早于切换结果，不能覆盖。 */
  let pendingChanges = 0;
  let channel: BroadcastChannel | undefined;
  let ambientAttached = false;

  /** 广播本地接待状态到其它标签页。 */
  function broadcastPresence() {
    ensureChannel();
    if (!channel) return;
    const payload: PresenceBroadcast = {
      configured: configured.value,
      enabled: enabled.value,
      presence: presence.value,
      serverPresence: serverPresence.value,
      type: 'presence',
    };
    try {
      channel.postMessage(payload);
    } catch {
      // 广播失败只是少一次跨标签同步，不影响本标签页使用。
    }
  }

  /** 懒创建广播通道：非浏览器环境（单测/SSR）下静默跳过。 */
  function ensureChannel() {
    if (channel) return;
    if (typeof BroadcastChannel === 'undefined') return;
    try {
      channel = new BroadcastChannel(AGENT_PRESENCE_CHANNEL);
      channel.addEventListener('message', handleBroadcast);
    } catch {
      channel = undefined;
    }
  }

  /** 接收其它标签页的接待状态：只落本地展示，不再二次广播，避免来回循环。 */
  function handleBroadcast(event: MessageEvent) {
    const payload = event.data as PresenceBroadcast | undefined;
    if (!payload || payload.type !== 'presence') return;
    configured.value = payload.configured === true;
    enabled.value = payload.enabled === true;
    const next = normalizePresence(payload.presence);
    presence.value = enabled.value ? next : 'OFFLINE';
    serverPresence.value = normalizePresence(payload.serverPresence);
    syncError.value = false;
    loaded.value = true;
  }

  /**
   * 同步服务端坐席信息（拉取/保存配置/切换状态/心跳确认都走这里）。
   *
   * `agent === null` 表示未配置坐席：与“接口异常”区分，前端展示配置引导。
   */
  function applyAgent(agent: AgentInfo | null) {
    if (agent) {
      configured.value = true;
      enabled.value = agent.enabled === '1';
      const next = enabled.value
        ? normalizePresence(agent.presenceStatus)
        : 'OFFLINE';
      presence.value = next;
      serverPresence.value = next;
    } else {
      configured.value = false;
      enabled.value = false;
      presence.value = 'OFFLINE';
      serverPresence.value = 'OFFLINE';
    }
    syncError.value = false;
    loaded.value = true;
    broadcastPresence();
  }

  /** 拉取服务端坐席状态；失败时保留“未确认”语义，由 syncError 提示重试。 */
  async function refresh() {
    // 拉取期间用户可能正在切换状态：以用户操作（更新的代际）为准，
    // 旧快照不覆盖乐观展示，避免“刚点上线又被后台刷新改回离线”。
    const generation = changeGeneration;
    try {
      const agent = await getSelfAgent();
      if (generation !== changeGeneration || pendingChanges > 0) return;
      applyAgent(agent);
    } catch {
      if (generation !== changeGeneration || pendingChanges > 0) return;
      // 接口异常：按离线展示但不打断工作台使用，配置状态标记为同步失败，
      // 由页面引导用户重试（而不是误报“未配置坐席”）。
      syncError.value = true;
      presence.value = 'OFFLINE';
    } finally {
      loaded.value = true;
    }
  }

  /** 幂等激活：工作台挂载或坐席配置保存后调用，整个登录会话只起一个心跳循环。 */
  function activate() {
    startHeartbeat();
    attachAmbientListeners();
    ensureChannel();
    refreshing ??= refresh().finally(() => {
      refreshing = null;
    });
  }

  /**
   * 切换接待状态。
   *
   * 下拉是受控组件：先乐观更新 presence 让下拉立即回显，请求成功后以服务端返回值
   * 为准；失败回滚到 serverPresence（服务端确认值），连续切换时只处理最新一次请求。
   */
  async function changePresence(value: PresenceStatus) {
    // 未启用坐席不允许切换接待状态（后端会拒绝，前端提前拦截）。
    if (!enabled.value) return;
    const generation = ++changeGeneration;
    const fallback = serverPresence.value;
    presence.value = value;
    syncing.value = true;
    pendingChanges += 1;
    try {
      const agent = await updateAgentPresence(value);
      if (generation !== changeGeneration) return;
      if (agent) applyAgent(agent);
    } catch (error) {
      // 已有更新的切换请求接管展示时，旧请求的失败不再回滚。
      if (generation === changeGeneration) {
        presence.value = fallback;
      }
      throw error;
    } finally {
      pendingChanges -= 1;
      if (generation === changeGeneration) {
        syncing.value = false;
      }
    }
  }

  /**
   * 退出登录 / 重置 store 时调用：`resetAllStores()` 会遍历所有 store 调 `$reset()`，
   * 缺少它会让 Pinia 3 抛错并中断 logout 的后续清理（清态、跳登录页）。
   * 同时必须停掉心跳与监听/广播：会话已失效时继续每 30s 续约只会不断触发 401。
   */
  function $reset() {
    if (heartbeatTimer) {
      clearInterval(heartbeatTimer);
      heartbeatTimer = undefined;
    }
    detachAmbientListeners();
    if (channel) {
      channel.close();
      channel = undefined;
    }
    refreshing = null;
    renewing = false;
    changeGeneration += 1;
    pendingChanges = 0;
    presence.value = 'OFFLINE';
    serverPresence.value = 'OFFLINE';
    enabled.value = false;
    configured.value = false;
    loaded.value = false;
    syncing.value = false;
    syncError.value = false;
  }

  function startHeartbeat() {
    if (heartbeatTimer) return;
    heartbeatTimer = setInterval(() => {
      // 只有“已配置 + 已启用 + 非离线”才需要续约；已分配会话的收发不依赖心跳。
      if (!configured.value || !enabled.value || presence.value === 'OFFLINE')
        return;
      void renewPresence();
    }, HEARTBEAT_INTERVAL);
  }

  /**
   * 心跳续约。
   *
   * 失败说明租约可能已丢（休眠唤醒/Redis 重启/断网超 90 秒），先向服务端确认真实
   * 状态：服务端说离线就以服务端为准；服务端也不可达才降级 OFFLINE，等用户重试。
   */
  async function renewPresence() {
    if (renewing) return;
    renewing = true;
    try {
      await heartbeatAgent();
    } catch {
      // 心跳失败期间用户可能在切换状态：以更新时间更晚的操作为准。
      const generation = changeGeneration;
      try {
        const agent = await getSelfAgent();
        if (generation !== changeGeneration || pendingChanges > 0) return;
        applyAgent(agent);
      } catch {
        if (generation !== changeGeneration || pendingChanges > 0) return;
        // 服务端也确认不了：先降级离线，停止继续心跳，避免无意义的重试风暴。
        presence.value = 'OFFLINE';
        serverPresence.value = 'OFFLINE';
      }
    } finally {
      renewing = false;
    }
  }

  function handleVisibilityChange() {
    // 切回标签页时静默对齐服务端：可能已经因休眠/掉网丢过租约。
    if (document.visibilityState !== 'visible') return;
    void refresh();
  }

  function handleOnline() {
    // 网络恢复后立即对齐一次，避免一直停留在“离线”假象里。
    void refresh();
  }

  /** 监听切回标签页/网络恢复：页面不用自己管生命周期，离开工作台也生效。 */
  function attachAmbientListeners() {
    if (ambientAttached) return;
    if (typeof document === 'undefined' || typeof window === 'undefined')
      return;
    ambientAttached = true;
    document.addEventListener('visibilitychange', handleVisibilityChange);
    window.addEventListener('online', handleOnline);
  }

  function detachAmbientListeners() {
    if (!ambientAttached) return;
    ambientAttached = false;
    if (typeof document !== 'undefined') {
      document.removeEventListener('visibilitychange', handleVisibilityChange);
    }
    if (typeof window !== 'undefined') {
      window.removeEventListener('online', handleOnline);
    }
  }

  return {
    $reset,
    activate,
    applyAgent,
    changePresence,
    configured,
    enabled,
    loaded,
    presence,
    refresh,
    serverPresence,
    syncError,
    syncing,
  };
});

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

/**
 * 客服坐席接待状态（应用级单例）。
 *
 * 在线状态是 Redis 里 90 秒过期的租约，此前心跳挂在工作台页面组件上，
 * 切换菜单即卸载停跳，坐席查个订单 90 秒后就掉线。这里提升到应用层：
 * 登录会话内设置过一次在线，心跳就持续续约，离开工作台不再掉线。
 *
 * 心跳失败说明租约已丢（休眠唤醒、Redis 重启、断网超过 90 秒），
 * 会按当前状态重写一次；若重写也失败则回落离线，等用户重新上线。
 */
export const useAgentPresenceStore = defineStore('agent-presence', () => {
  const presence = ref<PresenceStatus>('OFFLINE');
  const enabled = ref(false);
  const loaded = ref(false);
  let heartbeatTimer: ReturnType<typeof setInterval> | undefined;
  let renewing = false;
  let refreshing: null | Promise<void> = null;

  async function refresh() {
    try {
      const agent = await getSelfAgent();
      applyAgent(agent);
    } catch {
      // 坐席未配置或接口异常：按离线展示，不打断工作台使用。
      enabled.value = false;
      presence.value = 'OFFLINE';
    } finally {
      loaded.value = true;
    }
  }

  /** 幂等激活：工作台挂载或坐席配置保存后调用，整个登录会话只起一个心跳循环。 */
  function activate() {
    startHeartbeat();
    refreshing ??= refresh().finally(() => {
      refreshing = null;
    });
  }

  async function changePresence(value: PresenceStatus) {
    const previous = presence.value;
    try {
      const agent = await updateAgentPresence(value);
      applyAgent(agent);
    } catch (error) {
      presence.value = previous;
      throw error;
    }
  }

  /** 坐席配置保存后同步本地状态（禁用坐席必须立即回落离线）。 */
  function applyAgent(agent: AgentInfo) {
    enabled.value = agent.enabled === '1';
    presence.value = enabled.value ? agent.presenceStatus : 'OFFLINE';
  }

  function startHeartbeat() {
    if (heartbeatTimer) return;
    heartbeatTimer = setInterval(() => {
      if (!enabled.value || presence.value === 'OFFLINE') return;
      void renewPresence();
    }, HEARTBEAT_INTERVAL);
  }

  async function renewPresence() {
    if (renewing) return;
    try {
      await heartbeatAgent();
    } catch {
      renewing = true;
      try {
        const agent = await updateAgentPresence(presence.value);
        presence.value = agent.presenceStatus;
      } catch {
        presence.value = 'OFFLINE';
      } finally {
        renewing = false;
      }
    }
  }

  return {
    activate,
    applyAgent,
    changePresence,
    enabled,
    loaded,
    presence,
  };
});

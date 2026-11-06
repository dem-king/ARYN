import type { Ref } from 'vue';

import type { AgentInfo } from '#/api/message/agent';
import type { Conversation } from '#/api/message/types';
import type { PresenceStatus } from '#/store/agent-presence';

import { computed } from 'vue';

import { useAgentPresenceStore } from '#/store/agent-presence';

/** 接待状态下拉选项。 */
export const PRESENCE_OPTIONS: Array<{ label: string; value: PresenceStatus }> =
  [
    { label: '在线', value: 'ONLINE' },
    { label: '忙碌', value: 'BUSY' },
    { label: '暂停', value: 'PAUSED' },
    { label: '离线', value: 'OFFLINE' },
  ];

export interface UseDeskPresenceOptions {
  active: Ref<Conversation | undefined>;
  /** 当前登录工作人员 ID：用于判断主管保存的是不是自己的坐席配置。 */
  currentUserId: () => string;
  /** 坐席配置保存后刷新会话列表（配置可能影响可领取范围）。 */
  reloadConversations: () => Promise<void>;
}

/**
 * 客服工作台接待状态域：门禁推导 / 状态提示 / 容量 / 切换动作。
 *
 * 状态事实仍在应用级 `agent-presence` store（心跳、多标签同步、失败回滚），
 * 这里只做工作台展示口径的派生与用户动作。
 */
export function useDeskPresence(options: UseDeskPresenceOptions) {
  const presenceStore = useAgentPresenceStore();

  /** 坐席状态门禁：来自 presence store 的四项事实。 */
  const gate = computed(() => ({
    configured: presenceStore.configured,
    enabled: presenceStore.enabled,
    loaded: presenceStore.loaded,
    presence: presenceStore.presence,
  }));

  /** 坐席容量：已配置坐席且上限有效时展示「在办/上限」。 */
  const presenceCapacity = computed(() => {
    if (!presenceStore.configured || presenceStore.maxActiveCount <= 0) {
      return '';
    }
    return `${presenceStore.currentActiveCount}/${presenceStore.maxActiveCount}`;
  });

  /**
   * 接待状态提示条：区分同步失败 / 未配置 / 已禁用 / 离线四种语义。
   *
   * 与旧实现相比合并到「单条上下文提示」：由页面渲染时再与会话状态横幅合并优先级。
   */
  const presenceNotice = computed<
    | undefined
    | {
        action?: 'config' | 'online' | 'retry';
        text: string;
        tone: 'muted' | 'warning';
      }
  >(() => {
    if (!presenceStore.loaded) return undefined;
    if (presenceStore.syncError) {
      return {
        action: 'retry',
        text: '接待状态同步失败，请检查网络后重试',
        tone: 'warning',
      };
    }
    if (!presenceStore.configured) {
      return {
        action: 'config',
        text: '尚未配置客服坐席，完成配置后即可上线接待',
        tone: 'warning',
      };
    }
    if (!presenceStore.enabled) {
      return {
        text: '客服坐席已禁用，请联系主管启用后再上线',
        tone: 'warning',
      };
    }
    if (presenceStore.presence === 'OFFLINE') {
      return {
        action: 'online',
        text: '离线仅暂停自动分配；已分配的会话仍可回复',
        tone: 'warning',
      };
    }
    if (presenceStore.presence === 'BUSY') {
      return {
        text: '忙碌：暂停新会话自动分配；已分配的会话仍可回复',
        tone: 'muted',
      };
    }
    if (presenceStore.presence === 'PAUSED') {
      return {
        text: '暂停：暂停新会话自动分配；已分配的会话仍可回复',
        tone: 'muted',
      };
    }
    return undefined;
  });

  /** 输入区左下角说明：离线不阻塞已分配会话回复。 */
  const composerNote = computed(() => {
    const conversation = options.active.value;
    if (!conversation) return '选择会话后可回复';
    if (conversation.status === 'WAITING') {
      return '待领取会话只读预览，领取后可回复';
    }
    if (conversation.status === 'CLOSED') {
      return '会话已关闭，仅可查看历史消息';
    }
    if (presenceStore.presence === 'OFFLINE') {
      return '当前离线：已分配的会话仍可正常回复';
    }
    return 'HTTP 可靠发送 · WebSocket 实时索引';
  });

  /** 下拉切换接待状态；失败时 store 已回滚到服务端确认值，错误由请求层提示。 */
  async function changePresence(value: PresenceStatus) {
    try {
      await presenceStore.changePresence(value);
    } catch {
      // 已由请求层提示，这里无需重复处理。
    }
  }

  function handlePresenceChange(value: PresenceStatus) {
    void changePresence(value);
  }

  /** 离线提示条的一键上线。 */
  function goOnline() {
    void changePresence('ONLINE');
  }

  function retryPresence() {
    void presenceStore.refresh();
  }

  function handleAgentSaved(agent: AgentInfo) {
    // 主管可能给其他工作人员保存配置：只刷新列表，不覆盖当前坐席本地状态。
    if (String(agent.staffId) !== options.currentUserId()) {
      void options.reloadConversations();
      return;
    }
    // 首次配置坐席时本地还没有心跳循环，先补激活再同步状态。
    presenceStore.activate();
    presenceStore.applyAgent(agent);
  }

  return {
    PRESENCE_OPTIONS,
    changePresence,
    composerNote,
    gate,
    goOnline,
    handleAgentSaved,
    handlePresenceChange,
    presenceCapacity,
    presenceNotice,
    presenceStore,
    retryPresence,
  };
}

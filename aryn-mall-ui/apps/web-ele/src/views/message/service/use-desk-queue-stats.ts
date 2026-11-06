import type { Ref } from 'vue';

import type { QueueStats } from '#/api/message/types';

import {
  computed,
  onActivated,
  onBeforeUnmount,
  onDeactivated,
  onMounted,
  ref,
} from 'vue';

import { getQueueStats } from '#/api/message/conversation';

/** 队列统计算力/实时性都不高：30s 轮询，列表刷新时另有 10s 节流的按需刷新。 */
const POLL_INTERVAL_MS = 30_000;
/** 随列表刷新触发的按需刷新最小间隔，避免一条消息一次推送就多打一个接口。 */
const MIN_REFRESH_GAP_MS = 10_000;

/**
 * 工作台队列统计（P2-A 契约 §5）。
 *
 * <ul>
 *   <li>无权限 / 接口失败：静默降级（不阻塞列表），保留上一次成功数据；</li>
 *   <li>Redis 降级时后端返回全 0：`degraded` 供 UI 显示「在线状态暂不可用」，
 *       避免把 0 在线/0 容量误读成真实状态；</li>
 *   <li>口径与 `attention.waitingTotal` 不同（后者只统计单个默认队列），
 *       铃铛数字继续用 attention，队列面板只用本 composable（契约 §5.4）。</li>
 * </ul>
 */
export function useDeskQueueStats(options: { enabled: Ref<boolean> }) {
  const queueStats = ref<QueueStats>();
  const queueStatsFailed = ref(false);
  let pollTimer: ReturnType<typeof setInterval> | undefined;
  let requestGeneration = 0;
  let lastRefreshAt = 0;

  /**
   * 是否疑似 Redis/presence 降级：有会话在等待，但全租户在线坐席与可用容量都是 0。
   * 空闲时（无等待）全 0 可能是真实状态，不提示避免误报。
   */
  const queueStatsDegraded = computed(() => {
    const total = queueStats.value?.total;
    if (!total) return false;
    return (
      total.waitingCount > 0 &&
      total.onlineAgentCount === 0 &&
      total.availableCapacity === 0
    );
  });

  function stopPolling() {
    if (pollTimer) {
      clearInterval(pollTimer);
      pollTimer = undefined;
    }
  }

  function startPolling() {
    stopPolling();
    pollTimer = setInterval(() => {
      void refresh(true);
    }, POLL_INTERVAL_MS);
  }

  /** 拉取队列统计；`force` 用于 30s 轮询，列表刷新触发的按需刷新走节流。 */
  async function refresh(force = false) {
    if (!options.enabled.value) return;
    const now = Date.now();
    if (!force && now - lastRefreshAt < MIN_REFRESH_GAP_MS) return;
    lastRefreshAt = now;
    const generation = ++requestGeneration;
    try {
      const stats = await getQueueStats();
      if (generation !== requestGeneration) return;
      queueStats.value = stats;
      queueStatsFailed.value = false;
    } catch {
      if (generation !== requestGeneration) return;
      // 失败静默：保留上一次成功数据，由 UI 决定是否展示降级提示
      queueStatsFailed.value = true;
    }
  }

  onMounted(() => {
    void refresh(true);
    startPolling();
  });

  // keep-alive 页面切回时立即对齐一次；首次挂载与 onMounted 的请求已被节流合并。
  onActivated(() => {
    void refresh();
    startPolling();
  });
  onDeactivated(stopPolling);
  onBeforeUnmount(stopPolling);

  return {
    queueStats,
    queueStatsDegraded,
    queueStatsFailed,
    refreshQueueStats: () => refresh(),
  };
}

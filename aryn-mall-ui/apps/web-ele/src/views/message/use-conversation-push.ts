import type { MessagePushSignal } from '#/store/message';

import { onMounted, watch } from 'vue';

import { useMessageStore } from '#/store/message';

/**
 * 订阅会话实时推送，触发后由调用方按游标补拉。
 *
 * 推送只是“有变化”的信号：同一批消息可能产生多个事件，这里串行合并执行，
 * 避免并发补拉互相覆盖；单次失败不抛出，下一次推送或手动操作仍会刷新。
 */
export function useConversationPush(
  refresh: (signal: MessagePushSignal) => Promise<void> | void,
) {
  const messageStore = useMessageStore();
  let running = false;
  let pending: MessagePushSignal | undefined;

  async function run(signal: MessagePushSignal) {
    if (running) {
      pending = signal;
      return;
    }
    running = true;
    try {
      await refresh(signal);
    } catch {
      // 实时刷新失败不影响页面可用性，保留当前数据等待下一次推送。
    } finally {
      running = false;
      const next = pending;
      pending = undefined;
      if (next) void run(next);
    }
  }

  onMounted(() => messageStore.connect());
  watch(
    () => messageStore.lastConversationPush,
    (signal) => {
      if (signal) void run(signal);
    },
  );
}

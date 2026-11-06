<script setup lang="ts">
import type { ViewChatMessage } from '../message-state';
import type { ReadReceipt, TimelineRenderItem } from './service-state';

import type { Conversation } from '#/api/message/types';

import { ref } from 'vue';

import { ElEmpty, ElMessage } from 'element-plus';

import MessageBubble from './message-bubble.vue';
import { isLatestOutgoingMessage } from './service-state';

/**
 * 消息时间线：滚动容器 + 加载更早 + 日期分隔/未读边界/同人聚合 + 新消息提示。
 *
 * <p>滚动容器通过 `defineExpose({ scroller })` 暴露给页面：页面的消息域组合式
 * （`useDeskMessages`）直接持有该 DOM 做 scrollToBottom 与上翻锚点，滚动/加载
 * 更早/跳到底部都通过事件交回页面，组件内不持有任何业务状态。</p>
 */
const props = defineProps<{
  conversation?: Conversation;
  /** 当前登录坐席 staffId：下发给气泡判定「本人消息可撤回」。 */
  currentUserId?: string;
  hasOlder: boolean;
  messageLoadFailed: boolean;
  messageLoading: boolean;
  messages: ViewChatMessage[];
  /** 当前时间戳（ms）：撤回 2 分钟窗随页面定时刷新。 */
  now?: number;
  olderLoading: boolean;
  receipt: ReadReceipt;
  receiptLabel: string;
  stickToBottom: boolean;
  timelineItems: TimelineRenderItem[];
  unreadWhileAway: number;
}>();

const emit = defineEmits<{
  jumpBottom: [];
  loadOlder: [];
  quote: [message: ViewChatMessage];
  recall: [message: ViewChatMessage];
  retry: [message: ViewChatMessage];
  scroll: [];
}>();

/** 时间线滚动容器：页面把该元素接到 useDeskMessages 的 timeline ref。 */
const scroller = ref<HTMLElement>();

defineExpose({ scroller });

/**
 * 点击引用块定位被引用消息：已加载则滚动 + 短暂高亮；
 * 未加载（在上翻历史里）给出明确提示，不能静默无反馈。
 */
function locateQuoted(messageId: string) {
  const container = scroller.value;
  if (!container) return;
  const target = container.querySelector<HTMLElement>(
    `[data-message-id="${messageId}"]`,
  );
  if (!target) {
    ElMessage.info('引用的消息不在当前已加载的消息中，可上翻加载更早消息');
    return;
  }
  target.scrollIntoView({ behavior: 'smooth', block: 'center' });
  target.classList.remove('quote-highlight');
  // 强制 reflow：连续点击同一条时高亮仍能重新触发
  void target.offsetWidth;
  target.classList.add('quote-highlight');
  window.setTimeout(() => target.classList.remove('quote-highlight'), 1600);
}
</script>

<template>
  <div class="timeline-wrap">
    <div
      ref="scroller"
      v-loading="props.messageLoading"
      class="timeline"
      @scroll="emit('scroll')"
    >
      <div v-if="props.hasOlder" class="older-block">
        <button
          v-if="!props.olderLoading"
          class="older-trigger"
          type="button"
          @click="emit('loadOlder')"
        >
          加载更早消息
        </button>
        <span v-else class="older-loading">正在加载更早消息…</span>
      </div>
      <div
        v-if="props.messageLoading && props.messages.length === 0"
        class="timeline-skeleton"
      >
        <span
          v-for="index in 5"
          :key="index"
          class="skeleton-bubble"
          :class="{ mine: index % 3 === 0 }"
        ></span>
      </div>
      <ElEmpty
        v-if="!props.messageLoading && props.messages.length === 0"
        class="timeline-empty"
        :description="
          props.messageLoadFailed
            ? '消息加载失败，请重新打开会话'
            : '还没有消息'
        "
        :image-size="70"
      />
      <template v-for="item in props.timelineItems" :key="item.key">
        <div v-if="item.type === 'date'" class="timeline-date">
          {{ item.label }}
        </div>
        <div v-else-if="item.type === 'unread'" class="timeline-unread">
          以下为新消息
        </div>
        <MessageBubble
          v-else-if="item.type === 'message'"
          :conversation="props.conversation"
          :current-user-id="props.currentUserId"
          :group-start="item.groupStart"
          :is-latest-outgoing="
            isLatestOutgoingMessage(props.messages, item.message)
          "
          :message="item.message"
          :now="props.now"
          :receipt="props.receipt"
          :receipt-label="props.receiptLabel"
          @locate-quoted="locateQuoted"
          @quote="emit('quote', $event)"
          @recall="emit('recall', $event)"
          @retry="emit('retry', $event)"
        />
      </template>
    </div>
    <button
      v-if="!props.stickToBottom && props.unreadWhileAway > 0"
      class="new-message-indicator"
      type="button"
      @click="emit('jumpBottom')"
    >
      {{ props.unreadWhileAway }} 条新消息 ↓
    </button>
  </div>
</template>

<style scoped>
@keyframes desk-skeleton {
  0% {
    background-position: 0 0;
  }

  100% {
    background-position: -200% 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .skeleton-bubble {
    animation: none;
  }
}

.timeline-wrap {
  position: relative;
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.timeline {
  flex: 1;
  min-height: 0;
  padding: 28px 6%;
  overflow: auto;
}

.timeline-empty {
  padding: 24px 0;
}

/* 上翻加载更早消息 */
.older-block {
  display: flex;
  justify-content: center;
  padding: 4px 0 16px;
}

.older-trigger {
  padding: 5px 14px;
  font-size: 12px;
  color: var(--desk-accent-strong);
  background: var(--desk-accent-soft);
  border: 1px solid var(--desk-accent-line);
  border-radius: 999px;
}

.older-trigger:hover {
  background: var(--desk-accent-soft-hover);
  border-color: var(--desk-accent-line-hover);
}

.older-loading {
  font-size: 12px;
  color: var(--desk-dim);
}

/* 时间线日期分隔：两侧 hairline + 居中胶囊 */
.timeline-date {
  display: flex;
  gap: 10px;
  align-items: center;
  margin: 10px 0 20px;
  font-size: 11px;
  color: var(--desk-dim);
}

.timeline-date::before,
.timeline-date::after {
  flex: 1;
  height: 1px;
  content: '';
  background: var(--desk-line-faint);
}

/* 未读边界：打开会话时标出「以下为新消息」，不再需要猜哪些没看过 */
.timeline-unread {
  display: flex;
  gap: 10px;
  align-items: center;
  margin: 8px 0 18px;
  font-size: 11px;
  color: var(--desk-accent-strong);
}

.timeline-unread::before,
.timeline-unread::after {
  flex: 1;
  height: 1px;
  content: '';
  background: var(--desk-accent-line);
}

/* 骨架屏：首屏没有消息时用占位气泡，避免整块 loading 遮罩 */
.timeline-skeleton {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding-top: 12px;
}

.skeleton-bubble {
  width: 52%;
  height: 64px;
  background: linear-gradient(
    90deg,
    var(--desk-hover),
    var(--desk-accent-soft),
    var(--desk-hover)
  );
  background-size: 200% 100%;
  border-radius: 12px;
  animation: desk-skeleton 1.4s ease infinite;
}

.skeleton-bubble.mine {
  align-self: flex-end;
  width: 44%;
}

/* 用户上翻阅读时新消息不拽回底部，改为可点击的浮层提示 */
.new-message-indicator {
  position: absolute;
  bottom: 14px;
  left: 50%;
  z-index: 2;
  padding: 6px 14px;
  font-size: 12px;
  color: var(--desk-accent-contrast);
  background: var(--desk-accent);
  border-radius: 999px;
  box-shadow: 0 8px 20px rgb(4 19 30 / 45%);
  transform: translateX(-50%);
}

.new-message-indicator:hover {
  background: var(--desk-accent-strong);
}

/* 深色工作台里的空态文字跟随工作台配色，避免浅色模式默认灰字对比不足 */
.timeline-empty :deep(.el-empty__description p) {
  color: var(--desk-muted);
}
</style>

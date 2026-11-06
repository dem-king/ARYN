<script setup lang="ts">
import type { ViewChatMessage } from '../message-state';
import type { ReadReceipt } from './service-state';

import type { Conversation } from '#/api/message/types';

import { computed } from 'vue';

import { ElMessage } from 'element-plus';

import ChatMessageCard from '../chat-message-card.vue';
import { isStructuredMessage } from '../message-state';
import {
  canQuoteMessage,
  canRecallMessage,
  chatMessageSenderLabel,
  formatDateTime,
  quotedBlockView,
} from './service-state';

/**
 * 时间线单条消息气泡。
 *
 * <p>纯展示：结构化卡片/文本、引用块、发送者标签、时间、失败重发入口、已读回执，
 * 以及批次二的「引用/撤回」操作。二次确认与接口调用由页面消息域编排：
 * 气泡只 emit `quote` / `recall` / `locateQuoted`。已撤回消息整条渲染占位文案，
 * 不展示原内容、不可复制/重发/引用（契约 §16.3）。</p>
 */
const props = defineProps<{
  conversation?: Conversation;
  /** 当前登录坐席 staffId：撤回入口只出现在自己发送的消息上。 */
  currentUserId?: string;
  /** 是否同一发送人连续消息的组首（组首才显示发送者）。 */
  groupStart: boolean;
  /** 是否是时间线里最后一条已落库的我方消息（只有它显示已读回执）。 */
  isLatestOutgoing: boolean;
  message: ViewChatMessage;
  /** 当前时间戳（ms）：撤回 2 分钟窗随页面定时刷新，超时自动隐藏按钮。 */
  now?: number;
  receipt: ReadReceipt;
  receiptLabel: string;
}>();

const emit = defineEmits<{
  locateQuoted: [messageId: string];
  quote: [message: ViewChatMessage];
  recall: [message: ViewChatMessage];
  retry: [message: ViewChatMessage];
}>();

/** 已撤回：整条消息只显示占位，不再渲染正文/图片/卡片。 */
const recalled = computed(() => props.message.recallStatus === '1');

/** 引用块展示数据（契约 §15.4：有 quotedMessageId 即渲染）。 */
const quoted = computed(() => quotedBlockView(props.message));

const canQuote = computed(
  () => !recalled.value && canQuoteMessage(props.message),
);

/** 撤回入口：本人发送 + TEXT/IMAGE + 2 分钟窗内 + 会话未关闭。 */
const canRecall = computed(() => {
  if (recalled.value) return false;
  return canRecallMessage(props.message, {
    conversationStatus: props.conversation?.status,
    now: props.now,
    userId: props.currentUserId ?? '',
  });
});

/** 结构化消息（图片/业务卡片）交给卡片组件，这里只兜底文本与系统消息。 */
function displayMessage(message: ViewChatMessage) {
  if (message.messageType === 'TEXT') return message.content;
  return message.content || '[系统消息]';
}

function metaText(message: ViewChatMessage) {
  if (message.sendState === 'sending') return '发送中…';
  if (message.sendState === 'failed') return '发送失败';
  // 不再展示内部 seqNo：客服不需要看消息序号，反而干扰阅读
  return formatDateTime(message.createTime) || message.createTime;
}

/** 复制消息文本：优先 Clipboard API，失败降级为提示（不静默吞掉）。 */
async function copyMessage(message: ViewChatMessage) {
  const text = message.content || message.payload || '';
  if (!text) {
    ElMessage.info('该消息没有可复制的文本');
    return;
  }
  try {
    await navigator.clipboard.writeText(text);
    ElMessage.success('已复制消息');
  } catch {
    ElMessage.warning('复制失败，请手动选中文本复制');
  }
}

function locateQuoted() {
  const quotedMessageId = props.message.quotedMessageId;
  if (quotedMessageId) emit('locateQuoted', quotedMessageId);
}
</script>

<template>
  <div
    class="message-row"
    :class="{
      'group-start': props.groupStart,
      mine: props.message.senderType === 'SYS_USER',
      system: props.message.senderType === 'SYSTEM',
    }"
    :data-message-id="props.message.id"
  >
    <div class="bubble">
      <small
        v-if="props.groupStart && props.message.senderType !== 'SYSTEM'"
        class="bubble-sender"
      >
        {{ chatMessageSenderLabel(props.message, props.conversation) }}
      </small>

      <!-- 引用块：被引用消息撤回后只显示占位，不展示原内容 -->
      <button
        v-if="quoted && !quoted.recalled"
        type="button"
        class="quote-block"
        title="定位到被引用的消息"
        @click="locateQuoted"
      >
        <span class="quote-block-head">
          <span v-if="quoted.senderName">{{ quoted.senderName }}</span>
          <span v-if="quoted.typeLabel" class="quote-block-type">
            {{ quoted.typeLabel }}
          </span>
        </span>
        <span class="quote-block-text">{{ quoted.text }}</span>
      </button>
      <div v-else-if="quoted?.recalled" class="quote-block recalled">
        {{ quoted.text }}
      </div>

      <p v-if="recalled" class="recalled-placeholder">消息已撤回</p>
      <ChatMessageCard
        v-else-if="isStructuredMessage(props.message)"
        :message="props.message"
      />
      <p v-else>{{ displayMessage(props.message) }}</p>

      <div class="bubble-meta">
        <time>{{ metaText(props.message) }}</time>
        <span class="bubble-meta-right">
          <button
            v-if="!recalled && props.message.sendState === 'failed'"
            class="retry-link"
            type="button"
            @click="emit('retry', props.message)"
          >
            发送失败，点击重发
          </button>
          <em
            v-else-if="
              !recalled && props.isLatestOutgoing && props.receiptLabel
            "
            class="receipt"
            :class="props.receipt"
          >
            {{ props.receiptLabel }}
          </em>
          <span v-if="!recalled" class="bubble-actions">
            <button
              v-if="props.message.messageType === 'TEXT'"
              class="bubble-action"
              type="button"
              title="复制消息"
              @click="copyMessage(props.message)"
            >
              复制
            </button>
            <button
              v-if="canQuote"
              class="bubble-action"
              type="button"
              title="引用这条消息"
              @click="emit('quote', props.message)"
            >
              引用
            </button>
            <button
              v-if="canRecall"
              class="bubble-action recall"
              type="button"
              title="撤回这条消息（2 分钟内）"
              @click="emit('recall', props.message)"
            >
              撤回
            </button>
          </span>
        </span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.message-row {
  display: flex;
  margin-bottom: 4px;
}

/* 同一发送人的连续消息收紧间距，组首拉开距离，形成自然分组 */
.message-row.group-start {
  margin-top: 12px;
  margin-bottom: 6px;
}

.message-row.system {
  justify-content: center;
  margin: 8px 0;
}

.message-row.mine {
  justify-content: flex-end;
}

.bubble {
  max-width: min(68%, 680px);
  padding: 12px 15px;
  color: var(--desk-bubble-text);
  background: var(--desk-bubble-bg);
  border: 1px solid var(--desk-bubble-line);
  border-radius: 5px 15px 15px;
}

.mine .bubble {
  color: var(--desk-mine-text);
  background: var(--desk-mine-bg);
  border-color: var(--desk-mine-line);
  border-radius: 15px 5px 15px 15px;
}

.system .bubble {
  padding: 7px 13px;
  color: var(--desk-system-text);
  background: var(--desk-system-bg);
  border: 0;
  border-radius: 20px;
}

.bubble small {
  font-size: 10px;
  color: var(--desk-bubble-dim);
}

.bubble p {
  margin: 7px 0;
  white-space: pre-wrap;
}

/* 引用块：左侧强调线 + 弱底，点击定位原消息 */
.quote-block {
  display: block;
  width: 100%;
  padding: 6px 9px;
  margin: 6px 0 2px;
  overflow: hidden;
  text-align: left;
  background: var(--desk-hover);
  border-left: 2px solid var(--desk-accent-line);
  border-radius: 4px;
}

.quote-block:hover {
  background: var(--desk-accent-soft);
}

.quote-block-head {
  display: flex;
  gap: 6px;
  align-items: center;
  font-size: 10px;
  color: var(--desk-accent-strong);
}

.quote-block-type {
  padding: 0 4px;
  font-size: 9px;
  color: var(--desk-dim);
  background: var(--desk-accent-soft);
  border-radius: 4px;
}

.quote-block-text {
  display: block;
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 11px;
  color: var(--desk-muted);
  white-space: nowrap;
}

.quote-block.recalled {
  font-size: 11px;
  color: var(--desk-faint);
  cursor: default;
}

/* 撤回占位：不展示原内容 */
.recalled-placeholder {
  margin: 7px 0;
  font-size: 12px;
  font-style: italic;
  color: var(--desk-faint);
}

.bubble-meta {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  font-size: 10px;
  color: var(--desk-faint);
}

.bubble-meta time {
  font-size: 9px;
}

.bubble-meta-right {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-left: auto;
}

.retry-link {
  padding: 0;
  font-size: 10px;
  color: var(--desk-warning);
  text-decoration: underline;
}

.receipt {
  font-size: 10px;
  font-style: normal;
  color: var(--desk-warning);
}

.receipt.read {
  color: var(--desk-accent-strong);
}

.bubble-sender {
  display: block;
}

/* 消息操作（复制/引用/撤回）：hover 或键盘聚焦时出现，避免每条气泡都堆按钮 */
.bubble-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  opacity: 0;
}

.bubble:hover .bubble-actions,
.bubble:focus-within .bubble-actions {
  opacity: 1;
}

.bubble-action {
  padding: 0;
  font-size: 10px;
  color: var(--desk-dim);
}

.bubble-action:hover {
  color: var(--desk-accent-strong);
}

.bubble-action.recall:hover {
  color: var(--desk-warning);
}

/* 点击引用块定位原消息时的高亮（由时间线添加/移除类名） */
.message-row.quote-highlight .bubble {
  box-shadow: 0 0 0 2px var(--desk-accent);
  transition: box-shadow 0.2s ease;
}
</style>

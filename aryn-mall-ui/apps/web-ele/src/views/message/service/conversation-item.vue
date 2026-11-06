<script setup lang="ts">
import type { Conversation } from '#/api/message/types';

import { computed } from 'vue';

import { ElTag } from 'element-plus';

import {
  conversationDisplayName,
  conversationTimeLabel,
  waitingDurationLabel,
} from './service-state';

/**
 * 会话列表单行（纯展示）：头像/名称/摘要/相对时间/队列与负责人标签/未读或状态徽章。
 *
 * <p>点击只 emit `select`，由页面决定打开会话（含请求代际与滚动锚点）。
 * `waitingSince` 只在 WAITING 状态展示等待时长，定时刷新由 rail 的 `now` 驱动
 * （契约 §3.1：不得用 lastMessageTime 推算）。</p>
 */
const props = defineProps<{
  /** 是否是当前打开的会话（高亮 + aria-current）。 */
  active: boolean;
  item: Conversation;
  /** 当前时间戳（ms）：由侧栏每 60s 刷新，驱动等待时长重新计算。 */
  now?: number;
  /** 待领取/全部会话视图才展示队列与负责人标签。 */
  showTags: boolean;
}>();

const emit = defineEmits<{ select: [conversation: Conversation] }>();

/** 等待时长徽标文案：仅 WAITING 且有 waitingSince 时展示。 */
const waitingLabel = computed(() => {
  if (props.item.status !== 'WAITING') return '';
  return waitingDurationLabel(props.item.waitingSince, props.now ?? Date.now());
});
</script>

<template>
  <button
    class="conversation-item"
    :class="{ active: props.active }"
    :aria-current="props.active ? 'true' : undefined"
    @click="emit('select', props.item)"
  >
    <span class="avatar">
      <img
        v-if="
          props.item.conversationType === 'CUSTOMER_SERVICE' &&
          props.item.customerAvatar
        "
        :src="props.item.customerAvatar"
        alt=""
        class="avatar-image"
      />
      <template v-else>
        {{ props.item.conversationType === 'STAFF_DIRECT' ? '内' : '客' }}
      </template>
    </span>
    <span class="copy">
      <span class="copy-top">
        <strong>{{ conversationDisplayName(props.item) }}</strong>
        <time class="copy-time">
          {{ conversationTimeLabel(props.item.lastMessageTime) }}
        </time>
      </span>
      <small>{{ props.item.lastMessageSummary || '等待新消息' }}</small>
      <span v-if="props.showTags" class="copy-tags">
        {{ props.item.queueCode || 'DEFAULT' }}
        <template v-if="props.item.assignedStaffName">
          · {{ props.item.assignedStaffName }}
        </template>
      </span>
    </span>
    <span class="conversation-badges">
      <span v-if="waitingLabel" class="waiting-time">{{ waitingLabel }}</span>
      <ElTag v-if="props.item.unreadCount" round size="small" type="danger">
        {{ props.item.unreadCount }}
      </ElTag>
      <ElTag
        v-else-if="props.item.status === 'WAITING'"
        effect="plain"
        size="small"
        type="warning"
      >
        待领取
      </ElTag>
      <ElTag
        v-else-if="props.item.status === 'CLOSED'"
        effect="plain"
        size="small"
        type="info"
      >
        已关闭
      </ElTag>
    </span>
  </button>
</template>

<style scoped>
.conversation-item {
  display: flex;
  gap: 12px;
  align-items: center;
  width: 100%;
  padding: 13px;
  text-align: left;
  border-radius: 12px;
}

.conversation-item:hover,
.conversation-item.active {
  background: var(--desk-hover);
}

.avatar {
  display: grid;
  flex: none;
  place-items: center;
  width: 38px;
  height: 38px;
  overflow: hidden;
  color: var(--desk-accent-strong);
  background: var(--desk-avatar-bg);
  border: 1px solid var(--desk-avatar-line);
  border-radius: 11px;
}

.avatar-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: inherit;
}

.copy {
  flex: 1;
  min-width: 0;
}

.copy strong,
.copy small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.copy strong {
  font-size: 13px;
  color: var(--desk-text);
}

.copy small {
  margin-top: 5px;
  font-size: 11px;
  color: var(--desk-dim);
}

.copy-top {
  display: flex;
  gap: 6px;
  align-items: baseline;
  justify-content: space-between;
}

.copy-time {
  flex: none;
  font-size: 10px;
  color: var(--desk-dim);
}

.copy-tags {
  display: block;
  margin-top: 3px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 10px;
  color: var(--desk-faint);
  white-space: nowrap;
}

.conversation-badges {
  display: flex;
  flex: none;
  flex-direction: column;
  gap: 4px;
  align-items: flex-end;
}

/* 等待时长：等宽数字，避免每秒/每分钟变化时列表行抖动 */
.waiting-time {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 10px;
  font-variant-numeric: tabular-nums;
  color: var(--desk-warning);
  white-space: nowrap;
}
</style>

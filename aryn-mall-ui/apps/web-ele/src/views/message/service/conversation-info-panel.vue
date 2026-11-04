<script setup lang="ts">
import type { ViewChatMessage } from '../message-state';

import type { Conversation } from '#/api/message/types';

import { computed } from 'vue';

import { ElTag } from 'element-plus';

import {
  conversationDisplayName,
  conversationOwnerName,
  conversationStatusLabel,
  conversationStatusTone,
  formatContextPayload,
  formatDateTime,
  latestOutgoingMessage,
  readReceipt,
} from './service-state';

/**
 * 右侧「会话信息」面板。
 *
 * 展示会员昵称/负责客服/队列/时间线关键时间与最后一条我方消息的已读回执。
 * 昵称等快照字段缺失时回退 ID（后端可能还没回填参与者快照）。
 */
const props = defineProps<{
  conversation?: Conversation;
  messages: ViewChatMessage[];
}>();

const title = computed(() => conversationDisplayName(props.conversation));
const statusLabel = computed(() =>
  conversationStatusLabel(props.conversation?.status),
);
const statusTone = computed(() =>
  conversationStatusTone(props.conversation?.status),
);
const ownerName = computed(() => conversationOwnerName(props.conversation));

const lastOutgoing = computed(() => latestOutgoingMessage(props.messages));
const receipt = computed(() =>
  readReceipt(props.conversation?.peerLastReadSeq, lastOutgoing.value),
);
const receiptLabel = computed(() => {
  if (receipt.value === 'read') return '已读';
  if (receipt.value === 'unread') return '未读';
  return '—';
});

const closedTime = computed(() =>
  formatDateTime(props.conversation?.closedTime),
);
const reopenDeadline = computed(() =>
  formatDateTime(props.conversation?.reopenDeadline),
);
const contextPayload = computed(() =>
  formatContextPayload(props.conversation?.contextPayload),
);
</script>

<template>
  <aside class="info-panel">
    <div class="panel-heading">
      <strong>会话信息</strong>
      <small>SESSION INFO</small>
    </div>

    <template v-if="conversation">
      <div class="panel-status">
        <ElTag :type="statusTone" effect="plain" round>
          {{ statusLabel }}
        </ElTag>
        <span class="receipt-line">
          最后一条我方消息
          <em :class="receipt">{{ receiptLabel }}</em>
        </span>
      </div>

      <dl class="info-grid">
        <div>
          <dt>会员昵称</dt>
          <dd>{{ title }}</dd>
        </div>
        <div>
          <dt>会员 ID</dt>
          <dd>{{ conversation.customerId || '—' }}</dd>
        </div>
        <div>
          <dt>负责客服</dt>
          <dd>{{ ownerName }}</dd>
        </div>
        <div>
          <dt>会话队列</dt>
          <dd>{{ conversation.queueCode || 'DEFAULT' }}</dd>
        </div>
        <div>
          <dt>未读消息</dt>
          <dd>{{ conversation.unreadCount || 0 }}</dd>
        </div>
        <div>
          <dt>最后消息时间</dt>
          <dd>{{ formatDateTime(conversation.lastMessageTime) || '—' }}</dd>
        </div>
        <div>
          <dt>关闭时间</dt>
          <dd>{{ closedTime || '—' }}</dd>
        </div>
        <div v-if="reopenDeadline">
          <dt>重开截止</dt>
          <dd>{{ reopenDeadline }}</dd>
        </div>
      </dl>

      <div v-if="contextPayload" class="context-block">
        <strong>会话上下文</strong>
        <pre>{{ contextPayload }}</pre>
      </div>
    </template>

    <p v-else class="panel-empty">未选择会话</p>
  </aside>
</template>

<style scoped>
.info-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
  padding: 20px 18px;
  overflow: auto;
  background: var(--desk-surface);
  border-left: 1px solid var(--desk-line-faint);
}

.panel-heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.panel-heading strong {
  font-size: 14px;
  color: var(--desk-text);
}

.panel-heading small {
  font-size: 9px;
  color: var(--desk-dim);
  letter-spacing: 0.16em;
}

.panel-status {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 14px;
  font-size: 11px;
  color: var(--desk-muted);
  border-bottom: 1px solid var(--desk-line-faint);
}

.receipt-line em {
  margin-left: 5px;
  font-style: normal;
  color: var(--desk-warning);
}

.receipt-line em.read {
  color: var(--desk-accent-strong);
}

.info-grid {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin: 0;
}

.info-grid div {
  display: flex;
  gap: 10px;
  align-items: baseline;
  justify-content: space-between;
}

.info-grid dt {
  flex: none;
  font-size: 11px;
  color: var(--desk-dim);
}

.info-grid dd {
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
  color: var(--desk-text);
  text-align: right;
  white-space: nowrap;
}

.context-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-top: 14px;
  border-top: 1px solid var(--desk-line-faint);
}

.context-block strong {
  font-size: 12px;
  color: var(--desk-muted);
}

.context-block pre {
  max-height: 220px;
  padding: 10px;
  margin: 0;
  overflow: auto;
  font-size: 11px;
  line-height: 1.6;
  color: var(--desk-bubble-text);
  word-break: break-all;
  white-space: pre-wrap;
  background: var(--desk-bubble-bg);
  border: 1px solid var(--desk-bubble-line);
  border-radius: 10px;
}

.panel-empty {
  font-size: 12px;
  color: var(--desk-dim);
}
</style>

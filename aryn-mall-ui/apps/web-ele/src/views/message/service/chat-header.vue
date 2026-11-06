<script setup lang="ts">
import type { ConversationActions } from './service-state';

import type { Conversation } from '#/api/message/types';

import { computed } from 'vue';

import { ElButton, ElTag } from 'element-plus';

import {
  conversationDisplayName,
  conversationStatusLabel,
  conversationStatusTone,
} from './service-state';

/**
 * 会话头（纯展示）：状态行/标题/负责人 + 返回列表/会话信息入口 + 会话操作按钮。
 *
 * <p>按钮可见性与可用性全部由页面的 `conversationActions` 推导后传入，
 * 点击只 emit 语义事件（领取/强制分配/转交/关闭/分配日志），业务编排在页面。</p>
 */
const props = defineProps<{
  actions: ConversationActions;
  /** 领取进行中（按钮 loading）。 */
  claiming: boolean;
  conversation: Conversation;
  layoutMode: 'compact' | 'narrow' | 'wide';
  ownerName: string;
  /** 宽屏常驻上下文面板时不再出现「会话信息」入口。 */
  showContextPanel: boolean;
}>();

const emit = defineEmits<{
  back: [];
  claim: [];
  close: [];
  forceAssign: [];
  openAssignmentLogs: [];
  openContext: [];
  transfer: [];
}>();

/** 展示文案由会话快照派生，页面只负责业务动作。 */
const title = computed(() => conversationDisplayName(props.conversation));
const statusLabel = computed(() =>
  conversationStatusLabel(props.conversation.status),
);
const statusTone = computed(() =>
  conversationStatusTone(props.conversation.status),
);
</script>

<template>
  <header>
    <div class="conversation-heading">
      <p>
        {{ statusLabel }} · {{ props.conversation.queueCode || 'DEFAULT' }}
        <template
          v-if="
            props.conversation.conversationType === 'CUSTOMER_SERVICE' &&
            props.conversation.customerId
          "
        >
          · 会员 ID {{ props.conversation.customerId }}
        </template>
      </p>
      <h1>{{ title }}</h1>
      <small class="owner-line">负责客服：{{ props.ownerName }}</small>
    </div>
    <div class="actions">
      <ElButton
        v-if="props.layoutMode === 'narrow'"
        class="ghost-trigger"
        size="small"
        @click="emit('back')"
      >
        返回列表
      </ElButton>
      <ElButton
        v-if="!props.showContextPanel"
        class="ghost-trigger"
        size="small"
        @click="emit('openContext')"
      >
        会话信息
      </ElButton>
      <ElTag :type="statusTone" effect="plain" round>
        {{ statusLabel }}
      </ElTag>
      <ElButton
        v-if="props.actions.canClaim"
        type="primary"
        :disabled="props.actions.claimDisabled"
        :loading="props.claiming"
        @click="emit('claim')"
      >
        领取
      </ElButton>
      <ElButton
        v-if="props.actions.canForceAssign"
        @click="emit('forceAssign')"
      >
        强制分配
      </ElButton>
      <ElButton v-if="props.actions.canTransfer" @click="emit('transfer')">
        转交
      </ElButton>
      <ElButton
        v-if="props.actions.canClose"
        type="danger"
        plain
        @click="emit('close')"
      >
        关闭
      </ElButton>
      <ElButton
        v-if="props.actions.canOpenAssignmentLogs"
        @click="emit('openAssignmentLogs')"
      >
        分配日志
      </ElButton>
    </div>
  </header>
</template>

<style scoped>
header {
  display: flex;
  flex: none;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
  padding: 18px 24px;
  border-bottom: 1px solid var(--desk-line-faint);
}

.conversation-heading {
  min-width: 0;
}

header p {
  font-size: 10px;
  color: var(--desk-accent);
  letter-spacing: 0.16em;
}

h1 {
  margin-top: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 18px;
  white-space: nowrap;
}

.owner-line {
  display: block;
  margin-top: 4px;
  font-size: 11px;
  color: var(--desk-dim);
}

.actions {
  display: flex;
  flex: none;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  justify-content: flex-end;
}

.ghost-trigger {
  flex: none;
  color: var(--desk-muted);
  background: transparent;
  border-color: var(--desk-line);
}

.ghost-trigger:hover {
  color: var(--desk-accent-contrast);
  background: var(--desk-accent-soft-hover);
  border-color: var(--desk-accent-line-hover);
}

@media (max-width: 900px) {
  .actions {
    gap: 4px;
  }
}
</style>

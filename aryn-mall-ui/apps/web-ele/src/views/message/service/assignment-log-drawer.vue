<script setup lang="ts">
import type { ConversationAssignmentLog } from '#/api/message/types';

import { ref } from 'vue';

import { ElDrawer, ElEmpty, ElTable, ElTableColumn, ElTag } from 'element-plus';

import { getAssignmentLogs } from '#/api/message/conversation';

import { formatDateTime } from './service-state';

/** 分配动作的展示文案：后端 actionType 为枚举值，前端做中文映射。 */
const ACTION_LABELS: Record<string, string> = {
  ASSIGN: '自动分配',
  AUTO_ASSIGN: '自动分配',
  CLAIM: '领取',
  CLOSE: '关闭',
  FORCE_ASSIGN: '强制分配',
  INTERVENE: '主管介入',
  RELEASE: '释放',
  REOPEN: '重开',
  TRANSFER: '转交',
};

const ACTION_TONES: Record<
  string,
  'danger' | 'info' | 'primary' | 'success' | 'warning'
> = {
  ASSIGN: 'primary',
  AUTO_ASSIGN: 'primary',
  CLAIM: 'success',
  CLOSE: 'info',
  FORCE_ASSIGN: 'danger',
  INTERVENE: 'warning',
  RELEASE: 'warning',
  REOPEN: 'warning',
  TRANSFER: 'warning',
};

const visible = ref(false);
const loading = ref(false);
const logs = ref<ConversationAssignmentLog[]>([]);
const conversationId = ref('');
let requestGeneration = 0;

function actionLabel(actionType: string) {
  return ACTION_LABELS[actionType] ?? actionType;
}

function actionTone(actionType: string) {
  return ACTION_TONES[actionType] ?? 'info';
}

/** 打开分配日志抽屉；重复打开不同会话时用代际丢弃过期响应。 */
async function open(id: string) {
  conversationId.value = id;
  logs.value = [];
  visible.value = true;
  loading.value = true;
  const generation = ++requestGeneration;
  try {
    const records = await getAssignmentLogs(id);
    if (generation !== requestGeneration) return;
    logs.value = records ?? [];
  } catch {
    // 请求层已统一提示错误；这里保留空列表，抽屉仍可关闭。
  } finally {
    if (generation === requestGeneration) loading.value = false;
  }
}

defineExpose({ open });
</script>

<template>
  <ElDrawer v-model="visible" size="620px" title="会话分配日志">
    <p class="drawer-note">
      会话 ID：{{ conversationId }} ·
      记录自动分配、领取、转交与主管强制分配等动作。
    </p>
    <ElTable v-loading="loading" :data="logs" size="small" stripe>
      <ElTableColumn label="动作" width="100">
        <template #default="{ row }">
          <ElTag :type="actionTone(row.actionType)" effect="plain" size="small">
            {{ actionLabel(row.actionType) }}
          </ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn label="从" width="130">
        <template #default="{ row }">{{ row.fromStaffId || '—' }}</template>
      </ElTableColumn>
      <ElTableColumn label="到" width="140">
        <template #default="{ row }">{{ row.toStaffId || '—' }}</template>
      </ElTableColumn>
      <ElTableColumn label="操作人" width="130">
        <template #default="{ row }">
          {{ row.operatorType || '—' }}
          <template v-if="row.operatorId"> / {{ row.operatorId }}</template>
        </template>
      </ElTableColumn>
      <ElTableColumn label="原因" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ row.reason || '—' }}</template>
      </ElTableColumn>
      <ElTableColumn label="时间" width="160">
        <template #default="{ row }">
          {{ formatDateTime(row.createTime) || row.createTime || '—' }}
        </template>
      </ElTableColumn>
      <template #empty>
        <ElEmpty description="暂无分配记录" :image-size="70" />
      </template>
    </ElTable>
  </ElDrawer>
</template>

<style scoped>
.drawer-note {
  margin-bottom: 14px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>

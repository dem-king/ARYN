<script lang="ts" setup>
import type { BatchDetailItemLike } from '../logic';

import type {
  ZoneBatchDetail,
  ZoneBatchHeader,
  ZoneUndoResult,
} from '#/views/product/zone-batch/types';

import { reactive, ref, watch } from 'vue';

import { Refresh } from '@element-plus/icons-vue';
import {
  ElAlert,
  ElButton,
  ElDialog,
  ElDrawer,
  ElMessage,
  ElMessageBox,
  ElTable,
  ElTableColumn,
  ElTag,
  ElTooltip,
} from 'element-plus';

import {
  getBatchDetail,
  getBatchPage,
  undoBatch,
} from '#/api/product/goods-zone';
import Pagination from '#/components/pagination/index.vue';

import {
  changeTypeTagType,
  formatChangeType,
  formatTime,
  isBatchUndoable,
  readBatchTotal,
  resolveSyncMeta,
  resolveUndoMeta,
  summarizeBatchActions,
  summarizeBatchItems,
  summarizeBatchRecord,
  summarizeUndoResult,
} from '../logic';

/**
 * 批次记录抽屉：getBatchPage 分页 + getBatchDetail 明细 + undoBatch 撤销。
 * 撤销冲突项必须显式展示（冲突=商品当前值已被再次修改，跳过不覆盖）。
 */
const props = defineProps<{
  visible: boolean;
}>();

const emit = defineEmits<{
  changed: [];
  'update:visible': [visible: boolean];
}>();

const loading = ref(false);
const records = ref<ZoneBatchHeader[]>([]);
const page = reactive({ current: 1, size: 10, total: 0 });

/** 明细弹窗 */
const detailVisible = ref(false);
const detailLoading = ref(false);
const detail = ref<null | ZoneBatchDetail>(null);
const detailBatchId = ref('');

/** 撤销结果面板 */
const undoResultVisible = ref(false);
const undoLoadingBatchId = ref('');
const undoResult = ref<null | ZoneUndoResult>(null);

async function loadPage() {
  loading.value = true;
  try {
    const response = await getBatchPage({
      current: page.current,
      size: page.size,
    });
    records.value = response?.records ?? [];
    page.total = Number(response?.total ?? 0);
  } catch {
    records.value = [];
    page.total = 0;
  } finally {
    loading.value = false;
  }
}

function reset() {
  page.current = 1;
  page.total = 0;
  records.value = [];
  detail.value = null;
  undoResult.value = null;
}

async function openDetail(record: Record<string, any> | ZoneBatchHeader) {
  detailBatchId.value = String(record.batchId ?? '');
  detailVisible.value = true;
  detailLoading.value = true;
  try {
    detail.value = await getBatchDetail(detailBatchId.value);
  } catch {
    detail.value = null;
  } finally {
    detailLoading.value = false;
  }
}

async function handleUndo(record: Record<string, any> | ZoneBatchHeader) {
  const batchId = String(record.batchId ?? '');
  try {
    await ElMessageBox.confirm(
      `确认撤销批次「${batchId}」？系统只会回滚当前值仍等于该批次结果的商品，已被后续修改的商品会跳过。`,
      '撤销批次',
      {
        confirmButtonText: '确认撤销',
        cancelButtonText: '取消',
        type: 'warning',
      },
    );
  } catch {
    return;
  }
  undoLoadingBatchId.value = batchId;
  try {
    const result = await undoBatch(batchId);
    undoResult.value = result;
    undoResultVisible.value = true;
    const summary = summarizeUndoResult(result);
    if (summary.tone === 'warning') {
      ElMessage.warning(summary.summary);
    } else {
      ElMessage.success(summary.summary);
    }
    await loadPage();
    emit('changed');
  } finally {
    undoLoadingBatchId.value = '';
  }
}

function closeDrawer() {
  emit('update:visible', false);
}

/** ElDrawer 只在关闭后抛 update:modelValue(false)，这里显式忽略打开事件 */
function handleVisibleChange(value: boolean) {
  if (!value) {
    closeDrawer();
  }
}

const detailSummary = ref<ReturnType<typeof summarizeBatchItems>>({
  total: 0,
  success: 0,
  failed: 0,
  changed: 0,
  noChange: 0,
  invalid: 0,
});

watch(detailVisible, (visible) => {
  if (!visible) {
    detail.value = null;
    detailBatchId.value = '';
  }
});

watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      reset();
      void loadPage();
    }
  },
);

watch(detail, (value) => {
  detailSummary.value = summarizeBatchItems(
    (value?.items ?? []) as BatchDetailItemLike[],
  );
});

const undoSummary = ref<null | ReturnType<typeof summarizeUndoResult>>(null);

watch(undoResult, (value) => {
  undoSummary.value = value ? summarizeUndoResult(value) : null;
});
</script>

<template>
  <ElDrawer
    :model-value="visible"
    title="专区操作批次记录"
    size="78%"
    :close-on-click-modal="false"
    @update:model-value="handleVisibleChange"
  >
    <ElAlert
      type="info"
      :closable="false"
      show-icon
      class="mb10"
      title="每次批量加入/调整规则/移出都会生成一条批次；撤销只回滚当前值仍等于该批次结果的商品，被后续修改的商品会跳过并列入冲突明细。"
    />

    <div class="record-toolbar">
      <span class="record-toolbar__tip">
        共 <b>{{ page.total }}</b> 条批次
      </span>
      <ElButton :icon="Refresh" @click="loadPage">刷新</ElButton>
    </div>

    <ElTable
      v-loading="loading"
      :data="records"
      row-key="batchId"
      border
      empty-text="暂无操作批次"
    >
      <ElTableColumn prop="batchId" label="批次号" min-width="180">
        <template #default="{ row }">
          <span class="record-batch-id">{{ row.batchId }}</span>
        </template>
      </ElTableColumn>
      <ElTableColumn label="操作人" width="110" align="center">
        <template #default="{ row }">
          {{ row.operatorName || '—' }}
        </template>
      </ElTableColumn>
      <ElTableColumn label="操作时间" width="150" align="center">
        <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
      </ElTableColumn>
      <ElTableColumn label="动作" width="180" align="center">
        <template #default="{ row }">
          <span class="record-actions">
            {{ summarizeBatchActions(row.memberAction, row.clearanceAction) }}
          </span>
        </template>
      </ElTableColumn>
      <ElTableColumn label="商品数" width="80" align="center">
        <template #default="{ row }">{{ readBatchTotal(row) }}</template>
      </ElTableColumn>
      <ElTableColumn label="结果" min-width="190" align="center">
        <template #default="{ row }">
          <span class="record-result">
            {{ summarizeBatchRecord(row) }}
          </span>
        </template>
      </ElTableColumn>
      <ElTableColumn label="价格同步" width="120" align="center">
        <template #default="{ row }">
          <ElTooltip
            :content="resolveSyncMeta(row.syncStatus).tip"
            placement="top"
          >
            <ElTag size="small" :type="resolveSyncMeta(row.syncStatus).type">
              {{ resolveSyncMeta(row.syncStatus).label }}
            </ElTag>
          </ElTooltip>
        </template>
      </ElTableColumn>
      <ElTableColumn label="撤销状态" width="110" align="center">
        <template #default="{ row }">
          <ElTag size="small" :type="resolveUndoMeta(row.undoStatus).type">
            {{ resolveUndoMeta(row.undoStatus).label }}
          </ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn label="操作" width="150" align="center" fixed="right">
        <template #default="{ row }">
          <ElButton link type="primary" @click="openDetail(row)">
            明细
          </ElButton>
          <ElButton
            link
            type="danger"
            :disabled="!isBatchUndoable(row)"
            :loading="undoLoadingBatchId === row.batchId"
            @click="handleUndo(row)"
          >
            撤销
          </ElButton>
        </template>
      </ElTableColumn>
    </ElTable>

    <Pagination
      v-model:current="page.current"
      v-model:size="page.size"
      :total="page.total"
      @change="loadPage"
    />

    <!-- 批次明细 -->
    <ElDialog
      v-model="detailVisible"
      :title="`批次明细 - ${detailBatchId}`"
      width="880px"
      append-to-body
    >
      <div v-loading="detailLoading">
        <div v-if="detail" class="record-detail-summary">
          <span>
            操作人：{{ detail.operatorName || '—' }} · 时间：{{
              formatTime(detail.createTime)
            }}
            · 动作：{{
              summarizeBatchActions(detail.memberAction, detail.clearanceAction)
            }}
          </span>
          <span class="record-detail-summary__count">
            共 {{ detailSummary.total }} 件：变更 {{ detailSummary.changed }} ·
            无变化 {{ detailSummary.noChange }} · 失败
            {{ detailSummary.failed }}
          </span>
        </div>
        <ElTable
          :data="detail?.items ?? []"
          row-key="spuId"
          border
          max-height="460"
          empty-text="暂无明细"
        >
          <ElTableColumn label="商品" min-width="240">
            <template #default="{ row }">
              <div class="record-item-name">
                <span>{{ row.spuName || row.spuId }}</span>
                <span class="record-item-id">{{ row.spuId }}</span>
              </div>
            </template>
          </ElTableColumn>
          <ElTableColumn label="结果" width="100" align="center">
            <template #default="{ row }">
              <ElTag
                size="small"
                :type="row.success === false ? 'danger' : 'success'"
              >
                {{ row.success === false ? '失败' : '成功' }}
              </ElTag>
            </template>
          </ElTableColumn>
          <ElTableColumn label="变更类型" width="100" align="center">
            <template #default="{ row }">
              <ElTag size="small" :type="changeTypeTagType(row.changeType)">
                {{ formatChangeType(row.changeType) }}
              </ElTag>
            </template>
          </ElTableColumn>
          <ElTableColumn label="说明" min-width="240">
            <template #default="{ row }">
              <span class="record-item-message">
                {{ row.message || row.errorCode || '—' }}
              </span>
            </template>
          </ElTableColumn>
        </ElTable>
      </div>
      <template #footer>
        <ElButton @click="detailVisible = false">关闭</ElButton>
      </template>
    </ElDialog>

    <!-- 撤销结果：冲突项必须可见 -->
    <ElDialog
      v-model="undoResultVisible"
      title="撤销结果"
      width="720px"
      append-to-body
    >
      <ElAlert
        v-if="undoSummary"
        :type="
          undoSummary.tone === 'warning'
            ? 'warning'
            : undoSummary.tone === 'success'
              ? 'success'
              : 'info'
        "
        :title="undoSummary.summary"
        :closable="false"
        show-icon
      />
      <template v-if="undoResult?.conflicts?.length">
        <div class="record-conflict-title">
          冲突明细（{{ undoResult.conflicts.length }} 件，已跳过未回滚）
        </div>
        <ElTable
          :data="undoResult.conflicts"
          border
          max-height="300"
          row-key="spuId"
        >
          <ElTableColumn label="商品" min-width="220">
            <template #default="{ row }">
              {{ row.spuName || row.spuId }}
            </template>
          </ElTableColumn>
          <ElTableColumn label="商品ID" width="180" prop="spuId" />
          <ElTableColumn label="跳过原因" min-width="200" prop="reason" />
        </ElTable>
      </template>
      <template #footer>
        <ElButton type="primary" @click="undoResultVisible = false">
          知道了
        </ElButton>
      </template>
    </ElDialog>
  </ElDrawer>
</template>

<style scoped lang="scss">
.record-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.record-toolbar__tip b {
  color: var(--el-text-color-primary);
}

.record-batch-id {
  font-family: var(--el-font-family-mono, monospace);
  font-size: 12px;
  color: var(--el-text-color-regular);
}

.record-actions,
.record-result {
  font-size: 12px;
  color: var(--el-text-color-regular);
}

.record-detail-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.record-detail-summary__count {
  font-variant-numeric: tabular-nums;
}

.record-item-name {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.record-item-id {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.record-item-message {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.record-conflict-title {
  margin: 12px 0 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-color-warning);
}
</style>

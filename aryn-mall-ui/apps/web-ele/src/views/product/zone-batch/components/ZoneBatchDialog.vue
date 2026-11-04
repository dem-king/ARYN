<script lang="ts" setup>
/**
 * 专区批量操作共享弹窗 v2（契约 §3.3）。
 *
 * 状态机：
 * - 打开 → 调用 previewZoneTags 展示「执行前状态」；
 * - 编辑区：每专区动作单选 + 规则字段，底部实时影响汇总与动态确认按钮；
 * - 提交：>200 自动分片调用 batchZoneTags，展示进度；
 * - 结果面板：成功/无变化/失败明细 + sync.status + 复制失败/重试失败/查看本次商品/撤销本批次/完成；
 * - 关闭结果面板时向外抛 `done`，由使用方自行刷新列表。
 *
 * 本组件不负责「选择商品」，只接收 `spuIds`；不做二次确认之外的业务判断（校验以服务端为准）。
 */
import type {
  ZoneAction,
  ZoneBatchAggregate,
  ZoneBatchDoneEvent,
  ZoneBatchItem,
  ZoneBatchResult,
  ZonePreview,
  ZoneSyncState,
  ZoneTagDraft,
  ZoneType,
  ZoneUndoResult,
  ZoneWarning,
} from '../types';

import { computed, reactive, ref, watch } from 'vue';
import useClipboard from 'vue-clipboard3';

import {
  ElAlert,
  ElButton,
  ElCheckbox,
  ElCol,
  ElDatePicker,
  ElDialog,
  ElFormItem,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElProgress,
  ElRadio,
  ElRadioGroup,
  ElRow,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import {
  batchZoneTags,
  previewZoneTags,
  undoBatch,
} from '#/api/product/goods-zone';

import {
  aggregateBatchResults,
  buildBatchPayload,
  buildPreviewRequest,
  buildRemoveConfirmText,
  chunkIds,
  collectFailuresText,
  collectItemWarnings,
  collectPreviewWarnings,
  computeConfirmText,
  computeImpactText,
  computePreviewImpact,
  computeRemoveImpact,
  computeSyncState,
  computeZoneStatus,
  createDefaultDrafts,
  createFailedItems,
  describeSync,
  emptyZoneDraft,
  hasPreviewImpact,
  mergeBatchAggregate,
  resolveErrorMessage,
  ZONE_TYPES,
  zoneChangeTypeLabel,
  zoneLabel,
  zoneWarningTagType,
} from '../logic';

const props = withDefaults(
  defineProps<{
    /**
     * 打开时范围内专区的默认动作（仅 standard 模式生效，默认 NONE）。
     * 「添加商品」入口传 'ADD'，打开即预选「加入/更新规则」。
     */
    defaultAction?: ZoneAction;
    /** 默认 standard；remove 用于「移出专区」入口（只允许移出/不修改，默认移出） */
    mode?: 'remove' | 'standard';
    spuIds: string[];
    title?: string;
    visible: boolean;
    /** 默认 both；指定单专区时只允许操作该专区（如「添加商品」到当前专区） */
    zoneScope?: 'both' | ZoneType;
  }>(),
  {
    defaultAction: 'NONE',
    mode: 'standard',
    title: '批量设置专区规则',
    zoneScope: 'both',
  },
);

const emit = defineEmits<{
  done: [payload: ZoneBatchDoneEvent];
  'update:visible': [visible: boolean];
}>();

const { toClipboard } = useClipboard();

const phase = ref<'edit' | 'result'>('edit');
const preview = ref<null | ZonePreview>(null);
const previewError = ref('');
const previewLoading = ref(false);
const overwriteSkuRules = ref(false);

const drafts = reactive<Record<ZoneType, ZoneTagDraft>>({
  member: emptyZoneDraft('member'),
  clearance: emptyZoneDraft('clearance'),
});

const submitting = ref(false);
const retrying = ref(false);
const progress = reactive({ done: 0, total: 0 });

const aggregate = ref<null | ZoneBatchAggregate>(null);
const showDetail = ref(false);
const undoing = ref(false);
const undoResult = ref<null | ZoneUndoResult>(null);
const undoDone = ref(false);

let doneEmitted = false;
let previewTimer: null | ReturnType<typeof setTimeout> = null;
let previewToken = 0;

const busy = computed(() => submitting.value || retrying.value);
const selectedCount = computed(() => new Set(props.spuIds ?? []).size);
const scopedZones = computed<ZoneType[]>(() =>
  props.zoneScope === 'both' ? ZONE_TYPES : [props.zoneScope],
);
const draftList = computed<ZoneTagDraft[]>(() =>
  ZONE_TYPES.map((zone) => drafts[zone]),
);
const impact = computed(() => computePreviewImpact(preview.value));
const impactText = computed(() => computeImpactText(impact.value));
const confirmText = computed(() => computeConfirmText(impact.value));
const nothingToChange = computed(
  () => preview.value !== null && !hasPreviewImpact(impact.value),
);
const confirmDisabled = computed(
  () =>
    busy.value ||
    previewLoading.value ||
    preview.value === null ||
    Boolean(previewError.value) ||
    !hasPreviewImpact(impact.value),
);

const zoneStatuses = computed(() =>
  scopedZones.value.map((zone) => ({
    status: computeZoneStatus(preview.value, zone),
    zone,
  })),
);
const previewWarnings = computed(() => collectPreviewWarnings(preview.value));
const skuOverrideSpuCount = computed(
  () =>
    (preview.value?.items ?? []).filter(
      (item) => (item.before?.skuOverrideCount ?? 0) > 0,
    ).length,
);
const canOverwriteSkuRules = computed(
  () =>
    skuOverrideSpuCount.value > 0 &&
    draftList.value.some((draft) => draft.action === 'ADD'),
);

const progressPercent = computed(() => {
  if (progress.total <= 0) return 0;
  return Math.min(100, Math.round((progress.done / progress.total) * 100));
});

const resultSummary = computed(() => aggregate.value?.summary ?? null);
const resultBatchIds = computed(() => aggregate.value?.batchIds ?? []);
const failedItems = computed<ZoneBatchItem[]>(() =>
  (aggregate.value?.items ?? []).filter((item) => !item.success),
);
const failedCount = computed(
  () => aggregate.value?.failedCount ?? resultSummary.value?.failed ?? 0,
);
const resultWarnings = computed(() =>
  collectItemWarnings(aggregate.value?.items ?? []),
);
const syncState = computed<null | ZoneSyncState>(() =>
  computeSyncState(aggregate.value?.syncs ?? []),
);
const syncAlertType = computed(() => {
  if (syncState.value === 'FAILED') return 'warning';
  if (syncState.value === 'SUCCESS') return 'success';
  return 'info';
});
const syncAlertTitle = computed(() => {
  const failedSync = (aggregate.value?.syncs ?? []).find(
    (sync) => sync.status === 'FAILED',
  );
  return describeSync(syncState.value, failedSync?.message);
});
const partialFailure = computed(
  () => failedCount.value > 0 && (resultSummary.value?.success ?? 0) > 0,
);

const selectionOrder = computed(() => {
  const order = new Map<string, number>();
  (props.spuIds ?? []).forEach((id, index) => {
    if (!order.has(id)) order.set(id, index);
  });
  return order;
});
const detailItems = computed<ZoneBatchItem[]>(() => {
  const items = [...(aggregate.value?.items ?? [])];
  return items.sort((left, right) => {
    const leftIndex = selectionOrder.value.get(left.spuId) ?? 99_999;
    const rightIndex = selectionOrder.value.get(right.spuId) ?? 99_999;
    return leftIndex - rightIndex;
  });
});

/* ------------------------------------------------------------------ */
/* 数据加载                                                            */
/* ------------------------------------------------------------------ */

function setZoneAction(zone: ZoneType, action: ZoneAction) {
  if (props.mode === 'remove' && action === 'ADD') return;
  drafts[zone].action = action;
}

function resetDrafts() {
  const defaults = createDefaultDrafts(
    props.zoneScope,
    props.mode,
    props.defaultAction,
  );
  for (const draft of defaults) {
    drafts[draft.zone] = { ...draft };
  }
}

function openDialog() {
  doneEmitted = false;
  phase.value = 'edit';
  preview.value = null;
  previewError.value = '';
  previewLoading.value = false;
  overwriteSkuRules.value = false;
  aggregate.value = null;
  undoResult.value = null;
  undoDone.value = false;
  showDetail.value = false;
  progress.done = 0;
  progress.total = 0;
  resetDrafts();
  if (!props.spuIds?.length) {
    ElMessage.warning('请先选择商品');
    emit('update:visible', false);
    return;
  }
  void loadPreview();
}

function resetDialog() {
  if (previewTimer) {
    clearTimeout(previewTimer);
    previewTimer = null;
  }
  previewToken += 1;
  preview.value = null;
  previewLoading.value = false;
  previewError.value = '';
}

async function loadPreview() {
  const token = ++previewToken;
  previewLoading.value = true;
  previewError.value = '';
  try {
    const data = await previewZoneTags(
      buildPreviewRequest(
        props.spuIds ?? [],
        draftList.value,
        overwriteSkuRules.value,
      ),
    );
    if (token !== previewToken) return;
    preview.value = data ?? null;
  } catch (error) {
    if (token !== previewToken) return;
    preview.value = null;
    previewError.value = resolveErrorMessage(
      error,
      '执行前状态检查失败，请重试',
    );
  } finally {
    if (token === previewToken) previewLoading.value = false;
  }
}

function schedulePreview() {
  if (previewTimer) clearTimeout(previewTimer);
  previewTimer = setTimeout(() => {
    previewTimer = null;
    void loadPreview();
  }, 350);
}

const draftsSignature = computed(
  () =>
    `${JSON.stringify(ZONE_TYPES.map((zone) => drafts[zone]))}::${overwriteSkuRules.value}`,
);

watch(draftsSignature, () => {
  if (!props.visible || phase.value !== 'edit' || busy.value) return;
  // 动作/规则变化后重新预检（350ms 防抖；并发响应由 previewToken 丢弃）
  schedulePreview();
});

watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      openDialog();
    } else {
      resetDialog();
    }
  },
  { immediate: true },
);

/* ------------------------------------------------------------------ */
/* 提交                                                                */
/* ------------------------------------------------------------------ */

function closeDialog() {
  if (busy.value) {
    ElMessage.warning('正在提交，请稍候');
    return;
  }
  emit('update:visible', false);
  emitDoneIfResult();
}

function emitDoneIfResult() {
  if (doneEmitted || phase.value !== 'result') return;
  doneEmitted = true;
  emit('done', {
    batchIds: aggregate.value?.batchIds ?? [],
    failedCount: aggregate.value?.failedCount ?? 0,
    summary: aggregate.value?.summary ?? null,
  });
}

function handleBeforeClose(done: () => void) {
  if (busy.value) {
    ElMessage.warning('正在提交，请稍候');
    return;
  }
  done();
}

async function handleConfirm() {
  if (confirmDisabled.value) return;
  const removeZones = draftList.value.filter(
    (draft) => draft.action === 'REMOVE',
  );
  if (removeZones.length > 0) {
    const removeImpact = computeRemoveImpact(preview.value, draftList.value);
    try {
      await ElMessageBox.confirm(
        buildRemoveConfirmText(removeImpact),
        '移出专区确认',
        {
          cancelButtonText: '取消',
          confirmButtonClass: 'el-button--danger',
          confirmButtonText: '确认移出',
          type: 'warning',
        },
      );
    } catch {
      return;
    }
  }
  await submitBatches(props.spuIds ?? []);
}

function createFailureResult(items: ZoneBatchItem[]): ZoneBatchResult {
  return {
    batchId: '',
    items,
    summary: {
      added: 0,
      changed: 0,
      failed: items.length,
      noChange: 0,
      removed: 0,
      requested: items.length,
      success: 0,
      updated: 0,
    },
    sync: null,
    zones: [],
  };
}

async function submitBatches(spuIds: string[], isRetry = false) {
  if (busy.value) return;
  if (isRetry) retrying.value = true;
  else submitting.value = true;
  const chunks = chunkIds(spuIds);
  progress.done = 0;
  progress.total = chunks.length;
  const basePayload = buildBatchPayload(
    [],
    draftList.value,
    overwriteSkuRules.value,
  );
  const responses: ZoneBatchResult[] = [];
  const requestFailureItems: ZoneBatchItem[] = [];
  try {
    for (const chunk of chunks) {
      try {
        const response = await batchZoneTags({
          ...basePayload,
          spuIds: chunk,
        });
        if (response) {
          responses.push(response);
        } else {
          requestFailureItems.push(
            ...createFailedItems(chunk, '接口未返回批量执行结果'),
          );
        }
      } catch (error) {
        requestFailureItems.push(
          ...createFailedItems(
            chunk,
            resolveErrorMessage(error, '提交失败，请稍后重试'),
          ),
        );
      }
      progress.done += 1;
    }
    if (requestFailureItems.length > 0) {
      responses.push(createFailureResult(requestFailureItems));
    }
    if (responses.length === 0) {
      ElMessage.error('提交失败，请稍后重试');
      return;
    }
    aggregate.value = isRetry
      ? mergeBatchAggregate(aggregate.value, responses)
      : aggregateBatchResults(responses);
    phase.value = 'result';
    showDetail.value = false;
  } finally {
    submitting.value = false;
    retrying.value = false;
  }
}

function retryFailed() {
  const ids = failedItems.value.map((item) => item.spuId);
  if (ids.length === 0) return;
  void submitBatches(ids, true);
}

async function copyFailures() {
  const text = collectFailuresText(aggregate.value?.items ?? []);
  if (!text) {
    ElMessage.info('本次没有失败商品');
    return;
  }
  try {
    await toClipboard(text);
    ElMessage.success('失败清单已复制到剪贴板');
  } catch {
    ElMessage.error('复制失败，请展开明细手动复制');
  }
}

async function handleUndo() {
  const batchId =
    resultBatchIds.value.length === 1 ? resultBatchIds.value[0] : '';
  if (!batchId) return;
  try {
    await ElMessageBox.confirm(
      '撤销会把本批次改动恢复为执行前的值；期间被再次修改的商品会跳过并报告冲突。是否继续？',
      '撤销本批次',
      {
        cancelButtonText: '取消',
        confirmButtonText: '确认撤销',
        type: 'warning',
      },
    );
  } catch {
    return;
  }
  undoing.value = true;
  try {
    const result = await undoBatch(batchId);
    undoResult.value = result ?? null;
    undoDone.value = true;
    if (result) {
      ElMessage.success(
        `已撤销：恢复 ${result.restored ?? 0} 件，跳过 ${result.skipped ?? 0} 件`,
      );
    }
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '撤销失败，请稍后重试'));
  } finally {
    undoing.value = false;
  }
}

/* ------------------------------------------------------------------ */
/* 展示辅助                                                            */
/* ------------------------------------------------------------------ */

function itemResultLabel(item: Record<string, any>): string {
  if (!item.success) return '失败';
  const label = zoneChangeTypeLabel(item.changeType);
  return label === '无变化' ? '无变化' : `成功·${label}`;
}

function itemResultTagType(
  item: Record<string, any>,
): 'danger' | 'info' | 'success' | 'warning' {
  if (!item.success) return 'danger';
  if (item.changeType === 'NO_CHANGE') return 'info';
  return 'success';
}

function itemReason(item: Record<string, any>): string {
  const pieces: string[] = [];
  if (item.errorCode) pieces.push(`【${item.errorCode}】`);
  if (item.message) pieces.push(item.message);
  const warnings = ((item.warnings ?? []) as ZoneWarning[]).map(
    (warning) => warning.message,
  );
  if (warnings.length > 0) pieces.push(warnings.join('；'));
  return pieces.join('') || '-';
}
</script>

<template>
  <ElDialog
    :model-value="visible"
    :title="title"
    width="920px"
    top="5vh"
    destroy-on-close
    :close-on-click-modal="false"
    :before-close="handleBeforeClose"
    @update:model-value="
      (value: boolean) => {
        if (value === visible) return;
        if (value) emit('update:visible', true);
        else closeDialog();
      }
    "
  >
    <div v-loading="busy" class="zone-batch">
      <!-- ========== 编辑：状态预检 + 动作/规则 + 影响汇总 ========== -->
      <template v-if="phase === 'edit'">
        <ElAlert
          type="info"
          :closable="false"
          show-icon
          class="mb10"
          :title="`已选 ${selectedCount} 件商品；打开即自动检查当前专区状态，提交前展示本次影响，提交后展示逐条结果。`"
        />

        <ElAlert
          v-if="previewError"
          type="error"
          :closable="false"
          show-icon
          :title="previewError"
          class="mb10"
        >
          <ElButton size="small" @click="loadPreview">重新检查</ElButton>
        </ElAlert>

        <div v-if="preview || previewLoading" class="zone-batch__section">
          <div class="zone-batch__section-title">
            执行前状态（自动检查）
            <span class="zone-batch__muted"> 本次影响：{{ impactText }} </span>
          </div>
          <div class="zone-batch__status-list">
            <div
              v-for="entry in zoneStatuses"
              :key="entry.zone"
              class="zone-batch__status-row"
            >
              <ElTag type="warning" effect="plain" size="small">
                {{ zoneLabel(entry.zone) }}
              </ElTag>
              <span
                >已在 <b>{{ entry.status.existing }}</b> 件</span
              >
              <span
                >未加入 <b>{{ entry.status.notJoined }}</b> 件</span
              >
              <span v-if="entry.status.willAdd">
                将加入 <b>{{ entry.status.willAdd }}</b> 件
              </span>
              <span
                >规则将变 <b>{{ entry.status.willUpdate }}</b> 件</span
              >
              <span v-if="entry.status.willRemove">
                将移出
                <b class="text-danger">{{ entry.status.willRemove }}</b>
                件
              </span>
              <span
                >无变化 <b>{{ entry.status.noChange }}</b> 件</span
              >
              <span v-if="entry.status.withSkuOverride">
                有 SKU 精调 <b>{{ entry.status.withSkuOverride }}</b> 件
              </span>
              <span v-if="entry.status.invalid" class="text-danger">
                异常 <b>{{ entry.status.invalid }}</b> 件
              </span>
            </div>
          </div>
          <div v-if="previewWarnings.length > 0" class="zone-batch__warnings">
            <div
              v-for="warning in previewWarnings"
              :key="`${warning.code}-${warning.message}`"
              class="zone-batch__warning-item"
            >
              <ElTag
                :type="zoneWarningTagType(warning.code)"
                size="small"
                effect="plain"
              >
                {{ warning.count }} 件
              </ElTag>
              <span>{{ warning.message }}</span>
            </div>
          </div>
        </div>

        <div
          v-for="zone in scopedZones"
          :key="zone"
          class="zone-batch__section"
        >
          <div class="zone-batch__action-row">
            <span class="zone-batch__zone-title">{{ zoneLabel(zone) }}</span>
            <ElRadioGroup
              :model-value="drafts[zone].action"
              @update:model-value="
                (value) => setZoneAction(zone, value as ZoneAction)
              "
            >
              <ElRadio v-if="mode !== 'remove'" value="ADD" :disabled="busy">
                加入/更新规则
              </ElRadio>
              <!-- 移出是破坏性动作，只由「移出专区」入口（mode=remove）提供，
                   普通「批量设置规则」弹窗不再混排，避免误操作与入口重复 -->
              <ElRadio v-if="mode === 'remove'" value="REMOVE" :disabled="busy">
                <span class="text-danger">移出专区</span>
              </ElRadio>
              <ElRadio value="NONE" :disabled="busy">不修改</ElRadio>
            </ElRadioGroup>
          </div>

          <div v-if="drafts[zone].action === 'ADD'" class="zone-batch__rules">
            <template v-if="zone === 'member'">
              <ElRow :gutter="12">
                <ElCol :span="8">
                  <ElFormItem label="会员折扣率" class="mb0">
                    <ElInputNumber
                      v-model="drafts.member.memberDiscountRate"
                      :min="0.0001"
                      :max="0.9999"
                      :step="0.01"
                      :precision="4"
                      controls-position="right"
                      placeholder="0.9 = 9 折"
                      style="width: 100%"
                      :disabled="busy"
                    />
                  </ElFormItem>
                </ElCol>
                <ElCol :span="8">
                  <ElFormItem label="会员固定价" class="mb0">
                    <ElInputNumber
                      v-model="drafts.member.memberPriceFixed"
                      :min="0.01"
                      :precision="2"
                      controls-position="right"
                      placeholder="与折扣取低"
                      style="width: 100%"
                      :disabled="busy"
                    />
                  </ElFormItem>
                </ElCol>
              </ElRow>
              <div class="zone-batch__hint">
                规则不填 =
                沿用商品已有规则；商品无规则时使用专区默认折扣。会员价下限按商品成本自动兜底，个别商品精调请到商品编辑页「专区标签」。
              </div>
            </template>
            <template v-else>
              <ElRow :gutter="12">
                <ElCol :span="8">
                  <ElFormItem label="出清价" class="mb0">
                    <ElInputNumber
                      v-model="drafts.clearance.clearancePrice"
                      :min="0.01"
                      :precision="2"
                      controls-position="right"
                      placeholder="与折扣二选一"
                      style="width: 100%"
                      :disabled="busy"
                    />
                  </ElFormItem>
                </ElCol>
                <ElCol :span="8">
                  <ElFormItem label="出清折扣率" class="mb0">
                    <ElInputNumber
                      v-model="drafts.clearance.clearanceDiscountRate"
                      :min="0.0001"
                      :max="0.9999"
                      :step="0.01"
                      :precision="4"
                      controls-position="right"
                      placeholder="对原价计算"
                      style="width: 100%"
                      :disabled="busy"
                    />
                  </ElFormItem>
                </ElCol>
                <ElCol :span="8">
                  <ElFormItem label="出清截止时间" class="mb0">
                    <ElDatePicker
                      v-model="drafts.clearance.clearanceExpireTime"
                      type="datetime"
                      value-format="YYYY-MM-DD HH:mm:ss"
                      placeholder="空=长期有效"
                      style="width: 100%"
                      :disabled="busy"
                    />
                  </ElFormItem>
                </ElCol>
              </ElRow>
              <div class="zone-batch__hint">
                规则不填 =
                沿用商品已有规则；出清价与出清折扣至少填一个（或由专区默认折扣兜底），否则该商品提交时会失败并提示原因。
              </div>
            </template>
          </div>
          <div
            v-else-if="drafts[zone].action === 'REMOVE'"
            class="zone-batch__danger-hint"
          >
            移出将清空该商品在本专区的 SPU 规则与全部 SKU
            精调，提交前会再次确认影响范围；如需回滚可在结果面板撤销本批次。
          </div>
        </div>

        <ElCheckbox v-if="canOverwriteSkuRules" v-model="overwriteSkuRules">
          同时清除已配置的 SKU 精调（{{ skuOverrideSpuCount }}
          件商品），所有规格统一按本次规则
        </ElCheckbox>

        <ElAlert
          v-if="nothingToChange && !previewLoading"
          type="info"
          :closable="false"
          show-icon
          class="mt10"
          title="所选商品均无需变更"
        />
      </template>

      <!-- ========== 结果面板 ========== -->
      <template v-else>
        <ElAlert
          :type="syncAlertType"
          :closable="false"
          show-icon
          class="mb10"
          :title="syncAlertTitle"
        />

        <ElAlert
          v-if="partialFailure"
          type="warning"
          :closable="false"
          show-icon
          class="mb10"
          title="部分商品失败：成功商品已生效，失败项可复制清单或重试。"
        />

        <div class="zone-batch__stats">
          <div class="zone-batch__stat">
            <div class="zone-batch__stat-num">
              {{ resultSummary?.success ?? 0 }}
            </div>
            <div class="zone-batch__stat-label">成功</div>
          </div>
          <div class="zone-batch__stat">
            <div class="zone-batch__stat-num">
              {{ resultSummary?.noChange ?? 0 }}
            </div>
            <div class="zone-batch__stat-label">无变化</div>
          </div>
          <div class="zone-batch__stat">
            <div class="zone-batch__stat-num">
              {{ resultSummary?.changed ?? 0 }}
            </div>
            <div class="zone-batch__stat-label">发生变更</div>
          </div>
          <div class="zone-batch__stat">
            <div
              class="zone-batch__stat-num"
              :class="{ 'text-danger': failedCount > 0 }"
            >
              {{ failedCount }}
            </div>
            <div class="zone-batch__stat-label">失败</div>
          </div>
        </div>

        <div class="zone-batch__meta">
          <span v-if="resultBatchIds.length > 0">
            批次号：{{ resultBatchIds.join('、') }}
          </span>
          <span v-else class="zone-batch__muted">未生成批次号</span>
        </div>

        <ElTable
          v-if="failedItems.length > 0"
          :data="failedItems"
          border
          size="small"
          max-height="220"
          class="mb10"
        >
          <ElTableColumn label="商品" min-width="220">
            <template #default="scope">
              {{ scope.row.spuName || scope.row.spuId }}
            </template>
          </ElTableColumn>
          <ElTableColumn label="失败原因" min-width="320">
            <template #default="scope">
              {{ itemReason(scope.row) }}
            </template>
          </ElTableColumn>
        </ElTable>

        <div v-if="resultWarnings.length > 0" class="zone-batch__warnings">
          <div
            v-for="warning in resultWarnings"
            :key="`${warning.code}-${warning.message}`"
            class="zone-batch__warning-item"
          >
            <ElTag
              :type="zoneWarningTagType(warning.code)"
              size="small"
              effect="plain"
            >
              {{ warning.count }} 件
            </ElTag>
            <span>{{ warning.message }}</span>
          </div>
        </div>

        <ElAlert
          v-if="undoResult?.conflicts?.length"
          type="warning"
          :closable="false"
          show-icon
          class="mt10"
          title="以下商品在批次执行后被再次修改，已跳过撤销："
        >
          <div
            v-for="conflict in undoResult.conflicts"
            :key="conflict.spuId"
            class="zone-batch__conflict"
          >
            {{ conflict.spuName || conflict.spuId }}：{{ conflict.reason }}
          </div>
        </ElAlert>

        <div class="zone-batch__result-actions">
          <ElButton :disabled="failedItems.length === 0" @click="copyFailures">
            复制失败清单
          </ElButton>
          <ElButton
            v-if="failedItems.length > 0"
            type="warning"
            plain
            :loading="retrying"
            :disabled="busy"
            @click="retryFailed"
          >
            重试失败 {{ failedItems.length }} 件
          </ElButton>
          <ElButton @click="showDetail = true">查看本次商品</ElButton>
          <ElButton
            v-if="resultBatchIds.length === 1 && !undoDone"
            type="danger"
            plain
            :loading="undoing"
            :disabled="busy"
            @click="handleUndo"
          >
            撤销本批次
          </ElButton>
        </div>
      </template>
    </div>

    <template #footer>
      <div class="zone-batch__footer">
        <template v-if="phase === 'edit'">
          <div class="zone-batch__footer-impact">
            <template v-if="busy">
              <div class="zone-batch__progress">
                <span>
                  正在提交（第 {{ progress.done }}/{{ progress.total }} 片，每片
                  ≤200 件）
                </span>
                <ElProgress
                  :percentage="progressPercent"
                  :stroke-width="10"
                  style="width: 260px"
                />
              </div>
            </template>
            <template v-else>
              <span>{{ impactText }}</span>
              <span v-if="impact.invalid > 0" class="text-danger">
                其中 {{ impact.invalid }} 件无法执行
              </span>
            </template>
          </div>
          <div class="zone-batch__footer-buttons">
            <ElButton :disabled="busy" @click="closeDialog">取消</ElButton>
            <ElButton
              type="primary"
              :disabled="confirmDisabled"
              :loading="busy"
              @click="handleConfirm"
            >
              {{ confirmText }}
            </ElButton>
          </div>
        </template>
        <template v-else>
          <div class="zone-batch__footer-impact">
            <span class="zone-batch__muted">
              结果已保留在本面板，关闭后可到列表复核本次商品。
            </span>
          </div>
          <div class="zone-batch__footer-buttons">
            <ElButton type="primary" :disabled="busy" @click="closeDialog">
              完成
            </ElButton>
          </div>
        </template>
      </div>
    </template>

    <!-- 本次操作商品明细 -->
    <ElDialog
      v-model="showDetail"
      title="本次操作商品明细"
      width="760px"
      append-to-body
    >
      <ElTable :data="detailItems" border size="small" max-height="480">
        <ElTableColumn label="商品" min-width="220">
          <template #default="scope">
            <div>{{ scope.row.spuName || scope.row.spuId }}</div>
            <div class="zone-batch__muted">{{ scope.row.spuId }}</div>
          </template>
        </ElTableColumn>
        <ElTableColumn label="结果" width="120">
          <template #default="scope">
            <ElTag :type="itemResultTagType(scope.row)" size="small">
              {{ itemResultLabel(scope.row) }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="说明" min-width="300">
          <template #default="scope">
            {{ itemReason(scope.row) }}
          </template>
        </ElTableColumn>
      </ElTable>
      <template #footer>
        <ElButton @click="showDetail = false">关闭</ElButton>
      </template>
    </ElDialog>
  </ElDialog>
</template>

<style scoped lang="scss">
.zone-batch {
  &__section {
    padding: 12px 14px;
    margin-bottom: 12px;
    background: var(--el-fill-color-lighter);
    border-radius: 8px;
  }

  &__section-title {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 10px;
    font-weight: 600;
  }

  &__muted {
    font-size: 12px;
    font-weight: 400;
    color: var(--el-text-color-secondary);
  }

  &__status-list {
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  &__status-row {
    display: flex;
    flex-wrap: wrap;
    gap: 14px;
    align-items: center;
    font-size: 13px;
    color: var(--el-text-color-regular);
  }

  &__warnings {
    padding-top: 8px;
    margin-top: 10px;
    border-top: 1px dashed var(--el-border-color);
  }

  &__warning-item {
    display: flex;
    gap: 8px;
    align-items: center;
    margin-bottom: 4px;
    font-size: 12px;
    line-height: 20px;
    color: var(--el-color-warning-dark-2);
  }

  &__action-row {
    display: flex;
    gap: 16px;
    align-items: center;
  }

  &__zone-title {
    flex: 0 0 auto;
    font-weight: 600;
  }

  &__rules {
    padding-top: 10px;
    margin-top: 10px;
    border-top: 1px dashed var(--el-border-color);
  }

  &__hint,
  &__danger-hint {
    margin-top: 8px;
    font-size: 12px;
    line-height: 20px;
    color: var(--el-text-color-secondary);
  }

  &__danger-hint {
    color: var(--el-color-danger);
  }

  &__stats {
    display: flex;
    gap: 14px;
    margin-bottom: 12px;
  }

  &__stat {
    flex: 1;
    padding: 10px 0;
    text-align: center;
    background: var(--el-fill-color-lighter);
    border-radius: 8px;
  }

  &__stat-num {
    font-size: 22px;
    font-weight: 700;
    font-variant-numeric: tabular-nums;
  }

  &__stat-label {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }

  &__meta {
    margin-bottom: 10px;
    font-size: 12px;
    color: var(--el-text-color-regular);
  }

  &__result-actions {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-top: 12px;
  }

  &__conflict {
    font-size: 12px;
    line-height: 20px;
  }

  &__footer {
    display: flex;
    gap: 12px;
    align-items: center;
    justify-content: space-between;
  }

  &__footer-impact {
    display: flex;
    flex-direction: column;
    gap: 4px;
    font-size: 13px;
    color: var(--el-text-color-regular);
  }

  &__progress {
    display: flex;
    flex-direction: column;
    gap: 4px;
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }

  &__footer-buttons {
    display: flex;
    flex: 0 0 auto;
    gap: 8px;
  }
}

.mb0 {
  margin-bottom: 0;
}

.mb10 {
  margin-bottom: 10px;
}

.mt10 {
  margin-top: 10px;
}

.text-danger {
  color: var(--el-color-danger);
}
</style>

<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import type { ClearanceRuleDraft, ClearanceRuleRow } from './logic';

import { computed, nextTick, onMounted, reactive, ref } from 'vue';

import { useAccess } from '@vben/access';

import {
  ArrowRight,
  Delete,
  Discount,
  Edit,
  InfoFilled,
  Plus,
  Refresh,
  Timer,
  WarningFilled,
} from '@element-plus/icons-vue';
import {
  ElAlert,
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElIcon,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElSwitch,
  ElTable,
  ElTableColumn,
  ElTag,
  ElTooltip,
} from 'element-plus';

import {
  addClearanceRule,
  deleteClearanceRule,
  getClearanceRules,
  updateClearanceRule,
} from '#/api/promotion/clearance-rule';
import PageToolbar from '#/components/page/page-toolbar.vue';
import { useKeepAliveRefresh } from '#/composables/use-keep-alive-refresh';
import { formatMoney } from '#/utils/util';

import LadderChips from './components/LadderChips.vue';
import {
  buildLadder,
  buildRulePayload,
  buildTogglePayload,
  explainIneffective,
  findSameHoursRule,
  formatDiscount,
  formatHours,
  formatRateHint,
  mergeAdjacentBands,
  rateToZhe,
  simulatePrice,
  summarizeRules,
  toRuleDraft,
  validateDraft,
  zheToRate,
} from './logic';

/**
 * 「出清折扣规则」页。
 *
 * 业务语义：出清价 = 基础出清价 × 命中档折扣率。剩余时长 ≤ 档位小时数即命中，
 * 同一时刻命中多档时只取最深一档、不连乘（与后端 ClearanceRuleService.resolveTierRate 一致）。
 * 因此配置侧最关键的两件事是：
 * 1) 让运营一眼看懂「现在实际生效的价格阶梯」，而不是只看规则行；
 * 2) 在编辑时立刻提示「同时间档重复」与「本档被更长档覆盖、不会改变价格」。
 *
 * 页面结构：机制说明 + 有效阶梯 + 快速试算 → 工具栏 → 规则表格 → 编辑弹窗。
 */

/** 新增规则在预览阶梯里的临时标识，不与后端 ID 冲突 */
const DRAFT_ID = '__clearance_rule_draft__';
/** 剩余时长快捷预设（小时） */
const HOUR_PRESETS = [72, 48, 24, 12, 6];
/** 折扣力度快捷预设（折） */
const ZHE_PRESETS = [9, 8.5, 8, 7, 6, 5];

const { hasAccessByCodes } = useAccess();
const canSave = computed(() =>
  hasAccessByCodes(['promotion:shipactivity:save']),
);

const loading = ref(false);
const rules = ref<ClearanceRuleRow[]>([]);
const dialogVisible = ref(false);
const saving = ref(false);
const editingId = ref('');
const togglingId = ref('');
const formRef = ref<FormInstance>();

const form = reactive<{
  enabled: boolean;
  hoursBeforeExpire: null | number | undefined;
  remark: string;
  ruleName: string;
  sortOrder: number;
  zhe: number;
}>({
  ruleName: '',
  hoursBeforeExpire: 24,
  zhe: 8,
  enabled: true,
  sortOrder: 0,
  remark: '',
});

/** 快速试算输入：基础出清价与剩余小时数 */
const trialBase = ref<number | undefined>(100);
const trialHours = ref<number | undefined>(20);

/** 正在编辑规则的原始折扣率：折输入精度为 0.1 折，存量 3–4 位小数率未改动时必须原值回写，避免静默改价 */
const originalRate = ref<null | number>(null);
/** 原始折扣率换算成「折」后的展示值，用于判断用户是否真的改过折扣 */
const originalZhe = computed(() =>
  originalRate.value === null
    ? null
    : Number(rateToZhe(originalRate.value).toFixed(1)),
);
/** 折输入上限：原始折扣率超过 9.9 折时，输入框会把展示值钳到该上限 */
const ZHE_INPUT_MAX = 9.9;
/** 原始折扣率换算成折后，再按输入框上限钳制得到的展示值 */
const originalZheClamped = computed(() =>
  originalZhe.value === null
    ? null
    : Math.min(originalZhe.value, ZHE_INPUT_MAX),
);
/** 实际生效的折扣率：未改折扣时保留原始精度，否则按折输入换算 */
const effectiveRate = computed(() => {
  if (
    originalRate.value !== null &&
    originalZheClamped.value !== null &&
    form.zhe === originalZheClamped.value
  ) {
    return originalRate.value;
  }
  return zheToRate(form.zhe);
});
/** 存量折扣率无法用 0.1 折精确表达时提示用户 */
const rateRoundingHint = computed(() => {
  if (
    editingId.value &&
    originalRate.value !== null &&
    effectiveRate.value === originalRate.value &&
    zheToRate(form.zhe) !== originalRate.value
  ) {
    const percent = Math.round(originalRate.value * 10_000) / 100;
    return `原折扣率 ${percent}%，不改折扣力度时按原值保存`;
  }
  return '';
});

const dialogRules: FormRules = {
  ruleName: [
    { message: '请输入规则名称', required: true, trigger: 'blur' },
    { max: 64, message: '规则名称不能超过 64 个字符', trigger: 'blur' },
  ],
  hoursBeforeExpire: [
    { message: '请输入剩余时长阈值', required: true, trigger: 'change' },
    {
      trigger: 'change',
      validator: (
        _rule: any,
        value: null | number | undefined,
        callback: (error?: Error) => void,
      ) => {
        const hours = Number(value);
        if (!Number.isFinite(hours) || !Number.isInteger(hours) || hours <= 0) {
          callback(new Error('剩余时长需为大于 0 的整数小时'));
          return;
        }
        callback();
      },
    },
  ],
  zhe: [
    { message: '请输入折扣力度', required: true, trigger: 'change' },
    {
      trigger: 'change',
      validator: (
        _rule: any,
        value: null | number | undefined,
        callback: (error?: Error) => void,
      ) => {
        const zhe = Number(value);
        if (!Number.isFinite(zhe) || zhe < 0.1 || zhe > 9.9) {
          callback(new Error('折扣力度需在 0.1–9.9 折之间'));
          return;
        }
        callback();
      },
    },
  ],
};

/** 当前表单对应的草稿（规则名称与备注去除首尾空格） */
const draft = computed<ClearanceRuleDraft>(() => ({
  ruleName: form.ruleName.trim(),
  hoursBeforeExpire: form.hoursBeforeExpire ?? null,
  discountRate: effectiveRate.value,
  enabled: form.enabled,
  sortOrder: form.sortOrder ?? 0,
  remark: form.remark.trim(),
}));

/** 编辑中的规则（用于「配置后阶梯」预览，数值非法时为 null） */
const draftPreviewRow = computed<ClearanceRuleRow | null>(() => {
  const hours = form.hoursBeforeExpire;
  const rate = effectiveRate.value;
  if (
    hours === null ||
    hours === undefined ||
    !Number.isInteger(hours) ||
    hours <= 0
  ) {
    return null;
  }
  if (!Number.isFinite(rate) || rate <= 0 || rate >= 1) {
    return null;
  }
  return {
    id: DRAFT_ID,
    ruleName: form.ruleName.trim() || '当前编辑规则',
    hoursBeforeExpire: hours,
    discountRate: rate,
    enabled: form.enabled ? '1' : '0',
    sortOrder: form.sortOrder ?? 0,
    remark: form.remark,
  };
});

/** 配置后的规则集合：其它规则 + 编辑中的规则 */
const previewRules = computed<ClearanceRuleRow[]>(() => {
  const others = rules.value.filter((row) => row.id !== editingId.value);
  return draftPreviewRow.value ? [...others, draftPreviewRow.value] : others;
});

const previewLadder = computed(() =>
  mergeAdjacentBands(buildLadder(previewRules.value)),
);

/** 编辑中的规则落在预览阶梯的哪一段，用于高亮「本档」 */
const previewBandIndex = computed(() => {
  const row = draftPreviewRow.value;
  // 停用草稿不参与生效阶梯，避免「本档」高亮与实际阶梯不一致
  if (!row || row.enabled !== '1') {
    return -1;
  }
  return previewLadder.value.findIndex((band) => {
    const afterLower =
      band.fromHours === null || row.hoursBeforeExpire > band.fromHours;
    const atMostUpper =
      band.toHours === null || row.hoursBeforeExpire <= band.toHours;
    return afterLower && atMostUpper;
  });
});

const draftIneffectiveReason = computed<null | string>(() => {
  const row = draftPreviewRow.value;
  if (!row || row.enabled !== '1') {
    return null;
  }
  return explainIneffective(previewRules.value, row);
});

/** 同时间档重复：只对启用中的其它规则提示，停用规则不参与算价 */
const sameHoursRule = computed(() => {
  const hours = form.hoursBeforeExpire;
  if (!hours || hours <= 0) {
    return undefined;
  }
  return findSameHoursRule(
    rules.value.filter((row) => row.enabled === '1'),
    hours,
    editingId.value,
  );
});

const summary = computed(() => summarizeRules(rules.value));
const ladder = computed(() => mergeAdjacentBands(buildLadder(rules.value)));
const deepestLabel = computed(() => {
  const rate = summary.value.deepestRate;
  return rate === null ? '' : formatDiscount(rate);
});

/** 不会改变价格的启用规则：规则 ID → 原因 */
const ineffectiveReasonMap = computed<Record<string, string>>(() => {
  const result: Record<string, string> = {};
  rules.value.forEach((row) => {
    if (row.enabled !== '1') {
      return;
    }
    const reason = explainIneffective(rules.value, row);
    if (reason) {
      result[row.id] = reason;
    }
  });
  return result;
});

const trial = computed(() =>
  simulatePrice(rules.value, trialBase.value ?? 0, trialHours.value ?? 0),
);

const trialTag = computed(() => {
  const result = trial.value;
  if (result.rate === null) {
    return '未命中，按基础出清价';
  }
  return `命中 ${result.hitCount} 条，取最深 ${formatDiscount(result.rate)}`;
});

const trialDriverHint = computed(() => {
  const result = trial.value;
  if (result.rate === null) {
    return '当前剩余时长未命中任何已启用档位，按基础出清价销售';
  }
  return `最深档规则：${result.driverNames.join('、')}`;
});

const trialTagType = computed<'info' | 'primary'>(() =>
  trial.value.rate === null ? 'info' : 'primary',
);

async function load() {
  loading.value = true;
  try {
    rules.value = (await getClearanceRules()) || [];
  } catch {
    // 请求层已统一提示错误，这里兜住异常并保证 loading 收尾，避免重复 toast
  } finally {
    loading.value = false;
  }
}

function handleDialogOpen() {
  nextTick(() => formRef.value?.clearValidate());
}

function openDialog(row?: ClearanceRuleRow) {
  editingId.value = row?.id || '';
  if (row) {
    const rowDraft = toRuleDraft(row);
    originalRate.value = rowDraft.discountRate;
    form.ruleName = rowDraft.ruleName;
    form.hoursBeforeExpire = rowDraft.hoursBeforeExpire;
    form.zhe = Number(rateToZhe(rowDraft.discountRate ?? 0.8).toFixed(1));
    form.enabled = rowDraft.enabled;
    form.sortOrder = rowDraft.sortOrder;
    form.remark = rowDraft.remark;
  } else {
    originalRate.value = null;
    form.ruleName = '';
    form.hoursBeforeExpire = 24;
    form.zhe = 8;
    form.enabled = true;
    form.sortOrder = 0;
    form.remark = '';
  }
  dialogVisible.value = true;
}

async function submit() {
  const valid = await formRef.value
    ?.validate()
    .then(() => true)
    .catch(() => false);
  if (!valid) {
    return;
  }
  const message = validateDraft(draft.value);
  if (message) {
    ElMessage.warning(message);
    return;
  }
  saving.value = true;
  try {
    const payload = buildRulePayload(draft.value);
    await (editingId.value
      ? updateClearanceRule(editingId.value, payload)
      : addClearanceRule(payload));
    ElMessage.success('规则已保存，系统已触发出清价重算');
    dialogVisible.value = false;
    await load();
  } catch {
    // 请求层已统一提示错误，保持弹窗打开便于修改
  } finally {
    saving.value = false;
  }
}

async function handleToggle(row: ClearanceRuleRow, enabled: boolean) {
  const previous = row.enabled;
  const next = enabled ? '1' : '0';
  if (previous === next || togglingId.value) {
    return;
  }
  togglingId.value = row.id;
  row.enabled = next;
  try {
    await updateClearanceRule(row.id, buildTogglePayload(row, enabled));
    ElMessage.success(enabled ? '规则已启用' : '规则已停用');
    // 重拉列表回填 updateTime，并避免本地状态与服务端漂移
    await load();
  } catch {
    // 失败（含超时/响应丢失）一律以服务端为准重新拉取
    row.enabled = previous;
    await load();
  } finally {
    togglingId.value = '';
  }
}

async function remove(row: ClearanceRuleRow) {
  try {
    await ElMessageBox.confirm(
      `确认删除规则「${row.ruleName}」？删除后该时间档不再叠加折扣，系统会触发出清价重算。`,
      '删除出清规则',
      {
        cancelButtonText: '取消',
        confirmButtonClass: 'el-button--danger',
        confirmButtonText: '删除',
        type: 'warning',
      },
    );
  } catch {
    return;
  }
  try {
    await deleteClearanceRule(row.id);
    ElMessage.success('已删除');
    await load();
  } catch {
    // 请求层已统一提示错误
  }
}

function rowClassName({ row }: { row: ClearanceRuleRow }) {
  return row.enabled === '1' ? '' : 'is-rule-disabled';
}

onMounted(load);
useKeepAliveRefresh(load);
</script>

<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <!-- 机制说明 + 有效阶梯 + 快速试算 -->
      <section class="cr-hero">
        <div class="cr-hero__intro">
          <span class="cr-hero__badge">
            <ElIcon aria-hidden="true" :size="18"><Timer /></ElIcon>
          </span>
          <div class="cr-hero__copy">
            <div class="cr-hero__title-row">
              <h2 class="cr-hero__title">出清时间阶梯</h2>
              <ElTag
                class="cr-hero__count"
                effect="plain"
                size="small"
                type="info"
              >
                启用 <b class="cr-strong">{{ summary.enabledCount }}</b> /
                {{ summary.total }} 条
              </ElTag>
              <ElTag
                v-if="deepestLabel"
                class="cr-hero__deepest"
                effect="plain"
                size="small"
              >
                最深 {{ deepestLabel }}
              </ElTag>
            </div>
            <p class="cr-hero__desc">
              出清价 = 基础出清价 × 命中档折扣率。商品剩余时长 ≤
              档位小时数即命中；同一时刻命中多档时只取最深一档，不连乘。保存、启停或删除规则后，系统会触发出清价重算。
            </p>
          </div>
        </div>

        <div class="cr-ladder-block">
          <div class="cr-ladder-head">
            <span class="cr-ladder-head__title">当前生效价格阶梯</span>
            <span class="cr-ladder-head__hint">
              从距到期最远
              <ElIcon aria-hidden="true"><ArrowRight /></ElIcon>
              最近
            </span>
          </div>

          <LadderChips
            v-if="ladder.length > 0"
            :bands="ladder"
            highlight-deepest
            label="当前生效价格阶梯"
            show-tooltip
          />

          <div v-else class="cr-ladder-empty">
            <ElIcon aria-hidden="true"><InfoFilled /></ElIcon>
            <span>还没有启用中的规则，当前商品都按基础出清价销售。</span>
            <ElButton link type="primary" @click="openDialog()">
              去配置
            </ElButton>
          </div>
        </div>

        <div class="cr-trial">
          <span class="cr-trial__label">快速试算</span>
          <span class="cr-trial__field">
            基础出清价
            <ElInputNumber
              v-model="trialBase"
              aria-label="基础出清价（元）"
              class="cr-trial__num"
              :min="0"
              :precision="2"
              :step="10"
              controls-position="right"
            />
          </span>
          <span class="cr-trial__field">
            剩余
            <ElInputNumber
              v-model="trialHours"
              aria-label="剩余小时数"
              class="cr-trial__num cr-trial__num--hours"
              :min="0"
              :max="8760"
              :precision="0"
              controls-position="right"
            />
            小时
          </span>
          <ElIcon aria-hidden="true" class="cr-trial__arrow">
            <ArrowRight />
          </ElIcon>
          <span class="cr-trial__result">
            ¥{{ formatMoney(trial.finalPrice) }}
          </span>
          <ElTooltip :content="trialDriverHint" placement="top">
            <ElTag
              class="cr-trial__tag"
              :type="trialTagType"
              size="small"
              effect="plain"
            >
              {{ trialTag }}
            </ElTag>
          </ElTooltip>
        </div>
      </section>

      <!-- 工具栏 -->
      <PageToolbar :total="rules.length" total-label="条规则">
        <template #left>
          <ElButton
            type="primary"
            :icon="Plus"
            @click="openDialog()"
            v-access:code="'promotion:shipactivity:save'"
          >
            新增规则
          </ElButton>
        </template>
        <template #right>
          <ElTooltip content="刷新规则列表" placement="top">
            <ElButton
              aria-label="刷新规则列表"
              circle
              :icon="Refresh"
              :loading="loading"
              @click="load"
            />
          </ElTooltip>
        </template>
      </PageToolbar>

      <!-- 规则表格 -->
      <div v-if="rules.length > 0 || loading" class="cr-table">
        <ElTable
          v-loading="loading"
          border
          class="hx-data-table"
          :data="rules"
          empty-text="暂无出清折扣规则"
          :row-class-name="rowClassName"
          row-key="id"
        >
          <ElTableColumn label="规则名称" min-width="210">
            <template #default="scope">
              <div
                class="cr-rule-name"
                :class="{ 'is-muted': scope.row.enabled !== '1' }"
              >
                <span class="cr-rule-name__text" :title="scope.row.ruleName">
                  {{ scope.row.ruleName }}
                </span>
                <ElTooltip
                  v-if="ineffectiveReasonMap[scope.row.id]"
                  :content="ineffectiveReasonMap[scope.row.id]"
                  placement="top"
                >
                  <ElTag
                    class="cr-warn-tag"
                    effect="plain"
                    size="small"
                    type="warning"
                  >
                    <ElIcon aria-hidden="true" class="cr-tag-icon">
                      <WarningFilled />
                    </ElIcon>
                    不改变价格
                  </ElTag>
                </ElTooltip>
              </div>
              <div
                v-if="scope.row.remark"
                class="cr-rule-remark"
                :class="{ 'is-muted': scope.row.enabled !== '1' }"
                :title="scope.row.remark"
              >
                {{ scope.row.remark }}
              </div>
            </template>
          </ElTableColumn>

          <ElTableColumn align="center" label="生效时间" width="150">
            <template #default="scope">
              <span
                class="cr-time-chip"
                :class="{ 'is-muted': scope.row.enabled !== '1' }"
              >
                <ElIcon aria-hidden="true"><Timer /></ElIcon>
                {{ formatHours(scope.row.hoursBeforeExpire) }}
              </span>
            </template>
          </ElTableColumn>

          <ElTableColumn align="center" label="折扣力度" width="180">
            <template #default="scope">
              <div
                class="cr-discount"
                :class="{ 'is-muted': scope.row.enabled !== '1' }"
              >
                <b>{{ formatDiscount(scope.row.discountRate) }}</b>
                <span>{{ formatRateHint(scope.row.discountRate) }}</span>
              </div>
            </template>
          </ElTableColumn>

          <ElTableColumn align="center" label="状态" width="130">
            <template #default="scope">
              <div class="cr-status">
                <ElSwitch
                  :aria-label="`${
                    scope.row.enabled === '1' ? '停用' : '启用'
                  }规则 ${scope.row.ruleName}`"
                  :disabled="!canSave || Boolean(togglingId)"
                  :loading="togglingId === scope.row.id"
                  :model-value="scope.row.enabled === '1'"
                  size="small"
                  @change="
                    (value) =>
                      handleToggle(
                        scope.row as ClearanceRuleRow,
                        Boolean(value),
                      )
                  "
                />
                <span class="cr-status__text">
                  {{ scope.row.enabled === '1' ? '启用' : '停用' }}
                </span>
              </div>
            </template>
          </ElTableColumn>

          <ElTableColumn align="center" label="最近修改" width="180">
            <template #default="scope">
              <span class="cr-updated">
                {{ scope.row.updateTime || scope.row.createTime || '—' }}
              </span>
            </template>
          </ElTableColumn>

          <ElTableColumn align="center" fixed="right" label="操作" width="150">
            <template #default="scope">
              <ElButton
                link
                type="primary"
                :icon="Edit"
                @click="openDialog(scope.row as ClearanceRuleRow)"
                v-access:code="'promotion:shipactivity:save'"
              >
                修改
              </ElButton>
              <ElButton
                link
                type="danger"
                :icon="Delete"
                @click="remove(scope.row as ClearanceRuleRow)"
                v-access:code="'promotion:shipactivity:save'"
              >
                删除
              </ElButton>
            </template>
          </ElTableColumn>
        </ElTable>
      </div>

      <!-- 空态 -->
      <div v-else class="cr-empty">
        <div class="cr-empty__icon">
          <ElIcon aria-hidden="true" :size="26"><Discount /></ElIcon>
        </div>
        <p class="cr-empty__title">还没有出清时间阶梯</p>
        <p class="cr-empty__desc">
          配置「剩余多少小时打几折」后，系统会在商品临近到期时自动应用出清折扣；
          越临近到期可以配置越深的折扣。
        </p>
        <ElButton
          type="primary"
          :icon="Plus"
          @click="openDialog()"
          v-access:code="'promotion:shipactivity:save'"
        >
          创建第一条规则
        </ElButton>
      </div>

      <!-- 新增 / 修改弹窗 -->
      <ElDialog
        v-model="dialogVisible"
        class="cr-dialog"
        :close-on-click-modal="false"
        :title="editingId ? '修改出清折扣规则' : '新增出清折扣规则'"
        top="8vh"
        width="640px"
        @open="handleDialogOpen"
      >
        <ElForm
          ref="formRef"
          :model="form"
          :rules="dialogRules"
          class="cr-form"
          label-width="96px"
        >
          <ElFormItem label="规则名称" prop="ruleName">
            <ElInput
              v-model="form.ruleName"
              maxlength="64"
              placeholder="如：临期 24 小时 8 折"
              show-word-limit
            />
          </ElFormItem>

          <ElFormItem label="剩余时长" prop="hoursBeforeExpire">
            <div class="cr-field">
              <div class="cr-field__row">
                <ElInputNumber
                  v-model="form.hoursBeforeExpire"
                  aria-label="剩余到期时长（小时）"
                  class="cr-field__num"
                  :min="1"
                  :precision="0"
                  controls-position="right"
                />
                <span class="cr-field__suffix">小时</span>
                <span class="cr-field__hint">
                  剩余到期时长 ≤ 该小时数时命中本档
                </span>
              </div>
              <div class="cr-presets">
                <ElButton
                  v-for="hours in HOUR_PRESETS"
                  :key="hours"
                  :aria-pressed="form.hoursBeforeExpire === hours"
                  round
                  size="small"
                  :type="
                    form.hoursBeforeExpire === hours ? 'primary' : 'default'
                  "
                  @click="form.hoursBeforeExpire = hours"
                >
                  {{ hours }} 小时
                </ElButton>
              </div>
            </div>
          </ElFormItem>

          <ElFormItem label="折扣力度" prop="zhe">
            <div class="cr-field">
              <div class="cr-field__row">
                <ElInputNumber
                  v-model="form.zhe"
                  aria-label="折扣力度（折）"
                  class="cr-field__num cr-field__num--small"
                  :min="0.1"
                  :max="9.9"
                  :precision="1"
                  :step="0.1"
                  controls-position="right"
                />
                <span class="cr-field__suffix">折</span>
                <span class="cr-field__hint">
                  {{ formatRateHint(effectiveRate) }}
                </span>
              </div>
              <div class="cr-presets">
                <ElButton
                  v-for="zhe in ZHE_PRESETS"
                  :key="zhe"
                  :aria-pressed="form.zhe === zhe"
                  round
                  size="small"
                  :type="form.zhe === zhe ? 'primary' : 'default'"
                  @click="form.zhe = zhe"
                >
                  {{ zhe }} 折
                </ElButton>
              </div>
              <p v-if="rateRoundingHint" class="cr-field__notice">
                {{ rateRoundingHint }}
              </p>
            </div>
          </ElFormItem>

          <ElFormItem label="启用">
            <div class="cr-field__row">
              <ElSwitch v-model="form.enabled" />
              <span class="cr-field__hint">
                {{
                  form.enabled
                    ? '保存后立即参与出清价计算'
                    : '保存后该档暂不参与计算'
                }}
              </span>
            </div>
          </ElFormItem>

          <ElFormItem label="展示顺序">
            <div class="cr-field__row">
              <ElInputNumber
                v-model="form.sortOrder"
                aria-label="展示顺序"
                class="cr-field__num cr-field__num--small"
                :precision="0"
                controls-position="right"
              />
              <span class="cr-field__hint">
                仅影响后台列表展示顺序，不影响算价
              </span>
            </div>
          </ElFormItem>

          <ElFormItem label="备注">
            <ElInput
              v-model="form.remark"
              maxlength="255"
              placeholder="选填，仅后台可见"
              :rows="2"
              show-word-limit
              type="textarea"
            />
          </ElFormItem>
        </ElForm>

        <ElAlert
          v-if="sameHoursRule"
          class="cr-dialog__alert"
          :closable="false"
          show-icon
          :title="`已有同时间档规则「${sameHoursRule.ruleName}」，两档会同时命中，系统只取更深的折扣。`"
          type="info"
        />
        <ElAlert
          v-if="draftIneffectiveReason"
          class="cr-dialog__alert"
          :closable="false"
          show-icon
          :title="`该规则保存后不会改变出清价：${draftIneffectiveReason}`"
          type="warning"
        />

        <div class="cr-preview">
          <div class="cr-preview__head">
            <span class="cr-preview__title">配置后生效阶梯</span>
            <span class="cr-preview__hint">从距到期最远 → 最近</span>
          </div>
          <LadderChips
            v-if="previewLadder.length > 0"
            :bands="previewLadder"
            :current-index="previewBandIndex"
            compact
            label="配置后生效价格阶梯"
          />
          <p v-else class="cr-preview__empty">
            填写有效的剩余时长与折扣力度后，这里会实时预览配置后的价格阶梯。
          </p>
        </div>

        <template #footer>
          <div class="hx-form-footer">
            <ElButton @click="dialogVisible = false">取消</ElButton>
            <ElButton :loading="saving" type="primary" @click="submit">
              保存
            </ElButton>
          </div>
        </template>
      </ElDialog>
    </div>
  </div>
</template>

<style scoped>
@media (max-width: 1400px) {
  .cr-trial__result {
    font-size: 16px;
  }
}

.cr-hero {
  padding: 16px;
  margin-bottom: 12px;
  background:
    radial-gradient(
      120% 180% at 0% 0%,
      var(--el-color-primary-light-9),
      transparent 62%
    ),
    var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
}

.cr-hero__intro {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.cr-hero__badge {
  display: inline-flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-8);
  border-radius: 10px;
}

.cr-hero__copy {
  min-width: 0;
}

.cr-hero__title-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.cr-hero__title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  line-height: 24px;
  color: var(--el-text-color-primary);
}

.cr-hero__deepest {
  font-weight: 600;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-5);
}

.cr-hero__desc {
  max-width: 900px;
  margin: 6px 0 0;
  font-size: 13px;
  line-height: 1.7;
  color: var(--el-text-color-regular);
}

.cr-strong {
  font-weight: 600;
  color: var(--el-text-color-primary);
}

/* 语义标签在 light 主题下用 Element Plus 默认浅色文字对比度不足：
   用正文色承载文字、语义色只做边框/背景/图标，保证 ≥4.5:1 */
.cr-hero__count {
  color: var(--el-text-color-regular);
}

.cr-warn-tag {
  color: var(--el-text-color-regular);
  background: var(--el-color-warning-light-9);
  border-color: var(--el-color-warning-light-5);
}

.cr-warn-tag .cr-tag-icon {
  color: var(--el-color-warning-dark-2);
}

/* 暗色主题下 primary/light-3 都是深色混合（light-3 反而更暗），语义仍由边框/底色表达，
   文字统一用正文色保证 ≥4.5:1 */
html.dark .cr-hero__deepest,
html.dark .cr-trial__tag {
  color: var(--el-text-color-primary);
}

/* ---------------------------------------------------------------------------
 * 阶梯
 * ------------------------------------------------------------------------- */
.cr-ladder-block {
  padding-top: 12px;
  margin-top: 14px;
  border-top: 1px dashed var(--el-border-color-lighter);
}

.cr-ladder-head {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.cr-ladder-head__title {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--el-text-color-regular);
}

.cr-ladder-head__hint {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  font-size: 12px;
  color: var(--el-text-color-regular);
}

.cr-ladder-empty {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  font-size: 12.5px;
  color: var(--el-text-color-regular);
}

.cr-ladder-empty .el-icon {
  color: var(--el-color-warning);
}

/* ---------------------------------------------------------------------------
 * 快速试算
 * ------------------------------------------------------------------------- */
.cr-trial {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 14px;
  align-items: center;
  padding: 10px 12px;
  margin-top: 14px;
  font-size: 12.5px;
  color: var(--el-text-color-regular);
  background: var(--el-fill-color-lighter);
  border-radius: 10px;
}

.cr-trial__label {
  font-weight: 600;
  color: var(--el-text-color-regular);
}

.cr-trial__field {
  display: inline-flex;
  gap: 6px;
  align-items: center;
}

.cr-trial__num {
  width: 118px;
}

.cr-trial__num--hours {
  width: 92px;
}

.cr-trial__arrow {
  color: var(--el-text-color-placeholder);
}

.cr-trial__result {
  font-size: 18px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--el-color-primary);
}

/* ---------------------------------------------------------------------------
 * 表格
 * ------------------------------------------------------------------------- */
.cr-table {
  flex: none;
}

.cr-table :deep(.is-rule-disabled) td.el-table__cell {
  background: var(--el-fill-color-light);
}

/* 停用行弱化：子元素自身都声明了颜色，必须用后代选择器覆盖（.is-muted 的继承对它们无效） */
.cr-table :deep(.is-rule-disabled) .cr-rule-name__text,
.cr-table :deep(.is-rule-disabled) .cr-rule-remark,
.cr-table :deep(.is-rule-disabled) .cr-time-chip,
.cr-table :deep(.is-rule-disabled) .cr-discount b,
.cr-table :deep(.is-rule-disabled) .cr-discount span {
  color: var(--el-text-color-secondary);
}

.cr-rule-name {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.cr-rule-name__text {
  display: -webkit-box;
  overflow: hidden;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  font-weight: 500;
  color: var(--el-text-color-primary);
  word-break: break-all;
  -webkit-box-orient: vertical;
}

.cr-rule-remark {
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
  line-height: 18px;
  color: var(--el-text-color-regular);
  white-space: nowrap;
}

.is-muted {
  color: var(--el-text-color-secondary);
}

.cr-tag-icon {
  margin-right: 2px;
  vertical-align: -2px;
}

.cr-time-chip {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  padding: 2px 8px;
  font-size: 12.5px;
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-regular);
  background: var(--el-fill-color-light);
  border-radius: 999px;
}

.cr-discount {
  display: flex;
  flex-direction: column;
  gap: 1px;
  line-height: 18px;
}

.cr-discount b {
  font-size: 14px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-primary);
}

.cr-discount span {
  font-size: 12px;
  color: var(--el-text-color-regular);
}

.cr-status {
  display: inline-flex;
  gap: 6px;
  align-items: center;
}

.cr-status__text {
  font-size: 12.5px;
  color: var(--el-text-color-regular);
}

.cr-updated {
  font-size: 12.5px;
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-regular);
  white-space: nowrap;
}

/* ---------------------------------------------------------------------------
 * 空态
 * ------------------------------------------------------------------------- */
.cr-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 56px 16px;
  text-align: center;
  background: var(--el-bg-color);
  border: 1px dashed var(--el-border-color);
  border-radius: 12px;
}

.cr-empty__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  margin-bottom: 12px;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border-radius: 50%;
}

.cr-empty__title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.cr-empty__desc {
  max-width: 420px;
  margin: 6px 0 16px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--el-text-color-regular);
}

/* ---------------------------------------------------------------------------
 * 弹窗
 * ------------------------------------------------------------------------- */
.hx-layout-container :deep(.cr-dialog) {
  display: flex;
  flex-direction: column;
  max-height: calc(100vh - 8vh - 32px);
}

.hx-layout-container :deep(.cr-dialog .el-dialog__body) {
  flex: 1;
  min-height: 0;
  padding-top: 8px;
  overflow-y: auto;
}

.cr-form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.cr-field {
  width: 100%;
}

.cr-field__row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.cr-field__suffix {
  font-size: 12.5px;
  color: var(--el-text-color-regular);
}

.cr-field__num {
  width: 130px;
}

.cr-field__num--small {
  width: 110px;
}

.cr-field__hint {
  font-size: 12px;
  color: var(--el-text-color-regular);
}

.cr-field__notice {
  margin: 6px 0 0;
  font-size: 12px;
  font-weight: 500;
  line-height: 18px;
  color: var(--el-text-color-regular);
}

.cr-presets {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.cr-presets .el-button + .el-button {
  margin-left: 0;
}

.cr-dialog__alert {
  margin-bottom: 10px;
}

.cr-preview {
  padding: 12px;
  margin-top: 4px;
  background: var(--el-fill-color-lighter);
  border: 1px dashed var(--el-border-color);
  border-radius: 10px;
}

.cr-preview__head {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.cr-preview__title {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--el-text-color-regular);
}

.cr-preview__hint {
  font-size: 12px;
  color: var(--el-text-color-regular);
}

.cr-preview__empty {
  margin: 0;
  font-size: 12.5px;
  color: var(--el-text-color-regular);
}

/* ---------------------------------------------------------------------------
 * 顶部机制卡片
 * ------------------------------------------------------------------------- */
</style>

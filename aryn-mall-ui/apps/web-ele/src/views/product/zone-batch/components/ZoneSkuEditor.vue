<script lang="ts" setup>
/**
 * 单商品「专区标签」编辑器（契约 §3.3）。
 *
 * - 挂载/`spuId` 变化时调用 `getZoneTags` 回显 SPU + SKU 精调；
 * - SPU 区：会员/出清加入状态与规则编辑，支持显式清空规则（clearMemberRule/clearClearanceRule）；
 * - SKU 表：逐行「继承/覆盖」开关，覆盖时可精调规则或显式排除；
 * - 保存调用 `saveZoneTags`（`replaceSkuRules=true` 精确覆盖），成功后 emit `saved`；
 * - 独立保存，不依赖外层商品主表单；不破坏主表单其它页签与提交流程。
 */
import type {
  ZoneSkuEditRow,
  ZoneTagDetail,
  ZoneTagSavePayload,
  ZoneWarning,
} from '../types';

import { computed, reactive, ref, watch } from 'vue';

import {
  ElAlert,
  ElButton,
  ElCol,
  ElDatePicker,
  ElFormItem,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElRow,
  ElSelect,
  ElSwitch,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { getZoneTags, saveZoneTags } from '#/api/product/goods-zone';

import {
  buildSkuSaveItems,
  resolveErrorMessage,
  toSkuEditRow,
  zoneWarningTagType,
} from '../logic';

interface SpuZoneForm {
  clearanceDiscountRate: null | number;
  clearanceExpireTime: null | string;
  clearancePrice: null | number;
  isClearanceItem: 0 | 1;
  isMemberItem: 0 | 1;
  memberDiscountRate: null | number;
  memberPriceFixed: null | number;
  memberPriceFloor: null | number;
}

const props = defineProps<{ spuId: string }>();

const emit = defineEmits<{
  /** 保存成功并回填最新专区状态后触发；参数为最新详情，便于外层同步主表单字段 */
  saved: [detail?: ZoneTagDetail];
}>();

const loading = ref(false);
const saving = ref(false);
const loadError = ref('');
const detail = ref<null | ZoneTagDetail>(null);
const warnings = ref<ZoneWarning[]>([]);
const memberRuleCleared = ref(false);
const clearanceRuleCleared = ref(false);

const spuForm = reactive<SpuZoneForm>({
  clearanceDiscountRate: null,
  clearanceExpireTime: null,
  clearancePrice: null,
  isClearanceItem: 0,
  isMemberItem: 0,
  memberDiscountRate: null,
  memberPriceFixed: null,
  memberPriceFloor: null,
});

const skuRows = ref<ZoneSkuEditRow[]>([]);

const skuOverrideCount = computed(
  () =>
    skuRows.value.filter((row) => row.memberOverride || row.clearanceOverride)
      .length,
);

function toNumber(value: null | number | undefined) {
  if (value === null || value === undefined) return undefined;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : undefined;
}

function applyDetail(payload: null | undefined | ZoneTagDetail) {
  detail.value = payload ?? null;
  spuForm.isMemberItem = Number(payload?.isMemberItem) === 1 ? 1 : 0;
  spuForm.memberDiscountRate = toNumber(payload?.memberDiscountRate) ?? null;
  spuForm.memberPriceFixed = toNumber(payload?.memberPriceFixed) ?? null;
  spuForm.memberPriceFloor = toNumber(payload?.memberPriceFloor) ?? null;
  spuForm.isClearanceItem = Number(payload?.isClearanceItem) === 1 ? 1 : 0;
  spuForm.clearancePrice = toNumber(payload?.clearancePrice) ?? null;
  spuForm.clearanceDiscountRate =
    toNumber(payload?.clearanceDiscountRate) ?? null;
  spuForm.clearanceExpireTime = payload?.clearanceExpireTime ?? null;
  memberRuleCleared.value = false;
  clearanceRuleCleared.value = false;
  warnings.value = payload?.warnings ?? [];
  skuRows.value = (payload?.skus ?? []).map((sku) => toSkuEditRow(sku));
}

async function load() {
  if (!props.spuId) return;
  loading.value = true;
  loadError.value = '';
  try {
    applyDetail(await getZoneTags(props.spuId));
  } catch (error) {
    loadError.value = resolveErrorMessage(error, '读取专区标签失败');
  } finally {
    loading.value = false;
  }
}

watch(
  () => props.spuId,
  () => {
    void load();
  },
  { immediate: true },
);

function toggleMemberJoin(value: boolean) {
  spuForm.isMemberItem = value ? 1 : 0;
  if (!value) memberRuleCleared.value = false;
}

function toggleClearanceJoin(value: boolean) {
  spuForm.isClearanceItem = value ? 1 : 0;
  if (!value) clearanceRuleCleared.value = false;
}

async function clearMemberRules() {
  try {
    await ElMessageBox.confirm(
      '清空后会员折扣率、固定价与下限将按专区默认规则生效（会员标签保留）。是否继续？',
      '清空会员规则',
      {
        cancelButtonText: '取消',
        confirmButtonText: '确认清空',
        type: 'warning',
      },
    );
  } catch {
    return;
  }
  memberRuleCleared.value = true;
}

async function clearClearanceRules() {
  try {
    await ElMessageBox.confirm(
      '清空后出清价、出清折扣与截止时间将被移除（出清标签保留），该商品可能因缺规则而不产生出清价。是否继续？',
      '清空出清规则',
      {
        cancelButtonText: '取消',
        confirmButtonText: '确认清空',
        type: 'warning',
      },
    );
  } catch {
    return;
  }
  clearanceRuleCleared.value = true;
}

function buildPayload(): ZoneTagSavePayload {
  const payload: ZoneTagSavePayload = {
    clearClearanceRule: clearanceRuleCleared.value === true,
    clearMemberRule: memberRuleCleared.value === true,
    replaceSkuRules: true,
    skus: buildSkuSaveItems(skuRows.value),
  };
  if (spuForm.isMemberItem === 1) {
    payload.memberTag = true;
    payload.memberDiscountRate = memberRuleCleared.value
      ? null
      : (spuForm.memberDiscountRate ?? null);
    payload.memberPriceFixed = memberRuleCleared.value
      ? null
      : (spuForm.memberPriceFixed ?? null);
    payload.memberPriceFloor = memberRuleCleared.value
      ? null
      : (spuForm.memberPriceFloor ?? null);
  } else {
    payload.memberTag = false;
  }
  if (spuForm.isClearanceItem === 1) {
    payload.clearanceTag = true;
    payload.clearancePrice = clearanceRuleCleared.value
      ? null
      : (spuForm.clearancePrice ?? null);
    payload.clearanceDiscountRate = clearanceRuleCleared.value
      ? null
      : (spuForm.clearanceDiscountRate ?? null);
    payload.clearanceExpireTime = clearanceRuleCleared.value
      ? null
      : (spuForm.clearanceExpireTime ?? null);
  } else {
    payload.clearanceTag = false;
  }
  return payload;
}

async function save() {
  if (!props.spuId) {
    ElMessage.warning('请先保存商品基本信息，再配置专区标签');
    return;
  }
  if (saving.value) return;
  if (
    spuForm.isClearanceItem === 1 &&
    !clearanceRuleCleared.value &&
    spuForm.clearancePrice === null &&
    spuForm.clearanceDiscountRate === null
  ) {
    try {
      await ElMessageBox.confirm(
        '未填写出清价/出清折扣：若专区也未配置默认折扣，保存后该商品出清价不会生效。是否继续保存？',
        '出清规则缺失',
        {
          cancelButtonText: '去填写',
          confirmButtonText: '继续保存',
          type: 'warning',
        },
      );
    } catch {
      return;
    }
  }
  saving.value = true;
  try {
    await saveZoneTags(props.spuId, buildPayload());
    ElMessage.success('专区标签已保存');
    let latest: null | ZoneTagDetail = detail.value;
    try {
      latest = await getZoneTags(props.spuId);
      applyDetail(latest);
    } catch {
      // 回显刷新失败不影响已保存结果：保留本地编辑值
      memberRuleCleared.value = false;
      clearanceRuleCleared.value = false;
    }
    emit('saved', latest ?? undefined);
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '专区标签保存失败'));
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <div v-loading="loading" class="zone-sku-editor">
    <ElAlert
      type="info"
      :closable="false"
      show-icon
      class="mb10"
      title="专区按商品标签自动选品与算价：会员价 = min(售价 × 会员折扣率, 会员固定价)，且不低于会员价下限；出清价按固定价或原价 × 出清折扣计算，到期自动退出。本页签独立保存，不走底部商品「确认」按钮。"
    />

    <div v-if="!spuId" class="zone-sku-editor__tip">
      请先保存商品基本信息，再配置专区标签与 SKU 精调。
    </div>

    <template v-else>
      <ElAlert
        v-if="loadError"
        type="error"
        :closable="false"
        show-icon
        :title="loadError"
        class="mb10"
      >
        <ElButton size="small" @click="load">重新加载</ElButton>
      </ElAlert>

      <template v-else>
        <ElAlert
          v-if="warnings.length > 0"
          type="warning"
          :closable="false"
          show-icon
          class="mb10"
          title="当前专区状态提示"
        >
          <div
            v-for="warning in warnings"
            :key="`${warning.code}-${warning.message}`"
            class="zone-sku-editor__warning"
          >
            <ElTag
              :type="zoneWarningTagType(warning.code)"
              size="small"
              effect="plain"
            >
              {{ warning.code }}
            </ElTag>
            <span>{{ warning.message }}</span>
          </div>
        </ElAlert>

        <!-- 会员专区（SPU 级） -->
        <div class="zone-sku-editor__section">
          <div class="zone-sku-editor__section-head">
            <span class="zone-sku-editor__title">会员专区（SPU 级）</span>
            <ElSwitch
              :model-value="spuForm.isMemberItem === 1"
              inline-prompt
              active-text="加入"
              inactive-text="移出"
              @update:model-value="(value) => toggleMemberJoin(Boolean(value))"
            />
          </div>
          <template v-if="spuForm.isMemberItem === 1">
            <ElRow :gutter="12">
              <ElCol :span="8">
                <ElFormItem label="会员折扣率" class="mb0">
                  <ElInputNumber
                    v-model="spuForm.memberDiscountRate"
                    :min="0.0001"
                    :max="0.9999"
                    :step="0.01"
                    :precision="4"
                    controls-position="right"
                    placeholder="如 0.9 = 9 折，空=继承"
                    style="width: 100%"
                    :disabled="saving || memberRuleCleared"
                  />
                </ElFormItem>
              </ElCol>
              <ElCol :span="8">
                <ElFormItem label="会员固定价" class="mb0">
                  <ElInputNumber
                    v-model="spuForm.memberPriceFixed"
                    :min="0.01"
                    :precision="2"
                    controls-position="right"
                    placeholder="与按率算价取低"
                    style="width: 100%"
                    :disabled="saving || memberRuleCleared"
                  />
                </ElFormItem>
              </ElCol>
              <ElCol :span="8">
                <ElFormItem label="会员价下限" class="mb0">
                  <ElInputNumber
                    v-model="spuForm.memberPriceFloor"
                    :min="0.01"
                    :precision="2"
                    controls-position="right"
                    placeholder="空=按专区成本兜底"
                    style="width: 100%"
                    :disabled="saving || memberRuleCleared"
                  />
                </ElFormItem>
              </ElCol>
            </ElRow>
            <div class="zone-sku-editor__rule-actions">
              <template v-if="memberRuleCleared">
                <ElTag type="warning" size="small">保存后将清空会员规则</ElTag>
                <ElButton link @click="memberRuleCleared = false">
                  取消清空
                </ElButton>
              </template>
              <ElButton
                v-else
                link
                type="danger"
                :disabled="saving"
                @click="clearMemberRules"
              >
                清空会员规则
              </ElButton>
            </div>
          </template>
          <div v-else class="zone-sku-editor__tip">
            当前未加入会员专区；加入后才会参与 C 端会员价计算。
          </div>
        </div>

        <!-- 出清专区（SPU 级） -->
        <div class="zone-sku-editor__section">
          <div class="zone-sku-editor__section-head">
            <span class="zone-sku-editor__title">低价捡漏（SPU 级）</span>
            <ElSwitch
              :model-value="spuForm.isClearanceItem === 1"
              inline-prompt
              active-text="加入"
              inactive-text="移出"
              @update:model-value="
                (value) => toggleClearanceJoin(Boolean(value))
              "
            />
          </div>
          <template v-if="spuForm.isClearanceItem === 1">
            <ElRow :gutter="12">
              <ElCol :span="8">
                <ElFormItem label="出清价" class="mb0">
                  <ElInputNumber
                    v-model="spuForm.clearancePrice"
                    :min="0.01"
                    :precision="2"
                    controls-position="right"
                    placeholder="与出清折扣二选一"
                    style="width: 100%"
                    :disabled="saving || clearanceRuleCleared"
                  />
                </ElFormItem>
              </ElCol>
              <ElCol :span="8">
                <ElFormItem label="出清折扣率" class="mb0">
                  <ElInputNumber
                    v-model="spuForm.clearanceDiscountRate"
                    :min="0.0001"
                    :max="0.9999"
                    :step="0.01"
                    :precision="4"
                    controls-position="right"
                    placeholder="对原价计算"
                    style="width: 100%"
                    :disabled="saving || clearanceRuleCleared"
                  />
                </ElFormItem>
              </ElCol>
              <ElCol :span="8">
                <ElFormItem label="出清截止时间" class="mb0">
                  <ElDatePicker
                    v-model="spuForm.clearanceExpireTime"
                    type="datetime"
                    value-format="YYYY-MM-DD HH:mm:ss"
                    placeholder="空=长期有效"
                    style="width: 100%"
                    :disabled="saving || clearanceRuleCleared"
                  />
                </ElFormItem>
              </ElCol>
            </ElRow>
            <div class="zone-sku-editor__rule-actions">
              <template v-if="clearanceRuleCleared">
                <ElTag type="warning" size="small">保存后将清空出清规则</ElTag>
                <ElButton link @click="clearanceRuleCleared = false">
                  取消清空
                </ElButton>
              </template>
              <ElButton
                v-else
                link
                type="danger"
                :disabled="saving"
                @click="clearClearanceRules"
              >
                清空出清规则
              </ElButton>
            </div>
          </template>
          <div v-else class="zone-sku-editor__tip">
            当前未加入低价捡漏；加入后才会进入 C 端出清列表。
          </div>
        </div>

        <!-- SKU 精调 -->
        <div class="zone-sku-editor__section">
          <div class="zone-sku-editor__section-head">
            <span class="zone-sku-editor__title">
              SKU 级精调（{{ skuOverrideCount }} 个规格已覆盖）
            </span>
            <span class="zone-sku-editor__tip-inline">
              默认继承
              SPU；打开「覆盖」可对单个规格精调或排除。保存使用精确覆盖：关闭覆盖会把该
              SKU 恢复为继承 SPU。
            </span>
          </div>
          <ElTable
            v-if="skuRows.length > 0"
            :data="skuRows"
            row-key="skuId"
            border
            size="small"
          >
            <ElTableColumn label="规格" min-width="130">
              <template #default="scope">
                {{ scope.row.specsName || scope.row.skuId }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="售价 / 原价" width="120">
              <template #default="scope">
                <div>¥{{ scope.row.salesPrice ?? '-' }}</div>
                <div class="zone-sku-editor__tip-inline">
                  ¥{{ scope.row.originalPrice ?? '-' }}
                </div>
              </template>
            </ElTableColumn>
            <ElTableColumn label="会员专区" min-width="280">
              <template #default="scope">
                <div class="zone-sku-editor__sku-head">
                  <ElSwitch
                    v-model="scope.row.memberOverride"
                    size="small"
                    inline-prompt
                    active-text="覆盖"
                    inactive-text="继承"
                  />
                  <span
                    v-if="!scope.row.memberOverride"
                    class="zone-sku-editor__tip-inline"
                  >
                    继承 SPU 会员规则
                  </span>
                </div>
                <template v-if="scope.row.memberOverride">
                  <ElSelect
                    v-model="scope.row.memberTag"
                    size="small"
                    style="width: 100%; margin-bottom: 6px"
                  >
                    <ElOption :value="1" label="参与会员专区" />
                    <ElOption :value="0" label="从会员专区排除" />
                  </ElSelect>
                  <div
                    v-if="scope.row.memberTag === 1"
                    class="zone-sku-editor__sku-rules"
                  >
                    <ElInputNumber
                      v-model="scope.row.memberDiscountRate"
                      :min="0.0001"
                      :max="0.9999"
                      :step="0.01"
                      :precision="4"
                      size="small"
                      controls-position="right"
                      placeholder="折扣率(空=继承)"
                    />
                    <ElInputNumber
                      v-model="scope.row.memberPriceFixed"
                      :min="0.01"
                      :precision="2"
                      size="small"
                      controls-position="right"
                      placeholder="固定价(空=继承)"
                    />
                    <ElInputNumber
                      v-model="scope.row.memberPriceFloor"
                      :min="0.01"
                      :precision="2"
                      size="small"
                      controls-position="right"
                      placeholder="下限(空=继承)"
                    />
                  </div>
                </template>
              </template>
            </ElTableColumn>
            <ElTableColumn label="出清专区" min-width="280">
              <template #default="scope">
                <div class="zone-sku-editor__sku-head">
                  <ElSwitch
                    v-model="scope.row.clearanceOverride"
                    size="small"
                    inline-prompt
                    active-text="覆盖"
                    inactive-text="继承"
                  />
                  <span
                    v-if="!scope.row.clearanceOverride"
                    class="zone-sku-editor__tip-inline"
                  >
                    继承 SPU 出清规则
                  </span>
                </div>
                <template v-if="scope.row.clearanceOverride">
                  <ElSelect
                    v-model="scope.row.clearanceTag"
                    size="small"
                    style="width: 100%; margin-bottom: 6px"
                  >
                    <ElOption :value="1" label="参与低价捡漏" />
                    <ElOption :value="0" label="从低价捡漏排除" />
                  </ElSelect>
                  <div
                    v-if="scope.row.clearanceTag === 1"
                    class="zone-sku-editor__sku-rules"
                  >
                    <ElInputNumber
                      v-model="scope.row.clearancePrice"
                      :min="0.01"
                      :precision="2"
                      size="small"
                      controls-position="right"
                      placeholder="出清价(空=继承)"
                    />
                    <ElInputNumber
                      v-model="scope.row.clearanceDiscountRate"
                      :min="0.0001"
                      :max="0.9999"
                      :step="0.01"
                      :precision="4"
                      size="small"
                      controls-position="right"
                      placeholder="折扣率(空=继承)"
                    />
                    <ElDatePicker
                      v-model="scope.row.clearanceExpireTime"
                      type="datetime"
                      value-format="YYYY-MM-DD HH:mm:ss"
                      size="small"
                      placeholder="截止时间(空=继承)"
                    />
                  </div>
                </template>
              </template>
            </ElTableColumn>
          </ElTable>
          <div v-else class="zone-sku-editor__tip">
            该商品没有可精调的 SKU 数据（单规格或多规格未生成），SPU
            级规则即对全部规格生效。
          </div>
        </div>

        <div class="zone-sku-editor__footer">
          <span class="zone-sku-editor__tip-inline">
            保存仅更新专区标签与 SKU
            精调，不影响商品其它信息；保存后由后端尝试同步专区价格活动。
          </span>
          <div class="zone-sku-editor__footer-buttons">
            <ElButton :disabled="saving" @click="load">重新加载</ElButton>
            <ElButton type="primary" :loading="saving" @click="save">
              保存专区标签
            </ElButton>
          </div>
        </div>
      </template>
    </template>
  </div>
</template>

<style scoped lang="scss">
.zone-sku-editor {
  &__section {
    padding: 12px 14px;
    margin-bottom: 12px;
    background: var(--el-fill-color-lighter);
    border-radius: 8px;
  }

  &__section-head {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 10px;
  }

  &__title {
    font-weight: 600;
  }

  &__tip {
    padding: 6px 0;
    font-size: 12px;
    line-height: 20px;
    color: var(--el-text-color-secondary);
  }

  &__tip-inline {
    font-size: 12px;
    font-weight: 400;
    line-height: 18px;
    color: var(--el-text-color-secondary);
  }

  &__warning {
    display: flex;
    gap: 8px;
    align-items: center;
    font-size: 12px;
    line-height: 20px;
  }

  &__rule-actions {
    display: flex;
    gap: 8px;
    align-items: center;
    justify-content: flex-end;
    padding-top: 6px;
  }

  &__sku-head {
    display: flex;
    gap: 8px;
    align-items: center;
    margin-bottom: 6px;
  }

  &__sku-rules {
    display: flex;
    flex-direction: column;
    gap: 6px;

    .el-input-number,
    .el-date-editor {
      width: 100%;
    }
  }

  &__footer {
    display: flex;
    flex-wrap: wrap;
    gap: 12px;
    align-items: center;
    justify-content: space-between;
    padding-top: 8px;
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
</style>

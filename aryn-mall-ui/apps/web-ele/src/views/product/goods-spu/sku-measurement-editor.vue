<script setup lang="ts">
import type { SkuMeasurementForm } from './measurement-form';

import { computed, ref } from 'vue';

import {
  ElAlert,
  ElButton,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElOption,
  ElSelect,
} from 'element-plus';

import { previewMeasurement } from '#/api/product/sku-measurement';

import { normalizeMeasurement, validateMeasurement } from './measurement-form';
import SkuIdentifierPanel from './sku-identifier-panel.vue';

const props = defineProps<{ modelValue: SkuMeasurementForm }>();
const emit = defineEmits<{
  'update:modelValue': [value: SkuMeasurementForm];
}>();
const form = computed(() => normalizeMeasurement(props.modelValue));
const units: Record<string, string> = {
  MASS: 'g',
  VOLUME: 'ml',
  LENGTH: 'mm',
  COUNT: 'pcs',
};
function set(field: keyof SkuMeasurementForm, value: unknown) {
  emit('update:modelValue', { ...form.value, [field]: value });
}
function dimension(value: string) {
  emit('update:modelValue', {
    ...form.value,
    baseDimension: value || null,
    baseUnit: units[value] ?? null,
    baseQtyPerSalesUnit: value ? form.value.baseQtyPerSalesUnit : null,
    quantityPrecision: value ? form.value.quantityPrecision : 'UNKNOWN',
  });
}
const raw = ref('3');
const rawUnit = ref('斤');
const busy = ref(false);
const result = ref('');
async function preview() {
  const error = validateMeasurement(form.value);
  if (error) {
    result.value = error;
    return;
  }
  busy.value = true;
  try {
    const decision = await previewMeasurement(
      form.value,
      raw.value,
      rawUnit.value,
    );
    result.value =
      decision.suggestedSalesQuantity === null ||
      decision.suggestedSalesQuantity === undefined
        ? `待核对：${decision.unitStatus} / ${decision.reason}`
        : `建议 ${decision.suggestedSalesQuantity} ${form.value.salesUnit}；多买基准量 ${decision.excessBaseQuantity ?? '未知'} ${form.value.baseUnit ?? ''}；${decision.requiresReview ? '需要确认' : '精确换算'}`;
  } catch (error_) {
    result.value = error_ instanceof Error ? error_.message : '换算预览失败';
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <div class="grid gap-3" style="min-width: 300px">
    <ElAlert
      v-if="form.quantityPrecision === 'UNKNOWN'"
      title="待治理：未审核重量，仍可按整数销售单位购买。"
      :closable="false"
    />
    <ElAlert
      v-if="form.quantityPrecision === 'APPROX'"
      title="约量只提供采购建议，外部重量换算需要人工确认。"
      :closable="false"
    />
    <ElFormItem label="销售单位">
      <ElInput
        :model-value="form.salesUnit ?? ''"
        placeholder="件、袋、箱、把等"
        @update:model-value="(value) => set('salesUnit', value)"
      />
    </ElFormItem>
    <ElFormItem label="基准量纲">
      <ElSelect
        :model-value="form.baseDimension ?? ''"
        @update:model-value="dimension"
      >
        <ElOption label="未知" value="" /><ElOption
          label="质量(g)"
          value="MASS"
        /><ElOption label="体积(ml)" value="VOLUME" /><ElOption
          label="长度(mm)"
          value="LENGTH"
        /><ElOption label="计数(pcs)" value="COUNT" />
      </ElSelect>
    </ElFormItem>
    <ElFormItem label="每销售单位基准量">
      <ElInput
        :model-value="String(form.baseQtyPerSalesUnit ?? '')"
        :disabled="!form.baseDimension"
        :placeholder="form.baseUnit ?? '先选择基准量纲'"
        @update:model-value="
          (value) => set('baseQtyPerSalesUnit', value || null)
        "
      />
    </ElFormItem>
    <ElFormItem label="度量精度">
      <ElSelect
        :model-value="form.quantityPrecision ?? 'UNKNOWN'"
        @update:model-value="(value) => set('quantityPrecision', value)"
      >
        <ElOption label="未知 / 未审核" value="UNKNOWN" /><ElOption
          label="精确"
          value="EXACT"
        /><ElOption label="约量" value="APPROX" />
      </ElSelect>
    </ElFormItem>
    <ElFormItem label="起订量">
      <ElInputNumber
        :model-value="form.minOrderQty ?? 1"
        :min="1"
        :max="2147483647"
        :precision="0"
        @update:model-value="(value) => set('minOrderQty', value)"
      />
    </ElFormItem>
    <ElFormItem label="数量步长">
      <ElInputNumber
        :model-value="form.stepQty ?? 1"
        :min="1"
        :max="2147483647"
        :precision="0"
        @update:model-value="(value) => set('stepQty', value)"
      />
    </ElFormItem>
    <ElFormItem label="展示规格">
      <ElInput
        :model-value="form.unitSpecText ?? ''"
        :maxlength="500"
        @update:model-value="(value) => set('unitSpecText', value)"
      />
    </ElFormItem>
    <span>度量修订 {{ form.measurementRevision }}；每次从起订量按步长递增</span>
    <div class="flex gap-2">
      <ElInput v-model="raw" placeholder="原始数量" /><ElInput
        v-model="rawUnit"
        placeholder="原始单位"
      /><ElButton :loading="busy" @click="preview">换算预览</ElButton>
    </div>
    <ElAlert v-if="result" :title="result" :closable="false" />
    <SkuIdentifierPanel :sku-id="modelValue.id" />
  </div>
</template>

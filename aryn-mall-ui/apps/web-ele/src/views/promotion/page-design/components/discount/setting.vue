<script setup lang="ts">
import type { DiscountProps } from './types';

import { ref, watch } from 'vue';

import {
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElOption,
  ElRadio,
  ElRadioGroup,
  ElSelect,
  ElSwitch,
} from 'element-plus';

import {
  cloneDesignerValue,
  isSameDesignerValue,
} from '../../../page-designer/schema/clone';
import IdPickerField from '../common/id-picker-field/index.vue';

const props = defineProps<{ modelValue: DiscountProps }>();
const emit = defineEmits<{
  'update:modelValue': [value: DiscountProps];
}>();
const form = ref(cloneDesignerValue(props.modelValue));

watch(
  () => props.modelValue,
  (value) => {
    if (!isSameDesignerValue(form.value, value))
      form.value = cloneDesignerValue(value);
  },
  { deep: true },
);
watch(
  form,
  (value) => {
    if (!isSameDesignerValue(value, props.modelValue))
      emit('update:modelValue', cloneDesignerValue(value));
  },
  { deep: true },
);
</script>

<template>
  <ElForm :model="form" label-position="top" class="retail-setting">
    <ElFormItem label="区块标题">
      <ElInput v-model="form.title" maxlength="20" show-word-limit />
    </ElFormItem>
    <ElFormItem label="副标题">
      <ElInput
        v-model="form.subtitle"
        maxlength="20"
        placeholder="限时特惠 · 折扣价直接买"
        show-word-limit
      />
    </ElFormItem>
    <ElFormItem label="数据来源">
      <ElRadioGroup v-model="form.dataSource.mode">
        <ElRadio value="automatic">自动读取进行中活动</ElRadio>
        <ElRadio value="manual">手动选择</ElRadio>
      </ElRadioGroup>
    </ElFormItem>
    <ElFormItem v-if="form.dataSource.mode === 'manual'" label="折扣活动">
      <IdPickerField
        v-model="form.dataSource.targetIds"
        kind="discount-activity"
        :limit="5"
      />
    </ElFormItem>
    <ElFormItem label="展示活动数">
      <ElInputNumber v-model="form.count" :min="1" :max="5" />
    </ElFormItem>
    <ElFormItem label="每活动商品数">
      <ElInputNumber v-model="form.goodsLimit" :min="1" :max="8" />
    </ElFormItem>
    <ElFormItem label="显示活动倒计时">
      <ElSwitch v-model="form.showCountdown" />
    </ElFormItem>
    <ElFormItem label="无数据策略">
      <ElSelect v-model="form.emptyStrategy">
        <ElOption label="显示占位" value="placeholder" /><ElOption
          label="隐藏组件"
          value="hide"
        />
      </ElSelect>
    </ElFormItem>
    <ElFormItem label="失效数据策略">
      <ElSelect v-model="form.invalidStrategy">
        <ElOption label="隐藏失效项" value="hide" /><ElOption
          label="显示占位"
          value="placeholder"
        />
      </ElSelect>
    </ElFormItem>
  </ElForm>
</template>

<style scoped>
.retail-setting :deep(.el-select),
.retail-setting :deep(.el-input-number) {
  width: 100%;
}
</style>

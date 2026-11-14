<script setup lang="ts">
import type { TradeCalendarProps } from './types';

import { ref, watch } from 'vue';

import {
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElOption,
  ElSelect,
} from 'element-plus';

import {
  cloneDesignerValue,
  isSameDesignerValue,
} from '../../../page-designer/schema/clone';

const props = defineProps<{ modelValue: TradeCalendarProps }>();
const emit = defineEmits<{
  'update:modelValue': [value: TradeCalendarProps];
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
    <ElFormItem label="楼层标题">
      <ElInput
        v-model="form.title"
        maxlength="20"
        placeholder="留空显示进行中特供活动名"
        show-word-limit
      />
    </ElFormItem>
    <ElFormItem label="特供商品数量">
      <ElInputNumber
        v-model="form.count"
        :max="50"
        :min="1"
        controls-position="right"
      />
    </ElFormItem>
    <ElFormItem label="无数据策略">
      <ElSelect v-model="form.emptyStrategy">
        <ElOption label="隐藏组件" value="hide" />
        <ElOption label="显示占位" value="placeholder" />
      </ElSelect>
    </ElFormItem>
    <ElFormItem label="失效数据策略">
      <ElSelect v-model="form.invalidStrategy">
        <ElOption label="隐藏组件" value="hide" />
        <ElOption label="显示占位" value="placeholder" />
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

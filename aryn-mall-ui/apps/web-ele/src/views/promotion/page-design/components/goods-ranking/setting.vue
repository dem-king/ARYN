<script setup lang="ts">
import type { GoodsRankingProps } from './types';

import { ref, watch } from 'vue';

import {
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElOption,
  ElSelect,
  ElSwitch,
} from 'element-plus';

import {
  cloneDesignerValue,
  isSameDesignerValue,
} from '../../../page-designer/schema/clone';

const props = defineProps<{ modelValue: GoodsRankingProps }>();
const emit = defineEmits<{ 'update:modelValue': [value: GoodsRankingProps] }>();
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
    <ElFormItem label="排行指标">
      <ElSelect v-model="form.dataSource.metric">
        <ElOption label="销量排行" value="sales" /><ElOption
          label="新品排行"
          value="create_time"
        /><ElOption label="价格排行" value="sales_price" />
      </ElSelect>
    </ElFormItem>
    <ElFormItem label="展示数量">
      <ElInputNumber v-model="form.count" :min="1" :max="10" />
    </ElFormItem>
    <ElFormItem label="显示名次">
      <ElSwitch v-model="form.showRankNumber" />
    </ElFormItem>
    <ElFormItem label="商品原价">
      <ElSwitch v-model="form.showOriginalPrice" />
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

<script setup lang="ts">
import type { GoodsGroupProps } from './types';

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
import CommonStyle from '../common/common-style/index.vue';

const props = defineProps<{ modelValue: GoodsGroupProps }>();
const emit = defineEmits<{ 'update:modelValue': [value: GoodsGroupProps] }>();
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
    if (!isSameDesignerValue(value, props.modelValue)) {
      emit('update:modelValue', cloneDesignerValue(value));
    }
  },
  { deep: true },
);
</script>

<template>
  <ElForm :model="form" label-position="top" class="retail-setting">
    <ElFormItem label="区块标题">
      <ElInput v-model="form.title" maxlength="20" show-word-limit />
    </ElFormItem>
    <ElFormItem label="数据来源">
      <ElRadioGroup v-model="form.dataSource.mode">
        <ElRadio value="rule">规则选择</ElRadio>
        <ElRadio value="manual">手动选择</ElRadio>
      </ElRadioGroup>
    </ElFormItem>
    <ElFormItem v-if="form.dataSource.mode === 'manual'" label="商品 ID">
      <ElSelect
        v-model="form.dataSource.targetIds"
        allow-create
        default-first-option
        filterable
        multiple
        placeholder="输入商品 ID 后回车"
      />
    </ElFormItem>
    <template v-else>
      <ElFormItem label="分类 ID">
        <ElInput
          v-model="form.dataSource.categoryId"
          clearable
          placeholder="留空表示全部分类"
        />
      </ElFormItem>
      <ElFormItem label="排序">
        <ElSelect v-model="form.dataSource.sort">
          <ElOption label="销量优先" value="sales" /><ElOption
            label="新品优先"
            value="create_time"
          /><ElOption label="价格从低到高" value="sales_price" />
        </ElSelect>
      </ElFormItem>
    </template>
    <ElFormItem label="展示数量">
      <ElInputNumber v-model="form.count" :min="1" :max="20" />
    </ElFormItem>
    <ElFormItem label="每行列数">
      <ElRadioGroup v-model="form.columns">
        <ElRadio :value="2">2 列</ElRadio><ElRadio :value="3">3 列</ElRadio>
      </ElRadioGroup>
    </ElFormItem>
    <ElFormItem label="显示销量">
      <ElSwitch v-model="form.showSales" />
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
    <div class="retail-setting__section">通用样式</div>
    <CommonStyle v-model="form.commonStyle" />
  </ElForm>
</template>

<style scoped>
.retail-setting :deep(.el-select),
.retail-setting :deep(.el-input-number) {
  width: 100%;
}

.retail-setting__section {
  padding: 12px 0 6px;
  font-weight: 600;
  border-top: 1px solid var(--el-border-color-lighter);
}
</style>

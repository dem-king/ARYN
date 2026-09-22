<script setup lang="ts">
import type { ReplenishCardProps } from './types';

import { ref, watch } from 'vue';

import {
  ElAlert,
  ElForm,
  ElFormItem,
  ElInput,
  ElOption,
  ElSelect,
  ElSwitch,
} from 'element-plus';

import {
  cloneDesignerValue,
  isSameDesignerValue,
} from '../../../page-designer/schema/clone';
import CommonStyle from '../common/common-style/index.vue';

const props = defineProps<{ modelValue: ReplenishCardProps }>();
const emit = defineEmits<{
  'update:modelValue': [value: ReplenishCardProps];
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
    <ElAlert
      :closable="false"
      show-icon
      title="展示当前登录用户进行中的共享购物车（补给单）；未登录、未关联船舶或纯零售租户不显示"
      type="info"
    />
    <ElFormItem label="标题">
      <ElInput
        v-model="form.title"
        placeholder="今日补给单"
        maxlength="12"
        show-word-limit
      />
    </ElFormItem>
    <ElFormItem label="显示「按单加购」按钮">
      <ElSwitch v-model="form.showBatchAdd" />
    </ElFormItem>
    <ElFormItem label="显示明细预览">
      <ElSwitch v-model="form.showPreview" />
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
    <ElAlert
      :closable="false"
      show-icon
      title="同一页面最多放置一个补给单卡片"
      type="warning"
    />
    <div class="retail-setting__section">通用样式</div>
    <CommonStyle v-model="form.commonStyle" />
  </ElForm>
</template>

<style scoped>
.retail-setting :deep(.el-alert) {
  margin-bottom: 16px;
}

.retail-setting :deep(.el-select) {
  width: 100%;
}

.retail-setting__section {
  padding: 12px 0 6px;
  font-weight: 600;
  border-top: 1px solid var(--el-border-color-lighter);
}
</style>

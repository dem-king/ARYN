<script setup lang="ts">
import type { CountdownProps } from './types';

import { ref, watch } from 'vue';

import {
  ElDatePicker,
  ElForm,
  ElFormItem,
  ElInput,
  ElOption,
  ElSelect,
} from 'element-plus';

import {
  cloneDesignerValue,
  isSameDesignerValue,
} from '../../../page-designer/schema/clone';
import CommonStyle from '../common/common-style/index.vue';

const props = defineProps<{ modelValue: CountdownProps }>();
const emit = defineEmits<{ 'update:modelValue': [value: CountdownProps] }>();
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
    <ElFormItem label="目标时间">
      <ElDatePicker
        v-model="form.targetTime"
        type="datetime"
        value-format="YYYY-MM-DD HH:mm:ss"
        placeholder="选择结束时间"
      />
    </ElFormItem>
    <ElFormItem label="结束文案">
      <ElInput v-model="form.completedText" maxlength="20" show-word-limit />
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
        <ElOption label="显示占位" value="placeholder" /><ElOption
          label="隐藏组件"
          value="hide"
        />
      </ElSelect>
    </ElFormItem>
    <div class="retail-setting__section">通用样式</div>
    <CommonStyle v-model="form.commonStyle" />
  </ElForm>
</template>

<style scoped>
.retail-setting :deep(.el-select),
.retail-setting :deep(.el-date-editor) {
  width: 100%;
}

.retail-setting__section {
  padding: 12px 0 6px;
  font-weight: 600;
  border-top: 1px solid var(--el-border-color-lighter);
}
</style>

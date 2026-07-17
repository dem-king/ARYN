<script setup lang="ts">
import type { ShopInfoProps } from './types';

import { ref, watch } from 'vue';

import {
  ElAlert,
  ElForm,
  ElFormItem,
  ElOption,
  ElSelect,
  ElSwitch,
} from 'element-plus';

import {
  cloneDesignerValue,
  isSameDesignerValue,
} from '../../../page-designer/schema/clone';
import CommonStyle from '../common/common-style/index.vue';

const props = defineProps<{ modelValue: ShopInfoProps }>();
const emit = defineEmits<{ 'update:modelValue': [value: ShopInfoProps] }>();
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
      title="店铺名称、Logo 和联系方式读取当前租户资料"
      type="info"
    />
    <ElFormItem label="显示地址">
      <ElSwitch v-model="form.showDescription" />
    </ElFormItem>
    <ElFormItem label="显示电话">
      <ElSwitch v-model="form.showContact" />
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
        <ElOption label="隐藏组件" value="hide" /><ElOption
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

<script setup lang="ts">
import type { NewbieFloorProps } from './types';

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

const props = defineProps<{ modelValue: NewbieFloorProps }>();
const emit = defineEmits<{
  'update:modelValue': [value: NewbieFloorProps];
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
    <ElFormItem label="数据来源">
      <span class="text-gray-500">
        自动读取进行中的新人专享活动（在「新人专享活动」页维护）
      </span>
    </ElFormItem>
    <ElFormItem label="展示商品数">
      <ElInputNumber v-model="form.count" :min="1" :max="20" />
    </ElFormItem>
    <ElFormItem label="展示「新人」标签">
      <ElSwitch v-model="form.showTag" />
    </ElFormItem>
    <ElFormItem label="展示活动倒计时">
      <ElSwitch v-model="form.showCountdown" />
    </ElFormItem>
    <ElFormItem label="商品自动前进">
      <ElSwitch v-model="form.autoScroll" />
    </ElFormItem>
    <ElFormItem v-if="form.autoScroll" label="每屏停留(毫秒)">
      <!-- 历史草稿没有 interval 字段：空值展示回退默认 3000，与两端渲染侧兜底同口径 -->
      <ElInputNumber
        :model-value="form.interval ?? 3000"
        :min="1500"
        :max="10000"
        :step="500"
        @update:model-value="form.interval = $event ?? 3000"
      />
    </ElFormItem>
    <ElFormItem label="无数据策略">
      <ElSelect v-model="form.emptyStrategy">
        <ElOption label="显示占位" value="placeholder" />
        <ElOption label="隐藏组件" value="hide" />
      </ElSelect>
    </ElFormItem>
  </ElForm>
</template>

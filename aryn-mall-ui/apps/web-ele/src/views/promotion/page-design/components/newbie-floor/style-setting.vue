<script setup lang="ts">
import { ref, watch } from 'vue';

import { ElColorPicker, ElForm, ElFormItem } from 'element-plus';

import {
  cloneDesignerValue,
  isSameDesignerValue,
} from '../../../page-designer/schema/clone';

interface NewbieFloorStyleProps extends Record<string, unknown> {
  mainColor: string;
}

const props = defineProps<{ modelValue: NewbieFloorStyleProps }>();
const emit = defineEmits<{
  'update:modelValue': [value: NewbieFloorStyleProps];
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
    if (!isSameDesignerValue(value, props.modelValue)) {
      emit('update:modelValue', cloneDesignerValue(value));
    }
  },
  { deep: true },
);

/** 清空取色器时回空串，渲染侧据此跟随页面主题色 */
function updateMainColor(value: null | string) {
  form.value.mainColor = value || '';
}
</script>

<template>
  <ElForm :model="form" label-position="top">
    <ElFormItem label="区块主色（清空跟随页面主题）">
      <ElColorPicker
        :model-value="form.mainColor || null"
        @update:model-value="updateMainColor"
      />
    </ElFormItem>
  </ElForm>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';

import { ElForm, ElFormItem } from 'element-plus';

import {
  cloneDesignerValue,
  isSameDesignerValue,
} from '../../../page-designer/schema/clone';
import ColorPicker from '../common/common-color-picker/index.vue';

interface RichTextStyleProps extends Record<string, unknown> {
  bgColor?: string;
}

const props = defineProps<{ modelValue: RichTextStyleProps }>();
const emit = defineEmits<{
  'update:modelValue': [value: RichTextStyleProps];
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
</script>

<template>
  <ElForm :model="form" label-position="top">
    <ElFormItem label="背景颜色">
      <ColorPicker v-model="form.bgColor" />
    </ElFormItem>
  </ElForm>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';

import { ElForm, ElFormItem, ElRadio, ElRadioGroup } from 'element-plus';

import {
  cloneDesignerValue,
  isSameDesignerValue,
} from '../../../page-designer/schema/clone';
import ColorPicker from '../common/common-color-picker/index.vue';

interface SearchBarStyleProps extends Record<string, unknown> {
  bgColor: string;
  style: string;
}

const props = defineProps<{ modelValue: SearchBarStyleProps }>();
const emit = defineEmits<{
  'update:modelValue': [value: SearchBarStyleProps];
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
    <ElFormItem label="样式">
      <ElRadioGroup v-model="form.style">
        <ElRadio value="1">圆角</ElRadio>
        <ElRadio value="2">方形</ElRadio>
      </ElRadioGroup>
    </ElFormItem>
    <ElFormItem label="背景色">
      <ColorPicker v-model="form.bgColor" />
    </ElFormItem>
  </ElForm>
</template>

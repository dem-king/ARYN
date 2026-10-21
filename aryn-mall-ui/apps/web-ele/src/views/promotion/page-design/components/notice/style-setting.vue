<script setup lang="ts">
import { ref, watch } from 'vue';

import { ElForm, ElFormItem } from 'element-plus';

import {
  cloneDesignerValue,
  isSameDesignerValue,
} from '../../../page-designer/schema/clone';
import ColorPicker from '../common/common-color-picker/index.vue';

interface NoticeStyleProps extends Record<string, unknown> {
  color: string;
}

const props = defineProps<{ modelValue: NoticeStyleProps }>();
const emit = defineEmits<{ 'update:modelValue': [value: NoticeStyleProps] }>();
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
    <ElFormItem label="文字颜色">
      <ColorPicker v-model="form.color" />
    </ElFormItem>
  </ElForm>
</template>

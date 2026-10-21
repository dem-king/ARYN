<script setup lang="ts">
import { ref, watch } from 'vue';

import { ElColorPicker, ElFormItem } from 'element-plus';

import {
  cloneDesignerValue,
  isSameDesignerValue,
} from '../../../../page-designer/schema/clone';

interface BottomNavStyleProps extends Record<string, unknown> {
  activeColor: string;
  backgroundColor: string;
  textColor: string;
}

const props = defineProps<{ modelValue: BottomNavStyleProps }>();
const emit = defineEmits<{
  'update:modelValue': [value: BottomNavStyleProps];
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
  <ElFormItem label="背景色 / 文字色 / 选中色">
    <div class="color-row">
      <ElColorPicker v-model="form.backgroundColor" />
      <ElColorPicker v-model="form.textColor" />
      <ElColorPicker v-model="form.activeColor" />
    </div>
  </ElFormItem>
</template>

<style scoped>
.color-row {
  display: flex;
  gap: 12px;
}
</style>

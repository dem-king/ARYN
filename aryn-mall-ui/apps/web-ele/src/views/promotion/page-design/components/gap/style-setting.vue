<script setup lang="ts">
import { ref, watch } from 'vue';

import { ElForm, ElFormItem } from 'element-plus';

import {
  cloneDesignerValue,
  isSameDesignerValue,
} from '../../../page-designer/schema/clone';
import GradientColorPicker from '../common/common-gradient-color-picker/index.vue';

interface GapStyleProps extends Record<string, unknown> {
  gapBgColorDirection: string;
  gapBgEndColor: string;
  gapBgStartColor: string;
}

const props = defineProps<{ modelValue: GapStyleProps }>();
const emit = defineEmits<{ 'update:modelValue': [value: GapStyleProps] }>();
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
    <ElFormItem label="空白颜色">
      <GradientColorPicker
        v-model:direction="form.gapBgColorDirection"
        v-model:start-color="form.gapBgStartColor"
        v-model:end-color="form.gapBgEndColor"
      />
    </ElFormItem>
  </ElForm>
</template>

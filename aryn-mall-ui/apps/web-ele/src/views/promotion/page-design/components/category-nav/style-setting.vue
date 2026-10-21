<script setup lang="ts">
import { ref, watch } from 'vue';

import { ElForm, ElFormItem, ElSlider, ElSwitch } from 'element-plus';

import {
  cloneDesignerValue,
  isSameDesignerValue,
} from '../../../page-designer/schema/clone';
import ColorPicker from '../common/common-color-picker/index.vue';

interface CategoryNavStyleProps extends Record<string, unknown> {
  fontColor: string;
  imgRadius: number;
  imgSize: number;
  scrollShow: boolean;
}

const props = defineProps<{ modelValue: CategoryNavStyleProps }>();
const emit = defineEmits<{
  'update:modelValue': [value: CategoryNavStyleProps];
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
    <ElFormItem label="图标大小">
      <ElSlider
        v-model="form.imgSize"
        :min="20"
        :max="60"
        show-input
        :show-input-controls="false"
      />
    </ElFormItem>
    <ElFormItem label="图标圆角">
      <ElSlider
        v-model="form.imgRadius"
        :min="0"
        :max="30"
        show-input
        :show-input-controls="false"
      />
    </ElFormItem>
    <ElFormItem label="文字颜色">
      <ColorPicker v-model="form.fontColor" />
    </ElFormItem>
    <ElFormItem label="横向滚动">
      <ElSwitch v-model="form.scrollShow" />
    </ElFormItem>
  </ElForm>
</template>

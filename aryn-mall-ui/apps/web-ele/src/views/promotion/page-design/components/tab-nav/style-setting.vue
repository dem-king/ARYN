<script setup lang="ts">
import { ref, watch } from 'vue';

import {
  ElForm,
  ElFormItem,
  ElInputNumber,
  ElSlider,
  ElSwitch,
} from 'element-plus';

import {
  cloneDesignerValue,
  isSameDesignerValue,
} from '../../../page-designer/schema/clone';
import ColorPicker from '../common/common-color-picker/index.vue';

interface TabNavStyleProps extends Record<string, unknown> {
  displayMode: string;
  fontColor: string;
  imgRadius: number;
  imgSize: number;
  indicatorActiveColor: string;
  indicatorColor: string;
  indicatorDots: boolean;
  type: string;
}

const props = defineProps<{ modelValue: TabNavStyleProps }>();
const emit = defineEmits<{ 'update:modelValue': [value: TabNavStyleProps] }>();
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
    <ElFormItem label="文字颜色" v-if="form.type !== '1'">
      <ColorPicker v-model="form.fontColor" />
    </ElFormItem>
    <ElFormItem label="图片大小">
      <ElInputNumber v-model="form.imgSize" :min="1" :controls="false" />
    </ElFormItem>
    <ElFormItem label="图片圆角">
      <ElSlider
        v-model="form.imgRadius"
        show-input
        :show-input-controls="false"
        :min="0"
      />
    </ElFormItem>
    <template v-if="form.displayMode === 'pager'">
      <ElFormItem label="指示器">
        <ElSwitch v-model="form.indicatorDots" />
      </ElFormItem>
      <ElFormItem label="指示器颜色" v-if="form.indicatorDots">
        <ColorPicker v-model="form.indicatorColor" />
      </ElFormItem>
      <ElFormItem label="激活颜色" v-if="form.indicatorDots">
        <ColorPicker v-model="form.indicatorActiveColor" />
      </ElFormItem>
    </template>
  </ElForm>
</template>

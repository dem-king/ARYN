<script setup lang="ts">
import { ref, watch } from 'vue';

import { ElForm, ElFormItem, ElSlider, ElSwitch } from 'element-plus';

import {
  cloneDesignerValue,
  isSameDesignerValue,
} from '../../../page-designer/schema/clone';
import ColorPicker from '../common/common-color-picker/index.vue';

interface SwiperBannerStyleProps extends Record<string, unknown> {
  borderRadius: number;
  height: number;
  indicatorActiveColor: string;
  indicatorColor: string;
  indicatorDots: boolean;
  interval: number;
}

const props = defineProps<{ modelValue: SwiperBannerStyleProps }>();
const emit = defineEmits<{
  'update:modelValue': [value: SwiperBannerStyleProps];
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
    <ElFormItem label="播放间隔">
      <ElSlider
        v-model="form.interval"
        :min="1000"
        :max="5000"
        :step="500"
        show-input
        :show-input-controls="false"
      />
    </ElFormItem>
    <ElFormItem label="指示器">
      <ElSwitch v-model="form.indicatorDots" />
    </ElFormItem>
    <ElFormItem label="指示器颜色" v-if="form.indicatorDots">
      <ColorPicker v-model="form.indicatorColor" />
    </ElFormItem>
    <ElFormItem label="激活颜色" v-if="form.indicatorDots">
      <ColorPicker v-model="form.indicatorActiveColor" />
    </ElFormItem>
    <ElFormItem label="高度">
      <ElSlider
        v-model="form.height"
        :min="100"
        :max="400"
        show-input
        :show-input-controls="false"
      />
    </ElFormItem>
    <ElFormItem label="圆角">
      <ElSlider
        v-model="form.borderRadius"
        :min="0"
        :max="30"
        show-input
        :show-input-controls="false"
      />
    </ElFormItem>
  </ElForm>
</template>

<script setup lang="ts">
import type { DiyCommonStyle } from '@vben/types';

import { ref, watch } from 'vue';

import { ElFormItem, ElSlider, ElSwitch } from 'element-plus';

import {
  cloneDesignerValue,
  isSameDesignerValue,
} from '../../../page-designer/schema/clone';
import ColorPicker from '../common/common-color-picker/index.vue';
import CommonStyle from '../common/common-style/index.vue';

interface ImageAdStyleProps extends Record<string, unknown> {
  commonImageStyle: DiyCommonStyle;
  imgRadius: number;
  indicatorActiveColor: string;
  indicatorColor: string;
  indicatorDots: boolean;
  type: string;
}

const props = defineProps<{ modelValue: ImageAdStyleProps }>();
const emit = defineEmits<{
  'update:modelValue': [value: ImageAdStyleProps];
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
  <div>
    <ElFormItem label="指示点" v-if="form.type === '2'">
      <ElSwitch v-model="form.indicatorDots" />
    </ElFormItem>
    <ElFormItem label="指示点颜色" v-if="form.indicatorDots">
      <ColorPicker v-model="form.indicatorColor" />
    </ElFormItem>
    <ElFormItem label="选中颜色" v-if="form.indicatorDots">
      <ColorPicker v-model="form.indicatorActiveColor" />
    </ElFormItem>
    <ElFormItem label="图片圆角">
      <ElSlider
        v-model="form.imgRadius"
        show-input
        :show-input-controls="false"
        :min="0"
      />
    </ElFormItem>
    <div v-if="form.type === '1'">
      <h4>图片样式</h4>
      <div class="content-item">
        <CommonStyle v-model="form.commonImageStyle" />
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '../common/common.scss' as *;
</style>

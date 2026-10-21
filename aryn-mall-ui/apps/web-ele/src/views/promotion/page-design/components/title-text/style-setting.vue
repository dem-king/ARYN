<script setup lang="ts">
import { ref, watch } from 'vue';

import { ElFormItem, ElRadio, ElRadioGroup, ElSlider } from 'element-plus';

import {
  cloneDesignerValue,
  isSameDesignerValue,
} from '../../../page-designer/schema/clone';
import ColorPicker from '../common/common-color-picker/index.vue';

interface TitleTextStyleProps extends Record<string, unknown> {
  descColor: string;
  descSize: number;
  descWeight: string;
  moreBtn: boolean;
  moreBtnColor: string;
  moreBtnSize: number;
  moreBtnWeight: string;
  showDesc: boolean;
  titleColor: string;
  titleSize: number;
  titleWeight: string;
}

const props = defineProps<{ modelValue: TitleTextStyleProps }>();
const emit = defineEmits<{
  'update:modelValue': [value: TitleTextStyleProps];
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
    <div>
      <h4>标题文字</h4>
      <div class="content-item">
        <ElFormItem label="字体颜色">
          <ColorPicker v-model="form.titleColor" />
        </ElFormItem>
        <ElFormItem label="字体样式">
          <ElRadioGroup v-model="form.titleWeight">
            <ElRadio value="0">正常</ElRadio>
            <ElRadio value="1">加粗</ElRadio>
          </ElRadioGroup>
        </ElFormItem>
        <ElFormItem label="字体大小">
          <ElSlider
            v-model="form.titleSize"
            show-input
            :show-input-controls="false"
            :min="0"
          />
        </ElFormItem>
      </div>
    </div>
    <div v-if="form.showDesc">
      <h4>描述文字</h4>
      <div class="content-item">
        <ElFormItem label="字体颜色">
          <ColorPicker v-model="form.descColor" />
        </ElFormItem>
        <ElFormItem label="字体样式">
          <ElRadioGroup v-model="form.descWeight">
            <ElRadio value="0">正常</ElRadio>
            <ElRadio value="1">加粗</ElRadio>
          </ElRadioGroup>
        </ElFormItem>
        <ElFormItem label="字体大小">
          <ElSlider
            v-model="form.descSize"
            show-input
            :show-input-controls="false"
            :min="0"
          />
        </ElFormItem>
      </div>
    </div>
    <div v-if="form.moreBtn">
      <h4>查看更多</h4>
      <div class="content-item">
        <ElFormItem label="字体颜色">
          <ColorPicker v-model="form.moreBtnColor" />
        </ElFormItem>
        <ElFormItem label="字体样式">
          <ElRadioGroup v-model="form.moreBtnWeight">
            <ElRadio value="0">正常</ElRadio>
            <ElRadio value="1">加粗</ElRadio>
          </ElRadioGroup>
        </ElFormItem>
        <ElFormItem label="字体大小">
          <ElSlider
            v-model="form.moreBtnSize"
            show-input
            :show-input-controls="false"
            :min="0"
          />
        </ElFormItem>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '../common/common.scss' as *;
</style>

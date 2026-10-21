<script lang="ts" setup>
import { ref, watch } from 'vue';

import { ElForm, ElFormItem, ElSlider } from 'element-plus';
// 用于接收外部 v-model
const props = defineProps<{ modelValue: any }>();
const emit = defineEmits(['update:modelValue']);
// 设置默认值
const defaultConfig = {
  height: 30, // 高度
  gapBgColorDirection: 'to right',
  gapBgStartColor: '',
  gapBgEndColor: '',
  commonStyle: {
    styleTopMargin: 0,
    styleBottomMargin: 0,
    styleLeftMargin: 0,
    styleRightMargin: 0,
    styleTopPadding: 0,
    styleBottomPadding: 0,
    styleLeftPadding: 0,
    styleRightPadding: 0,
    styleLtRadius: 0,
    styleRtRadius: 0,
    styleLbRadius: 0,
    styleRbRadius: 0,
    bgColorDirection: 'to right',
    bgStartColor: '',
    bgEndColor: '',
    bgPicUrl: '',
  },
};

const form = ref({ ...defaultConfig, ...props.modelValue });
watch(
  form,
  (val) => {
    emit('update:modelValue', val);
  },
  { deep: true, immediate: true },
);
</script>
<template>
  <div class="setting-base">
    <div class="setting-title">
      <p>辅助空白</p>
      <div></div>
    </div>
    <div class="setting-form">
      <ElForm :model="form" label-width="80px">
        <ElFormItem label="高度设置">
          <ElSlider
            v-model="form.height"
            show-input
            :show-input-controls="false"
            :min="0"
          />
        </ElFormItem>
      </ElForm>
    </div>
  </div>
</template>

<style lang="scss">
@use '#/views/promotion/page-design/components/common/common.scss' as *;
</style>

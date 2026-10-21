<script lang="ts" setup>
import { defineAsyncComponent, ref, watch } from 'vue';

import { ElForm, ElFormItem } from 'element-plus';
// 用于接收外部 v-model
const props = defineProps<{ modelValue: any }>();
const emit = defineEmits(['update:modelValue']);
const Editor = defineAsyncComponent(
  () => import('#/components/editor/index.vue'),
);
// 设置默认值
const defaultConfig = {
  content: '',
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
      <p>富文本</p>
    </div>
    <div class="setting-form">
      <ElForm :model="form">
        <ElFormItem label="公告">
          <Editor
            v-model:get-html="form.content"
            :shop-id="undefined"
            height="500px"
            width="360px"
            :disable="false"
          />
        </ElFormItem>
      </ElForm>
    </div>
  </div>
</template>

<style lang="scss">
@use '#/views/promotion/page-design/components/common/common.scss' as *;
</style>

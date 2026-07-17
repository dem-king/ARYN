<script lang="ts" setup>
import { defineAsyncComponent, ref, watch } from 'vue';

import {
  ElForm,
  ElFormItem,
  ElInput,
  ElRadio,
  ElRadioGroup,
  ElSwitch,
  ElTabPane,
  ElTabs,
} from 'element-plus';

const props = defineProps<{ modelValue: any }>();
const emit = defineEmits(['update:modelValue']);

const CommonStyle = defineAsyncComponent(
  () => import('../common/common-style/index.vue'),
);
const ColorPicker = defineAsyncComponent(
  () => import('../common/common-color-picker/index.vue'),
);

const active = ref('0');

const defaultConfig = {
  placeholder: '搜索商品',
  style: '1',
  bgColor: 'rgba(245, 245, 245, 1)',
  showScan: true,
  commonStyle: {
    styleTopMargin: 10,
    styleBottomMargin: 0,
    styleLeftMargin: 10,
    styleRightMargin: 10,
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
      <p>搜索框</p>
      <div></div>
    </div>
    <div class="setting-form">
      <ElForm :model="form" label-width="80px">
        <ElTabs type="border-card" v-model="active">
          <ElTabPane label="组件内容" name="0">
            <ElFormItem label="占位文字">
              <ElInput v-model="form.placeholder" />
            </ElFormItem>
            <ElFormItem label="样式">
              <ElRadioGroup v-model="form.style">
                <ElRadio value="1">圆角</ElRadio>
                <ElRadio value="2">方形</ElRadio>
              </ElRadioGroup>
            </ElFormItem>
            <ElFormItem label="背景色">
              <ColorPicker v-model="form.bgColor" />
            </ElFormItem>
            <ElFormItem label="扫描图标">
              <ElSwitch v-model="form.showScan" />
            </ElFormItem>
          </ElTabPane>
          <ElTabPane label="组件样式" name="1">
            <h4>通用样式</h4>
            <div class="content-item">
              <CommonStyle v-model="form.commonStyle" />
            </div>
          </ElTabPane>
        </ElTabs>
      </ElForm>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '#/views/promotion/page-design/components/common/common.scss' as *;
</style>

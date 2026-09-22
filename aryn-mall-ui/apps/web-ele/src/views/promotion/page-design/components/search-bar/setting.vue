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
  // 热词轮播：2 个以上才在客户端启动轮播，单个热词等于固定文案
  hotWords: '',
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
            <ElFormItem label="热词轮播">
              <ElInput
                v-model="form.hotWords"
                placeholder="多个热词用逗号分隔，如：SKU 编码,品名,船上补给"
              />
              <div class="setting-tip">
                填 2 个及以上才会轮播；留空则只显示上方占位文字
              </div>
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

.setting-tip {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}
</style>

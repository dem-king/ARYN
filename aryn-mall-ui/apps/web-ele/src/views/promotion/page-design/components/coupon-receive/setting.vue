<script lang="ts" setup>
import { defineAsyncComponent, ref, watch } from 'vue';

import {
  ElForm,
  ElFormItem,
  ElRadio,
  ElRadioGroup,
  ElSlider,
  ElSwitch,
  ElTabPane,
  ElTabs,
} from 'element-plus';

const props = defineProps<{ modelValue: any }>();
const emit = defineEmits(['update:modelValue']);

const CommonStyle = defineAsyncComponent(
  () => import('../common/common-style/index.vue'),
);

const active = ref('0');

const defaultConfig = {
  showStyle: '2',
  showNum: 3,
  showReceiveBtn: true,
  commonStyle: {
    styleTopMargin: 0,
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
      <p>优惠券领取</p>
      <div></div>
    </div>
    <div class="setting-form">
      <ElForm :model="form" label-width="80px">
        <ElTabs type="border-card" v-model="active">
          <ElTabPane label="组件内容" name="0">
            <ElFormItem label="展示样式">
              <ElRadioGroup v-model="form.showStyle">
                <ElRadio value="1">列表</ElRadio>
                <ElRadio value="2">卡片</ElRadio>
              </ElRadioGroup>
            </ElFormItem>
            <ElFormItem label="显示数量">
              <ElSlider
                v-model="form.showNum"
                :min="1"
                :max="10"
                show-input
                :show-input-controls="false"
              />
            </ElFormItem>
            <ElFormItem label="领取按钮">
              <ElSwitch v-model="form.showReceiveBtn" />
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

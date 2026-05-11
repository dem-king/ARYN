<script lang="ts" setup>
import { defineAsyncComponent, ref, watch } from 'vue';

import { CircleCloseFilled } from '@element-plus/icons-vue';
import {
  ElButton,
  ElForm,
  ElFormItem,
  ElIcon,
  ElInput,
  ElInputNumber,
  ElRadio,
  ElRadioGroup,
  ElTabPane,
  ElTabs,
} from 'element-plus';
// 用于接收外部 v-model
const props = defineProps<{ modelValue: any }>();
const emit = defineEmits(['update:modelValue']);
const CommonStyle = defineAsyncComponent(
  () => import('../common/common-style/index.vue'),
);
const ColorPicker = defineAsyncComponent(
  () => import('../common/common-color-picker/index.vue'),
);
const LinkUrl = defineAsyncComponent(
  () => import('../common/link-url/index.vue'),
);
const active = ref('0');
// 设置默认值
const defaultConfig = {
  direction: 'horizontal', // 滚动方式 0.横向；1.竖向
  contentList: [], // 公告内容
  color: 'rgba(100, 101, 102, 1)',
  speed: 50, // 滚动速度
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
    bgStartColor: 'rgba(255, 248, 233, 1)',
    bgEndColor: 'rgba(255, 248, 233, 1)',
  },
};

const form = ref({ ...defaultConfig, ...props.modelValue });
const addNotice = () => {
  form.value.contentList.push({
    content: '公告',
    link: null,
  });
};
const deleteNotice = (index: number) => {
  form.value.contentList.splice(index, 1);
};
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
      <p>公告</p>
      <div></div>
    </div>
    <div class="setting-form">
      <ElForm :model="form" label-width="80px">
        <ElTabs type="border-card" v-model="active">
          <ElTabPane label="组件内容" name="0">
            <ElFormItem label="滚动方式">
              <ElRadioGroup v-model="form.direction">
                <ElRadio value="horizontal">左右滚动</ElRadio>
                <ElRadio value="vertical">上下滚动</ElRadio>
              </ElRadioGroup>
            </ElFormItem>
            <ElFormItem label="滚动速度">
              <ElInputNumber v-model="form.speed" :min="0" />
            </ElFormItem>
            <h4>公告内容</h4>
            <div
              v-for="(item, index) in form.contentList"
              :key="index"
              class="content-item"
            >
              <div class="delete-btn">
                <ElButton link @click="deleteNotice(index)">
                  <ElIcon><CircleCloseFilled /></ElIcon>
                </ElButton>
              </div>
              <ElFormItem label="标题" class="mt10" label-width="50px">
                <ElInput v-model="item.content" />
              </ElFormItem>
              <ElFormItem label="链接" class="mt10" label-width="50px">
                <LinkUrl v-model="item.link" />
              </ElFormItem>
            </div>
            <ElButton style="width: 100%" @click="addNotice">
              添加公告
            </ElButton>
          </ElTabPane>
          <ElTabPane label="组件样式" name="1">
            <ElFormItem label="文字颜色">
              <ColorPicker v-model="form.color" />
            </ElFormItem>
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

<style lang="scss">
@use '#/views/promotion/page-design/components/common/common.scss' as *;
</style>

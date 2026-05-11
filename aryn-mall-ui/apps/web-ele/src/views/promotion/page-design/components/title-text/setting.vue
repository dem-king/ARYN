<script lang="ts" setup>
import { defineAsyncComponent, ref, watch } from 'vue';

import {
  ElForm,
  ElFormItem,
  ElInput,
  ElRadio,
  ElRadioGroup,
  ElSlider,
  ElSwitch,
  ElTabPane,
  ElTabs,
} from 'element-plus';
// 用于接收外部 v-model
const props = defineProps<{ modelValue: any }>();
const emit = defineEmits(['update:modelValue']);
const LinkUrl = defineAsyncComponent(
  () => import('../common/link-url/index.vue'),
);
const CommonStyle = defineAsyncComponent(
  () => import('../common/common-style/index.vue'),
);
const ColorPicker = defineAsyncComponent(
  () => import('../common/common-color-picker/index.vue'),
);

const active = ref('0');
// 设置默认值
const defaultConfig = {
  title: '我是标题',
  desc: '我是描述',
  showDesc: false,
  showType: '1',
  titleSize: 14,
  descSize: 12,
  titleWeight: '0',
  descWeight: '0',
  titleColor: 'rgba(0, 0, 0, 1)',
  descColor: 'rgba(0, 0, 0, 1)',
  moreBtnColor: 'rgba(0, 0, 0, 1)',
  moreBtnSize: 14,
  moreBtnWeight: '0',
  bottomLine: false,
  moreBtn: false,
  moreBtnText: '查看更多',
  moreBtnStyle: '1',
  link: null,
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
    bgStartColor: 'rgba(255, 255, 255, 1)',
    bgEndColor: 'rgba(255, 255, 255, 1)',
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
      <p>标题</p>
      <div></div>
    </div>
    <div class="setting-form">
      <ElForm :model="form" label-width="90px">
        <ElTabs type="border-card" v-model="active">
          <ElTabPane label="组件内容" name="0">
            <ElFormItem label="标题内容">
              <ElInput v-model="form.title" placeholder="请输入标题内容" />
            </ElFormItem>
            <ElFormItem label="描述内容">
              <ElSwitch v-model="form.showDesc" />
            </ElFormItem>
            <ElFormItem v-if="form.showDesc">
              <ElInput v-model="form.desc" placeholder="请输入描述内容" />
            </ElFormItem>
            <!-- <el-form-item label="底部分割线">
							<el-switch v-model="state.form.bottomLine" active-text="显示" inactive-text="不显示" />
						</el-form-item> -->
            <ElFormItem label="查看更多">
              <ElSwitch v-model="form.moreBtn" />
            </ElFormItem>
            <div class="content-item" v-if="form.moreBtn">
              <ElFormItem label="样式">
                <ElRadioGroup v-model="form.moreBtnStyle">
                  <ElRadio value="1">样式一 </ElRadio>
                  <ElRadio value="2">样式二</ElRadio>
                  <ElRadio value="3">样式三</ElRadio>
                </ElRadioGroup>
              </ElFormItem>
              <ElFormItem label="文字" v-if="form.moreBtnStyle !== '3'">
                <ElInput v-model="form.moreBtnText" />
              </ElFormItem>
              <ElFormItem label="跳转链接" v-if="form.moreBtn">
                <LinkUrl v-model="form.link" />
              </ElFormItem>
            </div>
          </ElTabPane>
          <ElTabPane label="组件样式" name="1">
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
            <div v-if="form.moreBtn">
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

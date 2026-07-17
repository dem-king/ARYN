<script lang="ts" setup>
import { defineAsyncComponent, ref, watch } from 'vue';

import { CircleCloseFilled, DCaret, Plus } from '@element-plus/icons-vue';
import {
  ElButton,
  ElForm,
  ElFormItem,
  ElIcon,
  ElInput,
  ElRadio,
  ElRadioGroup,
  ElSlider,
  ElSwitch,
  ElTabPane,
  ElTabs,
} from 'element-plus';
import draggable from 'vuedraggable';

const props = defineProps<{ modelValue: any }>();
const emit = defineEmits(['update:modelValue']);

const SelectMaterial = defineAsyncComponent(
  () => import('#/components/select-material/index.vue'),
);
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
const dragOptions = {
  animation: 300,
  group: 'description',
  disabled: false,
  ghostClass: 'ghost',
};

const defaultConfig = {
  navList: [],
  showNum: 4,
  imgSize: 25,
  imgRadius: 0,
  fontColor: 'rgba(0, 0, 0, 1)',
  scrollShow: false,
  commonStyle: {
    styleTopMargin: 10,
    styleBottomMargin: 10,
    styleLeftMargin: 10,
    styleRightMargin: 10,
    styleTopPadding: 10,
    styleBottomPadding: 10,
    styleLeftPadding: 10,
    styleRightPadding: 10,
    styleLtRadius: 10,
    styleRtRadius: 10,
    styleLbRadius: 10,
    styleRbRadius: 10,
    bgColorDirection: 'to right',
    bgStartColor: 'rgba(255, 255, 255, 1)',
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

const add = () => {
  form.value.navList.push({ url: '', text: '导航', linkUrl: '' });
};

const del = (index: number) => {
  form.value.navList.splice(index, 1);
};
</script>

<template>
  <div class="setting-base">
    <div class="setting-title">
      <p>分类导航</p>
      <div></div>
    </div>
    <div class="setting-form">
      <ElForm :model="form" label-width="80px">
        <ElTabs type="border-card" v-model="active">
          <ElTabPane label="组件内容" name="0">
            <ElFormItem label="每行数量">
              <ElRadioGroup v-model="form.showNum">
                <ElRadio :value="4">4个</ElRadio>
                <ElRadio :value="5">5个</ElRadio>
              </ElRadioGroup>
            </ElFormItem>
            <ElFormItem label="导航设置">
              <p class="form-tip">可以上下拖动更改顺序</p>
            </ElFormItem>
            <draggable
              v-model="form.navList"
              v-bind="dragOptions"
              animation="700"
              item-key="id"
              @dragover.prevent
            >
              <template #item="{ element, index }">
                <div class="content-item">
                  <ElIcon size="24px" style="cursor: move"><DCaret /></ElIcon>
                  <div class="nav-item-info">
                    <div class="nav-item-row">
                      <span class="nav-label">图标</span>
                      <SelectMaterial
                        v-model="element.url"
                        :can-choose-images-num="1"
                      />
                    </div>
                    <div class="nav-item-row">
                      <span class="nav-label">文字</span>
                      <ElInput v-model="element.text" size="small" />
                    </div>
                    <div class="nav-item-row">
                      <span class="nav-label">链接</span>
                      <LinkUrl v-model="element.linkUrl" />
                    </div>
                  </div>
                  <div class="delete-btn">
                    <ElButton link @click="del(index)">
                      <ElIcon><CircleCloseFilled /></ElIcon>
                    </ElButton>
                  </div>
                </div>
              </template>
            </draggable>
            <div class="add-btn">
              <ElButton @click="add">
                <ElIcon :size="20"><Plus /></ElIcon>添加导航
              </ElButton>
            </div>
          </ElTabPane>
          <ElTabPane label="组件样式" name="1">
            <ElFormItem label="图标大小">
              <ElSlider
                v-model="form.imgSize"
                :min="20"
                :max="60"
                show-input
                :show-input-controls="false"
              />
            </ElFormItem>
            <ElFormItem label="图标圆角">
              <ElSlider
                v-model="form.imgRadius"
                :min="0"
                :max="30"
                show-input
                :show-input-controls="false"
              />
            </ElFormItem>
            <ElFormItem label="文字颜色">
              <ColorPicker v-model="form.fontColor" />
            </ElFormItem>
            <ElFormItem label="横向滚动">
              <ElSwitch v-model="form.scrollShow" />
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

<style scoped lang="scss">
@use '#/views/promotion/page-design/components/common/common.scss' as *;

.nav-item-info {
  flex: 1;
  padding-left: 8px;

  .nav-item-row {
    display: flex;
    gap: 8px;
    align-items: center;
    margin-bottom: 6px;

    .nav-label {
      flex-shrink: 0;
      width: 32px;
      font-size: 12px;
      color: #666;
    }
  }
}

.add-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  padding-top: 10px;
}
</style>

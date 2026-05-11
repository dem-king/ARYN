<script lang="ts" setup>
import { defineAsyncComponent, ref, watch } from 'vue';

import { CircleCloseFilled, DCaret, Plus } from '@element-plus/icons-vue';
import {
  ElButton,
  ElForm,
  ElFormItem,
  ElIcon,
  ElInput,
  ElInputNumber,
  ElMessageBox,
  ElRadio,
  ElRadioGroup,
  ElSlider,
  ElSwitch,
  ElTabPane,
  ElTabs,
} from 'element-plus';
import draggable from 'vuedraggable';

// 用于接收外部 v-model
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
const limitNum = ref(10);
const dragOptions = {
  animation: 300,
  group: 'description',
  disabled: false,
  ghostClass: 'ghost',
};
// 设置默认值
const defaultConfig = {
  type: '1',
  fontColor: 'rgba(0, 0, 0, 1)',
  showNum: 4,
  imgSize: 40, // 图片大小
  imgRadius: 0, // 图片圆角
  scrollShow: false, // 超出滚动显示
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
    bgEndColor: '',
    bgPicUrl: '',
  },
  navList: [
    { url: undefined, title: '导航一', link: null },
    { url: undefined, title: '导航二', link: null },
    { url: undefined, title: '导航三', link: null },
    { url: undefined, title: '导航四', link: null },
  ],
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
  if (form.value.navList.length < limitNum.value) {
    form.value.navList.push({
      url: undefined,
      title: '',
      link: null,
    });
  }
};
const del = (index: number) => {
  ElMessageBox.confirm('确定删除吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    form.value.navList.splice(index, 1);
  });
};
</script>

<template>
  <div class="setting-base">
    <div class="setting-title">
      <p>图文导航</p>
      <div></div>
    </div>
    <div class="setting-form">
      <ElForm :model="form" label-width="80px">
        <ElTabs type="border-card" v-model="active">
          <ElTabPane label="组件内容" name="0">
            <ElFormItem label="导航类型">
              <ElRadioGroup v-model="form.type">
                <ElRadio value="1">图片导航 </ElRadio>
                <ElRadio value="2">文字导航</ElRadio>
                <ElRadio value="3">图文导航</ElRadio>
              </ElRadioGroup>
            </ElFormItem>
            <ElFormItem label="滚动显示">
              <ElSwitch v-model="form.scrollShow" />
            </ElFormItem>
            <ElFormItem
              label="一行显示"
              v-if="form.type !== '2' && !form.scrollShow"
            >
              <ElRadioGroup v-model="form.showNum">
                <ElRadio :value="4">4个导航 </ElRadio>
                <ElRadio :value="5">5个导航</ElRadio>
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
                  <div class="left-nav" v-if="form.type !== '2'">
                    <SelectMaterial
                      v-model="element.url"
                      :can-choose-images-num="1"
                    />
                  </div>
                  <div class="right-nav">
                    <ElFormItem
                      label="标题"
                      label-width="40"
                      v-if="form.type !== '1'"
                    >
                      <ElInput
                        v-model="element.title"
                        placeholder="请输入标题"
                      />
                    </ElFormItem>
                    <ElFormItem label="链接" class="mt10" label-width="40">
                      <LinkUrl v-model="element.link" />
                    </ElFormItem>
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
              <ElButton
                @click="add"
                v-if="form.navList && form.navList.length < limitNum"
              >
                <ElIcon :size="20"> <Plus /> </ElIcon>添加导航（{{
                  form.navList.length
                }}/{{ limitNum }}）
              </ElButton>
            </div>
          </ElTabPane>
          <ElTabPane label="组件样式" name="1">
            <ElFormItem label="文字颜色" v-if="form.type !== '1'">
              <ColorPicker v-model="form.fontColor" />
            </ElFormItem>
            <ElFormItem label="图片大小">
              <ElInputNumber
                v-model="form.imgSize"
                :min="1"
                :controls="false"
              />
            </ElFormItem>
            <ElFormItem label="图片圆角">
              <ElSlider
                v-model="form.imgRadius"
                show-input
                :show-input-controls="false"
                :min="0"
              />
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

.content-item {
  display: flex;
  align-items: center;
  border-radius: 4px;

  .right-nav {
    padding-left: 10px;
  }
}

.add-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 10px 0 20px;
}
</style>

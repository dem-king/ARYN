<script lang="ts" setup>
import { defineAsyncComponent, ref, watch } from 'vue';

import { CircleCloseFilled, DCaret, Plus } from '@element-plus/icons-vue';
import {
  ElButton,
  ElDivider,
  ElForm,
  ElFormItem,
  ElIcon,
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
// 定义变量内容
const limitNum = ref(10);
const active = ref('0');
const dragOptions = {
  animation: 300,
  group: 'description',
  disabled: false,
  ghostClass: 'ghost',
};
// 设置默认值
const defaultConfig = {
  type: '1', // 1.一行一个 2.轮播海报
  interval: 5, // 轮播间隔时间
  swiperType: '1', // 轮播图类型
  height: 200,
  imgRadius: 0,
  indicatorDots: true, // 指示点
  indicatorColor: 'rgba(0, 0, 0, .3)', // 指示点颜色
  indicatorActiveColor: '#000000	', // 指示点颜色
  commonImageStyle: {
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
  imageList: [{ url: '', link: null }],
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
  if (form.value.imageList.length < limitNum.value) {
    form.value.imageList.push({
      url: '',
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
    form.value.imageList.splice(index, 1);
  });
};
</script>

<template>
  <div class="setting-base">
    <div class="setting-title">
      <p>图片广告</p>
      <div></div>
    </div>
    <div class="setting-form">
      <ElForm :model="form" label-width="95px">
        <ElTabs type="border-card" v-model="active">
          <ElTabPane label="组件内容" name="0">
            <ElFormItem label="图片类型">
              <ElRadioGroup v-model="form.type">
                <ElRadio value="1">一行一个 </ElRadio>
                <ElRadio value="2">轮播海报</ElRadio>
              </ElRadioGroup>
            </ElFormItem>
            <ElFormItem label="轮播类型" v-if="form.type === '2'">
              <ElRadioGroup v-model="form.swiperType">
                <ElRadio value="1">类型一 </ElRadio>
                <ElRadio value="2">类型二</ElRadio>
                <ElRadio value="3">类型三</ElRadio>
              </ElRadioGroup>
            </ElFormItem>
            <ElFormItem
              label="轮播间隔时间"
              v-if="form.type === '2'"
              label-width="100px"
            >
              <ElInputNumber
                v-model="form.interval"
                :min="1"
                :controls="false"
              />
            </ElFormItem>

            <ElFormItem label="高度设置">
              <ElInputNumber v-model="form.height" :min="1" :controls="false" />
            </ElFormItem>
            <ElDivider />
            <ElFormItem label="图片设置">
              <p class="form-tip">可以上下拖动更改顺序</p>
            </ElFormItem>
            <draggable
              v-model="form.imageList"
              v-bind="dragOptions"
              animation="700"
              item-key="id"
              @dragover.prevent
            >
              <template #item="{ element, index }">
                <div class="content-item">
                  <ElIcon size="24px" style="cursor: move"><DCaret /></ElIcon>
                  <div class="left-img">
                    <SelectMaterial
                      v-model="element.url"
                      :can-choose-images-num="1"
                    />
                  </div>
                  <div class="img-info">
                    <div class="info-top">
                      <div>
                        <p>链接配置</p>
                      </div>
                    </div>
                    <LinkUrl v-model="element.link" />
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
                v-if="form.imageList && form.imageList.length < limitNum"
              >
                <ElIcon :size="20"> <Plus /> </ElIcon>添加图片（{{
                  form.imageList.length
                }}/{{ limitNum }}）
              </ElButton>
            </div>
          </ElTabPane>
          <ElTabPane label="组件样式" name="1">
            <ElFormItem label="指示点" v-if="form.type === '2'">
              <ElSwitch v-model="form.indicatorDots" />
            </ElFormItem>
            <ElFormItem label="指示点颜色" v-if="form.indicatorDots">
              <ColorPicker v-model="form.indicatorColor" />
            </ElFormItem>
            <ElFormItem label="选中颜色" v-if="form.indicatorDots">
              <ColorPicker v-model="form.indicatorActiveColor" />
            </ElFormItem>
            <ElFormItem label="图片圆角">
              <ElSlider
                v-model="form.imgRadius"
                show-input
                :show-input-controls="false"
                :min="0"
              />
            </ElFormItem>
            <div v-if="form.type === '1'">
              <h4>图片样式</h4>
              <div class="content-item">
                <CommonStyle v-model="form.commonImageStyle" />
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

.content-item {
  display: flex;
  align-items: center;
  border-radius: 4px;

  .img-info {
    padding-left: 5px;

    p {
      padding-bottom: 10px;
    }

    .info-top {
      display: flex;
      justify-content: space-between;
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

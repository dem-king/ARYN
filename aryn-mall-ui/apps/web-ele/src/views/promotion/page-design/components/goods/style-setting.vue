<script setup lang="ts">
import type { DiyCommonStyle } from '@vben/types';

import { ref, watch } from 'vue';

import { ElFormItem, ElRadio, ElRadioGroup, ElSlider } from 'element-plus';

import {
  cloneDesignerValue,
  isSameDesignerValue,
} from '../../../page-designer/schema/clone';
import ColorPicker from '../common/common-color-picker/index.vue';
import CommonStyle from '../common/common-style/index.vue';

interface GoodsStyleProps extends Record<string, unknown> {
  buyBtnColor: string;
  buyBtnSize: number;
  descColor: string;
  descSize: number;
  descStyle: string;
  goodsCommonStyle: DiyCommonStyle;
  nameColor: string;
  nameSize: number;
  nameStyle: string;
  originalPriceColor: string;
  originalPriceSize: number;
  originalPriceStyle: string;
  salesPriceColor: string;
  salesPriceSize: number;
  salesPriceStyle: string;
  salesVolumeColor: string;
  salesVolumeSize: number;
  salesVolumeStyle: string;
  showBuyBtn: boolean;
  showDesc: boolean;
  showName: boolean;
  showOriginalPrice: boolean;
  showSalesPrice: boolean;
  showSalesVolume: boolean;
  showStock: boolean;
  stockColor: string;
  stockSize: number;
  stockStyle: string;
}

const props = defineProps<{ modelValue: GoodsStyleProps }>();
const emit = defineEmits<{ 'update:modelValue': [value: GoodsStyleProps] }>();
const form = ref(cloneDesignerValue(props.modelValue));

watch(
  () => props.modelValue,
  (value) => {
    if (!isSameDesignerValue(form.value, value))
      form.value = cloneDesignerValue(value);
  },
  { deep: true },
);
watch(
  form,
  (value) => {
    if (!isSameDesignerValue(value, props.modelValue)) {
      emit('update:modelValue', cloneDesignerValue(value));
    }
  },
  { deep: true },
);
</script>

<template>
  <div>
    <div v-if="form.showName">
      <h4>商品名称</h4>
      <div class="content-item">
        <ElFormItem label="字体颜色">
          <ColorPicker v-model="form.nameColor" />
        </ElFormItem>
        <ElFormItem label="字体样式">
          <ElRadioGroup v-model="form.nameStyle">
            <ElRadio value="0">正常</ElRadio>
            <ElRadio value="1">加粗</ElRadio>
          </ElRadioGroup>
        </ElFormItem>
        <ElFormItem label="字体大小">
          <ElSlider
            v-model="form.nameSize"
            show-input
            :show-input-controls="false"
            :min="0"
          />
        </ElFormItem>
      </div>
    </div>
    <div v-if="form.showDesc">
      <h4>商品描述</h4>
      <div class="content-item">
        <ElFormItem label="字体颜色">
          <ColorPicker v-model="form.descColor" />
        </ElFormItem>
        <ElFormItem label="字体样式">
          <ElRadioGroup v-model="form.descStyle">
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
    <div v-if="form.showSalesPrice">
      <h4>商品售价</h4>
      <div class="content-item">
        <ElFormItem label="字体颜色">
          <ColorPicker v-model="form.salesPriceColor" />
        </ElFormItem>
        <ElFormItem label="字体样式">
          <ElRadioGroup v-model="form.salesPriceStyle">
            <ElRadio value="0">正常</ElRadio>
            <ElRadio value="1">加粗</ElRadio>
          </ElRadioGroup>
        </ElFormItem>
        <ElFormItem label="字体大小">
          <ElSlider
            v-model="form.salesPriceSize"
            show-input
            :show-input-controls="false"
            :min="0"
          />
        </ElFormItem>
      </div>
    </div>
    <div v-if="form.showOriginalPrice">
      <h4>商品原价</h4>
      <div class="content-item">
        <ElFormItem label="字体颜色">
          <ColorPicker v-model="form.originalPriceColor" />
        </ElFormItem>
        <ElFormItem label="字体样式">
          <ElRadioGroup v-model="form.originalPriceStyle">
            <ElRadio value="0">正常</ElRadio>
            <ElRadio value="1">加粗</ElRadio>
          </ElRadioGroup>
        </ElFormItem>
        <ElFormItem label="字体大小">
          <ElSlider
            v-model="form.originalPriceSize"
            show-input
            :show-input-controls="false"
            :min="0"
          />
        </ElFormItem>
      </div>
    </div>
    <div v-if="form.showSalesVolume">
      <h4>商品销量</h4>
      <div class="content-item">
        <ElFormItem label="字体颜色">
          <ColorPicker v-model="form.salesVolumeColor" />
        </ElFormItem>
        <ElFormItem label="字体样式">
          <ElRadioGroup v-model="form.salesVolumeStyle">
            <ElRadio value="0">正常</ElRadio>
            <ElRadio value="1">加粗</ElRadio>
          </ElRadioGroup>
        </ElFormItem>
        <ElFormItem label="字体大小">
          <ElSlider
            v-model="form.salesVolumeSize"
            show-input
            :show-input-controls="false"
            :min="0"
          />
        </ElFormItem>
      </div>
    </div>
    <div v-if="form.showStock">
      <h4>商品库存</h4>
      <div class="content-item">
        <ElFormItem label="字体颜色">
          <ColorPicker v-model="form.stockColor" />
        </ElFormItem>
        <ElFormItem label="字体样式">
          <ElRadioGroup v-model="form.stockStyle">
            <ElRadio value="0">正常</ElRadio>
            <ElRadio value="1">加粗</ElRadio>
          </ElRadioGroup>
        </ElFormItem>
        <ElFormItem label="字体大小">
          <ElSlider
            v-model="form.stockSize"
            show-input
            :show-input-controls="false"
            :min="0"
          />
        </ElFormItem>
      </div>
    </div>
    <div v-if="form.showBuyBtn">
      <h4>购买按钮</h4>
      <div class="content-item">
        <ElFormItem label="字体颜色">
          <ColorPicker v-model="form.buyBtnColor" />
        </ElFormItem>
        <ElFormItem label="字体大小">
          <ElSlider
            v-model="form.buyBtnSize"
            show-input
            :show-input-controls="false"
            :min="0"
          />
        </ElFormItem>
      </div>
    </div>
    <div>
      <h4>商品样式</h4>
      <div class="content-item">
        <CommonStyle v-model="form.goodsCommonStyle" />
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '../common/common.scss' as *;
</style>

<script lang="ts" setup>
import { defineAsyncComponent, ref, watch } from 'vue';

import {
  ElCol,
  ElForm,
  ElFormItem,
  ElInputNumber,
  ElRow,
  ElSlider,
} from 'element-plus';

interface Value {
  styleTopMargin: number;
  styleBottomMargin: number;
  styleLeftMargin: number;
  styleRightMargin: number;
  styleTopPadding: number;
  styleBottomPadding: number;
  styleLeftPadding: number;
  styleRightPadding: number;
  styleLtRadius: number;
  styleRtRadius: number;
  styleLbRadius: number;
  styleRbRadius: number;
  bgColorDirection: string;
  bgStartColor: string;
  bgEndColor: string;
  bgPicUrl: string;
}

interface Props {
  modelValue?: Value;
}

const props = defineProps<Props>();
const emit = defineEmits(['update:modelValue']);
const defaultValue: Value = {
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
};
const selectMaterial = defineAsyncComponent(
  () => import('#/components/select-material/index.vue'),
);
const gradientColorPicker = defineAsyncComponent(
  () => import('../common-gradient-color-picker/index.vue'),
);

const form = ref(props.modelValue ?? defaultValue);
const styleMargin = ref(0);
const stylePadding = ref(0);
const styleRadius = ref(0);

// 外边距改变事件
const marginChange = (value: any) => {
  form.value.styleTopMargin = value;
  form.value.styleBottomMargin = value;
  form.value.styleLeftMargin = value;
  form.value.styleRightMargin = value;
};
// 内边距改变事件
const paddingChange = (value: any) => {
  form.value.styleTopPadding = value;
  form.value.styleBottomPadding = value;
  form.value.styleLeftPadding = value;
  form.value.styleRightPadding = value;
};
// 圆角改变事件
const radiusChange = (value: any) => {
  form.value.styleLtRadius = value;
  form.value.styleRtRadius = value;
  form.value.styleLbRadius = value;
  form.value.styleRbRadius = value;
};
watch(
  () => form,
  () => {
    emit('update:modelValue', form.value);
  },
  { deep: true, immediate: true },
);
watch(
  () => props.modelValue,
  () => {
    form.value = props.modelValue ?? defaultValue;
  },
);
</script>
<template>
  <div>
    <ElForm :model="form">
      <div class="common-item">
        <ElFormItem label="背景颜色">
          <gradientColorPicker
            v-model:direction="form.bgColorDirection"
            v-model:start-color="form.bgStartColor"
            v-model:end-color="form.bgEndColor"
          />
        </ElFormItem>
        <ElFormItem label="背景图">
          <div class="setting-form-item">
            <select-material
              v-model="form.bgPicUrl"
              :can-choose-images-num="1"
            />
            <p class="form-tip">背景图优先级高于背景色</p>
          </div>
        </ElFormItem>
      </div>

      <div class="common-item">
        <ElFormItem label="外边距">
          <ElSlider
            v-model="styleMargin"
            show-input
            :show-input-controls="false"
            :min="0"
            @change="marginChange"
          />
        </ElFormItem>
        <ElRow :gutter="20">
          <ElCol :span="12">
            <ElFormItem label="上边距">
              <ElInputNumber v-model="form.styleTopMargin" :min="-100" />
            </ElFormItem>
            <ElFormItem label="左边距">
              <ElInputNumber v-model="form.styleLeftMargin" :min="0" />
            </ElFormItem>
          </ElCol>
          <ElCol :span="12">
            <ElFormItem label="下边距">
              <ElInputNumber v-model="form.styleBottomMargin" :min="-100" />
            </ElFormItem>
            <ElFormItem label="右边距">
              <ElInputNumber v-model="form.styleRightMargin" :min="0" />
            </ElFormItem>
          </ElCol>
        </ElRow>
      </div>
      <div class="common-item">
        <ElFormItem label="内边距">
          <ElSlider
            v-model="stylePadding"
            show-input
            :show-input-controls="false"
            :min="0"
            @change="paddingChange"
          />
        </ElFormItem>
        <ElRow :gutter="20">
          <ElCol :span="12">
            <ElFormItem label="上边距">
              <ElInputNumber v-model="form.styleTopPadding" :min="0" />
            </ElFormItem>
            <ElFormItem label="左边距">
              <ElInputNumber v-model="form.styleLeftPadding" :min="0" />
            </ElFormItem>
          </ElCol>
          <ElCol :span="12">
            <ElFormItem label="下边距">
              <ElInputNumber v-model="form.styleBottomPadding" :min="0" />
            </ElFormItem>
            <ElFormItem label="右边距">
              <ElInputNumber v-model="form.styleRightPadding" :min="-0" />
            </ElFormItem>
          </ElCol>
        </ElRow>
      </div>
      <div class="common-item">
        <ElFormItem label="圆角">
          <ElSlider
            v-model="styleRadius"
            show-input
            :show-input-controls="false"
            :min="0"
            @change="radiusChange"
          />
        </ElFormItem>
        <ElRow :gutter="20">
          <ElCol :span="12">
            <ElFormItem label="左上">
              <ElInputNumber v-model="form.styleLtRadius" :min="0" />
            </ElFormItem>
            <ElFormItem label="右上">
              <ElInputNumber v-model="form.styleRtRadius" :min="0" />
            </ElFormItem>
          </ElCol>
          <ElCol :span="12">
            <ElFormItem label="左下">
              <ElInputNumber v-model="form.styleLbRadius" :min="0" />
            </ElFormItem>
            <ElFormItem label="右下">
              <ElInputNumber v-model="form.styleRbRadius" :min="0" />
            </ElFormItem>
          </ElCol>
        </ElRow>
      </div>
    </ElForm>
  </div>
</template>
<style lang="scss" scoped>
.common-item {
  padding: 10px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
</style>

<script setup lang="ts">
import type { PageSettings } from '../schema/types';

import { reactive, watch } from 'vue';

import {
  ElColorPicker,
  ElForm,
  ElFormItem,
  ElInput,
  ElSwitch,
} from 'element-plus';

import { cloneDesignerValue, isSameDesignerValue } from '../schema/clone';

const props = defineProps<{ modelValue: PageSettings }>();
const emit = defineEmits<{ 'update:modelValue': [value: PageSettings] }>();

const form = reactive<PageSettings>(cloneDesignerValue(props.modelValue));

watch(
  () => props.modelValue,
  (value) => {
    if (!isSameDesignerValue(form, value)) {
      Object.assign(form, cloneDesignerValue(value));
    }
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
  <ElForm :model="form" label-position="top" class="page-settings">
    <ElFormItem label="页面背景色">
      <ElColorPicker v-model="form.backgroundColor" show-alpha />
    </ElFormItem>
    <ElFormItem label="页面背景图">
      <ElInput
        v-model="form.backgroundImage"
        clearable
        placeholder="图片地址"
      />
    </ElFormItem>
    <ElFormItem label="显示导航栏">
      <ElSwitch v-model="form.navigation.visible" />
    </ElFormItem>
    <ElFormItem label="导航标题">
      <ElInput v-model="form.navigation.title" maxlength="30" />
    </ElFormItem>
    <ElFormItem label="导航背景 / 文字">
      <div class="color-row">
        <ElColorPicker v-model="form.navigation.backgroundColor" />
        <ElColorPicker v-model="form.navigation.textColor" />
      </div>
    </ElFormItem>
    <ElFormItem label="分享标题">
      <ElInput v-model="form.share.title" maxlength="60" />
    </ElFormItem>
    <ElFormItem label="分享描述">
      <ElInput
        v-model="form.share.description"
        maxlength="120"
        type="textarea"
      />
    </ElFormItem>
    <ElFormItem label="分享图片">
      <ElInput v-model="form.share.imageUrl" clearable placeholder="图片地址" />
    </ElFormItem>
    <ElFormItem label="下拉刷新">
      <ElSwitch v-model="form.enablePullDownRefresh" />
    </ElFormItem>
  </ElForm>
</template>

<style scoped>
.page-settings {
  padding: 16px;
}

.color-row {
  display: flex;
  gap: 12px;
}
</style>

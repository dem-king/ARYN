<script lang="ts" setup>
import { ref, watch } from 'vue';

import {
  ElAlert,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
} from 'element-plus';

import { sanitizeCustomHtml } from './sanitize-custom-html';

interface CustomHtmlForm {
  height: number;
  html: string;
}

const props = defineProps<{ modelValue: CustomHtmlForm }>();
const emit = defineEmits(['update:modelValue']);

const defaultConfig: CustomHtmlForm = {
  html: '',
  height: 300,
};

const form = ref<CustomHtmlForm>({ ...defaultConfig, ...props.modelValue });

watch(
  form,
  (val) => {
    // 保存到 schema 前自动净化 html 字段：移除 script / on* 事件 / javascript: 等危险内容
    emit('update:modelValue', {
      height: val.height,
      html: sanitizeCustomHtml(val.html ?? ''),
    });
  },
  { deep: true, immediate: true },
);
</script>

<template>
  <div class="setting-base">
    <div class="setting-title">
      <p>自定义HTML</p>
    </div>
    <div class="setting-form">
      <ElForm :model="form" label-position="top">
        <ElFormItem label="HTML 源码">
          <ElInput
            v-model="form.html"
            :autosize="{ minRows: 10, maxRows: 20 }"
            maxlength="50000"
            placeholder="输入 HTML 源码，例如 &lt;div&gt;自定义区块&lt;/div&gt;"
            type="textarea"
          />
          <ElAlert class="xss-tip" :closable="false" show-icon type="info">
            已自动过滤不安全标签与属性（script、iframe、事件绑定、javascript:
            链接等）
          </ElAlert>
        </ElFormItem>
        <ElFormItem label="容器高度 (px)">
          <ElInputNumber
            v-model="form.height"
            :max="2000"
            :min="100"
            :step="10"
            controls-position="right"
          />
        </ElFormItem>
      </ElForm>
    </div>
  </div>
</template>

<style lang="scss">
@use '#/views/promotion/page-design/components/common/common.scss' as *;

.xss-tip {
  margin-top: 8px;
}
</style>

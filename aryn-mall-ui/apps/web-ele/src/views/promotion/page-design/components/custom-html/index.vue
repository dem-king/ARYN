<script lang="ts" setup>
import { computed } from 'vue';

import { sanitizeCustomHtml } from './sanitize-custom-html';

interface CustomHtmlProps {
  height?: number;
  html?: string;
}

const props = defineProps<{
  showData: CustomHtmlProps;
}>();

/** 容器高度：默认 300px，范围由属性面板约束 */
const containerHeight = computed(() => {
  const height = Number(props.showData?.height);
  return Number.isFinite(height) && height > 0 ? height : 300;
});

/** 渲染前统一净化，避免画布内执行脚本 */
const sanitizedHtml = computed(() =>
  sanitizeCustomHtml(props.showData?.html ?? ''),
);
</script>

<template>
  <div class="custom-html-base" :style="{ height: `${containerHeight}px` }">
    <!-- 内容经 sanitizeCustomHtml 净化后渲染 -->
    <!-- eslint-disable vue/no-v-html -->
    <div
      v-if="sanitizedHtml"
      class="custom-html-body"
      v-html="sanitizedHtml"
    ></div>
    <!-- eslint-enable vue/no-v-html -->
    <div v-else class="custom-html-empty">自定义HTML</div>
  </div>
</template>

<style scoped lang="scss">
.custom-html-base {
  overflow: auto;
  background: #fff;

  :deep(img) {
    max-width: 100%;
    height: auto;
  }

  :deep(a) {
    color: var(--el-color-primary);
  }
}

.custom-html-body {
  /* 长串 URL/英文不会断行，运营粘贴的表格容易撑破预览画布 */
  overflow-wrap: anywhere;
}

.custom-html-empty {
  display: grid;
  place-items: center;
  height: 100%;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}
</style>

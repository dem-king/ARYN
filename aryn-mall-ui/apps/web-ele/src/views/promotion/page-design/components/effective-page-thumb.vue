<script setup lang="ts">
import type { CanvasThemeVars } from '../../page-designer/schema/theme-presets';
import type { DecorationDocument } from '../../page-designer/schema/types';

import type { PageDesignRecord } from '#/api/promotion/page-design';

import { computed, ref } from 'vue';

import { useElementSize } from '@vueuse/core';

import { migratePageContent } from '../../page-designer/schema/migrate';
import PreviewCanvas from '../../page-preview/components/preview-canvas.vue';

const props = defineProps<{
  page: PageDesignRecord;
  /** 商城默认主题变量（调用方解析传入），画布内部按「页面主题 > 商城默认」解析有效主题 */
  themeVars?: CanvasThemeVars;
}>();

/** 缩略图固定裁剪高度；画布按 375px 设计宽等比缩小 */
const THUMB_HEIGHT = 150;
const CANVAS_WIDTH = 375;

const hostRef = ref<HTMLElement>();
const { width } = useElementSize(hostRef);

const decorationDocument = computed<DecorationDocument | null>(() => {
  const content = props.page.pageContent;
  if (!content) return null;
  try {
    const raw = typeof content === 'string' ? JSON.parse(content) : content;
    return migratePageContent(raw);
  } catch {
    return null;
  }
});

const scale = computed(() =>
  width.value > 0 ? width.value / CANVAS_WIDTH : 0.6,
);
</script>

<template>
  <div
    ref="hostRef"
    class="page-thumb"
    :style="{ height: `${THUMB_HEIGHT}px` }"
  >
    <div
      v-if="decorationDocument"
      class="page-thumb-canvas"
      :style="{ transform: `scale(${scale})` }"
    >
      <PreviewCanvas
        :document="decorationDocument"
        :page-name="page.pageName"
        :page-type="page.pageType"
        :theme-vars="themeVars"
        :full-height="false"
      />
    </div>
    <div v-else class="page-thumb-empty">暂无预览</div>
  </div>
</template>

<style scoped>
.page-thumb {
  position: relative;
  overflow: hidden;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}

.page-thumb-canvas {
  width: 375px;
  transform-origin: top left;
}

.page-thumb-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>

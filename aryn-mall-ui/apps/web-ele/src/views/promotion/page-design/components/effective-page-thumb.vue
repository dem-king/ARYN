<script setup lang="ts">
import type { CanvasThemeVars } from '../../page-designer/schema/theme-presets';
import type { DecorationDocument } from '../../page-designer/schema/types';

import type { PageDesignRecord } from '#/api/promotion/page-design';

import { computed } from 'vue';

import { migratePageContent } from '../../page-designer/schema/migrate';
import PreviewCanvas from '../../page-preview/components/preview-canvas.vue';

const props = defineProps<{
  page: PageDesignRecord;
  /** 商城默认主题变量（调用方解析传入），画布内部按「页面主题 > 商城默认」解析有效主题 */
  themeVars?: CanvasThemeVars;
}>();

/** 手机视口缩略图：画布按 375px 设计宽渲染后固定缩放，
    裁剪窗即真机竖屏首屏（188 × 380 ≈ 375 × 760 设计像素，高在样式里）；
    固定尺寸而非随卡片满宽缩放，保证任何屏宽下都是手机竖屏比例 */
const CANVAS_WIDTH = 375;
const THUMB_WIDTH = 188;
const THUMB_SCALE = THUMB_WIDTH / CANVAS_WIDTH;

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
</script>

<template>
  <div class="page-thumb">
    <div class="page-thumb-frame">
      <div
        v-if="decorationDocument"
        class="page-thumb-canvas"
        :style="{ transform: `scale(${THUMB_SCALE})` }"
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
  </div>
</template>

<style scoped>
.page-thumb {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 396px;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}

.page-thumb-frame {
  width: 188px;
  height: 380px;
  overflow: hidden;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color);
  border-radius: 12px;
  box-shadow: 0 2px 8px rgb(15 23 42 / 8%);
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

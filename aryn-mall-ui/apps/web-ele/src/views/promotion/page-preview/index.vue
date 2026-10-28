<script setup lang="ts">
import type { DecorationDocument } from '../page-designer/schema/types';

import type {
  PageDesignTheme,
  PageDesignType,
} from '#/api/promotion/page-design';

import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  shallowRef,
  watch,
} from 'vue';
import { useRoute } from 'vue-router';

import { Refresh } from '@element-plus/icons-vue';
import { ElButton, ElResult, ElSkeleton } from 'element-plus';

import { getPreview, getThemes } from '#/api/promotion/page-design';

import { createDefaultDecorationDocument } from '../page-designer/schema/defaults';
import { migratePageContent } from '../page-designer/schema/migrate';
import { buildCanvasThemeVars } from '../page-designer/schema/theme-presets';
import PreviewCanvas from './components/preview-canvas.vue';

const route = useRoute();
const loading = shallowRef(true);
const errorMessage = shallowRef('');
const pageName = shallowRef('草稿预览');
/**
 * 页型：接口响应里带 pageType，必须透传给 PreviewCanvas —— 内嵌页型
 * （商详/分类/个人中心）预览不渲染装修自带导航栏，与 C 端 `embedded` 同构。
 * 此前漏传导致内嵌页型预览误渲染导航，「画布没有、预览有」。
 */
const pageType = shallowRef<PageDesignType>('0');
const document = shallowRef<DecorationDocument>(
  createDefaultDecorationDocument(),
);
const token = computed(() =>
  typeof route.params.token === 'string' ? route.params.token : '',
);
const isWeappShell = computed(() => route.query.terminal === 'weapp');

/**
 * 预览的有效主题（与编辑器画布、C 端 diy 渲染器同口径）：
 * 页面引用主题（themeRef，发布后固化为 themeSnapshot）优先，
 * 未引用时跟随商城默认主题，主题库不可用时退回内置默认配色。
 * 注：引用的主题已被删除时只能退回商城默认——草稿上没有快照颜色可查。
 */
const themes = shallowRef<PageDesignTheme[]>([]);
const previewThemeVars = computed(() => {
  const themeRef = document.value.themeRef;
  const theme =
    (themeRef
      ? themes.value.find((item) => item.id === themeRef)
      : undefined) ?? themes.value.find((item) => item.mallDefaultFlag === '1');
  return buildCanvasThemeVars(theme);
});

async function loadThemesQuietly() {
  try {
    themes.value = await getThemes();
  } catch {
    themes.value = [];
  }
}
void loadThemesQuietly();

async function loadPreview() {
  loading.value = true;
  errorMessage.value = '';
  try {
    if (!token.value) throw new Error('预览令牌缺失');
    const response = await getPreview(token.value);
    pageName.value = response.pageName;
    pageType.value = response.pageType;
    document.value = migratePageContent(response.pageContent);
    window.document.title = `${response.pageName} - 草稿预览`;
    // 重新加载后按当前窗口滚动位置补算一次（浏览器可能恢复了滚动位置）
    scrollTop.value = 0;
    void nextTick(scheduleCanvasScrollMeasure);
  } catch {
    errorMessage.value = '预览已过期、草稿已更新或链接无效';
  } finally {
    loading.value = false;
  }
}

/**
 * 草稿预览是浏览器整页滚动：把「画布顶边滚出视口的距离」经 prop 传给
 * PreviewCanvas，驱动导航栏滚动渐显。监听走 passive + rAF 合并，
 * 滚动过程中每帧最多算一次布局，避免强制同步布局引发掉帧。
 */
const canvasHostRef = shallowRef<HTMLElement | null>(null);
const scrollTop = shallowRef(0);
let scrollFrame = 0;

function measureCanvasScrollTop() {
  scrollFrame = 0;
  const host = canvasHostRef.value;
  if (!host) return;
  // 画布顶边在视口上方时为负值，取反即滚动距离（负值按 0 处理）
  const next = Math.round(Math.max(0, -host.getBoundingClientRect().top));
  if (next !== scrollTop.value) {
    scrollTop.value = next;
  }
}

function scheduleCanvasScrollMeasure() {
  if (scrollFrame) return;
  scrollFrame = window.requestAnimationFrame(measureCanvasScrollTop);
}

onMounted(() => {
  window.addEventListener('scroll', scheduleCanvasScrollMeasure, {
    passive: true,
  });
});
onBeforeUnmount(() => {
  window.removeEventListener('scroll', scheduleCanvasScrollMeasure);
  if (scrollFrame) {
    window.cancelAnimationFrame(scrollFrame);
    scrollFrame = 0;
  }
});

watch(token, loadPreview, { immediate: true });
</script>

<template>
  <div class="page-preview">
    <div v-if="loading" class="preview-loading">
      <ElSkeleton animated :rows="8" />
    </div>
    <ElResult
      v-else-if="errorMessage"
      icon="warning"
      :sub-title="errorMessage"
      title="无法打开草稿预览"
    >
      <template #extra>
        <ElButton :icon="Refresh" type="primary" @click="loadPreview">
          重新加载
        </ElButton>
      </template>
    </ElResult>
    <div v-else class="preview-shell" :class="{ weapp: isWeappShell }">
      <div v-if="isWeappShell" class="weapp-capsule" aria-hidden="true">
        <span class="weapp-title">{{ pageName }}</span>
        <span class="weapp-pill">
          <i class="weapp-dot"></i>
          <i class="weapp-dot"></i>
        </span>
      </div>
      <!-- 画布宿主：量它的 getBoundingClientRect().top 得到相对画布的滚动距离 -->
      <div ref="canvasHostRef" class="preview-canvas-host">
        <PreviewCanvas
          :document="document"
          :page-name="pageName"
          :page-type="pageType"
          :scroll-top="scrollTop"
          :theme-vars="previewThemeVars"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.page-preview {
  min-height: 100dvh;
  background: var(--el-fill-color-light);
}

.preview-shell.weapp {
  width: min(calc(100% - 32px), 375px);
  margin: 0 auto;

  /* 必须用 clip 而不是 hidden：hidden 会成为 sticky 导航的滚动祖先，导航滚到顶后不再吸住 */
  overflow: clip;
  background: var(--el-bg-color);
  border-radius: 12px;
  box-shadow: 0 12px 32px rgb(15 23 42 / 12%);
}

.weapp-capsule {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  background: #ededed;
}

.weapp-title {
  font-size: 14px;
  font-weight: 600;
}

.weapp-pill {
  display: inline-flex;
  gap: 8px;
  align-items: center;
  padding: 4px 10px;
  background: rgb(255 255 255 / 90%);
  border: 1px solid rgb(0 0 0 / 8%);
  border-radius: 999px;
}

.weapp-dot {
  width: 6px;
  height: 6px;
  background: #333;
  border-radius: 50%;
}

.preview-loading {
  width: min(calc(100% - 32px), 375px);
  min-height: 100dvh;
  padding: 96px 20px 20px;
  margin: 0 auto;
  background: var(--el-bg-color);
}
</style>

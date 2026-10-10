<script setup lang="ts">
import type { DecorationDocument } from '../page-designer/schema/types';

import type { PageDesignTheme } from '#/api/promotion/page-design';

import { computed, shallowRef, watch } from 'vue';
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
    document.value = migratePageContent(response.pageContent);
    window.document.title = `${response.pageName} - 草稿预览`;
  } catch {
    errorMessage.value = '预览已过期、草稿已更新或链接无效';
  } finally {
    loading.value = false;
  }
}

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
      <PreviewCanvas
        :document="document"
        :page-name="pageName"
        :theme-vars="previewThemeVars"
      />
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
  overflow: hidden;
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

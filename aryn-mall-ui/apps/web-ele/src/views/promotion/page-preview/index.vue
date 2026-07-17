<script setup lang="ts">
import type { DecorationDocument } from '../page-designer/schema/types';

import { computed, shallowRef, watch } from 'vue';
import { useRoute } from 'vue-router';

import { Refresh } from '@element-plus/icons-vue';
import { ElButton, ElResult, ElSkeleton } from 'element-plus';

import { getPreview } from '#/api/promotion/page-design';

import { createDefaultDecorationDocument } from '../page-designer/schema/defaults';
import { migratePageContent } from '../page-designer/schema/migrate';
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
    <PreviewCanvas v-else :document="document" :page-name="pageName" />
  </div>
</template>

<style scoped>
.page-preview {
  min-height: 100dvh;
  background: var(--el-fill-color-light);
}

.preview-loading {
  width: min(calc(100% - 32px), 375px);
  min-height: 100dvh;
  padding: 96px 20px 20px;
  margin: 0 auto;
  background: var(--el-bg-color);
}
</style>

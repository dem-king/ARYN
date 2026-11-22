<script setup lang="ts">
import type { RetailGoodsItem } from '../page-design/components/common/retail-preview/retail-data';

import type { CampaignPagePreview } from '#/api/promotion/campaign-draft';

import { computed, provide } from 'vue';

import { campaignPreviewGoodsKey } from '../page-design/components/common/retail-preview/campaign-preview';
import { migratePageContent } from '../page-designer/schema/migrate';
import PreviewCanvas from '../page-preview/components/preview-canvas.vue';

const props = defineProps<{
  goods: ReadonlyMap<string, RetailGoodsItem>;
  preview: CampaignPagePreview;
}>();
provide(
  campaignPreviewGoodsKey,
  computed(() => props.goods),
);
const document = computed(() =>
  migratePageContent(props.preview.page.pageContent),
);
</script>
<template>
  <div class="sample-preview">
    <p>固定样本预览 · 仅显示已核定报价商品</p>
    <PreviewCanvas
      :document="document"
      :page-name="preview.page.pageName"
      page-type="0"
      :full-height="false"
    />
  </div>
</template>
<style scoped>
.sample-preview {
  padding: 20px;
  margin: 24px 0;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
}

p {
  margin-bottom: 16px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  text-align: center;
}
</style>

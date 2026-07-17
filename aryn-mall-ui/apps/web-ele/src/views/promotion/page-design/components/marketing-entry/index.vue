<script setup lang="ts">
import type { RetailPreviewStatus } from '../common/retail-preview/use-retail-preview';
import type { MarketingEntryProps } from './types';

import { computed } from 'vue';

import { Picture, Promotion } from '@element-plus/icons-vue';
import { ElIcon, ElImage } from 'element-plus';

import RetailPreviewFrame from '../common/retail-preview/retail-preview-frame.vue';
import { validateMarketingEntry } from './types';

const props = defineProps<{ showData: MarketingEntryProps }>();
const validationErrors = computed(() => validateMarketingEntry(props.showData));
const visibleEntries = computed(() =>
  props.showData.entries.slice(0, props.showData.count),
);
const previewStatus = computed<RetailPreviewStatus>(() => {
  if (validationErrors.value.length > 0) return 'invalid';
  return visibleEntries.value.length > 0 ? 'data' : 'empty';
});
</script>

<template>
  <RetailPreviewFrame
    :common-style="showData.commonStyle"
    :message="validationErrors[0]"
    :status="previewStatus"
  >
    <nav
      class="marketing-grid"
      :style="{
        gridTemplateColumns: `repeat(${showData.columns}, minmax(0, 1fr))`,
      }"
    >
      <div
        v-for="entry in visibleEntries"
        :key="entry.id"
        class="marketing-entry"
      >
        <ElImage
          v-if="entry.iconUrl"
          :alt="entry.title"
          :src="entry.iconUrl"
          fit="cover"
        >
          <template #error>
            <span class="marketing-entry__icon"
              ><ElIcon><Picture /></ElIcon
            ></span>
          </template>
        </ElImage>
        <span v-else class="marketing-entry__icon">
          <ElIcon><Promotion /></ElIcon>
        </span>
        <span>{{ entry.title }}</span>
      </div>
    </nav>
  </RetailPreviewFrame>
</template>

<style scoped>
.marketing-grid {
  display: grid;
  gap: 10px 6px;
}

.marketing-entry {
  display: grid;
  gap: 6px;
  place-items: center;
  min-width: 0;
  font-size: 12px;
  color: #334155;
  text-align: center;
}

.marketing-entry :deep(.el-image),
.marketing-entry__icon {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  color: #fff;
  background: #e11d48;
  border-radius: 6px;
}

.marketing-entry > span:last-child {
  width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>

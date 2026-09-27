<script setup lang="ts">
import type { RetailPreviewStatus } from '../common/retail-preview/use-retail-preview';
import type { MarketingEntryProps } from './types';

import { computed } from 'vue';

import { Picture } from '@element-plus/icons-vue';
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
    <div v-if="visibleEntries.length > 0" class="entry-grid">
      <div
        v-for="entry in visibleEntries"
        :key="entry.id"
        class="entry-cell"
        :style="{ width: `${100 / showData.columns}%` }"
      >
        <div class="entry-button">
          <ElImage
            v-if="entry.iconUrl"
            :alt="entry.title"
            class="entry-icon"
            :src="entry.iconUrl"
            fit="cover"
          >
            <template #error>
              <span class="entry-icon entry-icon--empty">
                <ElIcon><Picture /></ElIcon>
              </span>
            </template>
          </ElImage>
          <!-- 无图标时小程序回落为标题首字 -->
          <span v-else class="entry-icon entry-icon--empty">
            {{ entry.title.slice(0, 1) }}
          </span>
          <span class="entry-title">{{ entry.title }}</span>
        </div>
      </div>
    </div>
    <div v-else class="entry-empty">暂无入口</div>
  </RetailPreviewFrame>
</template>

<!--
  样式与小程序 diy-marketing-entry 逐值对齐。
  换算口径：小程序屏宽在 rpx 下恒为 750，画布正好 375px，故 1rpx = 0.5px，
  本文件所有 px 值都是对应 rpx 值的一半；改任一端都要同步另一端。
-->
<style scoped lang="scss">
.entry-grid {
  display: flex;
  flex-wrap: wrap;
}

.entry-cell {
  box-sizing: border-box;
  padding: 5px 3px;
}

.entry-button {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
}

.entry-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  font-size: 15px;
  color: #606266;
  background: #f2f3f5;
  border-radius: 4px;
}

.entry-title {
  max-width: 100%;
  margin-top: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 11.5px;
  color: #303133;
  white-space: nowrap;
}

.entry-empty {
  padding: 15px 0;
  font-size: 12px;
  color: #909399;
  text-align: center;
}
</style>

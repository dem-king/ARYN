<script setup lang="ts">
import type { GoodsRankingProps } from './types';

import { watch } from 'vue';

import { Picture } from '@element-plus/icons-vue';
import { ElIcon, ElImage } from 'element-plus';

import { loadGoodsRanking } from '../common/retail-preview/retail-data';
import RetailPreviewFrame from '../common/retail-preview/retail-preview-frame.vue';
import { createRetailPreviewController } from '../common/retail-preview/use-retail-preview';
import { validateGoodsRanking } from './types';

const props = defineProps<{ showData: GoodsRankingProps }>();
const controller =
  createRetailPreviewController<
    Awaited<ReturnType<typeof loadGoodsRanking>>[number]
  >();
const { state } = controller;

watch(
  () => props.showData,
  (showData) =>
    controller.load(validateGoodsRanking(showData), () =>
      loadGoodsRanking(showData),
    ),
  { deep: true, immediate: true },
);
</script>

<template>
  <RetailPreviewFrame
    :common-style="showData.commonStyle"
    :message="state.message"
    :status="state.status"
    :title="showData.title"
  >
    <ol class="ranking-list">
      <li v-for="(item, index) in state.items" :key="item.id">
        <span v-if="showData.showRankNumber" class="ranking-number">{{
          index + 1
        }}</span>
        <ElImage
          v-if="item.imageUrl"
          :alt="item.name"
          :src="item.imageUrl"
          fit="cover"
        >
          <template #error>
            <span class="ranking-image"
              ><ElIcon><Picture /></ElIcon
            ></span>
          </template>
        </ElImage>
        <span v-else class="ranking-image">
          <ElIcon><Picture /></ElIcon>
        </span>
        <span class="ranking-name">{{ item.name }}</span>
        <span class="ranking-sales">{{ item.sales }} 件</span>
      </li>
    </ol>
  </RetailPreviewFrame>
</template>

<style scoped>
.ranking-list {
  display: grid;
  gap: 8px;
  padding: 0;
  margin: 0;
  list-style: none;
}

.ranking-list li {
  display: grid;
  grid-template-columns: 24px 48px minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
  min-height: 48px;
}

.ranking-number {
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  font-size: 12px;
  font-weight: 700;
  color: #fff;
  background: #ef4444;
  border-radius: 4px;
}

.ranking-list :deep(.el-image),
.ranking-image {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  color: #94a3b8;
  background: #f1f5f9;
  border-radius: 4px;
}

.ranking-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 13px;
  white-space: nowrap;
}

.ranking-sales {
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  color: #dc2626;
}
</style>

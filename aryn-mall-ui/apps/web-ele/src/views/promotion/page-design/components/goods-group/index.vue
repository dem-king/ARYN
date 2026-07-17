<script setup lang="ts">
import type { GoodsGroupProps } from './types';

import { watch } from 'vue';

import { Picture } from '@element-plus/icons-vue';
import { ElIcon, ElImage } from 'element-plus';

import { loadGoodsGroup } from '../common/retail-preview/retail-data';
import RetailPreviewFrame from '../common/retail-preview/retail-preview-frame.vue';
import { createRetailPreviewController } from '../common/retail-preview/use-retail-preview';
import { validateGoodsGroup } from './types';

const props = defineProps<{ showData: GoodsGroupProps }>();
const controller =
  createRetailPreviewController<
    Awaited<ReturnType<typeof loadGoodsGroup>>[number]
  >();
const { state } = controller;

watch(
  () => props.showData,
  (showData) =>
    controller.load(validateGoodsGroup(showData), () =>
      loadGoodsGroup(showData),
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
    <div
      class="goods-grid"
      :style="{
        gridTemplateColumns: `repeat(${showData.columns}, minmax(0, 1fr))`,
      }"
    >
      <article v-for="item in state.items" :key="item.id" class="goods-card">
        <ElImage
          v-if="item.imageUrl"
          :alt="item.name"
          :src="item.imageUrl"
          fit="cover"
        >
          <template #error>
            <div class="goods-card__image">
              <ElIcon><Picture /></ElIcon>
            </div>
          </template>
        </ElImage>
        <div v-else class="goods-card__image">
          <ElIcon><Picture /></ElIcon>
        </div>
        <div class="goods-card__body">
          <div class="goods-card__name">{{ item.name }}</div>
          <div class="goods-card__meta">
            <strong>¥{{ item.price.toFixed(2) }}</strong>
            <span v-if="showData.showSales">已售 {{ item.sales }}</span>
          </div>
        </div>
      </article>
    </div>
  </RetailPreviewFrame>
</template>

<style scoped>
.goods-grid {
  display: grid;
  gap: 8px;
}

.goods-card {
  min-width: 0;
  overflow: hidden;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
}

.goods-card :deep(.el-image),
.goods-card__image {
  display: grid;
  place-items: center;
  width: 100%;
  aspect-ratio: 1;
  color: #94a3b8;
  background: #f1f5f9;
}

.goods-card__body {
  padding: 8px;
}

.goods-card__name {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 13px;
  white-space: nowrap;
}

.goods-card__meta {
  display: flex;
  gap: 6px;
  align-items: end;
  justify-content: space-between;
  margin-top: 8px;
  font-size: 11px;
  color: #64748b;
}

.goods-card__meta strong {
  font-size: 14px;
  color: #dc2626;
}
</style>

<script setup lang="ts">
import type { GoodsRankingProps } from './types';

import { watch } from 'vue';

import { Picture } from '@element-plus/icons-vue';
import { ElIcon, ElImage } from 'element-plus';

import { shouldShowOriginalPrice } from '../common/retail-preview/price-display';
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
  >
    <div class="section-title">{{ showData.title }}</div>
    <div class="ranking-list">
      <div
        v-for="(item, index) in state.items"
        :key="item.id"
        class="ranking-item"
      >
        <span
          v-if="showData.showRankNumber"
          class="rank-number"
          :class="{ 'rank-number--top': index < 3 }"
        >
          {{ index + 1 }}
        </span>
        <ElImage
          v-if="item.imageUrl"
          :alt="item.name"
          class="ranking-image"
          :src="item.imageUrl"
          fit="cover"
        >
          <template #error>
            <span class="ranking-image ranking-image--empty">
              <ElIcon><Picture /></ElIcon>
            </span>
          </template>
        </ElImage>
        <span v-else class="ranking-image ranking-image--empty">
          <ElIcon><Picture /></ElIcon>
        </span>
        <div class="ranking-content">
          <div class="ranking-name">{{ item.name }}</div>
          <div class="ranking-stats">
            销量 {{ item.sales }} · 库存 {{ item.stock }}
          </div>
          <div class="ranking-price">
            ￥{{ item.price.toFixed(2) }}
            <!-- 划线原价：仅原价严格高于售价时显示（存量商品原价多为 0，会划出￥0） -->
            <span
              v-if="showData.showOriginalPrice && shouldShowOriginalPrice(item)"
              class="ranking-price-original"
            >
              ￥{{ item.originalPrice.toFixed(2) }}
            </span>
          </div>
        </div>
      </div>
    </div>
  </RetailPreviewFrame>
</template>

<!--
  样式与小程序 diy-goods-ranking 逐值对齐。
  换算口径：小程序屏宽在 rpx 下恒为 750，画布正好 375px，故 1rpx = 0.5px，
  本文件所有 px 值都是对应 rpx 值的一半；改任一端都要同步另一端。
-->
<style scoped lang="scss">
.section-title {
  margin-bottom: 6px;
  font-size: 15px;
  font-weight: 600;
  color: #1f2329;
}

.ranking-list {
  display: flex;
  flex-direction: column;
}

.ranking-item {
  display: flex;
  gap: 8px;
  align-items: center;
  min-height: 66px;
  border-bottom: 0.5px solid #eef0f3;

  &:last-child {
    border-bottom: 0;
  }
}

/* 小程序端排名是纯文字，前三名才转红加粗 */
.rank-number {
  width: 18px;
  font-size: 13px;
  color: #909399;
  text-align: center;
}

.rank-number--top {
  font-weight: 700;
  color: #e5484d;
}

.ranking-image {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  font-size: 11px;
  color: #a8abb2;
  background: #f3f4f6;
  border-radius: 4px;
}

.ranking-content {
  flex: 1;
  min-width: 0;
}

.ranking-name {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 13px;
  color: #303133;
  white-space: nowrap;
}

.ranking-stats {
  margin-top: 4px;
  font-size: 10.5px;
  color: #909399;
}

.ranking-price {
  margin-top: 3px;
  font-size: 13.5px;
  font-weight: 600;
  color: #e5484d;
}

/* 划线原价：与小程序 diy-goods-ranking 的 .ranking-price-original 逐值对齐（1px = 2rpx） */
.ranking-price-original {
  margin-left: 4px;
  font-size: 11px;
  font-weight: 400;
  color: #999;
  text-decoration: line-through;
}
</style>

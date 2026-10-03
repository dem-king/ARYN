<script setup lang="ts">
import type { GoodsGroupProps } from './types';

import { watch } from 'vue';

import { Picture, ShoppingCart } from '@element-plus/icons-vue';
import { ElIcon, ElImage } from 'element-plus';

import { shouldShowOriginalPrice } from '../common/retail-preview/price-display';
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
  >
    <div class="section-title">{{ showData.title }}</div>
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
          class="goods-image"
          :src="item.imageUrl"
          fit="contain"
        >
          <template #error>
            <div class="goods-image goods-image--empty">
              <ElIcon><Picture /></ElIcon>
            </div>
          </template>
        </ElImage>
        <div v-else class="goods-image goods-image--empty">
          <ElIcon><Picture /></ElIcon>
        </div>
        <div class="goods-name">{{ item.name }}</div>
        <div class="goods-meta">
          <div class="goods-meta-text">
            <span class="goods-price-line">
              <span class="goods-price">￥{{ item.price.toFixed(2) }}</span>
              <!-- 划线原价：仅原价严格高于售价时显示（存量商品原价多为 0，会划出￥0） -->
              <span
                v-if="
                  showData.showOriginalPrice && shouldShowOriginalPrice(item)
                "
                class="goods-price-original"
              >
                ￥{{ item.originalPrice.toFixed(2) }}
              </span>
            </span>
            <span v-if="showData.showSales" class="goods-sales">
              已售 {{ item.sales }}
            </span>
          </div>
          <!-- 小程序卡片右下角是真实可点的快捷加购按钮，这里按同尺寸静态呈现 -->
          <span v-if="item.stock > 0" class="goods-cart">
            <ElIcon><ShoppingCart /></ElIcon>
          </span>
        </div>
        <div v-if="item.stock <= 0" class="stock-label">暂时缺货</div>
      </article>
    </div>
  </RetailPreviewFrame>
</template>

<!--
  样式与小程序 diy-goods-group 逐值对齐。
  换算口径：小程序屏宽在 rpx 下恒为 750，画布正好 375px，故 1rpx = 0.5px，
  本文件所有 px 值都是对应 rpx 值的一半；改任一端都要同步另一端。
-->
<style scoped lang="scss">
.section-title {
  margin-bottom: 9px;
  font-size: 15px;
  font-weight: 600;
  color: #1f2329;
}

.goods-grid {
  display: grid;
  gap: 8px;
}

.goods-card {
  box-sizing: border-box;
  min-width: 0;
  overflow: hidden;
  background: #fff;
  border: 0.5px solid #eef0f3;
  border-radius: 4px;
}

/* 图片 1:1 容器 + contain 完整展示，与小程序 diy-goods-group 的
   goods-image-box（padding-top:100% 锁 1:1）+ mode=aspectFit 对齐 */
.goods-image {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  aspect-ratio: 1;
  font-size: 12px;
  color: #a8abb2;
  background: #fff;
}

.goods-image--empty {
  background: #f3f4f6;
}

.goods-name {
  margin: 7px 7px 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 13px;
  color: #303133;
  white-space: nowrap;
}

.goods-meta {
  display: flex;
  gap: 4px;
  align-items: center;
  justify-content: space-between;
  margin: 0 7px 7px;
}

.goods-meta-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.goods-price-line {
  display: flex;
  gap: 3px;
  align-items: baseline;
  min-width: 0;
}

.goods-price {
  font-size: 14px;
  font-weight: 600;
  color: var(--wot-color-theme-primary, #ff2237);
}

/* 划线原价：与小程序 diy-goods-group 的 .goods-price-original 逐值对齐（1px = 2rpx） */
.goods-price-original {
  flex: none;
  font-size: 11px;
  font-weight: 400;
  color: #999;
  text-decoration: line-through;
}

.goods-sales {
  font-size: 10px;
  color: #909399;
}

.goods-cart {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  font-size: 11px;
  color: #fff;
  background: #4d7fff;
  border-radius: 50%;
}

.stock-label {
  margin: -2px 7px 7px;
  font-size: 10px;
  color: #909399;
}
</style>

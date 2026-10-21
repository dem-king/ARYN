<script setup lang="ts">
import type { RetailGoodsItem } from '../../common/retail-preview/retail-data';
import type { GoodsWaterfallProps } from '../types';

import { watch } from 'vue';

import { Picture, ShoppingCart } from '@element-plus/icons-vue';
import { ElIcon, ElImage } from 'element-plus';

import { shouldShowOriginalPrice } from '../../common/retail-preview/price-display';
import { loadGoodsWaterfall } from '../../common/retail-preview/retail-data';
import RetailPreviewFrame from '../../common/retail-preview/retail-preview-frame.vue';
import { shouldShowSubtitle } from '../../common/retail-preview/subtitle-display';
import { createRetailPreviewController } from '../../common/retail-preview/use-retail-preview';
import { validateGoodsWaterfall } from '../types';

const props = defineProps<{ showData: GoodsWaterfallProps }>();
const controller = createRetailPreviewController<RetailGoodsItem>();
const { state } = controller;

watch(
  () => props.showData,
  (showData) =>
    controller.load(validateGoodsWaterfall(showData), () =>
      loadGoodsWaterfall(showData),
    ),
  { deep: true, immediate: true },
);

/**
 * 卡片信息 chip：与 C 端 cardTags 同口径——只渲染确实存在的信息。
 * 管理端取数未归一化规格/免配送费字段，故只出「现货 N」这一条；
 * 字段缺失就不编造，避免出现「现货 0」这类误导性文案。
 */
function cardTags(item: RetailGoodsItem): string[] {
  return item.stock > 0 ? [`现货 ${item.stock}`] : [];
}
</script>

<template>
  <RetailPreviewFrame
    :common-style="showData.commonStyle"
    :message="state.message"
    :status="state.status"
    type-label="商品瀑布流"
  >
    <div class="waterfall-inner">
      <div v-if="showData.title" class="waterfall-title">
        {{ showData.title }}
      </div>
      <div
        class="waterfall-columns"
        :style="{ columnCount: showData.columns || 2 }"
      >
        <article
          v-for="item in state.items"
          :key="item.id"
          class="waterfall-card"
        >
          <ElImage
            v-if="item.imageUrl"
            :alt="item.name"
            class="waterfall-pic"
            :src="item.imageUrl"
            fit="cover"
          >
            <template #error>
              <div class="waterfall-pic waterfall-pic--empty">
                <ElIcon><Picture /></ElIcon>
              </div>
            </template>
          </ElImage>
          <div v-else class="waterfall-pic waterfall-pic--empty">
            <ElIcon><Picture /></ElIcon>
          </div>
          <div class="waterfall-name">
            {{ item.name }}
          </div>
          <div v-if="shouldShowSubtitle(item)" class="waterfall-subtitle">
            {{ item.subTitle }}
          </div>
          <div v-if="cardTags(item).length > 0" class="waterfall-tags">
            <span
              v-for="tag in cardTags(item)"
              :key="tag"
              class="waterfall-tag"
            >
              {{ tag }}
            </span>
          </div>
          <div class="waterfall-meta">
            <div class="waterfall-meta-text">
              <div class="waterfall-price-line">
                <span
                  v-if="showData.showPrice !== false"
                  class="waterfall-price"
                >
                  ¥{{ item.price.toFixed(2) }}
                </span>
                <!-- 划线原价：仅原价严格高于售价时显示（存量商品原价多为 0，会划出￥0） -->
                <span
                  v-if="
                    showData.showOriginalPrice && shouldShowOriginalPrice(item)
                  "
                  class="waterfall-price-original"
                >
                  ¥{{ item.originalPrice.toFixed(2) }}
                </span>
              </div>
              <span v-if="showData.showSales" class="waterfall-sales">
                已售 {{ item.sales }}
              </span>
            </div>
            <!-- 小程序卡片右下角是真实可点的快捷加购按钮，这里按同尺寸静态呈现 -->
            <span v-if="item.stock > 0" class="waterfall-cart">
              <ElIcon><ShoppingCart /></ElIcon>
            </span>
          </div>
        </article>
      </div>
    </div>
  </RetailPreviewFrame>
</template>

<!--
  样式与小程序 diy-goods-waterfall 逐值对齐。
  换算口径：小程序屏宽在 rpx 下恒为 750，画布正好 375px，故 1rpx = 0.5px，
  本文件所有 px 值都是对应 rpx 值的一半；改任一端都要同步另一端。
  图片区固定 1:1：小程序用 mode=widthFix 跟随图片原始比例，
  画布无法预知远端图片比例，按 1:1 近似（与 goods-group 预览同口径）。
-->
<style scoped lang="scss">
.waterfall-inner {
  padding: 10px;
}

.waterfall-title {
  margin-bottom: 8px;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.waterfall-columns {
  column-gap: 8px;
}

.waterfall-card {
  display: block;
  margin-bottom: 8px;
  overflow: hidden;
  background: #fff;
  border: 0.5px solid #eef0f3;
  border-radius: 6px;
  break-inside: avoid;
}

.waterfall-pic {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  aspect-ratio: 1;
  font-size: 12px;
  color: #a8abb2;
  background: #fff;
}

.waterfall-pic--empty {
  background: #f3f4f6;
}

.waterfall-name {
  display: -webkit-box;
  padding: 6px 6px 0;
  overflow: hidden;
  -webkit-line-clamp: 2;
  font-size: 13px;
  color: #303133;
  word-break: normal;
  overflow-wrap: break-word;
  -webkit-box-orient: vertical;
}

.waterfall-subtitle {
  padding: 2px 6px 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 10.5px;
  line-height: 1.4;
  color: #909399;
  white-space: nowrap;
}

.waterfall-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 3px;
  padding: 4px 6px 0;
}

.waterfall-tag {
  padding: 1px 4px;
  font-size: 10px;
  line-height: 1.5;
  color: #8a97a6;
  background: #f2f3f5;
  border-radius: 2px;
}

.waterfall-meta {
  display: flex;
  gap: 4px;
  align-items: center;
  justify-content: space-between;
  padding: 2px 6px 6px;
}

.waterfall-meta-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.waterfall-price-line {
  display: flex;
  gap: 3px;
  align-items: baseline;
  min-width: 0;
}

.waterfall-price {
  font-size: 15px;
  font-weight: 600;
  color: var(--wot-color-theme-primary, #ff2237);
}

/* 划线原价：灰字小一号，仅作价格锚点，不与售价抢视觉层级 */
.waterfall-price-original {
  flex: none;
  font-size: 11px;
  font-weight: normal;
  color: #999;
  text-decoration: line-through;
}

.waterfall-sales {
  font-size: 11px;
  color: #999;
}

.waterfall-cart {
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
</style>

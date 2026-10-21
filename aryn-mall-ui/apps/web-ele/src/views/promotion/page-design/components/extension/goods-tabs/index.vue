<script setup lang="ts">
import type { RetailGoodsItem } from '../../common/retail-preview/retail-data';
import type {
  GoodsTabConfig,
  GoodsTabsProps,
  GoodsWaterfallProps,
} from '../types';

import { computed, ref, watch } from 'vue';

import { Picture, ShoppingCart } from '@element-plus/icons-vue';
import { ElIcon, ElImage } from 'element-plus';

import { shouldShowOriginalPrice } from '../../common/retail-preview/price-display';
import { loadGoodsWaterfall } from '../../common/retail-preview/retail-data';
import RetailPreviewFrame from '../../common/retail-preview/retail-preview-frame.vue';
import { createRetailPreviewController } from '../../common/retail-preview/use-retail-preview';
import { validateGoodsTabs } from '../types';

const props = defineProps<{ showData: GoodsTabsProps }>();
const controller = createRetailPreviewController<RetailGoodsItem>();
const { state } = controller;
const activeIndex = ref(0);

const tabs = computed<GoodsTabConfig[]>(() => props.showData.tabs ?? []);
const activeTab = computed(
  () => tabs.value[activeIndex.value] || tabs.value[0],
);

watch(
  () => props.showData.tabs,
  () => {
    if (activeIndex.value >= tabs.value.length) activeIndex.value = 0;
  },
  { deep: true },
);

watch(
  [() => props.showData, activeIndex],
  ([showData]) => {
    const tab = activeTab.value;
    controller.load(validateGoodsTabs(showData), () =>
      tab
        ? loadGoodsWaterfall({
            columns: showData.columns,
            commonStyle: showData.commonStyle,
            count: showData.count,
            dataSource: tab.dataSource,
            showOriginalPrice: showData.showOriginalPrice,
            showPrice: showData.showPrice,
            showSales: showData.showSales,
            title: tab.title,
          } as GoodsWaterfallProps)
        : Promise.resolve([]),
    );
  },
  { deep: true, immediate: true },
);

/**
 * 卡片信息 chip：与 C 端 cardTags 同口径——只渲染确实存在的信息。
 * 管理端取数未归一化规格/免配送费字段，故只出「现货 N」这一条。
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
    type-label="多Tab商品流"
  >
    <div class="goods-tabs-bar">
      <div
        v-for="(tab, index) in tabs"
        :key="tab.id"
        class="goods-tabs-tab"
        :class="{ 'goods-tabs-tab--active': index === activeIndex }"
        @click="activeIndex = index"
      >
        <span class="goods-tabs-label">{{ tab.title }}</span>
        <span v-if="index === activeIndex" class="goods-tabs-indicator"></span>
      </div>
    </div>

    <div class="goods-tabs-inner">
      <div
        class="goods-tabs-grid"
        :style="{
          gridTemplateColumns: `repeat(${showData.columns || 2}, minmax(0, 1fr))`,
        }"
      >
        <article
          v-for="item in state.items"
          :key="item.id"
          class="goods-tabs-card"
        >
          <div class="goods-tabs-image-box">
            <ElImage
              v-if="item.imageUrl"
              :alt="item.name"
              class="goods-tabs-image"
              :src="item.imageUrl"
              fit="contain"
            >
              <template #error>
                <div class="goods-tabs-image goods-tabs-image--empty">
                  <ElIcon><Picture /></ElIcon>
                </div>
              </template>
            </ElImage>
            <div v-else class="goods-tabs-image goods-tabs-image--empty">
              <ElIcon><Picture /></ElIcon>
            </div>
            <div v-if="item.stock <= 0" class="goods-tabs-sold-out">
              暂时缺货
            </div>
          </div>
          <div class="goods-tabs-name">{{ item.name }}</div>
          <div v-if="cardTags(item).length > 0" class="goods-tabs-tags">
            <span
              v-for="tag in cardTags(item)"
              :key="tag"
              class="goods-tabs-tag"
            >
              {{ tag }}
            </span>
          </div>
          <div class="goods-tabs-meta">
            <div class="goods-tabs-meta-text">
              <div class="goods-tabs-price-line">
                <span
                  v-if="showData.showPrice !== false"
                  class="goods-tabs-price"
                >
                  ¥{{ item.price.toFixed(2) }}
                </span>
                <!-- 划线原价：仅原价严格高于售价时显示（存量商品原价多为 0，会划出￥0） -->
                <span
                  v-if="
                    showData.showOriginalPrice && shouldShowOriginalPrice(item)
                  "
                  class="goods-tabs-price-original"
                >
                  ¥{{ item.originalPrice.toFixed(2) }}
                </span>
              </div>
              <span v-if="showData.showSales" class="goods-tabs-sales">
                已售 {{ item.sales }}
              </span>
            </div>
            <!-- 小程序卡片右下角是真实可点的快捷加购按钮，这里按同尺寸静态呈现 -->
            <span v-if="item.stock > 0" class="goods-tabs-cart">
              <ElIcon><ShoppingCart /></ElIcon>
            </span>
          </div>
        </article>
      </div>
    </div>
  </RetailPreviewFrame>
</template>

<!--
  样式与小程序 diy-goods-tabs 逐值对齐。
  换算口径：小程序屏宽在 rpx 下恒为 750，画布正好 375px，故 1rpx = 0.5px，
  本文件所有 px 值都是对应 rpx 值的一半；改任一端都要同步另一端。
-->
<style scoped lang="scss">
.goods-tabs-bar {
  display: flex;
  align-items: center;
  height: 44px;
  background: #fff;
  border-bottom: 0.5px solid #f2f3f5;
}

.goods-tabs-tab {
  position: relative;
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  height: 100%;
  padding: 0 6px;
  font-size: 15px;
  color: #4e5969;
  cursor: pointer;
}

.goods-tabs-tab--active {
  font-weight: 600;
  color: #1f2329;
}

.goods-tabs-indicator {
  position: absolute;
  bottom: 5px;
  left: 50%;
  width: 20px;
  height: 3px;
  margin-left: -10px;
  background: var(--wot-color-theme-primary, #ff2237);
  border-radius: 999px;
}

.goods-tabs-inner {
  padding: 8px 10px 10px;
}

.goods-tabs-grid {
  display: grid;
  gap: 8px;
}

.goods-tabs-card {
  box-sizing: border-box;
  min-width: 0;
  overflow: hidden;
  background: #fff;
  border: 0.5px solid #eef0f3;
  border-radius: 8px;
}

/* 图片 1:1 容器 + contain 完整展示，与小程序 goods-tabs__image-box 对齐 */
.goods-tabs-image-box {
  position: relative;
  width: 100%;
  aspect-ratio: 1;
  background: #fff;
}

.goods-tabs-image {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  font-size: 12px;
  color: #a8abb2;
}

.goods-tabs-image--empty {
  background: #f3f4f6;
}

.goods-tabs-sold-out {
  position: absolute;
  top: 0;
  right: 0;
  left: 0;
  padding: 4px;
  font-size: 11px;
  color: #fff;
  text-align: center;
  background: rgb(0 0 0 / 50%);
}

.goods-tabs-name {
  display: -webkit-box;
  min-height: 2.8em;
  margin: 7px 7px 0;
  overflow: hidden;
  -webkit-line-clamp: 2;
  font-size: 14px;
  line-height: 1.4;
  color: #303133;
  word-break: normal;
  overflow-wrap: break-word;
  -webkit-box-orient: vertical;
}

.goods-tabs-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 3px;
  padding: 4px 7px 0;
}

.goods-tabs-tag {
  padding: 1px 4px;
  font-size: 10px;
  line-height: 1.5;
  color: #8a97a6;
  background: #f2f3f5;
  border-radius: 3px;
}

.goods-tabs-meta {
  display: flex;
  gap: 4px;
  align-items: flex-end;
  justify-content: space-between;
  padding: 6px 7px 7px;
}

.goods-tabs-meta-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.goods-tabs-price-line {
  display: flex;
  flex-wrap: wrap;
  gap: 3px;
  align-items: baseline;
  min-width: 0;
}

.goods-tabs-price {
  font-size: 16px;
  font-weight: 600;
  color: var(--wot-color-theme-primary, #ff2237);
}

.goods-tabs-price-original {
  flex: none;
  font-size: 11px;
  font-weight: 400;
  color: #999;
  text-decoration: line-through;
}

.goods-tabs-sales {
  font-size: 10px;
  color: #909399;
}

.goods-tabs-cart {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  font-size: 12px;
  color: #fff;
  background: var(--wot-color-theme-primary, #4d7fff);
  border-radius: 50%;
}
</style>

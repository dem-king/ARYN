<script setup lang="ts">
import type { RetailGoodsItem } from '../../common/retail-preview/retail-data';
import type { GoodsRecommendProps } from '../types';

import { watch } from 'vue';

import { Picture } from '@element-plus/icons-vue';
import { ElIcon, ElImage } from 'element-plus';

import { shouldShowOriginalPrice } from '../../common/retail-preview/price-display';
import { loadGoodsRecommend } from '../../common/retail-preview/retail-data';
import RetailPreviewFrame from '../../common/retail-preview/retail-preview-frame.vue';
import { shouldShowSubtitle } from '../../common/retail-preview/subtitle-display';
import { createRetailPreviewController } from '../../common/retail-preview/use-retail-preview';
import { validateGoodsRecommend } from '../types';

const props = defineProps<{ showData: GoodsRecommendProps }>();
const controller = createRetailPreviewController<RetailGoodsItem>();
const { state } = controller;

watch(
  () => props.showData,
  (showData) =>
    controller.load(validateGoodsRecommend(showData), () =>
      loadGoodsRecommend(showData),
    ),
  { deep: true, immediate: true },
);
</script>

<template>
  <RetailPreviewFrame
    :common-style="showData.commonStyle"
    :message="state.message"
    :status="state.status"
    type-label="商品推荐"
  >
    <div class="gr-inner">
      <div v-if="showData.title" class="gr-head">
        <i class="gr-bar"></i>
        <span class="gr-title">{{ showData.title }}</span>
      </div>
      <div class="gr-track">
        <article v-for="item in state.items" :key="item.id" class="gr-card">
          <ElImage
            v-if="item.imageUrl"
            :alt="item.name"
            class="gr-pic"
            :src="item.imageUrl"
            fit="cover"
          >
            <template #error>
              <div class="gr-pic gr-pic--empty">
                <ElIcon><Picture /></ElIcon>
              </div>
            </template>
          </ElImage>
          <div v-else class="gr-pic gr-pic--empty">
            <ElIcon><Picture /></ElIcon>
          </div>
          <div class="gr-name">
            {{ item.name }}
          </div>
          <div v-if="shouldShowSubtitle(item)" class="gr-subtitle">
            {{ item.subTitle }}
          </div>
          <div class="gr-price-line">
            <span v-if="showData.showPrice !== false" class="gr-price">
              <span class="gr-price-symbol">¥</span>{{ item.price.toFixed(2) }}
            </span>
            <!-- 划线原价：仅原价严格高于售价时显示（存量商品原价多为 0，会划出￥0） -->
            <span
              v-if="showData.showOriginalPrice && shouldShowOriginalPrice(item)"
              class="gr-price-original"
            >
              ¥{{ item.originalPrice.toFixed(2) }}
            </span>
          </div>
        </article>
      </div>
    </div>
  </RetailPreviewFrame>
</template>

<!--
  样式与小程序 diy-goods-recommend 逐值对齐。
  换算口径：小程序屏宽在 rpx 下恒为 750，画布正好 375px，故 1rpx = 0.5px，
  本文件所有 px 值都是对应 rpx 值的一半；改任一端都要同步另一端。
-->
<style scoped lang="scss">
.gr-inner {
  padding: 10px;
}

.gr-head {
  display: flex;
  gap: 6px;
  align-items: center;
  margin-bottom: 8px;
}

.gr-bar {
  width: 4px;
  height: 14px;
  background: var(--wot-color-theme-primary, #ff2237);
  border-radius: 2px;
}

.gr-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

/* 横滑轨道：卡片 200rpx 宽、圆角 16rpx（1:1 对齐小程序，画布内横向溢出裁切） */
.gr-track {
  display: flex;
  gap: 8px;
  overflow: hidden;
}

.gr-card {
  flex: 0 0 100px;
  width: 100px;
  overflow: hidden;
  background: #fff;
  border: 0.5px solid #eef0f3;
  border-radius: 8px;
}

.gr-pic {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100px;
  height: 100px;
  font-size: 12px;
  color: #a8abb2;
  background: #fff;
}

.gr-pic--empty {
  background: #f3f4f6;
}

.gr-name {
  display: -webkit-box;
  height: 37px;
  padding: 6px 6px 0;
  overflow: hidden;
  -webkit-line-clamp: 2;
  font-size: 13px;
  line-height: 1.4;
  color: #303133;
  word-break: normal;
  overflow-wrap: break-word;
  -webkit-box-orient: vertical;
}

.gr-subtitle {
  padding: 1px 6px 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 10px;
  line-height: 1.4;
  color: #909399;
  white-space: nowrap;
}

.gr-price-line {
  display: flex;
  gap: 4px;
  align-items: baseline;
  padding: 2px 6px 6px;
}

.gr-price {
  font-size: 15px;
  font-weight: 600;
  color: var(--wot-color-theme-primary, #ff2237);
}

.gr-price-symbol {
  font-size: 11px;
}

/* 划线原价：灰字小一号，仅作价格锚点，不与售价抢视觉层级 */
.gr-price-original {
  flex: none;
  font-size: 11px;
  font-weight: normal;
  color: #999;
  text-decoration: line-through;
}
</style>

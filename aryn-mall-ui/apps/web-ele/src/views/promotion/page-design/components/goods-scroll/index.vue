<script setup lang="ts">
import type { GoodsScrollProps } from './types';

import { computed, watch } from 'vue';

import { Picture } from '@element-plus/icons-vue';
import { ElIcon, ElImage } from 'element-plus';

import { shouldShowOriginalPrice } from '../common/retail-preview/price-display';
import { loadGoodsGroup } from '../common/retail-preview/retail-data';
import RetailPreviewFrame from '../common/retail-preview/retail-preview-frame.vue';
import { createRetailPreviewController } from '../common/retail-preview/use-retail-preview';
import { validateGoodsScroll } from './types';

const props = defineProps<{ showData: GoodsScrollProps }>();
const controller =
  createRetailPreviewController<
    Awaited<ReturnType<typeof loadGoodsGroup>>[number]
  >();
const { state } = controller;

watch(
  () => props.showData,
  (showData) =>
    controller.load(validateGoodsScroll(showData), () =>
      loadGoodsGroup(showData as never),
    ),
  { deep: true, immediate: true },
);

/** 一屏 4 个时按小程序口径收窄价格与按钮，否则两者会挤在一起 */
const perViewClass = computed(() =>
  props.showData.perView === 4 ? 'is-per-view-4' : '',
);
</script>

<template>
  <RetailPreviewFrame
    :common-style="showData.commonStyle"
    :message="state.message"
    :status="state.status"
  >
    <div class="gs-head">
      <i class="gs-bar"></i>
      <span class="gs-title">{{ showData.title }}</span>
      <span v-if="showData.subtitle" class="gs-subtitle">
        {{ showData.subtitle }}
      </span>
    </div>
    <div class="gs-track" :class="perViewClass">
      <article v-for="item in state.items" :key="item.id" class="gs-card">
        <ElImage
          v-if="item.imageUrl"
          :alt="item.name"
          class="gs-img"
          :src="item.imageUrl"
          fit="cover"
        >
          <template #error>
            <div class="gs-img gs-img--empty">
              <ElIcon><Picture /></ElIcon>
            </div>
          </template>
        </ElImage>
        <div v-else class="gs-img gs-img--empty">
          <ElIcon><Picture /></ElIcon>
        </div>
        <div class="gs-body">
          <div class="gs-name">{{ item.name }}</div>
          <div class="gs-bottom">
            <span class="gs-price"
              ><i>¥</i>{{ item.price.toFixed(2) }}<!--
                划线原价嵌在 .gs-price 内，跟随该行一起省略号截断：价格行定高 23px，
                另起一行会撑破卡片（与小程序 diy-goods-scroll 的算法一致）。
              --><i
                v-if="
                  showData.showOriginalPrice && shouldShowOriginalPrice(item)
                "
                class="gs-price-original"
                >¥{{ item.originalPrice.toFixed(2) }}</i
              ></span
            >
            <span class="gs-btn">抢</span>
          </div>
          <span v-if="showData.showSales" class="gs-sales">
            已售 {{ item.sales }}
          </span>
        </div>
      </article>
    </div>
  </RetailPreviewFrame>
</template>

<!--
  样式与小程序 diy-goods-scroll 逐值对齐。
  换算口径：小程序屏宽在 rpx 下恒为 750，画布正好 375px，故 1rpx = 0.5px，
  本文件所有 px 值都是对应 rpx 值的一半；改任一端都要同步另一端。
-->
<style scoped lang="scss">
.gs-head {
  display: flex;
  align-items: center;
  padding: 4px 12px 0;
  margin-bottom: 8px;
}

.gs-bar {
  width: 3px;
  height: 14px;
  margin-right: 5px;
  background: linear-gradient(180deg, #0b63e5, #17a2c7);
  border-radius: 1.5px;
}

.gs-title {
  font-size: 16px;
  font-weight: 700;
  color: #14202e;
}

.gs-subtitle {
  padding: 1px 6px;
  margin-left: 6px;
  font-size: 10px;
  color: #e5484d;
  background: #fff1f0;
  border-radius: 999px;
}

.gs-track {
  display: flex;
  gap: 10px;
  padding: 0 12px;
  overflow-x: auto;
}

.gs-card {
  box-sizing: border-box;
  flex: 0 0 auto;
  width: calc((100% - 20px) / 3);
  overflow: hidden;
  background: #fff;

  /* 小程序此处为 1rpx（=0.5px），保持同值 */
  border: 0.5px solid #eef0f3;
  border-radius: 8px;
  box-shadow: 0 1px 6px rgb(11 99 229 / 6%);
}

.gs-img {
  display: block;
  width: 100%;
  aspect-ratio: 1;
  background: #f3f4f6;
}

.gs-img--empty {
  display: grid;
  place-items: center;
  font-size: 12px;
  color: #a8abb2;
}

.gs-body {
  padding: 6px;
}

.gs-name {
  display: -webkit-box;
  height: 36px;
  overflow: hidden;
  -webkit-line-clamp: 2;
  font-size: 13px;
  line-height: 18px;
  color: #303133;
  overflow-wrap: break-word;
  -webkit-box-orient: vertical;
}

.gs-bottom {
  display: flex;
  gap: 4px;
  align-items: center;
  justify-content: space-between;
  height: 23px;
  margin-top: 4px;
}

.gs-price {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 15px;
  font-weight: 700;
  color: #e5484d;
  white-space: nowrap;
}

.gs-price i {
  font-size: 11px;
  font-style: normal;
}

/* 划线原价：与小程序 diy-goods-scroll 的 .gs-price-original 逐值对齐（1px = 2rpx） */
.gs-price-original {
  margin-left: 3px;
  font-size: 10px;
  font-weight: 400;
  color: #999;
  text-decoration: line-through;
}

.gs-btn {
  flex: 0 0 auto;
  padding: 2px 10px;
  font-size: 11px;
  line-height: 17px;
  color: #fff;
  background: linear-gradient(90deg, #ff7a45, #ff4d4f);
  border-radius: 999px;
  box-shadow: 0 1px 4px rgb(255 77 79 / 28%);
}

.gs-sales {
  display: block;
  height: 14px;
  margin-top: 3px;
  font-size: 10px;
  line-height: 14px;
  color: #909399;
}

.gs-track.is-per-view-4 {
  .gs-card {
    width: calc((100% - 30px) / 4);
  }

  .gs-price {
    font-size: 12px;
  }

  .gs-price i {
    font-size: 9px;
  }

  .gs-price-original {
    margin-left: 2px;
    font-size: 9px;
  }

  .gs-bottom {
    gap: 2px;
  }

  .gs-btn {
    padding: 1px 5px;
    font-size: 10px;
  }
}
</style>

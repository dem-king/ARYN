<script setup lang="ts">
import type { RetailDiscountActivityItem } from '../common/retail-preview/retail-data';
import type { DiscountProps } from './types';

import { computed, watch } from 'vue';

import { Picture } from '@element-plus/icons-vue';
import { ElIcon, ElImage } from 'element-plus';

import { loadDiscounts } from '../common/retail-preview/retail-data';
import RetailPreviewFrame from '../common/retail-preview/retail-preview-frame.vue';
import { createRetailPreviewController } from '../common/retail-preview/use-retail-preview';
import { validateDiscount } from './types';

const props = defineProps<{ showData: DiscountProps }>();
const controller = createRetailPreviewController<RetailDiscountActivityItem>();
const { state } = controller;

watch(
  () => props.showData,
  (showData) =>
    controller.load(validateDiscount(showData), () => loadDiscounts(showData)),
  { deep: true, immediate: true },
);

/** 与小程序一致：按键口径展示剩余时长，已结束或时间不可解析时给结束文案 */
function remainingLabel(endTime: string, activityStatus: number) {
  if (activityStatus === 2) return '活动已结束';
  const target = Date.parse(endTime);
  if (Number.isNaN(target)) return '活动已结束';
  const diff = target - Date.now();
  if (diff <= 0) return '活动已结束';
  const totalMinutes = Math.floor(diff / 60_000);
  const days = Math.floor(totalMinutes / 1440);
  const hours = Math.floor((totalMinutes % 1440) / 60);
  const minutes = totalMinutes % 60;
  const clock = `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}`;
  return days > 0 ? `剩余 ${days}天${clock}` : `剩余 ${clock}`;
}

/** 打折值为 0.8 表示 8 折，乘以 10 取整展示；与小程序 discountLabel 同口径 */
function discountLabel(activity: {
  discountType: number;
  discountValue: number;
}) {
  switch (activity.discountType) {
    case 1: {
      return `${Math.round(activity.discountValue * 10)}折`;
    }
    case 2: {
      return `减￥${activity.discountValue}`;
    }
    case 3: {
      return '一口价';
    }
    default: {
      return '折扣';
    }
  }
}

const activities = computed(() => state.items);
</script>

<template>
  <RetailPreviewFrame
    :common-style="showData.commonStyle"
    :message="state.message"
    :status="state.status"
  >
    <div class="section-title">{{ showData.title }}</div>
    <div class="activity-list">
      <div
        v-for="activity in activities"
        :key="activity.activityId"
        class="activity-item"
      >
        <div class="activity-header">
          <span class="activity-name">
            {{ activity.activityName || '限时折扣' }}
          </span>
          <span class="activity-tag">{{ discountLabel(activity) }}</span>
          <span v-if="showData.showCountdown" class="activity-time">
            {{
              activity.status === 2 || !activity.endTime
                ? '活动已结束'
                : remainingLabel(activity.endTime, activity.status)
            }}
          </span>
        </div>
        <div v-if="activity.goodsList.length === 0" class="goods-empty">
          全场商品参与，进入商品详情查看折扣价
        </div>
        <div v-else class="goods-list">
          <article
            v-for="goods in activity.goodsList"
            :key="goods.skuId"
            class="goods-card"
          >
            <ElImage
              v-if="goods.goodsImage"
              :alt="goods.goodsName"
              class="goods-image"
              :src="goods.goodsImage"
              fit="cover"
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
            <div class="goods-content">
              <div class="goods-name">
                {{ goods.goodsName || '折扣商品' }}
              </div>
              <div class="goods-price-row">
                <span class="goods-price">
                  ￥{{ goods.discountPrice.toFixed(2) }}
                </span>
                <span v-if="goods.originalPrice" class="goods-original">
                  ￥{{ goods.originalPrice.toFixed(2) }}
                </span>
              </div>
            </div>
          </article>
        </div>
      </div>
    </div>
  </RetailPreviewFrame>
</template>

<!--
  样式与小程序 diy-discount 逐值对齐。
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

.activity-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.activity-header {
  display: flex;
  gap: 6px;
  align-items: baseline;
  margin-bottom: 6px;
}

.activity-name {
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 13.5px;
  font-weight: 600;
  color: #1f2329;
  white-space: nowrap;
}

.activity-tag {
  padding: 1px 5px;
  font-size: 10.5px;
  color: #e5484d;
  background: #ffe1dc;
  border-radius: 2px;
}

.activity-time {
  font-size: 11px;
  color: #e5484d;
}

.goods-list {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.goods-empty {
  padding: 12px 0;
  font-size: 12px;
  color: #909399;
  text-align: center;
}

.goods-card {
  display: flex;
  gap: 9px;
  min-height: 70px;
  overflow: hidden;
  background: #fff;
  border: 0.5px solid #ffe1dc;
  border-radius: 4px;
}

.goods-image {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 75px;
  min-height: 70px;
  font-size: 11px;
  color: #e5484d;
  background: #fff1ee;
}

.goods-content {
  flex: 1;
  min-width: 0;
  padding: 8px 8px 8px 0;
}

.goods-name {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 13.5px;
  font-weight: 500;
  color: #303133;
  white-space: nowrap;
}

.goods-price-row {
  display: flex;
  gap: 5px;
  align-items: baseline;
  margin-top: 7px;
}

.goods-price {
  font-size: 15px;
  font-weight: 700;
  color: #e5484d;
}

.goods-original {
  font-size: 10.5px;
  color: #a8abb2;
  text-decoration: line-through;
}
</style>

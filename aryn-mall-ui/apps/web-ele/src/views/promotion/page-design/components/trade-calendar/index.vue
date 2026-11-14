<script setup lang="ts">
import type { RetailPreviewStatus } from '../common/retail-preview/use-retail-preview';
import type { TradeCalendarProps } from './types';

import type { AppCalendarSpecial } from '#/api/promotion/trade-calendar';

import { computed, onBeforeUnmount, onMounted, ref } from 'vue';

import {
  getAppCalendarSpecials,
  getAppTradeCalendars,
} from '#/api/promotion/trade-calendar';

import RetailPreviewFrame from '../common/retail-preview/retail-preview-frame.vue';
import { validateTradeCalendar } from './types';

/**
 * 档期特供楼层预览：与 C 端 diy-trade-calendar 同源拉数（生效档期 + 特供商品），
 * 卡片样式与小程序逐值对齐（1rpx = 0.5px，见文件底部注释）。
 * 接口失败按「无数据」处理（emptyStrategy 默认隐藏整楼），不阻塞画布。
 */
const props = defineProps<{ showData: TradeCalendarProps }>();

const validationErrors = computed(() => validateTradeCalendar(props.showData));
const previewStatus = computed<RetailPreviewStatus>(() =>
  validationErrors.value.length > 0 ? 'invalid' : 'data',
);

interface PreviewCard {
  endTime: string;
  picUrl?: null | string;
  price: number;
  remainingQuota: null | number;
  salesPrice: null | number;
  spuName: string;
}

const cards = ref<PreviewCard[]>([]);
const loaded = ref(false);

const load = async () => {
  loaded.value = true;
  try {
    const calendars = (await getAppTradeCalendars()) || [];
    const calendar = calendars[0];
    if (!calendar) {
      cards.value = [];
      return;
    }
    const specials: AppCalendarSpecial[] =
      (await getAppCalendarSpecials(calendar.id)) || [];
    const result: PreviewCard[] = [];
    for (const special of specials) {
      for (const item of special.items || []) {
        if (result.length >= props.showData.count) break;
        const price = Number(item.price);
        if (!Number.isFinite(price) || price <= 0) continue;
        const sales = Number(item.originalPrice);
        result.push({
          endTime: special.endTime,
          picUrl: (item as any).picUrl || '',
          price,
          remainingQuota:
            special.totalQuota === null || special.totalQuota === undefined
              ? null
              : (special.remainingQuota ?? 0),
          salesPrice:
            Number.isFinite(sales) && sales > price
              ? Math.round(sales * 100) / 100
              : null,
          spuName: (item as any).spuName || item.skuId,
        });
      }
    }
    cards.value = result;
  } catch {
    cards.value = [];
  }
};

const floorTitle = computed(
  () => props.showData.title || cards.value[0]?.spuName || '档期特供',
);

const countdownText = computed(() => {
  let earliest = '';
  for (const card of cards.value) {
    if (!card.endTime) continue;
    if (!earliest || card.endTime < earliest) earliest = card.endTime;
  }
  if (!earliest) return '';
  const remaining = Math.max(0, Date.parse(earliest) - Date.now());
  if (!Number.isFinite(remaining) || remaining <= 0) return '';
  const totalSeconds = Math.floor(remaining / 1000);
  const hours = Math.floor((totalSeconds % 86_400) / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  return [hours, minutes, seconds]
    .map((value) => String(value).padStart(2, '0'))
    .join(':');
});

const quotaText = (card: PreviewCard) =>
  card.remainingQuota === null || card.remainingQuota === undefined
    ? '不限量'
    : `余 ${card.remainingQuota} 件`;

onMounted(() => {
  load();
});
onBeforeUnmount(() => {
  // 组件卸载即可，请求由 requestClient 自行收敛
});
</script>

<template>
  <RetailPreviewFrame
    :common-style="showData.commonStyle"
    :message="validationErrors[0]"
    :status="previewStatus"
  >
    <div v-if="loaded" class="trade-calendar">
      <div class="tc-header">
        <span class="tc-title">{{ floorTitle }}</span>
        <span v-if="countdownText" class="tc-countdown">
          {{ countdownText }}
        </span>
      </div>
      <div class="tc-scroll">
        <div
          v-for="(card, index) in cards"
          :key="`${card.spuName}-${index}`"
          class="tc-card"
        >
          <div class="tc-figure">
            <img v-if="card.picUrl" alt="" class="tc-img" :src="card.picUrl" />
            <span v-else class="tc-img-fallback">
              {{ (card.spuName || '?').slice(0, 1) }}
            </span>
            <span class="tc-badge">档期特供</span>
          </div>
          <span class="tc-name">{{ card.spuName }}</span>
          <div class="tc-price-row">
            <span class="tc-price">¥{{ card.price }}</span>
            <span v-if="card.salesPrice" class="tc-strike">
              ¥{{ card.salesPrice }}
            </span>
          </div>
          <span class="tc-quota">{{ quotaText(card) }}</span>
        </div>
      </div>
    </div>
  </RetailPreviewFrame>
</template>

<!--
  样式与小程序 diy-trade-calendar 逐值对齐。
  换算口径：小程序屏宽在 rpx 下恒为 750，画布正好 375px，故 1rpx = 0.5px，
  本文件所有 px 值都是对应 rpx 值的一半；改任一端都要同步另一端。
  卡宽 220rpx=110px、间距 16rpx=8px、内边距 20rpx=10px、圆角 12rpx=6px、
  主价 28rpx=14px、标题 30rpx=15px、倒计时 26rpx=13px、名称 24rpx=12px、
  划线/角标/余量 20rpx=10px。
-->
<style scoped lang="scss">
.trade-calendar {
  padding: 10px 0;
  overflow: hidden;
}

.tc-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 0 10px 8px;
}

.tc-title {
  font-size: 15px;
  font-weight: 700;
  color: #303133;
}

.tc-countdown {
  font-family: monospace;
  font-size: 13px;
  font-weight: 700;
  color: var(--wot-color-theme-primary, #ff2237);
}

.tc-scroll {
  display: flex;
  gap: 8px;
  padding: 0 10px;
  overflow-x: auto;
}

.tc-card {
  flex-shrink: 0;
  width: 110px;
}

.tc-figure {
  position: relative;
  width: 110px;
  height: 110px;
  overflow: hidden;
  background: #f5f7fa;
  border-radius: 6px;
}

.tc-img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.tc-img-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  font-size: 28px;
  color: #c0c4cc;
}

.tc-badge {
  position: absolute;
  top: 0;
  left: 0;
  padding: 2px 5px;
  font-size: 10px;
  color: #fff;
  background: var(--wot-color-theme-primary, #ff2237);
  border-radius: 0 0 6px;
}

.tc-name {
  display: block;
  width: 110px;
  margin-top: 5px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
  color: #303133;
  white-space: nowrap;
}

.tc-price-row {
  display: flex;
  gap: 4px;
  align-items: baseline;
  margin-top: 2px;
}

.tc-price {
  font-size: 14px;
  font-weight: 700;
  color: var(--wot-color-theme-primary, #ff2237);
}

.tc-strike {
  font-size: 10px;
  color: #909399;
  text-decoration: line-through;
}

.tc-quota {
  display: inline-block;
  padding: 1px 5px;
  margin-top: 3px;
  font-size: 10px;
  color: #909399;
  background: #f5f7fa;
  border-radius: 4px;
}
</style>

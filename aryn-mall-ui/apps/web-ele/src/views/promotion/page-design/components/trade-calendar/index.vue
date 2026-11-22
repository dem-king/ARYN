<script setup lang="ts">
import type { RetailPreviewStatus } from '../common/retail-preview/use-retail-preview';
import type { TradeCalendarProps } from './types';

import type { AppCalendarSpecial } from '#/api/promotion/trade-calendar';

import { computed, onBeforeUnmount, onMounted, ref, shallowRef } from 'vue';

import { getAppCalendarSpecials } from '#/api/promotion/trade-calendar';

import RetailPreviewFrame from '../common/retail-preview/retail-preview-frame.vue';
import { validateTradeCalendar } from './types';

/**
 * 档期特供楼层预览（2026-11-21 视觉重构：海军蓝档期牌 + 整场余量尺 + 印章式特供票）：
 * 与 C 端 diy-trade-calendar 同源拉数（不指定 calendarId，
 * 聚合全部生效档期，与实机口径一致），卡片样式与小程序逐值对齐
 * （1rpx = 0.5px，见文件底部注释）。
 * 接口失败按「无数据」处理（emptyStrategy 默认隐藏整楼），不阻塞画布。
 */
const props = defineProps<{ showData: TradeCalendarProps }>();

const validationErrors = computed(() => validateTradeCalendar(props.showData));
const previewStatus = computed<RetailPreviewStatus>(() =>
  validationErrors.value.length > 0 ? 'invalid' : 'data',
);

interface PreviewCard {
  activityId: string;
  activityName: string;
  calendarName: string;
  endTime: string;
  perUserLimit: null | number;
  picUrl?: null | string;
  price: number;
  remainingQuota: null | number;
  salesPrice: null | number;
  spuName: string;
  totalQuota: null | number;
}

const cards = ref<PreviewCard[]>([]);
const loaded = ref(false);

const load = async () => {
  loaded.value = true;
  try {
    const specials: AppCalendarSpecial[] =
      (await getAppCalendarSpecials()) || [];
    const result: PreviewCard[] = [];
    for (const special of specials) {
      for (const item of special.items || []) {
        if (result.length >= props.showData.count) break;
        const price = Number(item.price);
        if (!Number.isFinite(price) || price <= 0) continue;
        const sales = Number(item.originalPrice);
        const totalQuota =
          special.totalQuota === null || special.totalQuota === undefined
            ? null
            : Number(special.totalQuota);
        result.push({
          activityId: special.activityId,
          activityName: special.activityName,
          calendarName: special.calendarName || '',
          endTime: special.endTime,
          perUserLimit:
            item.perUserLimit === null || item.perUserLimit === undefined
              ? null
              : Number(item.perUserLimit),
          picUrl: item.picUrl || '',
          price,
          remainingQuota:
            totalQuota === null ? null : Number(special.remainingQuota ?? 0),
          salesPrice:
            Number.isFinite(sales) && sales > price
              ? Math.round(sales * 100) / 100
              : null,
          spuName: item.spuName || item.skuId,
          totalQuota,
        });
      }
    }
    cards.value = result;
  } catch {
    cards.value = [];
  }
};

// 与 C 端一致：标题优先，其次首个特供的档期名，最后兜底「档期特供」
const floorTitle = computed(
  () =>
    props.showData.title ||
    cards.value[0]?.calendarName ||
    cards.value[0]?.activityName ||
    '档期特供',
);

/** 进行中的特供活动数（同一活动多个 SKU 只算一场） */
const activityCount = computed(
  () => new Set(cards.value.map((card) => card.activityId)).size,
);

/**
 * 楼层副标题：单场显示活动名；多场并存时只报场次数。
 * 余量口径移到档期牌底部余量尺，与 C 端一致。
 */
const subtitle = computed(() => {
  const first = cards.value[0];
  if (!first) return '';
  if (activityCount.value > 1) return `${activityCount.value} 场特供进行中`;
  return first.activityName && first.activityName !== floorTitle.value
    ? first.activityName
    : '';
});

/** 档期牌底部余量尺：只在单场活动时展示，整层只出现一次 */
const gaugeLabel = computed(() => {
  const first = cards.value[0];
  if (!first || activityCount.value > 1) return '';
  return first.totalQuota === null
    ? '不限量'
    : `整场限量 ${first.totalQuota} 件`;
});

const remainingText = computed(() => {
  const first = cards.value[0];
  if (
    !first ||
    activityCount.value > 1 ||
    first.totalQuota === null ||
    first.remainingQuota === null
  ) {
    return '';
  }
  return `余 ${first.remainingQuota} 件`;
});

const showGaugeBar = computed(() => {
  const first = cards.value[0];
  return Boolean(
    first &&
    activityCount.value === 1 &&
    first.totalQuota !== null &&
    first.totalQuota > 0 &&
    first.remainingQuota !== null,
  );
});

/** 填充按「剩余/总量」：开档满格，卖一件退一格，与 C 端同口径 */
const gaugeWidth = computed(() => {
  const first = cards.value[0];
  if (!first || first.totalQuota === null || first.totalQuota <= 0) return 0;
  const remaining = Math.min(
    Math.max(Number(first.remainingQuota ?? 0), 0),
    first.totalQuota,
  );
  return `${Math.round((remaining / first.totalQuota) * 1000) / 10}%`;
});

// 每秒刷新的当前时刻（与 C 端 useCountdownTicker 同源），驱动倒计时逐秒递减
const now = shallowRef(Date.now());
let ticker: ReturnType<typeof setInterval> | undefined;

const countdownText = computed(() => {
  let earliest = '';
  for (const card of cards.value) {
    if (!card.endTime) continue;
    if (!earliest || card.endTime < earliest) earliest = card.endTime;
  }
  if (!earliest) return '';
  const remaining = Math.max(0, Date.parse(earliest) - now.value);
  if (!Number.isFinite(remaining) || remaining <= 0) return '';
  const totalSeconds = Math.floor(remaining / 1000);
  // 与 C 端 formatRemainingTime 一致：满 1 天补「N天」前缀，其余 HH:MM:SS
  const days = Math.floor(totalSeconds / 86_400);
  const hours = Math.floor((totalSeconds % 86_400) / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  const time = [hours, minutes, seconds]
    .map((value) => String(value).padStart(2, '0'))
    .join(':');
  return days > 0 ? `${days}天 ${time}` : time;
});

/** 立省额：只在划线售价严格高于特供价时有意义，整数不带 .0 */
const saveAmountText = (card: PreviewCard) => {
  if (card.salesPrice === null) return '';
  const saved = Math.round((card.salesPrice - card.price) * 100) / 100;
  if (saved <= 0) return '';
  return `省¥${Number(saved.toFixed(2))}`;
};

const limitText = (card: PreviewCard) =>
  card.perUserLimit && card.perUserLimit > 0
    ? `每人限 ${card.perUserLimit} 件`
    : '不限购';

/** 「查看全部」入口：与 C 端同口径，缺省展示（存量装修 JSON 无该字段） */
const showMore = computed(() => props.showData.showMore !== false);

onMounted(() => {
  load();
  ticker = setInterval(() => {
    now.value = Date.now();
  }, 1000);
});
onBeforeUnmount(() => {
  // 清理逐秒 ticker，避免组件卸载后定时器泄漏
  if (ticker) clearInterval(ticker);
});
</script>

<template>
  <RetailPreviewFrame
    :common-style="showData.commonStyle"
    :message="validationErrors[0]"
    :status="previewStatus"
  >
    <div v-if="loaded" class="trade-calendar">
      <div class="tc-board">
        <div class="tc-head-row">
          <span class="tc-mark">档</span>
          <span class="tc-title">{{ floorTitle }}</span>
        </div>
        <div v-if="subtitle || countdownText" class="tc-sub-row">
          <span v-if="subtitle" class="tc-sub">{{ subtitle }}</span>
          <span v-if="countdownText" class="tc-timer">
            <span class="tc-timer-label">距结束</span>
            <span class="tc-timer-value">{{ countdownText }}</span>
          </span>
        </div>
        <div v-if="gaugeLabel || showMore" class="tc-cut"></div>
        <div v-if="gaugeLabel || showMore" class="tc-foot">
          <span v-if="gaugeLabel" class="tc-gauge-label">
            {{ gaugeLabel }}
          </span>
          <span v-if="showGaugeBar" class="tc-gauge">
            <span class="tc-gauge-fill" :style="{ width: gaugeWidth }"></span>
          </span>
          <span v-if="remainingText" class="tc-gauge-left">
            {{ remainingText }}
          </span>
          <span v-if="showMore" class="tc-more">
            查看全部
            <span class="tc-more-arrow">›</span>
          </span>
        </div>
      </div>
      <div class="tc-scroll">
        <div class="tc-track">
          <div
            v-for="(card, index) in cards"
            :key="`${card.activityId}-${index}`"
            class="tc-card"
          >
            <div class="tc-figure">
              <img
                v-if="card.picUrl"
                alt=""
                class="tc-img"
                :src="card.picUrl"
              />
              <span v-else class="tc-img-fallback">
                {{ (card.spuName || '?').slice(0, 1) }}
              </span>
              <span class="tc-stamp">档期特供</span>
            </div>
            <div class="tc-info">
              <span class="tc-name">{{ card.spuName }}</span>
              <div class="tc-price-row">
                <span class="tc-price">
                  <span class="tc-symbol">¥</span>{{ card.price }}
                </span>
                <span v-if="card.salesPrice" class="tc-strike">
                  ¥{{ card.salesPrice }}
                </span>
              </div>
              <div class="tc-perf"></div>
              <div class="tc-meta">
                <span v-if="saveAmountText(card)" class="tc-save">
                  {{ saveAmountText(card) }}
                </span>
                <span class="tc-limit">{{ limitText(card) }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </RetailPreviewFrame>
</template>

<!--
  样式与小程序 diy-trade-calendar 逐值对齐。
  换算口径：小程序屏宽在 rpx 下恒为 750，画布正好 375px，故 1rpx = 0.5px，
  本文件所有 px 值都是对应 rpx 值的一半；改任一端都要同步另一端。
  档期牌：外距 10px、内距 12px、圆角 14px；档期标 17px / 档期名 15px /
  距结束 11px / 副标题 10px；余量尺 4px、查看全部 10px。
  卡片：一屏三张 110px、圆角 8px、图区 110px、
  名称两行 32px、价行 20px、限购行 16px。
-->
<style scoped lang="scss">
.trade-calendar {
  padding: 8px 0 3px;
  overflow: hidden;
}

/* ========== 档期牌：海军蓝底承担「这是当期档期」的识别 ========== */
.tc-board {
  padding: 12px 12px 10px;
  margin: 0 10px;
  overflow: hidden;
  background: linear-gradient(145deg, #0d2337 0%, #173a52 62%, #1c455f 100%);
  border-radius: 14px;
  box-shadow: 0 8px 22px rgb(13 35 55 / 22%);
}

.tc-head-row {
  display: flex;
  gap: 6px;
  align-items: center;
}

/* 档期标：品牌红印章落在深蓝底上，小面积高对比，不铺整块底色 */
.tc-mark {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 17px;
  height: 17px;
  font-size: 9.5px;
  font-weight: 700;
  line-height: 1;
  color: #fff;
  background: linear-gradient(
    135deg,
    var(--wot-color-theme-secondary, #ff6b7a),
    var(--wot-color-theme-primary, #ff2237)
  );
  border-radius: 4px;
  box-shadow: 0 2px 6px rgb(255 34 55 / 35%);
}

.tc-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 15px;
  font-weight: 700;
  color: #fff;
  letter-spacing: 0.2px;
  white-space: nowrap;
}

/* 第二行：活动名留给左，倒计时贴右，深蓝底上只保留一个暖色数字焦点 */
.tc-sub-row {
  display: flex;
  gap: 8px;
  align-items: baseline;
  margin-top: 6px;
}

.tc-sub {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 10px;
  line-height: 1.4;
  color: rgb(255 255 255 / 62%);
  white-space: nowrap;
}

.tc-timer {
  display: flex;
  flex: 0 0 auto;
  gap: 3px;
  align-items: baseline;
  margin-left: auto;
  font-variant-numeric: tabular-nums;
}

.tc-timer-label {
  font-size: 10px;
  color: rgb(255 255 255 / 52%);
}

.tc-timer-value {
  font-size: 11px;
  font-weight: 600;
  color: #ffc46b;
  letter-spacing: 0.1px;
}

/* 票根撕线：虚线只做分隔，不做额外装饰，避免深色块里叠太多纹理 */
.tc-cut {
  margin: 9px -12px 8px;
  border-top: 0.5px dashed rgb(255 255 255 / 22%);
}

/* 底部一行：整场限量 + 余量尺 + 剩余件数 + 会场入口 */
.tc-foot {
  display: flex;
  gap: 6px;
  align-items: center;
}

.tc-gauge-label {
  flex: 0 0 auto;
  font-size: 9px;
  color: rgb(255 255 255 / 62%);
  white-space: nowrap;
}

.tc-gauge {
  flex: 1;
  min-width: 30px;
  height: 4px;
  overflow: hidden;
  background: rgb(255 255 255 / 14%);
  border-radius: 999px;
}

.tc-gauge-fill {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, #ffd48a, #ffb347);
  border-radius: 999px;
}

.tc-gauge-left {
  flex: 0 0 auto;
  font-size: 9px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: #ffc46b;
  white-space: nowrap;
}

.tc-more {
  display: flex;
  flex: 0 0 auto;
  gap: 1px;
  align-items: center;
  padding: 4px 0 4px 6px;
  margin: -4px 0 -4px auto;
  font-size: 10px;
  font-weight: 600;
  line-height: 1;
  color: #ffc46b;
}

.tc-more-arrow {
  margin-left: 1px;
}

.tc-scroll {
  padding-bottom: 4px;
  margin-top: 10px;
  overflow-x: auto;
}

.tc-track {
  display: flex;
  gap: 8px;
  width: max-content;
  padding: 0 10px;
}

/* ========== 特供票：米白纸面 + 撕线 + 印章，脱离普通商品白卡 ========== */
.tc-card {
  display: flex;
  flex: 0 0 auto;
  flex-direction: column;
  width: 110px;
  overflow: hidden;
  background: #fffdf9;
  border: 0.5px solid #f0e2d3;
  border-radius: 8px;
  box-shadow: 0 3px 10px rgb(160 106 31 / 8%);
}

.tc-figure {
  position: relative;
  flex: 0 0 auto;
  height: 110px;
  background: #fbf5ec;
}

.tc-img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.tc-img-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  font-size: 24px;
  color: #c0b09e;
}

/* 印章式角标：描边 + 微旋转，和普通商品卡的实心角标区分 */
.tc-stamp {
  position: absolute;
  top: 7px;
  left: 7px;
  padding: 2px 5px;
  font-size: 8px;
  font-weight: 700;
  line-height: 1.3;
  color: #e02038;
  letter-spacing: 0.6px;
  background: rgb(255 253 249 / 88%);
  border: 1.5px solid rgb(255 34 55 / 82%);
  border-radius: 3px;
  transform: rotate(-7deg);
}

.tc-info {
  padding: 7px 8px 8px;
}

.tc-name {
  display: -webkit-box;
  height: 32px;
  overflow: hidden;
  -webkit-line-clamp: 2;
  font-size: 12px;
  font-weight: 500;
  line-height: 16px;
  color: #2b2f36;
  overflow-wrap: break-word;
  white-space: normal;
  -webkit-box-orient: vertical;
}

.tc-price-row {
  display: flex;
  gap: 4px;
  align-items: baseline;
  height: 20px;
  margin-top: 2px;
}

.tc-price {
  display: flex;
  align-items: baseline;
  min-width: 0;
  overflow: hidden;
  font-size: 16px;
  font-weight: 800;
  line-height: 1;
  color: var(--wot-color-theme-primary, #ff2237);
  white-space: nowrap;
}

.tc-symbol {
  font-size: 0.68em;
}

.tc-strike {
  flex: 0 0 auto;
  font-size: 10px;
  color: #a8abb2;
  text-decoration: line-through;
}

/* 票根撕线：贯穿卡面、贴到左右边，强化「一张特供票」而不是普通商品卡 */
.tc-perf {
  margin: 6px -8px 5px;
  border-top: 0.5px dashed #eadccb;
}

.tc-meta {
  display: flex;
  gap: 4px;
  align-items: center;
  height: 16px;
}

.tc-save {
  flex: 0 0 auto;
  padding: 1px 5px;
  font-size: 9px;
  font-weight: 600;
  line-height: 1.5;
  color: var(--wot-color-theme-primary, #ff2237);
  background: #fff0ee;
  border-radius: 3px;
}

.tc-limit {
  margin-left: auto;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 9px;
  color: #8f959e;
  white-space: nowrap;
}
</style>

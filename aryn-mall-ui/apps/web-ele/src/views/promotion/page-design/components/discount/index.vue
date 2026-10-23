<script setup lang="ts">
import type {
  RetailDiscountActivityItem,
  RetailDiscountGoodsItem,
} from '../common/retail-preview/retail-data';
import type { DiscountProps } from './types';

import { computed, watch } from 'vue';

import { Picture } from '@element-plus/icons-vue';
import { ElIcon, ElImage } from 'element-plus';

import { loadDiscounts } from '../common/retail-preview/retail-data';
import RetailPreviewFrame from '../common/retail-preview/retail-preview-frame.vue';
import { usePreviewNow } from '../common/retail-preview/use-preview-now';
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

/** 画布秒级时钟：倒计时每秒跳动（C 端由 useCountdownTicker 提供，两端同口径） */
const now = usePreviewNow();

const DEFAULT_SUBTITLE = '限时特惠 · 折扣价直接买';

/** 楼层头副标题：运营未配置（空串/缺字段）时回落到模块默认文案 */
const subtitle = computed(
  () => props.showData.subtitle?.trim() || DEFAULT_SUBTITLE,
);

/** 每个活动最多展示的商品数：1-8，存量草稿缺字段时回落默认 4 */
const goodsLimit = computed(() => {
  const limit = Number(props.showData.goodsLimit);
  return Number.isInteger(limit) && limit >= 1 && limit <= 8 ? limit : 4;
});

const activities = computed(() => state.items);

/**
 * 折扣文案（两端同口径）：打折 0.8 → 8折、0.95 → 9.5折、0.88 → 8.8折
 * （保留一位小数，整数去掉 .0）；减价给金额；固定价只表达「一口价」。
 */
function discountLabel(activity: {
  discountType: number;
  discountValue: number;
}) {
  switch (activity.discountType) {
    case 1: {
      const zhe = Math.round(activity.discountValue * 100) / 10;
      return `${Number.isInteger(zhe) ? zhe : zhe.toFixed(1)}折`;
    }
    case 2: {
      return `减¥${activity.discountValue}`;
    }
    case 3: {
      return '一口价';
    }
    default: {
      return '折扣';
    }
  }
}

/** 形态 A：全场型（scope=1 且没有商品清单），用权益卡而不是灰字解释空清单 */
function isFullScope(activity: {
  goodsList: readonly unknown[];
  scope: number;
}) {
  return activity.scope === 1 && activity.goodsList.length === 0;
}

/** 形态 A 说明：打折活动换算出等价的「下单立减 X%」，其余类型不编造比例 */
function fullDesc(activity: { discountType: number; discountValue: number }) {
  if (activity.discountType !== 1) return '全场商品参与 · 下单立减';
  const percent = Math.round((1 - activity.discountValue) * 100);
  return `全场商品参与 · 下单立减 ${percent}%`;
}

/** 商品型活动按 goodsLimit 截断，避免一次渲染整份清单把楼层撑得过高 */
function goodsOf(activity: { goodsList: readonly RetailDiscountGoodsItem[] }) {
  return activity.goodsList.slice(0, goodsLimit.value);
}

/** 原价严格大于折后价才划线；相等或缺失时原价与省额整块隐藏 */
function hasOriginalPrice(goods: RetailDiscountGoodsItem) {
  return goods.originalPrice > goods.discountPrice;
}

function savedLabel(goods: RetailDiscountGoodsItem) {
  return `省¥${Math.max(0, goods.originalPrice - goods.discountPrice).toFixed(2)}`;
}

/** 后端时间串兼容 `yyyy-MM-dd HH:mm:ss`（iOS/部分内核直接 Date.parse 会 NaN） */
function parseTime(value: string) {
  if (!value) return Number.NaN;
  const direct = Date.parse(value);
  if (!Number.isNaN(direct)) return direct;
  return Date.parse(value.replace(' ', 'T'));
}

/**
 * 倒计时口径与小程序 `formatRemainingTime` 一致：
 * `N天 HH:MM:SS` / `HH:MM:SS`，已结束或时间不可解析时给「已结束」。
 */
function countdownText(target: number) {
  if (!Number.isFinite(target)) return '已结束';
  const diff = target - now.value;
  if (diff <= 0) return '已结束';
  const totalSeconds = Math.floor(diff / 1000);
  const days = Math.floor(totalSeconds / 86_400);
  const clock = [
    Math.floor((totalSeconds % 86_400) / 3600),
    Math.floor((totalSeconds % 3600) / 60),
    totalSeconds % 60,
  ]
    .map((value) => String(value).padStart(2, '0'))
    .join(':');
  return days > 0 ? `${days}天 ${clock}` : clock;
}

/** 头部倒计时取「仍在进行中的活动里最早的结束时间」，全部结束显示「已结束」 */
const headerCountdown = computed(() => {
  if (state.items.length === 0) return '';
  const targets = state.items
    .map((activity) => parseTime(activity.endTime))
    .filter((target) => !Number.isNaN(target) && target > now.value);
  if (targets.length === 0) return '已结束';
  return countdownText(Math.min(...targets));
});

/** 活动头倒计时：该活动自身的结束时间（已结束状态直接给结束文案） */
function blockCountdown(activity: { endTime: string; status?: number }) {
  if (activity.status === 2) return '已结束';
  const text = countdownText(parseTime(activity.endTime));
  return text === '已结束' ? text : `剩 ${text}`;
}
</script>

<template>
  <RetailPreviewFrame
    :common-style="showData.commonStyle"
    :message="state.message"
    :status="state.status"
    type-label="折扣"
  >
    <div class="promo-head--light">
      <span class="promo-icon">折</span>
      <span class="promo-text">
        <span class="promo-title">{{ showData.title || '限时折扣' }}</span>
        <span class="promo-sub">{{ subtitle }}</span>
      </span>
      <span
        v-if="showData.showCountdown && headerCountdown"
        class="promo-cd-light"
      >
        <b class="cd-label">距结束</b>
        <time class="cd-time">{{ headerCountdown }}</time>
      </span>
    </div>

    <template v-for="activity in activities" :key="activity.activityId">
      <!-- 形态 A：全场型活动 = 折扣大牌权益卡（整卡不可点：画布预览不跳转） -->
      <div v-if="isFullScope(activity)" class="dc-full">
        <div class="dc-ratio">
          <span class="dc-ratio-value">{{ discountLabel(activity) }}</span>
          <span class="dc-ratio-label">全场</span>
        </div>
        <div class="dc-full-main">
          <div class="dc-full-name">
            {{ activity.activityName || '限时折扣' }}
          </div>
          <div class="dc-full-desc">{{ fullDesc(activity) }}</div>
        </div>
        <span class="dc-full-btn">去逛逛</span>
      </div>

      <!-- 形态 B：商品型活动 = 活动头小牌 + 双列 4:3 商品卡 -->
      <div v-else class="dc-block">
        <div class="dc-block-head">
          <span class="dc-ratio-tag">{{ discountLabel(activity) }}</span>
          <span class="dc-block-name">
            {{ activity.activityName || '限时折扣' }}
          </span>
          <span v-if="showData.showCountdown" class="dc-block-cd">
            {{ blockCountdown(activity) }}
          </span>
        </div>
        <div class="dc-grid">
          <article
            v-for="goods in goodsOf(activity)"
            :key="goods.skuId"
            class="dc-card"
          >
            <div class="dc-media">
              <ElImage
                v-if="goods.goodsImage"
                :alt="goods.goodsName"
                class="dc-img"
                :src="goods.goodsImage"
                fit="cover"
              >
                <template #error>
                  <div class="dc-img dc-img--empty">
                    <ElIcon><Picture /></ElIcon>
                  </div>
                </template>
              </ElImage>
              <div v-else class="dc-img dc-img--empty">
                <ElIcon><Picture /></ElIcon>
              </div>
              <span class="dc-off">{{ discountLabel(activity) }}</span>
            </div>
            <div class="dc-body">
              <div class="dc-name">
                {{ goods.goodsName || '折扣商品' }}
              </div>
              <div class="dc-price">
                <span class="dc-price-sym">¥</span>
                <span class="dc-price-val">
                  {{ goods.discountPrice.toFixed(2) }}
                </span>
                <span v-if="hasOriginalPrice(goods)" class="dc-original">
                  ¥{{ goods.originalPrice.toFixed(2) }}
                </span>
                <span v-if="hasOriginalPrice(goods)" class="dc-save">
                  {{ savedLabel(goods) }}
                </span>
              </div>
            </div>
          </article>
        </div>
      </div>
    </template>
  </RetailPreviewFrame>
</template>

<!--
  样式与小程序 diy-discount 逐值对齐。
  换算口径：小程序屏宽在 rpx 下恒为 750，画布正好 375px，故 1rpx = 0.5px，
  本文件所有 px 值都是对应 rpx 值的一半；改任一端都要同步另一端。
  色彩角色：价格/折扣大牌/小微标/CTA 走主题主色 token，浅底与描边走
  --wot-color-theme-background（页面底色），标题/说明/划线价用固定中性色。
-->
<style scoped lang="scss">
/* ---- 楼层头（轻促销：浅底渐变 + 主色字符图标 + 内联倒计时） ---- */
.promo-head--light {
  position: relative;
  box-sizing: border-box;
  display: flex;
  gap: 8px;
  align-items: center;
  height: 52px;
  padding: 0 12px;
  overflow: hidden;
  background: linear-gradient(
    105deg,
    var(--wot-color-theme-background, #fff1ee) 0%,
    #fff 78%
  );
  border: 1px solid var(--wot-color-theme-background, #fff1ee);
  border-radius: 10px;
}

.promo-head--light::after {
  position: absolute;
  top: -40px;
  right: -30px;
  width: 104px;
  height: 104px;
  pointer-events: none;
  content: '';
  border: 1px solid rgb(255 255 255 / 90%);
  border-radius: 50%;
}

/* 字符图标：C 端 UnoCSS carbon 图标是 background-image、无法染白，
   两端统一用汉字字符，画布与实机视觉一致 */
.promo-icon {
  z-index: 1;
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 27px;
  height: 27px;
  font-size: 15px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(
    135deg,
    var(--wot-color-theme-secondary, #ff6b7a),
    var(--wot-color-theme-primary, #ff2237)
  );
  border-radius: 8px;
}

.promo-text {
  z-index: 1;
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}

.promo-title {
  overflow: hidden;
  font-size: 19px;
  font-weight: 800;
  line-height: 1.1;
  color: #1f2329;
  letter-spacing: 0.3px;
  white-space: nowrap;
}

.promo-sub {
  margin-top: 3px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 9.5px;
  font-weight: 500;
  color: #8a8f99;
  white-space: nowrap;
}

.promo-cd-light {
  z-index: 1;
  box-sizing: border-box;
  display: flex;
  flex: 0 0 auto;
  gap: 4px;
  align-items: center;
  height: 26px;
  padding: 0 9px;
  background: #fff;
  border: 1px solid var(--wot-color-theme-background, #fff1ee);
  border-radius: 999px;
}

.cd-label {
  font-size: 8.5px;
  font-weight: 500;
  color: #8a8f99;
}

.cd-time {
  font-size: 11px;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
  color: var(--wot-color-theme-primary, #ff2237);
}

/* ---- 形态 A：全场型权益卡 ---- */
.dc-full {
  box-sizing: border-box;
  display: flex;
  gap: 10px;
  align-items: center;
  height: 80px;
  padding: 12px;
  margin-top: 10px;
  background: linear-gradient(
    105deg,
    var(--wot-color-theme-background, #fff1ee),
    #fff 78%
  );
  border: 1px solid var(--wot-color-theme-background, #fff1ee);
  border-radius: 10px;
}

/* 折扣力度即主视觉：56px 渐变大牌 */
.dc-ratio {
  display: flex;
  flex: 0 0 auto;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  color: #fff;
  background: linear-gradient(
    135deg,
    var(--wot-color-theme-secondary, #ff6b7a),
    var(--wot-color-theme-primary, #ff2237)
  );
  border-radius: 10px;
  box-shadow: 0 4px 10px rgb(15 23 42 / 14%);
}

.dc-ratio-value {
  font-size: 21px;
  font-weight: 800;
  line-height: 1;
  letter-spacing: -0.5px;
}

.dc-ratio-label {
  margin-top: 2px;
  font-size: 9px;
  font-weight: 600;
  line-height: 1;
  opacity: 0.92;
}

.dc-full-main {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}

.dc-full-name {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 13px;
  font-weight: 700;
  color: #1f2329;
  white-space: nowrap;
}

.dc-full-desc {
  margin-top: 4px;
  font-size: 10px;
  color: #8a8f99;
}

/* 折扣是轻促销：CTA 用弱按钮（白底 + 主色描边 + 主色字），视觉弱于秒杀实心 CTA */
.dc-full-btn {
  box-sizing: border-box;
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  height: 28px;
  padding: 0 12px;
  font-size: 11.5px;
  font-weight: 700;
  color: var(--wot-color-theme-primary, #ff2237);
  white-space: nowrap;
  background: #fff;
  border: 1px solid var(--wot-color-theme-primary, #ff2237);
  border-radius: 999px;
}

/* ---- 形态 B：活动头小牌 + 双列商品卡 ---- */
.dc-block {
  margin-top: 14px;
}

.dc-block-head {
  display: flex;
  gap: 6px;
  align-items: center;
  margin-bottom: 8px;
}

.dc-ratio-tag {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  height: 22px;
  padding: 0 8px;
  font-size: 12px;
  font-weight: 800;
  line-height: 1;
  color: #fff;
  letter-spacing: 0.2px;
  background: linear-gradient(
    135deg,
    var(--wot-color-theme-secondary, #ff6b7a),
    var(--wot-color-theme-primary, #ff2237)
  );
  border-radius: 5px;
}

.dc-block-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12.5px;
  font-weight: 600;
  color: #1f2329;
  white-space: nowrap;
}

.dc-block-cd {
  flex: 0 0 auto;
  font-size: 10px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--wot-color-theme-primary, #ff2237);
}

.dc-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.dc-card {
  overflow: hidden;
  background: #fff;
  border: 1px solid #f1f2f4;
  border-radius: 10px;
  box-shadow: 0 4px 12px rgb(15 23 42 / 5%);
}

.dc-media {
  position: relative;
}

.dc-img {
  display: block;
  width: 100%;
  height: 128.5px;
  aspect-ratio: 4 / 3;
  object-fit: cover;
  background: #f3f4f6;
}

.dc-img--empty {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  color: var(--wot-color-theme-primary, #ff2237);
}

.dc-off {
  position: absolute;
  top: 6px;
  left: 6px;
  display: flex;
  align-items: center;
  height: 19px;
  padding: 0 7px;
  font-size: 10.5px;
  font-weight: 800;
  color: #fff;
  letter-spacing: 0.2px;
  background: linear-gradient(
    135deg,
    var(--wot-color-theme-secondary, #ff6b7a),
    var(--wot-color-theme-primary, #ff2237)
  );
  border-radius: 6px;
  box-shadow: 0 2px 6px rgb(15 23 42 / 16%);
}

.dc-body {
  padding: 7px 9px 9px;
}

.dc-name {
  display: -webkit-box;
  height: 32px;
  overflow: hidden;
  -webkit-line-clamp: 2;
  font-size: 12px;
  font-weight: 500;
  line-height: 16px;
  color: #1f2329;
  -webkit-box-orient: vertical;
}

.dc-price {
  display: flex;
  gap: 4px;
  align-items: baseline;
  margin-top: 5px;
  white-space: nowrap;
}

.dc-price-sym {
  font-size: 10px;
  font-weight: 700;
  color: var(--wot-color-theme-primary, #ff2237);
}

.dc-price-val {
  font-size: 19px;
  font-weight: 800;
  line-height: 1;
  color: var(--wot-color-theme-primary, #ff2237);
  letter-spacing: -0.3px;
}

.dc-original {
  font-size: 10px;
  color: #b6b9c0;
  text-decoration: line-through;
}

.dc-save {
  padding: 2px 5px;
  margin-left: auto;
  font-size: 9px;
  font-weight: 600;
  color: var(--wot-color-theme-primary, #ff2237);
  background: var(--wot-color-theme-background, #fff1ee);
  border-radius: 4px;
}
</style>

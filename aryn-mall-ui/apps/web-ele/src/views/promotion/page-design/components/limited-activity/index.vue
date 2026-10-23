<script setup lang="ts">
import type { RetailActivityItem } from '../common/retail-preview/retail-data';
import type { LimitedActivityProps } from './types';

import { computed, watch } from 'vue';

import { Picture } from '@element-plus/icons-vue';
import { ElIcon, ElImage } from 'element-plus';

import { loadLimitedActivities } from '../common/retail-preview/retail-data';
import RetailPreviewFrame from '../common/retail-preview/retail-preview-frame.vue';
import { usePreviewNow } from '../common/retail-preview/use-preview-now';
import { createRetailPreviewController } from '../common/retail-preview/use-retail-preview';
import { validateLimitedActivity } from './types';

const props = defineProps<{ showData: LimitedActivityProps }>();
const controller = createRetailPreviewController<RetailActivityItem>();
const { state } = controller;
const now = usePreviewNow();

const DEFAULT_SUBTITLE = '邀好友一起拼 · 人越多越划算';

watch(
  () => props.showData,
  (showData) =>
    controller.load(validateLimitedActivity(showData), () =>
      loadLimitedActivities(showData),
    ),
  { deep: true, immediate: true },
);

const subtitle = computed(
  () => props.showData.subtitle?.trim() || DEFAULT_SUBTITLE,
);

/** 后端时间串兼容 `yyyy-MM-dd HH:mm:ss`（iOS 直接 Date.parse 会 NaN） */
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
  if (Number.isNaN(target)) return '已结束';
  const diff = target - now.value;
  if (diff <= 0) return '已结束';
  const totalSeconds = Math.floor(diff / 1000);
  const days = Math.floor(totalSeconds / 86_400);
  const clock = [
    Math.floor((totalSeconds % 86_400) / 3600),
    Math.floor((totalSeconds % 3600) / 60),
    totalSeconds % 60,
  ]
    .map((part) => String(part).padStart(2, '0'))
    .join(':');
  return days > 0 ? `${days}天 ${clock}` : clock;
}

/** 头部倒计时取「仍在进行中的活动里最早的结束时间」，全部结束显示「已结束」 */
const headerCountdown = computed(() => {
  if (state.items.length === 0) return '';
  const targets = state.items
    .map((item) => parseTime(item.endTime))
    .filter((target) => !Number.isNaN(target) && target > now.value);
  if (targets.length === 0) return '已结束';
  return countdownText(Math.min(...targets));
});

/** 省额：原价严格大于拼团价才展示，避免给未填原价的活动划出假线 */
function savings(item: RetailActivityItem) {
  if (item.originalPrice <= item.activityPrice) return '';
  return (item.originalPrice - item.activityPrice).toFixed(2);
}

/** 成团人数徽标：缺失/0 降级为「拼团」（存量数据没有 groupNum） */
function badgeLabel(item: RetailActivityItem) {
  return item.groupNum > 0 ? `${item.groupNum}人成团` : '拼团';
}

function ctaLabel(item: RetailActivityItem) {
  if (item.status === 'pending') return '即将开团';
  if (item.status === 'ended') return '已结束';
  return '去拼单';
}

function timeLabel(item: RetailActivityItem) {
  if (item.status === 'ended') return '已结束';
  const remaining = countdownText(parseTime(item.endTime));
  return remaining === '已结束' ? remaining : `剩 ${remaining}`;
}

const items = computed(() =>
  state.items.map((item) => ({
    ...item,
    badge: badgeLabel(item),
    cta: ctaLabel(item),
    originalText: `¥${item.originalPrice.toFixed(2)}`,
    priceText: item.activityPrice.toFixed(2),
    saveText: savings(item),
    time: timeLabel(item),
  })),
);
</script>

<template>
  <RetailPreviewFrame
    :common-style="showData.commonStyle"
    :message="state.message"
    :status="state.status"
    type-label="拼团"
  >
    <div class="promo-head--light">
      <span class="promo-icon">拼</span>
      <span class="promo-text">
        <span class="promo-title">{{ showData.title || '拼团' }}</span>
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

    <div :class="showData.layout === 'grid' ? 'gb-grid' : 'gb-list'">
      <article
        v-for="item in items"
        :key="item.id"
        class="gb-card"
        :class="{ 'is-ended': item.status === 'ended' }"
      >
        <div class="gb-media">
          <ElImage
            v-if="item.imageUrl"
            :alt="item.name"
            class="gb-img"
            :src="item.imageUrl"
            fit="cover"
          >
            <template #error>
              <div class="gb-img gb-img--empty">
                <ElIcon><Picture /></ElIcon>
              </div>
            </template>
          </ElImage>
          <div v-else class="gb-img gb-img--empty">
            <ElIcon><Picture /></ElIcon>
          </div>
          <span class="gb-badge">{{ item.badge }}</span>
        </div>
        <div class="gb-body">
          <div class="gb-name">{{ item.name || '拼团活动' }}</div>
          <div class="gb-price">
            <span class="sym">¥</span>
            <span class="val">{{ item.priceText }}</span>
            <span v-if="item.saveText" class="orig">{{
              item.originalText
            }}</span>
            <span
              v-if="showData.layout !== 'grid' && item.saveText"
              class="save"
            >
              省¥{{ item.saveText }}
            </span>
          </div>
          <span
            v-if="showData.layout === 'grid' && item.saveText"
            class="save save--block"
          >
            省¥{{ item.saveText }}
          </span>
          <div class="gb-foot">
            <span v-if="showData.showCountdown" class="gb-time">
              ⏱ {{ item.time }}
            </span>
            <span
              class="gb-btn"
              :class="{
                'is-pending': item.status === 'pending',
                'is-ended': item.status === 'ended',
              }"
            >
              {{ item.cta }}
            </span>
          </div>
        </div>
      </article>
    </div>
  </RetailPreviewFrame>
</template>

<!--
  样式与小程序 diy-limited-activity 逐值对齐（px = rpx / 2）。
  2026-10-22 重设计口径：
  - 楼层头：浅底渐变 + 主色图标块 + 内联倒计时，促销强度「中」（介于秒杀与折扣之间）；
  - 单列横排卡（默认）：成团徽标 + 双价对比 + 省额 + 倒计时 + 去拼单；
  - 双列货架（layout=grid）：同一套卡片改纵向，图占满卡宽，省额独占一行；
  - 状态：active 渐变 CTA / pending 浅色禁用 / ended 整卡降透明度 + 灰 CTA。
  改任一端必须同步另一端，并由 preview-parity.test.ts 守卫。
-->
<style scoped lang="scss">
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

.gb-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 10px;
}

.gb-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 10px;
}

.gb-card {
  box-sizing: border-box;
  display: flex;
  gap: 10px;
  padding: 9px;
  background: #fff;
  border: 1px solid #f1f2f4;
  border-radius: 10px;
  box-shadow: 0 4px 12px rgb(15 23 42 / 5%);
}

.gb-card.is-ended {
  opacity: 0.6;
}

.gb-media {
  position: relative;
  flex: 0 0 auto;
}

.gb-img {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 88px;
  height: 88px;
  font-size: 11px;
  color: var(--wot-color-theme-primary, #ff2237);
  background: #f3f4f6;
  border-radius: 8px;
}

.gb-img :deep(img) {
  object-fit: cover;
}

.gb-img--empty {
  background: var(--wot-color-theme-background, #fff1ee);
}

.gb-badge {
  position: absolute;
  top: 5px;
  left: 5px;
  display: flex;
  align-items: center;
  height: 17px;
  padding: 0 6px;
  font-size: 9.5px;
  font-weight: 700;
  color: #fff;
  letter-spacing: 0.2px;
  background: linear-gradient(
    135deg,
    var(--wot-color-theme-secondary, #ff6b7a),
    var(--wot-color-theme-primary, #ff2237)
  );
  border-radius: 999px;
}

.gb-body {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}

.gb-name {
  display: -webkit-box;
  height: 34px;
  overflow: hidden;
  -webkit-line-clamp: 2;
  font-size: 12.5px;
  font-weight: 500;
  line-height: 17px;
  color: #1f2329;
  -webkit-box-orient: vertical;
}

.gb-price {
  display: flex;
  gap: 4px;
  align-items: baseline;
  margin-top: 4px;
  font-size: 20px;
  white-space: nowrap;
}

.gb-price .sym {
  font-size: 10px;
  font-weight: 700;
  color: var(--wot-color-theme-primary, #ff2237);
}

.gb-price .val {
  font-size: 20px;
  font-weight: 800;
  line-height: 1;
  color: var(--wot-color-theme-primary, #ff2237);
  letter-spacing: -0.3px;
}

.gb-price .orig {
  font-size: 10px;
  color: #b6b9c0;
  text-decoration: line-through;
}

.gb-price .save {
  margin-left: auto;
}

.save {
  padding: 2px 5px;
  font-size: 9.5px;
  font-weight: 600;
  color: var(--wot-color-theme-primary, #ff2237);
  background: var(--wot-color-theme-background, #fff1ee);
  border-radius: 4px;
}

.save--block {
  align-self: flex-start;
  margin-top: 4px;
}

.gb-foot {
  display: flex;
  gap: 6px;
  align-items: center;
  justify-content: space-between;
  padding-top: 5px;
  margin-top: auto;
}

.gb-time {
  font-size: 9.5px;
  font-weight: 500;
  font-variant-numeric: tabular-nums;
  color: #8a8f99;
  white-space: nowrap;
}

.gb-btn {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  height: 24px;
  padding: 0 12px;
  margin-left: auto;
  font-size: 11px;
  font-weight: 700;
  color: #fff;
  letter-spacing: 0.2px;
  background: linear-gradient(
    135deg,
    var(--wot-color-theme-secondary, #ff6b7a),
    var(--wot-color-theme-primary, #ff2237)
  );
  border-radius: 999px;
}

.gb-btn.is-pending {
  color: var(--wot-color-theme-primary, #ff2237);
  background: var(--wot-color-theme-background, #fff1ee);
}

.gb-btn.is-ended {
  color: #b6b9c0;
  background: #f1f2f4;
}

.gb-grid .gb-card {
  flex-direction: column;
  gap: 0;
  padding: 0;
  overflow: hidden;
}

.gb-grid .gb-media {
  width: 100%;
}

/* 双列卡：图按设计稿 343rpx（=171.5px）方形；实际卡宽由装修容器内边距决定 */
.gb-grid .gb-img {
  width: 100%;
  height: 171.5px;
  border-radius: 0;
}

.gb-grid .gb-body {
  padding: 8px 9px 9px;
}

.gb-grid .gb-foot {
  padding-top: 6px;
}
</style>

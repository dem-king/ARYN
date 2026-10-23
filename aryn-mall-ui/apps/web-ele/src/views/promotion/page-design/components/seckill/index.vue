<script setup lang="ts">
import type { RetailSeckillSessionItem } from '../common/retail-preview/retail-data';
import type { SeckillProps } from './types';

import { computed, ref, watch } from 'vue';

import { Picture } from '@element-plus/icons-vue';
import { ElIcon, ElImage } from 'element-plus';

import { loadSeckillSessions } from '../common/retail-preview/retail-data';
import RetailPreviewFrame from '../common/retail-preview/retail-preview-frame.vue';
import { usePreviewNow } from '../common/retail-preview/use-preview-now';
import { createRetailPreviewController } from '../common/retail-preview/use-retail-preview';
import { validateSeckill } from './types';

const props = defineProps<{ showData: SeckillProps }>();
const controller = createRetailPreviewController<RetailSeckillSessionItem>();
const { state } = controller;
const now = usePreviewNow();

const DEFAULT_SUBTITLE = '整点开抢 · 限量特价，手慢无';

watch(
  () => props.showData,
  (showData) =>
    controller.load(validateSeckill(showData), () =>
      loadSeckillSessions(showData),
    ),
  { deep: true, immediate: true },
);

const sessions = computed(() => state.items);
const subtitle = computed(
  () => props.showData.subtitle?.trim() || DEFAULT_SUBTITLE,
);

/** 场次切换：默认落在第一个「进行中」场次，没有则第一场（未开始） */
const activeIndex = ref(0);
watch(
  sessions,
  (list) => {
    const running = list.findIndex((session) => session.status === 1);
    activeIndex.value = Math.max(running, 0);
  },
  { immediate: true },
);
const activeSession = computed(() => sessions.value[activeIndex.value]);
const activeGoods = computed(() => activeSession.value?.goodsList ?? []);
const activePending = computed(() => (activeSession.value?.status ?? 0) !== 1);
const activeCta = computed(() =>
  activeSession.value ? ctaText(activeSession.value) : '',
);

/** 后端时间串兼容 `yyyy-MM-dd HH:mm:ss`（iOS 直接 Date.parse 会 NaN） */
function parseTime(value: string) {
  const direct = Date.parse(value);
  if (!Number.isNaN(direct)) return direct;
  return Date.parse(value.replace(' ', 'T'));
}

/** 倒计时拆成「天 + HH:MM:SS」，供头部深色分格渲染 */
function remainingParts(targetText: string, status: number) {
  if (status === 2) return null;
  const target = parseTime(targetText);
  if (Number.isNaN(target)) return null;
  const totalSeconds = Math.max(0, Math.floor((target - now.value) / 1000));
  if (totalSeconds <= 0) return null;
  return {
    days: Math.floor(totalSeconds / 86_400),
    hours: Math.floor((totalSeconds % 86_400) / 3600),
    minutes: Math.floor((totalSeconds % 3600) / 60),
    seconds: totalSeconds % 60,
  };
}

const countdown = computed(() => {
  const session = activeSession.value;
  if (!session) return null;
  const target =
    session.status === 1 ? session.endTime : (session.startTime ?? '');
  const parts = remainingParts(target, session.status);
  if (!parts) return null;
  return {
    ...parts,
    label: session.status === 1 ? '距结束' : '距开始',
  };
});

function pad(value: number) {
  return String(value).padStart(2, '0');
}

/** 场次 Tab 小字：进行中 / 开抢时间 / 已结束 */
function tabHint(session: { startTime: string; status: number }) {
  if (session.status === 1) return '进行中';
  if (session.status === 2) return '已结束';
  const start = parseTime(session.startTime);
  if (Number.isNaN(start)) return '即将开始';
  const date = new Date(start);
  return `${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/** 省额：原价严格大于秒杀价才展示（原价缺失/为 0 时不划假线） */
function savings(goods: { originalPrice: number; seckillPrice: number }) {
  if (goods.originalPrice <= goods.seckillPrice) return '';
  return (goods.originalPrice - goods.seckillPrice).toFixed(2);
}

/**
 * 已售进度：与 C 端 `aryn-mall-uniapp/src/utils/seckill-progress.ts` 同一判定规则
 * （库存未知隐藏 → 售罄「已抢光」→ 0%「限量 N 件」→ 剩余 ≤5 件或 ≤20%「仅剩 N 件」
 * → 其余「已抢 N%」）。编辑器预览必须与实机同口径，改一处必须同步另一处。
 */
function soldInfo(goods: { remainingStock: number; seckillStock: number }) {
  const stock = Number(goods.seckillStock);
  const remaining = Number(goods.remainingStock);
  if (!Number.isFinite(stock) || !Number.isFinite(remaining)) return null;
  if (remaining < 0 || stock <= 0) return null;
  const sold = Math.min(stock, Math.max(0, stock - remaining));
  const pct = Math.round((sold / stock) * 100);
  if (remaining <= 0) return { pct: 100, text: '已抢光', low: true };
  if (pct <= 0) return { pct, text: `限量 ${stock} 件`, low: false };
  const low = remaining <= 5 || remaining <= stock * 0.2;
  return {
    pct,
    low,
    text: low ? `仅剩 ${remaining} 件` : `已抢 ${pct}%`,
  };
}

/** 场次级 CTA 状态：未开始只做提示，已结束置灰 */
function ctaText(session: { status: number }) {
  if (session.status === 0) return '即将开始';
  if (session.status === 2) return '本场已结束';
  return '马上抢';
}
</script>

<template>
  <RetailPreviewFrame
    :common-style="showData.commonStyle"
    :message="state.message"
    :status="state.status"
    type-label="秒杀"
  >
    <div class="promo-head">
      <span class="promo-icon">秒</span>
      <span class="promo-text">
        <span class="promo-title">{{ showData.title || '限时秒杀' }}</span>
        <span class="promo-sub">{{ subtitle }}</span>
      </span>
      <span v-if="showData.showCountdown && countdown" class="promo-cd">
        <b class="cd-label">
          {{ countdown.label }}
          <template v-if="countdown.days > 0"> {{ countdown.days }}天</template>
        </b>
        <span class="cd-blocks">
          <span class="cd-block">{{ pad(countdown.hours) }}</span>
          <span class="cd-colon">:</span>
          <span class="cd-block">{{ pad(countdown.minutes) }}</span>
          <span class="cd-colon">:</span>
          <span class="cd-block">{{ pad(countdown.seconds) }}</span>
        </span>
      </span>
    </div>

    <div v-if="sessions.length > 1" class="sk-tabs">
      <button
        v-for="(session, index) in sessions"
        :key="session.sessionId"
        class="sk-tab"
        :class="{ 'is-on': index === activeIndex }"
        type="button"
        @click="activeIndex = index"
      >
        {{ session.sessionName || '秒杀场次' }}
        <small>{{ tabHint(session) }}</small>
      </button>
    </div>

    <div v-if="activeGoods.length > 0" class="sk-scroll">
      <article v-for="goods in activeGoods" :key="goods.skuId" class="sk-card">
        <div class="sk-media">
          <ElImage
            v-if="goods.goodsImage"
            :alt="goods.goodsName"
            class="sk-img"
            :src="goods.goodsImage"
            fit="cover"
          >
            <template #error>
              <div class="sk-img sk-img--empty">
                <ElIcon><Picture /></ElIcon>
              </div>
            </template>
          </ElImage>
          <div v-else class="sk-img sk-img--empty">
            <ElIcon><Picture /></ElIcon>
          </div>
          <span v-if="savings(goods)" class="sk-off">
            省¥{{ savings(goods) }}
          </span>
        </div>
        <div class="sk-body">
          <div class="sk-name">{{ goods.goodsName || '秒杀商品' }}</div>
          <div
            v-if="showData.showProgress && soldInfo(goods)"
            class="sk-progress"
          >
            <div class="sk-track">
              <div
                class="sk-bar"
                :style="{ width: `${soldInfo(goods)?.pct ?? 0}%` }"
              ></div>
            </div>
            <span class="sk-pct" :class="{ 'is-low': soldInfo(goods)?.low }">
              {{ soldInfo(goods)?.text }}
            </span>
          </div>
          <div class="sk-price">
            <span class="sk-sym">¥</span>
            <span class="sk-val">{{ goods.seckillPrice.toFixed(2) }}</span>
            <span
              v-if="goods.originalPrice > goods.seckillPrice"
              class="sk-orig"
            >
              ¥{{ goods.originalPrice.toFixed(2) }}
            </span>
          </div>
          <div class="sk-btn" :class="{ 'is-pending': activePending }">
            {{ activeCta }}
          </div>
        </div>
      </article>
    </div>
    <div v-else class="sk-empty">本场暂无秒杀商品</div>
  </RetailPreviewFrame>
</template>

<!--
  样式与小程序 diy-seckill 逐值对齐（px = rpx / 2）。
  2026-10-22 重设计口径：
  - 楼层头：品牌渐变 + 深色分格倒计时，作为「抢」的最强促销信号；
  - 场次 Tab：多场次才显示，默认落在进行中；
  - 横滑卡：280rpx 宽、图 1:1、价格唯一最大字、双态进度、实心 CTA。
  改任一端必须同步另一端，并由 preview-parity.test.ts 守卫。
-->
<style scoped lang="scss">
.promo-head {
  position: relative;
  display: flex;
  gap: 8px;
  align-items: center;
  height: 56px;
  padding: 0 12px;
  overflow: hidden;
  color: #fff;
  background:
    linear-gradient(180deg, rgb(0 0 0 / 5%), rgb(0 0 0 / 14%)),
    radial-gradient(
      120% 180% at 100% 0%,
      rgb(255 255 255 / 26%),
      rgb(255 255 255 / 0%) 56%
    ),
    linear-gradient(
      118deg,
      var(--wot-color-theme-secondary, #ff6b7a) 0%,
      var(--wot-color-theme-primary, #ff2237) 58%,
      var(--wot-color-theme-primary, #ff2237) 100%
    );
  border-radius: 10px;
  box-shadow: 0 6px 16px rgb(15 23 42 / 10%);
}

.promo-head::after {
  position: absolute;
  top: -40px;
  right: -26px;
  width: 108px;
  height: 108px;
  pointer-events: none;
  content: '';
  border: 1px solid rgb(255 255 255 / 20%);
  border-radius: 50%;
}

.promo-head::before {
  position: absolute;
  top: -16px;
  right: -4px;
  width: 56px;
  height: 56px;
  pointer-events: none;
  content: '';
  border: 1px solid rgb(255 255 255 / 13%);
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
  background: rgb(255 255 255 / 22%);
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
  letter-spacing: 0.3px;
  white-space: nowrap;
}

.promo-sub {
  margin-top: 3px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 9.5px;
  font-weight: 500;
  color: rgb(255 255 255 / 82%);
  white-space: nowrap;
}

.promo-cd {
  z-index: 1;
  display: flex;
  flex: 0 0 auto;
  flex-direction: column;
  gap: 2px;
  align-items: flex-end;
}

.cd-label {
  font-size: 8px;
  font-weight: 500;
  color: rgb(255 255 255 / 82%);
  letter-spacing: 0.3px;
}

.cd-blocks {
  display: flex;
  gap: 2px;
  align-items: center;
}

.cd-block {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 20px;
  height: 21px;
  padding: 0 3px;
  font-size: 11.5px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: #fff;
  letter-spacing: 0.2px;
  background: #1f2329;
  border-radius: 4px;
}

.cd-colon {
  display: flex;
  align-items: center;
  font-size: 10px;
  font-weight: 700;
  color: rgb(255 255 255 / 85%);
}

.sk-tabs {
  display: flex;
  gap: 8px;
  margin-top: 10px;
  overflow: hidden;
}

.sk-tab {
  display: flex;
  flex: 0 0 auto;
  gap: 4px;
  align-items: center;
  height: 30px;
  padding: 0 13px;
  font-size: 12px;
  font-weight: 600;
  color: #6b7280;
  white-space: nowrap;
  cursor: pointer;
  background: #f5f6f8;
  border: 0;
  border-radius: 999px;
}

.sk-tab small {
  font-size: 9.5px;
  font-weight: 600;
  color: #8a8f99;
}

.sk-tab.is-on {
  color: #fff;
  background: linear-gradient(
    135deg,
    var(--wot-color-theme-secondary, #ff6b7a),
    var(--wot-color-theme-primary, #ff2237)
  );
  box-shadow: 0 3px 8px rgb(15 23 42 / 14%);
}

.sk-tab.is-on small {
  color: rgb(255 255 255 / 85%);
}

.sk-scroll {
  display: flex;
  gap: 8px;
  padding-bottom: 2px;
  margin-top: 10px;
  overflow: auto hidden;
  scrollbar-width: none;
}

.sk-scroll::-webkit-scrollbar {
  display: none;
}

.sk-card {
  display: flex;
  flex: 0 0 140px;
  flex-direction: column;
  overflow: hidden;
  background: #fff;
  border: 0.5px solid #f1f2f4;
  border-radius: 10px;
  box-shadow: 0 4px 12px rgb(15 23 42 / 6%);
}

.sk-media {
  position: relative;
}

.sk-img {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 140px;
  height: 140px;
  font-size: 11px;
  color: var(--wot-color-theme-primary, #ff2237);
  background: #f3f4f6;
}

.sk-img :deep(img) {
  object-fit: cover;
}

.sk-img--empty {
  color: var(--wot-color-theme-primary, #ff2237);
  background: #f3f4f6;
}

.sk-off {
  position: absolute;
  top: 6px;
  left: 6px;
  display: flex;
  align-items: center;
  height: 18px;
  padding: 0 6px;
  font-size: 9.5px;
  font-weight: 700;
  color: #fff;
  letter-spacing: 0.1px;
  background: linear-gradient(
    135deg,
    var(--wot-color-theme-secondary, #ff6b7a),
    var(--wot-color-theme-primary, #ff2237)
  );
  border-radius: 999px;
}

.sk-body {
  padding: 8px 9px 10px;
}

.sk-name {
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

.sk-progress {
  display: flex;
  gap: 5px;
  align-items: center;
  margin-top: 6px;
}

.sk-track {
  flex: 1;
  height: 6px;
  overflow: hidden;
  background: var(--wot-color-theme-background, #fff1ee);
  border-radius: 999px;
}

.sk-bar {
  height: 100%;
  background: linear-gradient(
    90deg,
    var(--wot-color-theme-secondary, #ff6b7a),
    var(--wot-color-theme-primary, #ff2237)
  );
  border-radius: 999px;
}

.sk-pct {
  flex: 0 0 auto;
  font-size: 9.5px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--wot-color-theme-primary, #ff2237);
}

.sk-pct.is-low {
  font-weight: 700;
}

.sk-price {
  display: flex;
  gap: 4px;
  align-items: baseline;
  margin-top: 6px;
  white-space: nowrap;
}

.sk-sym {
  font-size: 11px;
  font-weight: 700;
  color: var(--wot-color-theme-primary, #ff2237);
}

.sk-val {
  font-size: 21px;
  font-weight: 800;
  line-height: 1;
  color: var(--wot-color-theme-primary, #ff2237);
  letter-spacing: -0.4px;
}

.sk-orig {
  font-size: 10px;
  color: #b6b9c0;
  text-decoration: line-through;
}

.sk-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 28px;
  margin-top: 8px;
  font-size: 12px;
  font-weight: 700;
  color: #fff;
  letter-spacing: 0.4px;
  background: linear-gradient(
    135deg,
    var(--wot-color-theme-secondary, #ff6b7a),
    var(--wot-color-theme-primary, #ff2237)
  );
  border-radius: 999px;
}

.sk-btn.is-pending {
  color: var(--wot-color-theme-primary, #ff2237);
  background: var(--wot-color-theme-background, #fff1ee);
}

.sk-empty {
  padding: 16px 0;
  font-size: 12px;
  color: #909399;
  text-align: center;
}
</style>

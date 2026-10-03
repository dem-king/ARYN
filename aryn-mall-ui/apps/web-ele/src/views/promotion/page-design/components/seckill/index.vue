<script setup lang="ts">
import type { RetailSeckillSessionItem } from '../common/retail-preview/retail-data';
import type { SeckillProps } from './types';

import { computed, watch } from 'vue';

import { Picture } from '@element-plus/icons-vue';
import { ElIcon, ElImage } from 'element-plus';

import { loadSeckillSessions } from '../common/retail-preview/retail-data';
import RetailPreviewFrame from '../common/retail-preview/retail-preview-frame.vue';
import { createRetailPreviewController } from '../common/retail-preview/use-retail-preview';
import { validateSeckill } from './types';

const props = defineProps<{ showData: SeckillProps }>();
const controller = createRetailPreviewController<RetailSeckillSessionItem>();
const { state } = controller;

watch(
  () => props.showData,
  (showData) =>
    controller.load(validateSeckill(showData), () =>
      loadSeckillSessions(showData),
    ),
  { deep: true, immediate: true },
);

/** 与小程序一致：进行中算距结束、未开始算距开始，已结束或时间不可解析时给结束文案。
 * 时钟格式对齐 C 端 formatRemainingTime：HH:MM:SS，跨天加「N天 」前缀 */
function remainingLabel(
  session: { endTime: string; startTime: string },
  sessionStatus: number,
) {
  if (sessionStatus === 2) return '本场已结束';
  const target = Date.parse(
    sessionStatus === 1 ? session.endTime : session.startTime,
  );
  if (Number.isNaN(target)) return '本场已结束';
  const totalSeconds = Math.max(0, Math.floor((target - Date.now()) / 1000));
  if (totalSeconds <= 0) return '本场已结束';
  const days = Math.floor(totalSeconds / 86_400);
  const clock = [
    Math.floor((totalSeconds % 86_400) / 3600),
    Math.floor((totalSeconds % 3600) / 60),
    totalSeconds % 60,
  ]
    .map((value) => String(value).padStart(2, '0'))
    .join(':');
  const remaining = days > 0 ? `${days}天 ${clock}` : clock;
  return `${countdownLabel(sessionStatus)} ${remaining}`;
}

function countdownLabel(sessionStatus: number) {
  return sessionStatus === 1 ? '距结束' : '距开始';
}

/** 与小程序同口径：剩余库存未知(-1)时返回 null，隐藏进度条而非画成 0% */
function soldPercent(goods: { remainingStock: number; seckillStock: number }) {
  if (goods.remainingStock < 0 || goods.seckillStock <= 0) return null;
  const sold = goods.seckillStock - goods.remainingStock;
  return Math.min(
    100,
    Math.max(0, Math.round((sold / goods.seckillStock) * 100)),
  );
}

const sessions = computed(() => state.items);
</script>

<template>
  <RetailPreviewFrame
    :common-style="showData.commonStyle"
    :message="state.message"
    :status="state.status"
    type-label="秒杀"
  >
    <div class="section-title">{{ showData.title }}</div>
    <div class="session-list">
      <div
        v-for="session in sessions"
        :key="session.sessionId"
        class="session-item"
      >
        <div class="session-header">
          <span class="session-name">{{
            session.sessionName || '秒杀场次'
          }}</span>
          <span v-if="showData.showCountdown" class="session-time">
            {{ remainingLabel(session, session.status) }}
          </span>
        </div>
        <div class="goods-list">
          <article
            v-for="goods in session.goodsList"
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
              <div class="goods-name">{{ goods.goodsName || '秒杀商品' }}</div>
              <div
                v-if="showData.showProgress && soldPercent(goods) !== null"
                class="goods-progress"
              >
                <div class="progress-track">
                  <div
                    class="progress-bar"
                    :style="{ width: `${soldPercent(goods)}%` }"
                  ></div>
                </div>
                <span class="progress-text">已售{{ soldPercent(goods) }}%</span>
              </div>
              <div class="goods-price-row">
                <span class="goods-price">
                  ￥{{ goods.seckillPrice.toFixed(2) }}
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
  样式与小程序 diy-seckill 逐值对齐。
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

.session-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.session-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 6px;
}

.session-name {
  font-size: 13.5px;
  font-weight: 600;
  color: #1f2329;
}

.session-time {
  font-size: 11px;
  color: var(--wot-color-theme-primary, #ff2237);
}

.goods-list {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.goods-card {
  display: flex;
  gap: 9px;
  min-height: 70px;
  overflow: hidden;
  background: #fff;
  border: 0.5px solid var(--wot-color-theme-background, #ffe1dc);
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
  color: var(--wot-color-theme-primary, #ff2237);
  background: var(--wot-color-theme-background, #fff1ee);
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

.goods-progress {
  display: flex;
  gap: 5px;
  align-items: center;
  margin-top: 5px;
}

.progress-track {
  width: 80px;
  height: 6px;
  overflow: hidden;
  background: var(--wot-color-theme-background, #ffe1dc);
  border-radius: 3px;
}

.progress-bar {
  height: 100%;
  background: var(--wot-color-theme-primary, #ff2237);
  border-radius: 3px;
}

.progress-text {
  font-size: 10.5px;
  color: var(--wot-color-theme-primary, #ff2237);
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
  color: var(--wot-color-theme-primary, #ff2237);
}

.goods-original {
  font-size: 10.5px;
  color: #a8abb2;
  text-decoration: line-through;
}
</style>

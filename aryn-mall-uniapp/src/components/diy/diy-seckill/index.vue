<script setup lang="ts">
import type { SeckillProps } from '@/components/diy/retail-types'

import { computed } from 'vue'

import { followDecorationLink } from '@/components/diy/link-resolver'
import { loadSeckillSessions } from '@/components/diy/retail-data'
import RetailState from '@/components/diy/retail-state.vue'
import { retailCommonStyle } from '@/components/diy/retail-types'
import { formatRemainingTime, useCountdownTicker } from '@/composables/useCountdown'
import { useDiyStyle } from '@/composables/useDiyStyle'
import { useRetailData } from '@/composables/useRetailData'

const props = withDefaults(defineProps<{ showData?: Partial<SeckillProps> }>(), {
  showData: () => ({}),
})
const showData = computed<SeckillProps>(() => ({
  commonStyle: props.showData.commonStyle || retailCommonStyle,
  count: Number(props.showData.count) || 1,
  dataSource: props.showData.dataSource || { mode: 'automatic' },
  emptyStrategy: props.showData.emptyStrategy || 'placeholder',
  invalidStrategy: props.showData.invalidStrategy || 'hide',
  showCountdown: props.showData.showCountdown !== false,
  showProgress: props.showData.showProgress !== false,
  title: props.showData.title || '限时秒杀',
}))
const dynamicStyles = useDiyStyle(computed(() => showData.value.commonStyle))
const { items, shouldRender, status } = useRetailData(showData, loadSeckillSessions)
const { now } = useCountdownTicker()

/** 进行中算距结束、未开始算距开始——与秒杀会场页 updateCountdown 口径一致 */
function remaining(session: { endTime: string, startTime: string, status: number }) {
  return formatRemainingTime(session.status === 1 ? session.endTime : session.startTime, now.value)
}

/** 进行中标「距结束」，未开始标「距开始」——与秒杀会场页口径一致 */
function countdownLabel(sessionStatus: number) {
  return sessionStatus === 1 ? '距结束' : '距开始'
}

/**
 * 已售进度百分比。
 *
 * `remainingStock < 0` 表示后端没下发剩余库存（而非真的没库存），
 * 此时返回 null 让模板隐藏进度条，避免把「未知」画成 0% 或 100%。
 */
function soldPercent(goods: { remainingStock: number, seckillStock: number }) {
  if (goods.remainingStock < 0 || goods.seckillStock <= 0)
    return null
  const sold = goods.seckillStock - goods.remainingStock
  return Math.min(100, Math.max(0, Math.round((sold / goods.seckillStock) * 100)))
}

function openGoods(spuId: string, skuId: string) {
  followDecorationLink({
    params: { skuId },
    path: '',
    targetId: spuId,
    type: 'goods',
  })
}
</script>

<template>
  <view v-if="shouldRender" class="seckill" :style="dynamicStyles">
    <view class="section-title">
      {{ showData.title }}
    </view>
    <RetailState v-if="status !== 'ready'" :status="status" />
    <view v-else class="session-list">
      <view v-for="session in items" :key="session.sessionId" class="session-item">
        <view class="session-header">
          <text class="session-name">
            {{ session.sessionName || '秒杀场次' }}
          </text>
          <text v-if="showData.showCountdown" class="session-time">
            {{ session.status === 2 || !remaining(session)
              ? '本场已结束'
              : `${countdownLabel(session.status)} ${remaining(session)}` }}
          </text>
        </view>
        <view class="goods-list">
          <view
            v-for="goods in session.goodsList"
            :key="goods.skuId"
            class="goods-card"
            @click="openGoods(goods.spuId, goods.skuId)"
          >
            <image
              v-if="goods.goodsImage"
              class="goods-image"
              :src="resolveImageSrc(goods.goodsImage)"
              mode="aspectFill"
              lazy-load
            />
            <view v-else class="goods-image goods-image--empty">
              秒杀
            </view>
            <view class="goods-content">
              <view class="goods-name">
                {{ goods.goodsName || '秒杀商品' }}
              </view>
              <view v-if="showData.showProgress && soldPercent(goods) !== null" class="goods-progress">
                <view class="progress-track">
                  <view class="progress-bar" :style="{ width: `${soldPercent(goods)}%` }" />
                </view>
                <text class="progress-text">
                  已售{{ soldPercent(goods) }}%
                </text>
              </view>
              <view class="goods-price-row">
                <text class="goods-price">
                  ￥{{ goods.seckillPrice.toFixed(2) }}
                </text>
                <text v-if="goods.originalPrice" class="goods-original">
                  ￥{{ goods.originalPrice.toFixed(2) }}
                </text>
              </view>
            </view>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<!--
  样式与限时活动（拼团）楼层保持同一视觉语言：
  标题字号/价格色/卡片圆角均沿用同一套值，运营切换楼层时观感一致。
-->
<style scoped lang="scss">
.section-title { margin-bottom: 12rpx; color: #1f2329; font-size: 30rpx; font-weight: 600; }
.session-list { display: flex; flex-direction: column; gap: 16rpx; }
.session-header { display: flex; margin-bottom: 12rpx; align-items: baseline; justify-content: space-between; }
.session-name { color: #1f2329; font-size: 27rpx; font-weight: 600; }
.session-time { color: var(--wot-color-theme-primary, #ff2237); font-size: 22rpx; }
.goods-list { display: flex; flex-direction: column; gap: 14rpx; }
.goods-card { display: flex; overflow: hidden; min-height: 140rpx; gap: 18rpx; border: 1rpx solid #ffe1dc; border-radius: 8rpx; background: #fff; }
.goods-image { display: flex; width: 150rpx; min-height: 140rpx; flex: 0 0 auto; align-items: center; justify-content: center; background: var(--wot-color-theme-background, #fff1ee); color: var(--wot-color-theme-primary, #ff2237); font-size: 22rpx; }
.goods-content { min-width: 0; flex: 1; padding: 16rpx 16rpx 16rpx 0; }
.goods-name { overflow: hidden; color: #303133; font-size: 27rpx; font-weight: 500; text-overflow: ellipsis; white-space: nowrap; }
.goods-progress { display: flex; margin-top: 10rpx; align-items: center; gap: 10rpx; }
.progress-track { overflow: hidden; width: 160rpx; height: 12rpx; border-radius: 6rpx; background: #ffe1dc; }
.progress-bar { height: 100%; border-radius: 6rpx; background: var(--wot-color-theme-primary, #ff2237); }
.progress-text { color: var(--wot-color-theme-primary, #ff2237); font-size: 21rpx; }
.goods-price-row { display: flex; margin-top: 14rpx; align-items: baseline; gap: 10rpx; }
.goods-price { color: var(--wot-color-theme-primary, #ff2237); font-size: 30rpx; font-weight: 700; }
.goods-original { color: #a8abb2; font-size: 21rpx; text-decoration: line-through; }
</style>

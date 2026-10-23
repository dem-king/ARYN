<script setup lang="ts">
import type { LimitedActivityProps } from '@/components/diy/retail-types'

import { computed } from 'vue'

import { followDecorationLink } from '@/components/diy/link-resolver'
import { loadLimitedActivities } from '@/components/diy/retail-data'
import RetailState from '@/components/diy/retail-state.vue'
import { retailCommonStyle } from '@/components/diy/retail-types'
import { formatRemainingTime, useCountdownTicker } from '@/composables/useCountdown'
import { useDiyStyle } from '@/composables/useDiyStyle'
import { useRetailData } from '@/composables/useRetailData'

const props = withDefaults(defineProps<{ showData?: Partial<LimitedActivityProps> }>(), {
  showData: () => ({}),
})

const DEFAULT_SUBTITLE = '邀好友一起拼 · 人越多越划算'

const showData = computed<LimitedActivityProps>(() => ({
  commonStyle: props.showData.commonStyle || retailCommonStyle,
  count: Number(props.showData.count) || 3,
  dataSource: props.showData.dataSource || { mode: 'automatic', sort: 'start-time' },
  emptyStrategy: props.showData.emptyStrategy || 'placeholder',
  invalidStrategy: props.showData.invalidStrategy || 'hide',
  layout: props.showData.layout === 'grid' ? 'grid' : 'list',
  showCountdown: props.showData.showCountdown !== false,
  subtitle: props.showData.subtitle || '',
  title: props.showData.title || '拼团',
}))
const dynamicStyles = useDiyStyle(computed(() => showData.value.commonStyle))
const { items, shouldRender, status } = useRetailData(showData, loadLimitedActivities)
const { now } = useCountdownTicker()

const isGrid = computed(() => showData.value.layout === 'grid')
const subtitle = computed(() => showData.value.subtitle.trim() || DEFAULT_SUBTITLE)

/** 后端时间串兼容 `yyyy-MM-dd HH:mm:ss`（部分小程序内核直接 Date.parse 会 NaN） */
function toTimestamp(value: string) {
  if (!value)
    return Number.NaN
  const direct = Date.parse(value)
  if (!Number.isNaN(direct))
    return direct
  return Date.parse(value.replace(' ', 'T'))
}

/** 头部倒计时取「仍进行中活动里最早的结束时间」，全部结束显示「已结束」 */
const headerCountdown = computed(() => {
  if (items.value.length === 0)
    return ''
  const targets = items.value
    .map(item => toTimestamp(item.endTime))
    .filter(target => !Number.isNaN(target) && target > now.value)
  if (targets.length === 0)
    return '已结束'
  const remaining = formatRemainingTime(new Date(Math.min(...targets)).toISOString(), now.value)
  return remaining || '已结束'
})

/** 省额：原价严格大于拼团价才展示，避免给未填原价的活动划出假线 */
function savings(item: { activityPrice: number, originalPrice: number }) {
  if (item.originalPrice <= item.activityPrice)
    return ''
  return (item.originalPrice - item.activityPrice).toFixed(2)
}

/** 成团人数徽标：缺失/0 降级为「拼团」（存量数据没有 groupNum） */
function badgeLabel(item: { groupNum: number }) {
  return item.groupNum > 0 ? `${item.groupNum}人成团` : '拼团'
}

function ctaLabel(item: { status: string }) {
  if (item.status === 'pending')
    return '即将开团'
  if (item.status === 'ended')
    return '已结束'
  return '去拼单'
}

function timeLabel(item: { endTime: string, status: string }) {
  if (item.status === 'ended')
    return '已结束'
  const target = toTimestamp(item.endTime)
  if (Number.isNaN(target))
    return '已结束'
  return `剩 ${formatRemainingTime(new Date(target).toISOString(), now.value) || '已结束'}`
}

const viewItems = computed(() =>
  items.value.map(item => ({
    ...item,
    badge: badgeLabel(item),
    cta: ctaLabel(item),
    originalText: `¥${item.originalPrice.toFixed(2)}`,
    priceText: item.activityPrice.toFixed(2),
    saveText: savings(item),
    time: timeLabel(item),
  })),
)

function openActivity(id: string) {
  followDecorationLink({ params: {}, path: '', targetId: id, type: 'activity' })
}
</script>

<template>
  <view v-if="shouldRender" class="limited-activity" :style="dynamicStyles">
    <view class="promo-head--light">
      <view class="promo-icon">
        <text class="promo-icon-glyph">
          拼
        </text>
      </view>
      <view class="promo-text">
        <text class="promo-title">
          {{ showData.title }}
        </text>
        <text class="promo-sub">
          {{ subtitle }}
        </text>
      </view>
      <view v-if="showData.showCountdown && headerCountdown" class="promo-cd-light">
        <text class="cd-label">
          距结束
        </text>
        <text class="cd-time">
          {{ headerCountdown }}
        </text>
      </view>
    </view>

    <RetailState v-if="status !== 'ready'" :status="status" />

    <view v-else :class="isGrid ? 'gb-grid' : 'gb-list'">
      <view
        v-for="item in viewItems"
        :key="item.id"
        class="gb-card"
        :class="{ 'is-ended': item.status === 'ended' }"
        @click="openActivity(item.id)"
      >
        <view class="gb-media">
          <image
            v-if="item.imageUrl"
            class="gb-img"
            :src="resolveImageSrc(item.imageUrl)"
            mode="aspectFill"
            lazy-load
          />
          <view v-else class="gb-img gb-img--empty">
            拼团
          </view>
          <view class="gb-badge">
            {{ item.badge }}
          </view>
        </view>
        <view class="gb-body">
          <view class="gb-name">
            {{ item.name || '拼团活动' }}
          </view>
          <view class="gb-price">
            <text class="sym">
              ¥
            </text>
            <text class="val">
              {{ item.priceText }}
            </text>
            <text v-if="item.saveText" class="orig">
              {{ item.originalText }}
            </text>
            <text v-if="!isGrid && item.saveText" class="save">
              省¥{{ item.saveText }}
            </text>
          </view>
          <view v-if="isGrid && item.saveText" class="save save--block">
            省¥{{ item.saveText }}
          </view>
          <view class="gb-foot">
            <text v-if="showData.showCountdown" class="gb-time">
              ⏱ {{ item.time }}
            </text>
            <view
              class="gb-btn"
              :class="{ 'is-pending': item.status === 'pending', 'is-ended': item.status === 'ended' }"
            >
              {{ item.cta }}
            </view>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<!--
  样式与管理端 page-design/components/limited-activity 逐值对齐（rpx = px × 2）。
  2026-10-22 重设计口径：
  - 楼层头：浅底渐变 + 汉字图标块 + 内联倒计时，促销强度「中」；
  - 单列横排卡（默认）：成团徽标 + 双价对比 + 省额 + 倒计时 + 去拼单；
  - 双列货架（layout=grid）：同一套卡片改纵向，图占满卡宽、省额独占一行；
  - 状态：active 渐变 CTA / pending 浅色禁用 / ended 整卡降透明度 + 灰 CTA。
-->
<style scoped lang="scss">
.promo-head--light {
  position: relative;
  display: flex;
  align-items: center;
  box-sizing: border-box;
  height: 104rpx;
  padding: 0 24rpx;
  overflow: hidden;
  gap: 16rpx;
  background: linear-gradient(105deg, var(--wot-color-theme-background, #fff1ee) 0%, #fff 78%);
  border: 1rpx solid var(--wot-color-theme-background, #fff1ee);
  border-radius: 20rpx;
}

.promo-head--light::after {
  position: absolute;
  top: -80rpx;
  right: -60rpx;
  width: 208rpx;
  height: 208rpx;
  pointer-events: none;
  content: '';
  border: 2rpx solid rgba(255, 255, 255, 0.9);
  border-radius: 50%;
}

.promo-icon {
  z-index: 1;
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 54rpx;
  height: 54rpx;
  background: linear-gradient(135deg, var(--wot-color-theme-secondary, #ff6b7a), var(--wot-color-theme-primary, #ff2237));
  border-radius: 16rpx;
}

.promo-icon-glyph {
  color: #fff;
  font-size: 30rpx;
  font-weight: 700;
}

.promo-text {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}

.promo-title {
  overflow: hidden;
  color: #1f2329;
  font-size: 38rpx;
  font-weight: 800;
  line-height: 1.1;
  letter-spacing: 0.6rpx;
  white-space: nowrap;
}

.promo-sub {
  margin-top: 6rpx;
  overflow: hidden;
  color: #8a8f99;
  font-size: 19rpx;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.promo-cd-light {
  z-index: 1;
  display: flex;
  flex: 0 0 auto;
  box-sizing: border-box;
  align-items: center;
  height: 52rpx;
  padding: 0 18rpx;
  gap: 8rpx;
  background: #fff;
  border: 1rpx solid var(--wot-color-theme-background, #fff1ee);
  border-radius: 999rpx;
}

.cd-label {
  color: #8a8f99;
  font-size: 17rpx;
  font-weight: 500;
}

.cd-time {
  color: var(--wot-color-theme-primary, #ff2237);
  font-size: 22rpx;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}

.gb-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
  margin-top: 20rpx;
}

.gb-grid {
  display: grid;
  margin-top: 20rpx;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20rpx;
}

.gb-card {
  display: flex;
  box-sizing: border-box;
  gap: 20rpx;
  padding: 18rpx;
  background: #fff;
  border: 1rpx solid #f1f2f4;
  border-radius: 20rpx;
  box-shadow: 0 8rpx 24rpx rgba(15, 23, 42, 0.05);
}

.gb-card.is-ended {
  opacity: 0.6;
}

.gb-media {
  position: relative;
  flex: 0 0 auto;
}

/* 缩略图必须显式给 rpx 宽高：小程序 <image> 基座自带 height:240px，
   只给宽度会让图片竖着撑开整张卡片（H5 端按宽高比自适应，不会暴露此问题） */
.gb-img {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 176rpx;
  height: 176rpx;
  color: var(--wot-color-theme-primary, #ff2237);
  font-size: 22rpx;
  background: #f3f4f6;
  border-radius: 16rpx;
}

.gb-img--empty {
  background: var(--wot-color-theme-background, #fff1ee);
}

.gb-badge {
  position: absolute;
  top: 10rpx;
  left: 10rpx;
  display: flex;
  align-items: center;
  height: 34rpx;
  padding: 0 12rpx;
  color: #fff;
  font-size: 19rpx;
  font-weight: 700;
  letter-spacing: 0.4rpx;
  background: linear-gradient(135deg, var(--wot-color-theme-secondary, #ff6b7a), var(--wot-color-theme-primary, #ff2237));
  border-radius: 999rpx;
}

.gb-body {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}

.gb-name {
  display: -webkit-box;
  height: 68rpx;
  overflow: hidden;
  color: #1f2329;
  font-size: 25rpx;
  font-weight: 500;
  line-height: 34rpx;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.gb-price {
  display: flex;
  align-items: baseline;
  margin-top: 8rpx;
  gap: 8rpx;
  font-size: 40rpx;
  white-space: nowrap;
}

.gb-price .sym {
  color: var(--wot-color-theme-primary, #ff2237);
  font-size: 20rpx;
  font-weight: 700;
}

.gb-price .val {
  color: var(--wot-color-theme-primary, #ff2237);
  font-size: 40rpx;
  font-weight: 800;
  line-height: 1;
  letter-spacing: -0.6rpx;
}

.gb-price .orig {
  color: #b6b9c0;
  font-size: 20rpx;
  text-decoration: line-through;
}

.gb-price .save {
  margin-left: auto;
}

.save {
  padding: 4rpx 10rpx;
  color: var(--wot-color-theme-primary, #ff2237);
  font-size: 19rpx;
  font-weight: 600;
  background: var(--wot-color-theme-background, #fff1ee);
  border-radius: 8rpx;
}

.save--block {
  align-self: flex-start;
  margin-top: 8rpx;
}

.gb-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: auto;
  padding-top: 10rpx;
  gap: 12rpx;
}

.gb-time {
  overflow: hidden;
  color: #8a8f99;
  font-size: 19rpx;
  font-weight: 500;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-variant-numeric: tabular-nums;
}

.gb-btn {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  height: 48rpx;
  padding: 0 24rpx;
  margin-left: auto;
  color: #fff;
  font-size: 22rpx;
  font-weight: 700;
  letter-spacing: 0.4rpx;
  background: linear-gradient(135deg, var(--wot-color-theme-secondary, #ff6b7a), var(--wot-color-theme-primary, #ff2237));
  border-radius: 999rpx;
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
  padding: 0;
  overflow: hidden;
  gap: 0;
}

.gb-grid .gb-media {
  width: 100%;
}

/* 双列卡：图按设计稿 343rpx 方形铺满卡宽（实际卡宽由装修容器内边距决定） */
.gb-grid .gb-img {
  width: 100%;
  height: 343rpx;
  border-radius: 0;
}

.gb-grid .gb-body {
  padding: 16rpx 18rpx 18rpx;
}

.gb-grid .gb-foot {
  padding-top: 12rpx;
}
</style>

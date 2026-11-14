<script setup lang="ts">
import type { AppCalendarSpecial } from '@/api/promotion/trade-calendar'

/**
 * 采购档期特供楼层（2026-11-14 船供 B2B 活动体系重构）。
 *
 * 数据（服务端同源口径）：取当前生效的采购档期（服务端过滤档期窗）及其进行中的
 * 档期特供活动（type=12），特供商品带特供价/划线售价/剩余限量；无档期或无特供
 * 默认整楼隐藏（emptyStrategy 默认 hide），不留空白占位。
 *
 * 卡片（与 preview-parity 守卫逐值对齐）：220rpx 等宽方框图（contain，商品图源
 * 横/竖混杂不裁切）、单行商品名、特供价主题色 + 划线售价、剩余限量小签。
 * 倒计时取最早结束的进行中特供活动（档期窗兜底），过期自动隐藏倒计时。
 *
 * 接口均为需登录端点（/promotion/app/calendar/*）：未登录时接口失败静默 →
 * 走 emptyStrategy（默认整楼隐藏），与会员价补价层的游客降级同语义。
 */
import type { TradeCalendarProps } from '@/components/diy/retail-types'
import { computed } from 'vue'

import { useI18n } from 'vue-i18n'
import { getCalendarSpecials, getTradeCalendars } from '@/api/promotion/trade-calendar'
import RetailState from '@/components/diy/retail-state.vue'
import { retailCommonStyle } from '@/components/diy/retail-types'
import { formatRemainingTime, useCountdownTicker } from '@/composables/useCountdown'
import { useDiyStyle } from '@/composables/useDiyStyle'
import { useRetailData } from '@/composables/useRetailData'
import { resolveImageSrc } from '@/utils/image'

const props = withDefaults(defineProps<{ showData?: Partial<TradeCalendarProps> }>(), {
  showData: () => ({}),
})
const { t } = useI18n()
const badgeText = computed(() => t('diy.tradeCalendar.badge'))

/** 卡宽与间距（rpx）：与管理端预览逐值对齐（1rpx = 0.5px） */
const CARD_WIDTH_RPX = 220
const CARD_GAP_RPX = 16
const LIST_PADDING_RPX = 20
const showData = computed<TradeCalendarProps>(() => ({
  commonStyle: props.showData.commonStyle || retailCommonStyle,
  count: Number(props.showData.count) || 10,
  dataSource: props.showData.dataSource || { mode: 'automatic' },
  emptyStrategy: props.showData.emptyStrategy || 'hide',
  // 接口需登录：未登录失败按「无数据」隐藏整楼，不走 invalid 占位
  invalidStrategy: props.showData.invalidStrategy || 'hide',
  title: props.showData.title || '',
}))
const dynamicStyles = useDiyStyle(computed(() => showData.value.commonStyle))

interface SpecialCard {
  activityId: string
  activityName: string
  endTime: string
  /** 剩余限量（件）；null=不限量 */
  remainingQuota: null | number
  picUrl: string
  price: number
  /** 划线售价（严格大于特供价才展示） */
  salesPrice: number | null
  skuId: string
  skuName: string
  spuId: string
  spuName: string
}

async function loadSpecialCards(source: TradeCalendarProps): Promise<SpecialCard[]> {
  const calendars = await getTradeCalendars()
  const calendar = (calendars || [])[0]
  if (!calendar)
    return []
  const specials: AppCalendarSpecial[] = await getCalendarSpecials(calendar.id)
  const cards: SpecialCard[] = []
  for (const special of specials || []) {
    for (const item of special.items || []) {
      if (cards.length >= source.count)
        return cards
      const price = Number(item.price)
      if (!Number.isFinite(price) || price <= 0)
        continue
      const sales = Number(item.originalPrice)
      cards.push({
        activityId: special.activityId,
        activityName: special.activityName,
        endTime: special.endTime,
        remainingQuota: special.totalQuota === null || special.totalQuota === undefined ? null : (special.remainingQuota ?? 0),
        picUrl: item.picUrl || '',
        price,
        salesPrice: Number.isFinite(sales) && sales > price ? Math.round(sales * 100) / 100 : null,
        skuId: item.skuId,
        skuName: '',
        spuId: item.spuId || '',
        spuName: item.spuName || item.skuId,
      })
    }
  }
  return cards
}

const { items, shouldRender, status } = useRetailData(showData, loadSpecialCards)

const { now } = useCountdownTicker()
/** 最早结束的进行中特供活动（特供窗 ≤ 档期窗，倒计时以特供为准） */
const countdownText = computed(() => {
  let earliest = ''
  for (const item of items.value) {
    if (!item.endTime)
      continue
    if (!earliest || item.endTime < earliest)
      earliest = item.endTime
  }
  if (!earliest)
    return ''
  return formatRemainingTime(earliest, now.value)
})

const floorTitle = computed(() =>
  showData.value.title
  || items.value[0]?.activityName
  || t('diy.tradeCalendar.fallbackTitle'))

const cardStyle = computed(() => ({
  gap: `${CARD_GAP_RPX}rpx`,
  padding: `0 ${LIST_PADDING_RPX}rpx`,
}))

function quotaText(card: SpecialCard) {
  return card.remainingQuota === null || card.remainingQuota === undefined
    ? t('diy.tradeCalendar.quotaUnlimited')
    : t('diy.tradeCalendar.quotaLeft', { count: card.remainingQuota })
}

function onImageError(card: SpecialCard) {
  card.picUrl = ''
}
</script>

<template>
  <view v-if="shouldRender" class="trade-calendar" :style="dynamicStyles">
    <view v-if="status === 'ready'" class="tc-body">
      <view class="tc-header">
        <text class="tc-title">
          {{ floorTitle }}
        </text>
        <text v-if="countdownText" class="tc-countdown">
          {{ countdownText }}
        </text>
      </view>
      <scroll-view class="tc-scroll" :style="cardStyle" scroll-x :show-scrollbar="false">
        <view
          v-for="card in items"
          :key="`${card.activityId}-${card.skuId}`"
          class="tc-card"
          :style="{ width: `${CARD_WIDTH_RPX}rpx` }"
        >
          <view class="tc-figure">
            <image
              v-if="card.picUrl"
              class="tc-img"
              :src="resolveImageSrc(card.picUrl)"
              mode="aspectFit"
              @error="onImageError(card)"
            />
            <view v-else class="tc-img-fallback">
              {{ (card.spuName || '?').slice(0, 1) }}
            </view>
            <text class="tc-badge">
              {{ badgeText }}
            </text>
          </view>
          <text class="tc-name">
            {{ card.spuName }}
          </text>
          <view class="tc-price-row">
            <text class="tc-price">
              ¥{{ card.price }}
            </text>
            <text v-if="card.salesPrice" class="tc-strike">
              ¥{{ card.salesPrice }}
            </text>
          </view>
          <text class="tc-quota">
            {{ quotaText(card) }}
          </text>
        </view>
      </scroll-view>
    </view>
    <RetailState v-else :status="status" />
  </view>
</template>

<style scoped lang="scss">
.trade-calendar { overflow: hidden; }
.tc-body { padding: 20rpx 0; }
.tc-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 0 20rpx 16rpx;
}
.tc-title { color: #303133; font-size: 30rpx; font-weight: 700; }
.tc-countdown {
  color: var(--wot-color-theme-primary, #ff2237);
  font-family: monospace;
  font-size: 26rpx;
  font-weight: 700;
}
.tc-scroll { display: flex; white-space: nowrap; }
.tc-card { display: inline-block; flex-shrink: 0; }
.tc-figure {
  background: #f5f7fa;
  border-radius: 12rpx;
  height: 220rpx;
  overflow: hidden;
  position: relative;
  width: 220rpx;
}
.tc-img { height: 100%; width: 100%; }
.tc-img-fallback {
  align-items: center;
  color: #c0c4cc;
  display: flex;
  font-size: 56rpx;
  height: 100%;
  justify-content: center;
}
.tc-badge {
  background: var(--wot-color-theme-primary, #ff2237);
  border-radius: 0 0 12rpx 0;
  color: #fff;
  font-size: 20rpx;
  left: 0;
  padding: 4rpx 10rpx;
  position: absolute;
  top: 0;
}
.tc-name {
  color: #303133;
  display: block;
  font-size: 24rpx;
  margin-top: 10rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  width: 220rpx;
}
.tc-price-row {
  align-items: baseline;
  display: flex;
  gap: 8rpx;
  margin-top: 4rpx;
}
.tc-price {
  color: var(--wot-color-theme-primary, #ff2237);
  font-size: 28rpx;
  font-weight: 700;
}
.tc-strike {
  color: #909399;
  font-size: 20rpx;
  text-decoration: line-through;
}
.tc-quota {
  background: #f5f7fa;
  border-radius: 8rpx;
  color: #909399;
  display: inline-block;
  font-size: 20rpx;
  margin-top: 6rpx;
  padding: 2rpx 10rpx;
}
</style>

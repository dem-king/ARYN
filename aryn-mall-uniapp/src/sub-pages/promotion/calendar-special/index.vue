<script setup lang="ts">
/**
 * 档期特供会场（首页 diy-trade-calendar 楼层头「查看全部」的落地页）。
 *
 * 与楼层同源口径：一次聚合全部生效档期下进行中的 type=12 特供活动，服务端已按
 * 采购场景/船舶圈定过滤并剔除限量耗尽；本页不受楼层 count 限制，整场特供全量展示。
 * 游客可访问（接口在免登白名单内），失败按空态处理并给出下拉刷新/回首页出口。
 */
import { onLoad } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { getCalendarSpecials } from '@/api/promotion/trade-calendar'
import PriceText from '@/components/price-text/index.vue'
import { formatRemainingTime, useCountdownTicker } from '@/composables/useCountdown'
import { useShipContextStore } from '@/store/shipContextStore'
import { resolveImageSrc } from '@/utils/image'
import { navigateToUrl } from '@/utils/tab-bar'

definePage({
  name: 'calendar-special',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '档期特供',
  },
})

const { t } = useI18n()
const shipContextStore = useShipContextStore()

interface SpecialCard {
  activityId: string
  activityName: string
  calendarName: string
  endTime: string
  perUserLimit: null | number
  picUrl: string
  price: number
  remainingQuota: null | number
  salesPrice: number | null
  skuId: string
  spuId: string
  spuName: string
  totalQuota: null | number
}

const items = ref<SpecialCard[]>([])
const loading = ref(true)
const failed = ref(false)
const refreshing = ref(false)

async function load() {
  loading.value = true
  try {
    const specials = await getCalendarSpecials({
      purchaseScene: shipContextStore.purchaseScene,
      vesselId: shipContextStore.vesselId || undefined,
    })
    const cards: SpecialCard[] = []
    for (const special of specials || []) {
      for (const item of special.items || []) {
        const price = Number(item.price)
        if (!Number.isFinite(price) || price <= 0)
          continue
        const sales = Number(item.originalPrice)
        const totalQuota = special.totalQuota === null || special.totalQuota === undefined
          ? null
          : Number(special.totalQuota)
        cards.push({
          activityId: special.activityId,
          activityName: special.activityName,
          calendarName: special.calendarName || '',
          endTime: special.endTime,
          perUserLimit: item.perUserLimit === null || item.perUserLimit === undefined
            ? null
            : Number(item.perUserLimit),
          picUrl: item.picUrl || '',
          price,
          remainingQuota: totalQuota === null ? null : Number(special.remainingQuota ?? 0),
          salesPrice: Number.isFinite(sales) && sales > price ? Math.round(sales * 100) / 100 : null,
          skuId: item.skuId,
          spuId: item.spuId || '',
          spuName: item.spuName || item.skuId,
          totalQuota,
        })
      }
    }
    items.value = cards
    failed.value = false
  }
  catch {
    items.value = []
    failed.value = true
  }
  finally {
    loading.value = false
  }
}

async function onRefresh() {
  refreshing.value = true
  try {
    await load()
  }
  finally {
    refreshing.value = false
  }
}

onLoad(() => {
  void load()
})

const { now } = useCountdownTicker()

const floorTitle = computed(() =>
  items.value[0]?.calendarName
  || items.value[0]?.activityName
  || t('promotion.calendarSpecial.title'))

/** 进行中的特供活动数（同一活动多个 SKU 只算一场） */
const activityCount = computed(() => new Set(items.value.map(item => item.activityId)).size)

/** 会场副标题：单场显示活动名；多场只报场次数（余量/限购不再塞进这一行） */
const subtitle = computed(() => {
  const first = items.value[0]
  if (!first)
    return ''
  if (activityCount.value > 1)
    return t('promotion.calendarSpecial.multiActivity', { count: activityCount.value })
  return first.activityName && first.activityName !== floorTitle.value
    ? first.activityName
    : ''
})

/** 与首页楼层同口径：余量只在档期牌底部出现一次，多场额度不可合并 */
const quotaLabel = computed(() => {
  const first = items.value[0]
  if (!first || activityCount.value > 1)
    return ''
  return first.totalQuota === null
    ? t('promotion.calendarSpecial.quotaUnlimited')
    : t('promotion.calendarSpecial.quotaTotal', { count: first.totalQuota })
})

const remainingText = computed(() => {
  const first = items.value[0]
  if (!first || activityCount.value > 1 || first.totalQuota === null || first.remainingQuota === null)
    return ''
  return t('diy.tradeCalendar.quotaLeft', { count: first.remainingQuota })
})

const showQuotaBar = computed(() => {
  const first = items.value[0]
  return Boolean(first
    && activityCount.value === 1
    && first.totalQuota !== null
    && first.totalQuota > 0
    && first.remainingQuota !== null)
})

const quotaPercent = computed(() => {
  const first = items.value[0]
  if (!first || first.totalQuota === null || first.totalQuota <= 0)
    return 0
  const remaining = Math.min(Math.max(Number(first.remainingQuota ?? 0), 0), first.totalQuota)
  return Math.round((remaining / first.totalQuota) * 1000) / 10
})

/** 最早结束的进行中特供活动（与楼层同口径） */
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

function saveAmountText(card: SpecialCard) {
  if (card.salesPrice === null)
    return ''
  const saved = Math.round((card.salesPrice - card.price) * 100) / 100
  if (saved <= 0)
    return ''
  return t('diy.common.saveAmount', { amount: String(Number(saved.toFixed(2))) })
}

function limitText(card: SpecialCard) {
  return card.perUserLimit && card.perUserLimit > 0
    ? t('diy.tradeCalendar.limitPerUser', { count: card.perUserLimit })
    : t('diy.tradeCalendar.limitNone')
}

function onImageError(card: SpecialCard) {
  card.picUrl = ''
}

function openGoods(card: SpecialCard) {
  if (!card.spuId)
    return
  uni.navigateTo({ url: `/sub-pages/product/goods-detail/index?id=${card.spuId}` })
}

function goHome() {
  navigateToUrl('/pages/home/index')
}
</script>

<template>
  <view class="cs-page">
    <hr-navbar :title="t('promotion.calendarSpecial.title')" />
    <scroll-view
      scroll-y
      class="page-scroll"
      refresher-enabled
      :refresher-triggered="refreshing"
      @refresherrefresh="onRefresh"
    >
      <view v-if="items.length" class="cs-hero">
        <view class="cs-hero-row">
          <view class="cs-hero-mark">
            {{ t('diy.tradeCalendar.mark') }}
          </view>
          <text class="cs-hero-title">
            {{ floorTitle }}
          </text>
        </view>
        <view v-if="subtitle || countdownText" class="cs-hero-sub-row">
          <text v-if="subtitle" class="cs-hero-sub">
            {{ subtitle }}
          </text>
          <view v-if="countdownText" class="cs-hero-timer">
            <text class="cs-hero-timer-label">
              {{ t('diy.tradeCalendar.endsIn') }}
            </text>
            <text class="cs-hero-timer-value">
              {{ countdownText }}
            </text>
          </view>
        </view>
        <view class="cs-hero-cut" />
        <view class="cs-hero-foot">
          <text v-if="quotaLabel" class="cs-gauge-label">
            {{ quotaLabel }}
          </text>
          <view v-if="showQuotaBar" class="cs-gauge">
            <view class="cs-gauge-fill" :style="{ width: `${quotaPercent}%` }" />
          </view>
          <text v-if="remainingText" class="cs-gauge-left">
            {{ remainingText }}
          </text>
          <text class="cs-hero-count">
            {{ t('promotion.calendarSpecial.count', { count: items.length }) }}
          </text>
        </view>
      </view>

      <view v-if="items.length" class="cs-grid">
        <view
          v-for="card in items"
          :key="`${card.activityId}-${card.skuId}`"
          class="cs-card"
          @click="openGoods(card)"
        >
          <view class="cs-figure">
            <image
              v-if="card.picUrl"
              class="cs-img"
              :src="resolveImageSrc(card.picUrl)"
              mode="aspectFit"
              @error="onImageError(card)"
            />
            <view v-else class="cs-img cs-img--empty">
              {{ (card.spuName || '?').slice(0, 1) }}
            </view>
            <text class="cs-stamp">
              {{ t('diy.tradeCalendar.badge') }}
            </text>
          </view>
          <view class="cs-info">
            <view class="cs-name">
              {{ card.spuName }}
            </view>
            <view class="cs-price-row">
              <price-text class="cs-price" :value="card.price" format="trim" />
              <text v-if="card.salesPrice" class="cs-strike">
                ¥{{ card.salesPrice }}
              </text>
            </view>
            <view class="cs-perf" />
            <view class="cs-meta">
              <text v-if="saveAmountText(card)" class="cs-save">
                {{ saveAmountText(card) }}
              </text>
              <text class="cs-limit">
                {{ limitText(card) }}
              </text>
            </view>
          </view>
        </view>
      </view>

      <view v-if="!loading && !items.length" class="cs-state">
        <view class="cs-state-title">
          {{ failed ? t('promotion.calendarSpecial.loadFailedTitle') : t('promotion.calendarSpecial.emptyTitle') }}
        </view>
        <view class="cs-state-desc">
          {{ failed ? t('promotion.calendarSpecial.loadFailedDesc') : t('promotion.calendarSpecial.emptyDesc') }}
        </view>
        <button class="cs-state-btn" @click="goHome">
          {{ t('promotion.calendarSpecial.backHome') }}
        </button>
      </view>

      <view v-if="items.length" class="cs-foot">
        {{ t('promotion.calendarSpecial.footNote') }}
      </view>
      <view class="cs-bottom-space" />
    </scroll-view>
  </view>
</template>

<style scoped lang="scss">
.cs-page {
  display: flex;
  height: 100vh;
  flex-direction: column;
  overflow: hidden;
  background: var(--app-bg);
}

.page-scroll {
  min-height: 0;
  flex: 1;
}

/* ---- 档期牌：与首页楼层同一套海军蓝语言，页面级放大一档 ---- */
.cs-hero {
  position: relative;
  overflow: hidden;
  margin: 20rpx 24rpx 0;
  padding: 28rpx 26rpx 24rpx;
  border-radius: 28rpx;
  background: linear-gradient(145deg, #0d2337 0%, #173a52 62%, #1c455f 100%);
  box-shadow: 0 16rpx 44rpx rgb(13 35 55 / 22%);
  color: #fff;
}

.cs-hero-row {
  display: flex;
  align-items: center;
  gap: 14rpx;
}

.cs-hero-mark {
  display: flex;
  width: 40rpx;
  height: 40rpx;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  border-radius: 10rpx;
  box-shadow: 0 4rpx 12rpx rgb(255 34 55 / 35%);
  background: linear-gradient(
    135deg,
    var(--wot-color-theme-secondary, #ff6b7a),
    var(--wot-color-theme-primary, #ff2237)
  );
  font-size: 22rpx;
  font-weight: 700;
  line-height: 1;
}

.cs-hero-title {
  overflow: hidden;
  min-width: 0;
  flex: 1;
  font-size: 34rpx;
  font-weight: 700;
  letter-spacing: 0.4rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cs-hero-sub-row {
  display: flex;
  align-items: baseline;
  gap: 16rpx;
  margin-top: 14rpx;
}

.cs-hero-sub {
  overflow: hidden;
  min-width: 0;
  flex: 1;
  color: rgb(255 255 255 / 62%);
  font-size: 22rpx;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cs-hero-timer {
  display: flex;
  flex: 0 0 auto;
  align-items: baseline;
  gap: 6rpx;
  margin-left: auto;
  font-variant-numeric: tabular-nums;
}

.cs-hero-timer-label {
  color: rgb(255 255 255 / 52%);
  font-size: 20rpx;
}

.cs-hero-timer-value {
  color: #ffc46b;
  font-size: 24rpx;
  font-weight: 600;
  letter-spacing: 0.2rpx;
}

.cs-hero-cut {
  margin: 20rpx -26rpx 18rpx;
  border-top: 1rpx dashed rgb(255 255 255 / 22%);
}

.cs-hero-foot {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.cs-gauge-label {
  flex: 0 0 auto;
  color: rgb(255 255 255 / 62%);
  font-size: 20rpx;
  white-space: nowrap;
}

.cs-gauge {
  overflow: hidden;
  min-width: 80rpx;
  height: 8rpx;
  flex: 1;
  border-radius: 999rpx;
  background: rgb(255 255 255 / 14%);
}

.cs-gauge-fill {
  height: 100%;
  border-radius: 999rpx;
  background: linear-gradient(90deg, #ffd48a, #ffb347);
}

.cs-gauge-left {
  flex: 0 0 auto;
  color: #ffc46b;
  font-size: 20rpx;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.cs-hero-count {
  flex: 0 0 auto;
  margin-left: auto;
  color: #ffc46b;
  font-size: 20rpx;
  font-weight: 600;
  white-space: nowrap;
}

/* ---- 双列特供票：米白纸面 + 撕线 + 印章，和楼层卡面同一套语言 ---- */
.cs-grid {
  display: grid;
  padding: 20rpx 24rpx 0;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16rpx;
}

.cs-card {
  display: flex;
  overflow: hidden;
  flex-direction: column;
  border: 1rpx solid #f0e2d3;
  border-radius: 20rpx;
  background: #fffdf9;
  box-shadow: 0 6rpx 20rpx rgb(160 106 31 / 8%);
}

.cs-figure {
  position: relative;
  width: 100%;
  padding-top: 100%;
  background: #fbf5ec;
}

.cs-img {
  position: absolute;
  top: 0;
  left: 0;
  display: block;
  width: 100%;
  height: 100%;
}

.cs-img--empty {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c0b09e;
  font-size: 52rpx;
}

/* 印章式角标：描边 + 微旋转，和普通商品卡的实心角标区分 */
.cs-stamp {
  position: absolute;
  top: 14rpx;
  left: 14rpx;
  z-index: 1;
  transform: rotate(-7deg);
  padding: 5rpx 12rpx;
  border: 3rpx solid rgb(255 34 55 / 82%);
  border-radius: 8rpx;
  background: rgb(255 253 249 / 88%);
  color: #e02038;
  font-size: 20rpx;
  font-weight: 700;
  letter-spacing: 1.4rpx;
  line-height: 1.3;
}

.cs-info {
  display: flex;
  padding: 16rpx 18rpx 18rpx;
  flex-direction: column;
}

/* 固定两行高：双列卡片价格行必须齐平 */
.cs-name {
  display: -webkit-box;
  overflow: hidden;
  height: 68rpx;
  color: #2b2f36;
  font-size: 25rpx;
  font-weight: 500;
  line-height: 34rpx;
  overflow-wrap: break-word;
  white-space: normal;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.cs-price-row {
  display: flex;
  margin-top: 6rpx;
  height: 44rpx;
  align-items: baseline;
  gap: 8rpx;
}

.cs-price {
  display: flex;
  overflow: hidden;
  min-width: 0;
  align-items: baseline;
  color: var(--wot-color-theme-primary, #ff2237);
  font-size: 34rpx;
  font-weight: 800;
  line-height: 1;
  white-space: nowrap;
}

.cs-strike {
  flex: 0 0 auto;
  color: #a8abb2;
  font-size: 20rpx;
  text-decoration: line-through;
}

/* 票根撕线：贯穿卡面、贴到左右边 */
.cs-perf {
  margin: 14rpx -18rpx 12rpx;
  border-top: 1rpx dashed #eadccb;
}

.cs-meta {
  display: flex;
  height: 32rpx;
  align-items: center;
  gap: 8rpx;
}

.cs-save {
  flex: 0 0 auto;
  padding: 2rpx 10rpx;
  border-radius: 6rpx;
  background: #fff0ee;
  color: var(--wot-color-theme-primary, #ff2237);
  font-size: 18rpx;
  font-weight: 600;
  line-height: 1.5;
}

.cs-limit {
  overflow: hidden;
  margin-left: auto;
  color: #8f959e;
  font-size: 18rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ---- 空态 / 失败态 ---- */
.cs-state {
  display: flex;
  margin: 60rpx 24rpx 0;
  padding: 80rpx 40rpx;
  align-items: center;
  flex-direction: column;
  border-radius: 24rpx;
  background: var(--app-card);
}

.cs-state-title {
  color: #303133;
  font-size: 28rpx;
  font-weight: 600;
}

.cs-state-desc {
  margin-top: 12rpx;
  color: #8f959e;
  font-size: 22rpx;
  line-height: 1.6;
  text-align: center;
}

.cs-state-btn {
  margin-top: 32rpx;
  padding: 0 40rpx;
  border: none;
  border-radius: 999rpx;
  background: var(--wot-color-theme-primary, #ff2237);
  color: #fff;
  font-size: 24rpx;
  font-weight: 600;
  line-height: 64rpx;
}

.cs-foot {
  padding: 24rpx 24rpx 0;
  color: #b0b4bb;
  font-size: 20rpx;
  line-height: 1.6;
  text-align: center;
}

.cs-bottom-space {
  height: calc(24rpx + env(safe-area-inset-bottom));
}
</style>

<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { splitPrice } from '@/components/diy/price-display'
import { useFrostedNavbar } from '@/composables/useFrostedNavbar'
import { useZonePrices } from '@/composables/useZonePrices'
import { getPage } from '@/sub-pages/api/product/footprint'
import { formatClockTime, resolveHistoryDay } from '@/utils/history-display'
import { stockHintText } from '@/utils/stock-display'
import { zonePriceBadgeText } from '@/utils/zone-price'

const { handlePageScroll } = useFrostedNavbar()

definePage({
  name: 'footprint',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '我的足迹',
  },
})

interface FootprintItem {
  spuId?: string
  createTime?: string
  spuUrls?: string
  name?: string
  stock?: number | string
  salesPrice?: number | string
  originalPrice?: number | string
}

interface FootprintDay {
  browseDate?: string
  items?: FootprintItem[]
}

interface FootprintItemView {
  key: string
  spuId: string
  pic: string
  name: string
  available: boolean
  price: ReturnType<typeof splitPrice>
  /** 划线基准（会员价/捡漏价命中时为卡片价，否则为「原价 > 售价」的普通划线） */
  strikePrice: number | null
  /** 身份价角标文案（会员价/捡漏价），空串不渲染 */
  badgeText: string
  /** 角标样式类型（member/clearance），无角标为空串 */
  badgeKind: string
  /** 未达标/未登录的「会员专享 ¥x」引导 */
  guideText: string
  soldOut: boolean
  stockTag: string
  clock: string
}

interface FootprintDayView {
  key: string
  title: string
  subtitle: string
  countText: string
  items: FootprintItemView[]
}

const { t } = useI18n()
const list = ref<FootprintDay[]>([])
const globalLoading = useGlobalLoading()
const router = useRouter()
const pagingRef = ref()
/** 列表专区价：足迹接口只回 SPU，会员价/捡漏价按本页 spuId 异步补拉（先渲染后补价） */
const zone = useZonePrices()

onLoad(async () => {
  nextTick(() => {
    pagingRef.value?.reload()
  })
})

async function queryList(pageNo: number, pageSize: number) {
  globalLoading.loading(t('common.loading'))
  try {
    const response = await getPage({
      current: pageNo,
      size: pageSize,
    })
    const records: FootprintDay[] = response?.records ?? []
    pagingRef.value?.complete(records)
    // 卡片先按普通售价渲染，本页商品的会员价/捡漏价异步补拉（失败静默、缓存 60s）
    const spuIds = records.flatMap(day => (Array.isArray(day?.items) ? day.items : []).map(item => item?.spuId))
    void zone.loadForSpuIds(spuIds)
  }
  finally {
    globalLoading.close()
  }
}

function buildItemView(item: FootprintItem | null | undefined, dayKey: string, index: number): FootprintItemView {
  const available = Boolean(item?.name)
  const stock = Number(item?.stock)
  const soldOut = available && Number.isFinite(stock) && stock <= 0
  const spuId = String(item?.spuId || '')
  // 专区价与列表卡同一口径：主价（会员价/捡漏价/售价）+ 划线 + 角标 + 未达标引导
  const display = available
    ? zone.cardPriceOf({ id: spuId, price: item?.salesPrice, originalPrice: item?.originalPrice })
    : null
  const mainPrice = display?.mainPrice ?? 0
  return {
    key: item?.spuId || `${dayKey}-${index}`,
    spuId,
    pic: typeof item?.spuUrls === 'string' ? item.spuUrls : '',
    name: available ? String(item?.name) : t('accountSettings.footprint.unavailable'),
    available,
    price: available && mainPrice > 0 ? splitPrice(mainPrice) : null,
    strikePrice: display?.strikePrice ?? null,
    badgeText: zonePriceBadgeText(display?.badgeKind ?? ''),
    badgeKind: display?.badgeKind ?? '',
    guideText: display?.guideText ?? '',
    soldOut,
    stockTag: available && !soldOut ? stockHintText(item?.stock) : '',
    clock: formatClockTime(item?.createTime) || item?.createTime || '',
  }
}

const dayGroups = computed<FootprintDayView[]>(() => {
  const now = new Date()
  return list.value.map((day, dayIndex) => {
    const items = [...(Array.isArray(day.items) ? day.items : [])]
      .sort((a, b) => String(b?.createTime || '').localeCompare(String(a?.createTime || '')))
      .map((item, index) => buildItemView(item, day.browseDate || String(dayIndex), index))

    const parts = resolveHistoryDay(day.browseDate, now)
    let title = day.browseDate || ''
    let subtitle = ''
    if (parts) {
      const dateText = parts.year === now.getFullYear()
        ? t('accountSettings.footprint.dateLabel', { month: parts.month, day: parts.day })
        : t('accountSettings.footprint.dateLabelWithYear', { year: parts.year, month: parts.month, day: parts.day })
      const weekday = t(`accountSettings.footprint.weekday${parts.weekday}`)
      if (parts.isToday) {
        title = t('accountSettings.footprint.today')
        subtitle = `${dateText} ${weekday}`
      }
      else if (parts.isYesterday) {
        title = t('accountSettings.footprint.yesterday')
        subtitle = `${dateText} ${weekday}`
      }
      else {
        title = dateText
        subtitle = weekday
      }
    }

    return {
      key: day.browseDate || String(dayIndex),
      title,
      subtitle,
      countText: items.length > 1 ? t('accountSettings.footprint.itemCount', { count: items.length }) : '',
      items,
    }
  }).filter(group => group.items.length > 0)
})

function openItem(item: FootprintItemView) {
  if (!item.available || !item.spuId)
    return
  router.push({
    name: 'goods-detail',
    params: {
      id: item.spuId,
    },
  })
}

function toGoodsList() {
  router.push({ name: 'goods-list' })
}
</script>

<template>
  <!-- eslint-disable vue/singleline-html-element-content-newline -->
  <hr-navbar :title="t('accountSettings.footprint.navTitle')" />
  <z-paging
    ref="pagingRef"
    v-model="list"
    :auto="false"
    :refresher-default-text="t('catalog.refresherPull')"
    :refresher-pulling-text="t('catalog.refresherRelease')"
    :refresher-refreshing-text="t('catalog.refresherRefreshing')"
    :refresher-complete-text="t('catalog.refresherDone')"
    :loading-more-default-text="t('catalog.loadMore')"
    :loading-more-loading-text="t('common.loading')"
    :loading-more-fail-text="t('catalog.loadMoreFailed')"
    :loading-more-no-more-text="t('catalog.noMore')"
    @query="queryList"
    @scroll="handlePageScroll"
  >
    <!-- 顶栏覆盖层等高占位：列表整屏滚动，首屏内容从顶栏下沿开始 -->
    <hr-navbar-space />

    <template #empty>
      <view class="fp-empty">
        <view class="fp-empty__badge">
          <text class="i-carbon:time fp-empty__icon" />
        </view>
        <text class="fp-empty__title">{{ t('accountSettings.footprint.empty') }}</text>
        <text class="fp-empty__hint">{{ t('accountSettings.footprint.emptyHint') }}</text>
        <view class="fp-empty__btn" hover-class="fp-empty__btn--pressed" @click="toGoodsList">
          {{ t('accountSettings.footprint.goShopping') }}
        </view>
      </view>
    </template>

    <view v-if="dayGroups.length" class="fp-list">
      <view v-for="day in dayGroups" :key="day.key" class="fp-day">
        <view class="fp-day__head">
          <text class="fp-day__title">{{ day.title }}</text>
          <text v-if="day.subtitle" class="fp-day__sub">{{ day.subtitle }}</text>
          <text v-if="day.countText" class="fp-day__count">{{ day.countText }}</text>
        </view>
        <view class="fp-card">
          <view
            v-for="(item, index) in day.items"
            :key="item.key"
            class="fp-item"
            :class="{ 'fp-item--divided': index > 0 }"
            :hover-class="item.available ? 'fp-item--pressed' : 'none'"
            :hover-stay-time="60"
            @click="openItem(item)"
          >
            <view class="fp-item__pic-wrap">
              <image v-if="item.pic" class="fp-item__pic" :src="item.pic" mode="aspectFill" />
              <view v-else class="fp-item__pic fp-item__pic--empty">
                <text class="i-carbon:image fp-item__pic-icon" />
              </view>
              <view v-if="item.soldOut" class="fp-item__mask">{{ t('labels.soldOut') }}</view>
            </view>
            <view class="fp-item__info">
              <text class="fp-item__name" :class="{ 'fp-item__name--unavailable': !item.available }">{{ item.name }}</text>
              <view v-if="item.price" class="fp-item__price">
                <text
                  v-if="item.badgeText"
                  class="fp-item__badge"
                  :class="`fp-item__badge--${item.badgeKind}`"
                >
                  {{ item.badgeText }}
                </text>
                <text class="fp-item__currency">¥</text>
                <text class="fp-item__amount">{{ item.price.integer }}</text>
                <text class="fp-item__decimal">.{{ item.price.decimal }}</text>
                <text v-if="item.strikePrice !== null" class="fp-item__original">¥{{ item.strikePrice }}</text>
              </view>
              <!-- 未达标/未登录：主价保持售价，只给会员引导（与列表卡同源文案） -->
              <text v-if="item.guideText" class="fp-item__guide">{{ item.guideText }}</text>
              <view class="fp-item__foot">
                <view class="fp-item__time">
                  <text class="i-carbon:time fp-item__time-icon" />
                  <text class="fp-item__time-text">{{ item.clock }}</text>
                </view>
                <text v-if="item.stockTag" class="fp-item__stock">{{ item.stockTag }}</text>
              </view>
            </view>
          </view>
        </view>
      </view>
    </view>
  </z-paging>
</template>

<style lang="scss" scoped>
@import '@/styles/goods-tag.scss';
$fp-brand: var(--theme-color-primary, var(--wot-color-theme-primary, #FF2237));
$fp-price: #fa2c19;
$fp-ink: var(--app-text);
$fp-muted: var(--app-text-3);
$fp-hairline: var(--app-border);

.fp-list {
  padding: 16rpx 24rpx calc(32rpx + env(safe-area-inset-bottom));
}

.fp-day {
  margin-bottom: 32rpx;
}

.fp-day__head {
  display: flex;
  align-items: baseline;
  padding: 0 4rpx 16rpx;
}

.fp-day__title {
  font-size: 32rpx;
  font-weight: 700;
  line-height: 44rpx;
  color: $fp-ink;
}

.fp-day__sub {
  margin-left: 12rpx;
  font-size: 24rpx;
  color: $fp-muted;
}

.fp-day__count {
  margin-left: auto;
  font-size: 22rpx;
  color: $fp-muted;
}

.fp-card {
  padding: 0 24rpx;
  background: var(--app-card);
  border-radius: 24rpx;
  box-shadow: 0 6rpx 24rpx rgba(17, 24, 39, 0.04);
}

.fp-item {
  display: flex;
  padding: 24rpx 0;
}

.fp-item--divided {
  border-top: 1rpx solid $fp-hairline;
}

.fp-item--pressed {
  background: var(--app-card-2);
}

.fp-item__pic-wrap {
  position: relative;
  width: 168rpx;
  height: 168rpx;
  flex: none;
}

.fp-item__pic {
  display: block;
  width: 168rpx;
  height: 168rpx;
  border-radius: 16rpx;
  background: var(--app-card-2);
}

.fp-item__pic--empty {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--app-text-3);
}

.fp-item__pic-icon {
  font-size: 48rpx;
}

.fp-item__mask {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 16rpx;
  background: rgba(17, 24, 39, 0.44);
  color: #fff;
  font-size: 22rpx;
  font-weight: 500;
}

.fp-item__info {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  margin-left: 20rpx;
}

.fp-item__name {
  display: -webkit-box;
  overflow: hidden;
  color: $fp-ink;
  font-size: 28rpx;
  font-weight: 500;
  line-height: 38rpx;
  word-break: break-all;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.fp-item__name--unavailable {
  color: $fp-muted;
}

.fp-item__price {
  display: flex;
  align-items: baseline;
  margin-top: 10rpx;
}

/* 身份价角标：与列表卡同一套颜色语义（会员金 / 捡漏红） */
.fp-item__badge {
  flex: none;
  margin-right: 8rpx;
  padding: 2rpx 8rpx;
  border-radius: 6rpx;
  color: #fff;
  font-size: 20rpx;
  font-weight: 500;
  line-height: 1.4;

  &--member {
    background: linear-gradient(135deg, #b8860b 0%, #e0a82e 100%);
  }

  &--clearance {
    @include goods-tag-clearance;
  }

  /* 新人/秒杀角标与主卡同一套颜色语义（此前缺分支=白字白底，视觉增强版补齐） */
  &--newbie {
    @include goods-tag-newbie;
  }

  &--seckill {
    background: linear-gradient(135deg, var(--wot-color-theme-primary, #ff2237) 0%, #ff6b45 100%);
  }
}

/* 未达标引导：价格行下方弱化展示 */
.fp-item__guide {
  margin-top: 4rpx;
  color: #b45309;
  font-size: 20rpx;
  line-height: 1.4;
}

.fp-item__currency {
  color: $fp-price;
  font-size: 22rpx;
  font-weight: 600;
}

.fp-item__amount {
  color: $fp-price;
  font-size: 34rpx;
  font-weight: 700;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}

.fp-item__decimal {
  color: $fp-price;
  font-size: 24rpx;
  font-weight: 600;
}

.fp-item__original {
  margin-left: 12rpx;
  color: var(--app-text-3);
  font-size: 22rpx;
  text-decoration: line-through;
}

.fp-item__foot {
  display: flex;
  align-items: center;
  margin-top: auto;
  padding-top: 12rpx;
}

.fp-item__time {
  display: flex;
  align-items: center;
  color: $fp-muted;
}

.fp-item__time-icon {
  font-size: 22rpx;
}

.fp-item__time-text {
  margin-left: 6rpx;
  color: $fp-muted;
  font-size: 22rpx;
}

.fp-item__stock {
  margin-left: auto;
  padding: 2rpx 12rpx;
  border-radius: 8rpx;
  background: #fff7e8;
  color: #b45309;
  font-size: 20rpx;
  line-height: 30rpx;
}

.fp-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 120rpx 64rpx 80rpx;
}

.fp-empty__badge {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 176rpx;
  height: 176rpx;
  border-radius: 50%;
  background: var(--app-card);
  box-shadow: 0 8rpx 24rpx rgba(17, 24, 39, 0.05);
  color: var(--app-text-3);
}

.fp-empty__icon {
  font-size: 72rpx;
}

.fp-empty__title {
  margin-top: 32rpx;
  color: $fp-ink;
  font-size: 30rpx;
  font-weight: 600;
}

.fp-empty__hint {
  margin-top: 12rpx;
  color: $fp-muted;
  font-size: 24rpx;
  line-height: 1.6;
  text-align: center;
}

.fp-empty__btn {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 80rpx;
  margin-top: 40rpx;
  padding: 0 56rpx;
  border-radius: 999rpx;
  background: $fp-brand;
  color: #fff;
  font-size: 28rpx;
  font-weight: 600;
}

.fp-empty__btn--pressed {
  opacity: 0.86;
}
</style>

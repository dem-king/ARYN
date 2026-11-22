<script setup lang="ts">
/**
 * 采购档期特供楼层（2026-11-14 船供 B2B 活动体系重构，2026-11-15 交互/视觉重构）。
 *
 * 数据（服务端同源口径）：取全部生效档期下进行中的档期特供活动（type=12，
 * 不指定 calendarId=跨档期聚合），服务端已按采购场景/船舶圈定过滤并剔除限量
 * 耗尽活动；特供商品带特供价/划线售价/活动级余量；无档期或无特供默认整楼隐藏
 * （emptyStrategy 默认 hide），不留空白占位。
 *
 * 交互（2026-11-15 修复）：卡片点击跳商详，spuId 由服务端按 SKU 回填；
 * 此前服务端漏回填 spuId，`onCardTap` 的空值守卫把整层点成「死链」。
 *
 * 视觉（2026-11-21 重构）：整层改为「档期票」形态，与普通商品卡拉开识别——
 * 楼层头是一块海军蓝档期牌（档期标 + 档期名 + 距结束 + 整场余量尺 + 查看全部），
 * 商品卡改为米白特供票（撕线 / 印章式档期特供 / 档期价行），一屏三张横滑。
 * 余量是「活动级共享池」而非单款库存，只在档期牌底部以一条余量尺出现一次，
 * 不再逐卡重复，避免被读成「每款各剩 80 件」。
 *
 * 样式与管理端 trade-calendar 预览逐值对齐（1rpx = 0.5px），改一端必须同步另一端。
 */
import type { TradeCalendarProps } from '@/components/diy/retail-types'
import { computed } from 'vue'

import { useI18n } from 'vue-i18n'
import { getCalendarSpecials } from '@/api/promotion/trade-calendar'
import { followDecorationLink } from '@/components/diy/link-resolver'
import RetailState from '@/components/diy/retail-state.vue'
import { retailCommonStyle } from '@/components/diy/retail-types'
import PriceText from '@/components/price-text/index.vue'
import { formatRemainingTime, useCountdownTicker } from '@/composables/useCountdown'
import { useDiyStyle } from '@/composables/useDiyStyle'
import { useRetailData } from '@/composables/useRetailData'
import { useShipContextStore } from '@/store/shipContextStore'
import { resolveImageSrc } from '@/utils/image'

const props = withDefaults(defineProps<{ showData?: Partial<TradeCalendarProps> }>(), {
  showData: () => ({}),
})
const { t } = useI18n()
const shipContextStore = useShipContextStore()
const badgeText = computed(() => t('diy.tradeCalendar.badge'))

const showData = computed<TradeCalendarProps>(() => ({
  commonStyle: props.showData.commonStyle || retailCommonStyle,
  count: Number(props.showData.count) || 10,
  dataSource: props.showData.dataSource || { mode: 'automatic' },
  emptyStrategy: props.showData.emptyStrategy || 'hide',
  // 接口免登：请求失败按「无数据」隐藏整楼，不走 invalid 占位
  invalidStrategy: props.showData.invalidStrategy || 'hide',
  // 存量装修 JSON 没有这两个字段：缺省放开入口、回落到内置会场页
  moreLink: props.showData.moreLink || null,
  showMore: props.showData.showMore !== false,
  title: props.showData.title || '',
}))
const dynamicStyles = useDiyStyle(computed(() => showData.value.commonStyle))

/** 内置档期特供会场页（与 pages.json 的 promotion/calendar-special/index 对应） */
const CALENDAR_SPECIAL_PAGE = '/sub-pages/promotion/calendar-special/index'

interface SpecialCard {
  activityId: string
  activityName: string
  /** 归属档期名（标题回退用；服务端回填，缺失为空串） */
  calendarName: string
  endTime: string
  /** 每人限购件数（件）；null/0=不限 */
  perUserLimit: null | number
  picUrl: string
  price: number
  /** 活动级剩余限量（件）；null=不限量 */
  remainingQuota: null | number
  /** 划线售价（严格大于特供价才展示） */
  salesPrice: number | null
  skuId: string
  spuId: string
  spuName: string
  /** 活动级限量（件）；null=不限量 */
  totalQuota: null | number
}

async function loadSpecialCards(source: TradeCalendarProps): Promise<SpecialCard[]> {
  // 不指定 calendarId：跨全部生效档期聚合特供（服务端已过滤档期窗/场景圈定/限量耗尽）
  const specials = await getCalendarSpecials({
    purchaseScene: shipContextStore.purchaseScene,
    vesselId: shipContextStore.vesselId || undefined,
  })
  const cards: SpecialCard[] = []
  for (const special of specials || []) {
    for (const item of special.items || []) {
      if (cards.length >= source.count)
        return cards
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
  || items.value[0]?.calendarName
  || items.value[0]?.activityName
  || t('diy.tradeCalendar.fallbackTitle'))

/** 进行中的特供活动数（同一活动多个 SKU 只算一场） */
const activityCount = computed(() => new Set(items.value.map(item => item.activityId)).size)

/**
 * 楼层副标题：单场显示活动名；多场并存时只报场次数。
 * 余量口径移到档期牌底部的余量尺，不再塞进副标题，避免一行小字过长。
 */
const subtitle = computed(() => {
  const first = items.value[0]
  if (!first)
    return ''
  if (activityCount.value > 1)
    return t('diy.tradeCalendar.multiActivity', { count: activityCount.value })
  return first.activityName && first.activityName !== floorTitle.value
    ? first.activityName
    : ''
})

/**
 * 档期牌底部余量尺：只在「单场活动」时展示（多场额度不可合并）；
 * 余量是活动级共享池，整层只出现一次。不限量活动只留文字，不出尺。
 */
const quotaLabel = computed(() => {
  const first = items.value[0]
  if (!first || activityCount.value > 1)
    return ''
  return first.totalQuota === null
    ? t('diy.tradeCalendar.quotaUnlimited')
    : t('diy.tradeCalendar.quotaTotal', { count: first.totalQuota })
})

const quotaRemainingText = computed(() => {
  const first = items.value[0]
  if (!first || activityCount.value > 1 || first.totalQuota === null || first.remainingQuota === null)
    return ''
  return t('diy.tradeCalendar.quotaLeft', { count: first.remainingQuota })
})

/** 余量尺填充按「剩余/总量」：开档满格，卖一件退一格，比进度条更符合备货语义 */
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

function onImageError(card: SpecialCard) {
  card.picUrl = ''
}

/** 「查看全部」目标：运营配置优先；未配置（含 custom 空路径）回落内置会场页 */
const moreTarget = computed(() => {
  const configured = showData.value.moreLink
  if (configured && (configured.type !== 'custom' || configured.path))
    return configured
  return { type: 'custom' as const, path: CALENDAR_SPECIAL_PAGE, params: {} }
})

const showMoreEntry = computed(() => showData.value.showMore !== false)

function openMore() {
  followDecorationLink(moreTarget.value)
}

/** 立省额：只在划线售价严格高于特供价时有意义，整数不带 .0 */
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

/** 点击卡片跳商详（spuId 缺失时不可点，避免跳到空详情） */
function onCardTap(card: SpecialCard) {
  if (!card.spuId)
    return
  followDecorationLink({ params: {}, path: '', targetId: card.spuId, type: 'goods' })
}
</script>

<template>
  <view v-if="shouldRender" class="trade-calendar" :style="dynamicStyles">
    <view v-if="status === 'ready'" class="tc-body">
      <view class="tc-board">
        <view class="tc-head-row">
          <view class="tc-mark">
            {{ t('diy.tradeCalendar.mark') }}
          </view>
          <text class="tc-title">
            {{ floorTitle }}
          </text>
        </view>
        <view v-if="subtitle || countdownText" class="tc-sub-row">
          <text v-if="subtitle" class="tc-sub">
            {{ subtitle }}
          </text>
          <view v-if="countdownText" class="tc-timer">
            <text class="tc-timer-label">
              {{ t('diy.tradeCalendar.endsIn') }}
            </text>
            <text class="tc-timer-value">
              {{ countdownText }}
            </text>
          </view>
        </view>
        <view v-if="quotaLabel || showMoreEntry" class="tc-cut" />
        <view v-if="quotaLabel || showMoreEntry" class="tc-foot">
          <text v-if="quotaLabel" class="tc-gauge-label">
            {{ quotaLabel }}
          </text>
          <view v-if="showQuotaBar" class="tc-gauge">
            <view class="tc-gauge-fill" :style="{ width: `${quotaPercent}%` }" />
          </view>
          <text v-if="quotaRemainingText" class="tc-gauge-left">
            {{ quotaRemainingText }}
          </text>
          <view v-if="showMoreEntry" class="tc-more" @click.stop="openMore">
            <text>{{ t('diy.tradeCalendar.viewAll') }}</text>
            <wd-icon name="arrow-right" size="18rpx" />
          </view>
        </view>
      </view>
      <scroll-view class="tc-scroll" scroll-x :show-scrollbar="false">
        <view class="tc-track">
          <view
            v-for="card in items"
            :key="`${card.activityId}-${card.skuId}`"
            class="tc-card"
            @click="onCardTap(card)"
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
              <text class="tc-stamp">
                {{ badgeText }}
              </text>
            </view>
            <view class="tc-info">
              <view class="tc-name">
                {{ card.spuName }}
              </view>
              <view class="tc-price-row">
                <price-text class="tc-price" :value="card.price" format="trim" />
                <text v-if="card.salesPrice" class="tc-strike">
                  ¥{{ card.salesPrice }}
                </text>
              </view>
              <view class="tc-perf" />
              <view class="tc-meta">
                <text v-if="saveAmountText(card)" class="tc-save">
                  {{ saveAmountText(card) }}
                </text>
                <text class="tc-limit">
                  {{ limitText(card) }}
                </text>
              </view>
            </view>
          </view>
        </view>
      </scroll-view>
    </view>
    <RetailState v-else :status="status" />
  </view>
</template>

<!--
  与管理端 page-design/components/trade-calendar 预览逐值对齐（1rpx = 0.5px）。
  · 档期牌：外距 20rpx、内距 24rpx、圆角 28rpx；档期标 34rpx / 档期名 30rpx /
    距结束 22rpx / 副标题 20rpx；底部余量尺 8rpx、查看全部 20rpx。
  · 卡片：一屏三张 220rpx 宽、圆角 16rpx、图区 220rpx 方框（contain，不裁商品）；
  · 商品名固定两行高 64rpx，价行 40rpx、限购行 32rpx，同排卡片纵向齐平。
-->
<style scoped lang="scss">
.tc-body { padding: 16rpx 0 6rpx; }

/* ========== 档期牌：海军蓝底承担「这是当期档期」的识别 ========== */
.tc-board { overflow: hidden; margin: 0 20rpx; padding: 24rpx 24rpx 20rpx; border-radius: 28rpx; background: linear-gradient(145deg, #0d2337 0%, #173a52 62%, #1c455f 100%); box-shadow: 0 16rpx 44rpx rgb(13 35 55 / 22%); }
.tc-head-row { display: flex; align-items: center; gap: 12rpx; }
/* 档期标：品牌红印章落在深蓝底上，小面积高对比，不铺整块底色 */
.tc-mark { display: flex; width: 34rpx; height: 34rpx; flex: 0 0 auto; align-items: center; justify-content: center; border-radius: 8rpx; box-shadow: 0 4rpx 12rpx rgb(255 34 55 / 35%); background: linear-gradient(135deg, var(--wot-color-theme-secondary, #ff6b7a), var(--wot-color-theme-primary, #ff2237)); color: #fff; font-size: 19rpx; font-weight: 700; line-height: 1; }
.tc-title { overflow: hidden; min-width: 0; flex: 1; color: #fff; font-size: 30rpx; font-weight: 700; letter-spacing: 0.4rpx; text-overflow: ellipsis; white-space: nowrap; }
/* 第二行：活动名留给左，倒计时贴右，深蓝底上只保留一个暖色数字焦点 */
.tc-sub-row { display: flex; align-items: baseline; gap: 16rpx; margin-top: 12rpx; }
.tc-sub { overflow: hidden; min-width: 0; flex: 1; color: rgb(255 255 255 / 62%); font-size: 20rpx; line-height: 1.4; text-overflow: ellipsis; white-space: nowrap; }
.tc-timer { display: flex; flex: 0 0 auto; align-items: baseline; gap: 6rpx; margin-left: auto; font-variant-numeric: tabular-nums; }
.tc-timer-label { color: rgb(255 255 255 / 52%); font-size: 20rpx; }
.tc-timer-value { color: #ffc46b; font-size: 22rpx; font-weight: 600; letter-spacing: 0.2rpx; }
/* 票根撕线：虚线只做分隔，不做额外装饰，避免深色块里叠太多纹理 */
.tc-cut { margin: 18rpx -24rpx 16rpx; border-top: 1rpx dashed rgb(255 255 255 / 22%); }
/* 底部一行：整场限量 + 余量尺 + 剩余件数 + 会场入口 */
.tc-foot { display: flex; align-items: center; gap: 12rpx; }
.tc-gauge-label { flex: 0 0 auto; color: rgb(255 255 255 / 62%); font-size: 18rpx; white-space: nowrap; }
.tc-gauge { overflow: hidden; min-width: 60rpx; height: 8rpx; flex: 1; border-radius: 999rpx; background: rgb(255 255 255 / 14%); }
.tc-gauge-fill { height: 100%; border-radius: 999rpx; background: linear-gradient(90deg, #ffd48a, #ffb347); }
.tc-gauge-left { flex: 0 0 auto; color: #ffc46b; font-size: 18rpx; font-weight: 600; font-variant-numeric: tabular-nums; white-space: nowrap; }
.tc-more { display: flex; flex: 0 0 auto; align-items: center; gap: 2rpx; margin: -8rpx 0 -8rpx auto; padding: 8rpx 0 8rpx 12rpx; color: #ffc46b; font-size: 20rpx; font-weight: 600; line-height: 1; }
.tc-scroll { width: 100%; margin-top: 20rpx; padding-bottom: 8rpx; }
/* 轨道显式给宽度：小程序块级 flex 量不到溢出内容时最后一张滑不全 */
.tc-track { display: flex; width: max-content; padding: 0 20rpx; gap: 16rpx; }

/* ========== 特供票：米白纸面 + 撕线 + 印章，脱离普通商品白卡 ========== */
.tc-card { display: flex; overflow: hidden; width: 220rpx; flex: 0 0 auto; flex-direction: column; border: 1rpx solid #f0e2d3; border-radius: 16rpx; background: #fffdf9; box-shadow: 0 6rpx 20rpx rgb(160 106 31 / 8%); }
.tc-figure { position: relative; height: 220rpx; flex: 0 0 auto; background: #fbf5ec; }
.tc-img { display: block; width: 100%; height: 100%; }
.tc-img-fallback { display: flex; height: 100%; align-items: center; justify-content: center; color: #c0b09e; font-size: 48rpx; }
/* 印章式角标：描边 + 微旋转，和普通商品卡的实心角标区分 */
.tc-stamp { position: absolute; top: 14rpx; left: 14rpx; transform: rotate(-7deg); padding: 4rpx 10rpx; border: 3rpx solid rgb(255 34 55 / 82%); border-radius: 6rpx; background: rgb(255 253 249 / 88%); color: #e02038; font-size: 16rpx; font-weight: 700; letter-spacing: 1.2rpx; line-height: 1.3; }
.tc-info { padding: 14rpx 16rpx 16rpx; }
/* 固定两行高：同一排卡片价格行必须齐平；两行 clamp 兜住超长商品名 */
.tc-name { display: -webkit-box; overflow: hidden; height: 64rpx; color: #2b2f36; font-size: 24rpx; font-weight: 500; line-height: 32rpx; overflow-wrap: break-word; white-space: normal; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.tc-price-row { display: flex; margin-top: 4rpx; height: 40rpx; align-items: baseline; gap: 8rpx; }
.tc-price { display: flex; overflow: hidden; min-width: 0; align-items: baseline; color: var(--wot-color-theme-primary, #ff2237); font-size: 32rpx; font-weight: 800; line-height: 1; white-space: nowrap; }
.tc-strike { flex: 0 0 auto; color: #a8abb2; font-size: 20rpx; text-decoration: line-through; }
/* 票根撕线：贯穿卡面、贴到左右边，强化「一张特供票」而不是普通商品卡 */
.tc-perf { margin: 12rpx -16rpx 10rpx; border-top: 1rpx dashed #eadccb; }
.tc-meta { display: flex; height: 32rpx; align-items: center; gap: 8rpx; }
.tc-save { flex: 0 0 auto; padding: 2rpx 10rpx; border-radius: 6rpx; background: #fff0ee; color: var(--wot-color-theme-primary, #ff2237); font-size: 18rpx; font-weight: 600; line-height: 1.5; }
.tc-limit { overflow: hidden; margin-left: auto; color: #8f959e; font-size: 18rpx; text-overflow: ellipsis; white-space: nowrap; }
</style>

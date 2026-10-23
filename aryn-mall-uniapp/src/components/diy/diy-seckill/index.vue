<script setup lang="ts">
import type { SeckillProps } from '@/components/diy/retail-types'

import { computed, ref, watch } from 'vue'

import { followDecorationLink } from '@/components/diy/link-resolver'
import { loadSeckillSessions } from '@/components/diy/retail-data'
import RetailState from '@/components/diy/retail-state.vue'
import { retailCommonStyle } from '@/components/diy/retail-types'
import { useCountdownTicker } from '@/composables/useCountdown'
import { useDiyStyle } from '@/composables/useDiyStyle'
import { useRetailData } from '@/composables/useRetailData'
import { resolveSeckillProgress } from '@/utils/seckill-progress'

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
  subtitle: props.showData.subtitle || '',
  title: props.showData.title || '限时秒杀',
}))
const dynamicStyles = useDiyStyle(computed(() => showData.value.commonStyle))
const { items, shouldRender, status } = useRetailData(showData, loadSeckillSessions)
const { now } = useCountdownTicker()

/** 场次切换：默认落在第一个「进行中」场次，没有则第一场（未开始） */
const activeIndex = ref(0)
watch(
  items,
  (list) => {
    const running = list.findIndex(session => session.status === 1)
    activeIndex.value = running >= 0 ? running : 0
  },
  { immediate: true },
)
const activeSession = computed(() => items.value[activeIndex.value])
const activeGoods = computed(() => activeSession.value?.goodsList ?? [])
const activePending = computed(() => (activeSession.value?.status ?? 0) !== 1)
const activeCta = computed(() => (activeSession.value ? ctaText(activeSession.value) : ''))

const CARD_WIDTH_RPX = 280
const CARD_GAP_RPX = 16

/** scroll-view 的滚动宽度是 px：按 windowWidth/750 把 rpx 换算成 px */
function rpxToPx(rpx: number) {
  return (rpx * uni.getSystemInfoSync().windowWidth) / 750
}

/**
 * 轨道显式宽度：小程序里块级 flex 轨道的宽度默认只有 scroll-view 那么宽，
 * 原生滚动区量不到溢出内容，最后一张卡会滑不全（新人专享楼层曾复现同样问题）。
 * CSS 侧保留 `width: max-content`，这里再按卡片数下发 px 宽度兜底。
 */
const trackStyle = computed(() => {
  const len = activeGoods.value.length
  const widthRpx = len * CARD_WIDTH_RPX + Math.max(0, len - 1) * CARD_GAP_RPX
  return { width: `${rpxToPx(widthRpx)}px` }
})

const DEFAULT_SUBTITLE = '整点开抢 · 限量特价，手慢无'
const subtitle = computed(() => showData.value.subtitle.trim() || DEFAULT_SUBTITLE)

/** 后端时间串兼容 `yyyy-MM-dd HH:mm:ss`（iOS 直接 Date.parse 会 NaN） */
function parseTime(value: string) {
  const direct = Date.parse(value)
  if (!Number.isNaN(direct))
    return direct
  return Date.parse(value.replace(' ', 'T'))
}

/** 倒计时拆成「天 + HH:MM:SS」，供头部深色分格渲染 */
function remainingParts(targetText: string, sessionStatus: number) {
  if (sessionStatus === 2)
    return null
  const target = parseTime(targetText)
  if (Number.isNaN(target))
    return null
  const totalSeconds = Math.max(0, Math.floor((target - now.value) / 1000))
  if (totalSeconds <= 0)
    return null
  return {
    days: Math.floor(totalSeconds / 86400),
    hours: Math.floor((totalSeconds % 86400) / 3600),
    minutes: Math.floor((totalSeconds % 3600) / 60),
    seconds: totalSeconds % 60,
  }
}

const countdown = computed(() => {
  const session = activeSession.value
  if (!session)
    return null
  const target = session.status === 1 ? session.endTime : (session.startTime ?? '')
  const parts = remainingParts(target, session.status)
  if (!parts)
    return null
  return {
    ...parts,
    label: session.status === 1 ? '距结束' : '距开始',
  }
})

function pad(value: number) {
  return String(value).padStart(2, '0')
}

/** 场次 Tab 小字：进行中 / 开抢时间 / 已结束 */
function tabHint(session: { startTime: string, status: number }) {
  if (session.status === 1)
    return '进行中'
  if (session.status === 2)
    return '已结束'
  const start = parseTime(session.startTime)
  if (Number.isNaN(start))
    return '即将开始'
  const date = new Date(start)
  return `${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/** 省额：原价严格大于秒杀价才展示（原价缺失/为 0 时不划假线） */
function savings(goods: { originalPrice: number, seckillPrice: number }) {
  if (goods.originalPrice <= goods.seckillPrice)
    return ''
  return (goods.originalPrice - goods.seckillPrice).toFixed(2)
}

/**
 * 已售进度：判定规则与会场页、管理端预览共用 utils/seckill-progress，
 * 楼层卡片窄，只取短文案（「已抢 N%」/「仅剩 N 件」/「限量 N 件」/「已抢光」）。
 */
function soldInfo(goods: { remainingStock: number, seckillStock: number }) {
  const progress = resolveSeckillProgress(goods.seckillStock, goods.remainingStock)
  if (!progress)
    return null
  return { low: progress.low, pct: progress.pct, text: progress.shortText }
}

/** 场次级 CTA 状态：未开始只做提示，已结束置灰 */
function ctaText(session: { status: number }) {
  if (session.status === 0)
    return '即将开始'
  if (session.status === 2)
    return '本场已结束'
  return '马上抢'
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
    <view class="promo-head">
      <view class="promo-icon">
        <text class="promo-icon-glyph">秒</text>
      </view>
      <view class="promo-text">
        <text class="promo-title">{{ showData.title }}</text>
        <text class="promo-sub">{{ subtitle }}</text>
      </view>
      <view v-if="showData.showCountdown && countdown" class="promo-cd">
        <text class="cd-label">
          {{ countdown.label }}<template v-if="countdown.days > 0"> {{ countdown.days }}天</template>
        </text>
        <view class="cd-blocks">
          <text class="cd-block">{{ pad(countdown.hours) }}</text>
          <text class="cd-colon">:</text>
          <text class="cd-block">{{ pad(countdown.minutes) }}</text>
          <text class="cd-colon">:</text>
          <text class="cd-block">{{ pad(countdown.seconds) }}</text>
        </view>
      </view>
    </view>

    <scroll-view
      v-if="items.length > 1"
      class="sk-tabs"
      :show-scrollbar="false"
      scroll-x
    >
      <view class="sk-tabs-track">
        <view
          v-for="(session, index) in items"
          :key="session.sessionId"
          class="sk-tab"
          :class="{ 'is-on': index === activeIndex }"
          @click="activeIndex = index"
        >
          <text class="sk-tab-name">
            {{ session.sessionName || '秒杀场次' }}
          </text>
          <text class="sk-tab-hint">
            {{ tabHint(session) }}
          </text>
        </view>
      </view>
    </scroll-view>

    <RetailState v-if="status !== 'ready'" :status="status" />
    <template v-else>
      <scroll-view
        v-if="activeGoods.length > 0"
        class="sk-scroll"
        :show-scrollbar="false"
        scroll-x
      >
        <view class="sk-track" :style="trackStyle">
          <view
            v-for="goods in activeGoods"
            :key="goods.skuId"
            class="sk-card"
            @click="openGoods(goods.spuId, goods.skuId)"
          >
            <view class="sk-media">
              <image
                v-if="goods.goodsImage"
                class="sk-img"
                :src="resolveImageSrc(goods.goodsImage)"
                mode="aspectFill"
                lazy-load
              />
              <view v-else class="sk-img sk-img--empty">
                秒杀
              </view>
              <text v-if="savings(goods)" class="sk-off">省¥{{ savings(goods) }}</text>
            </view>
            <view class="sk-body">
              <view class="sk-name">
                {{ goods.goodsName || '秒杀商品' }}
              </view>
              <view v-if="showData.showProgress && soldInfo(goods)" class="sk-progress">
                <view class="sk-track-bar">
                  <view class="sk-bar" :style="{ width: `${soldInfo(goods)?.pct ?? 0}%` }" />
                </view>
                <text class="sk-pct" :class="{ 'is-low': soldInfo(goods)?.low }">
                  {{ soldInfo(goods)?.text }}
                </text>
              </view>
              <view class="sk-price">
                <text class="sk-sym">¥</text>
                <text class="sk-val">{{ goods.seckillPrice.toFixed(2) }}</text>
                <text v-if="goods.originalPrice > goods.seckillPrice" class="sk-orig">
                  ¥{{ goods.originalPrice.toFixed(2) }}
                </text>
              </view>
              <view class="sk-btn" :class="{ 'is-pending': activePending }">
                {{ activeCta }}
              </view>
            </view>
          </view>
        </view>
      </scroll-view>
      <view v-else class="sk-empty">
        本场暂无秒杀商品
      </view>
    </template>
  </view>
</template>

<!--
  样式与管理端 seckill/index.vue 逐值对齐（rpx = px × 2）。
  2026-10-22 重设计：品牌渐变头 + 深色分格倒计时 + 场次 Tab + 横滑卡 + 双态进度。
  改任一端必须同步另一端，并由 preview-parity.test.ts 守卫。
-->
<style scoped lang="scss">
.promo-head { position: relative; display: flex; height: 112rpx; padding: 0 24rpx; gap: 16rpx; align-items: center; overflow: hidden; color: #fff; border-radius: 20rpx; box-shadow: 0 12rpx 32rpx rgb(15 23 42 / 10%); background: linear-gradient(180deg, rgb(0 0 0 / 5%), rgb(0 0 0 / 14%)), radial-gradient(120% 180% at 100% 0%, rgb(255 255 255 / 26%), rgb(255 255 255 / 0%) 56%), linear-gradient(118deg, var(--wot-color-theme-secondary, #ff6b7a) 0%, var(--wot-color-theme-primary, #ff2237) 58%, var(--wot-color-theme-primary, #ff2237) 100%); }
.promo-head::after { position: absolute; top: -80rpx; right: -52rpx; width: 216rpx; height: 216rpx; content: ''; border: 1rpx solid rgb(255 255 255 / 20%); border-radius: 50%; pointer-events: none; }
.promo-head::before { position: absolute; top: -32rpx; right: -8rpx; width: 112rpx; height: 112rpx; content: ''; border: 1rpx solid rgb(255 255 255 / 13%); border-radius: 50%; pointer-events: none; }
.promo-icon { z-index: 1; display: flex; width: 54rpx; height: 54rpx; flex: 0 0 auto; align-items: center; justify-content: center; background: rgb(255 255 255 / 22%); border-radius: 16rpx; }
.promo-icon-glyph { color: #fff; font-size: 30rpx; font-weight: 700; line-height: 1; }
.promo-text { z-index: 1; display: flex; overflow: hidden; min-width: 0; flex: 1; flex-direction: column; }
.promo-title { overflow: hidden; color: #fff; font-size: 38rpx; font-weight: 800; line-height: 1.1; text-overflow: ellipsis; white-space: nowrap; }
.promo-sub { margin-top: 6rpx; overflow: hidden; color: rgb(255 255 255 / 82%); font-size: 19rpx; font-weight: 500; text-overflow: ellipsis; white-space: nowrap; }
.promo-cd { z-index: 1; display: flex; flex: 0 0 auto; flex-direction: column; gap: 4rpx; align-items: flex-end; }
.cd-label { color: rgb(255 255 255 / 82%); font-size: 16rpx; font-weight: 500; }
.cd-blocks { display: flex; gap: 4rpx; align-items: center; }
.cd-block { display: flex; min-width: 40rpx; height: 42rpx; padding: 0 6rpx; align-items: center; justify-content: center; background: #1f2329; border-radius: 8rpx; color: #fff; font-size: 23rpx; font-weight: 700; font-variant-numeric: tabular-nums; }
.cd-colon { color: rgb(255 255 255 / 85%); font-size: 20rpx; font-weight: 700; }
.sk-tabs { width: 100%; margin-top: 20rpx; white-space: nowrap; }
.sk-tabs-track { display: inline-flex; gap: 16rpx; padding-bottom: 2rpx; }
.sk-tab { display: flex; height: 60rpx; padding: 0 26rpx; flex: 0 0 auto; align-items: center; background: #f5f6f8; border-radius: 999rpx; gap: 8rpx; }
.sk-tab.is-on { background: linear-gradient(135deg, var(--wot-color-theme-secondary, #ff6b7a), var(--wot-color-theme-primary, #ff2237)); box-shadow: 0 6rpx 16rpx rgb(15 23 42 / 14%); }
.sk-tab-name { color: #6b7280; font-size: 24rpx; font-weight: 600; }
.sk-tab-hint { color: #8a8f99; font-size: 19rpx; font-weight: 600; }
.sk-tab.is-on .sk-tab-name { color: #fff; }
.sk-tab.is-on .sk-tab-hint { color: rgb(255 255 255 / 85%); }
.sk-scroll { width: 100%; margin-top: 20rpx; white-space: nowrap; }
.sk-track { display: flex; width: max-content; padding-bottom: 4rpx; gap: 16rpx; }
.sk-card { display: flex; overflow: hidden; width: 280rpx; flex: 0 0 auto; flex-direction: column; background: #fff; border: 1rpx solid #f1f2f4; border-radius: 20rpx; box-shadow: 0 8rpx 24rpx rgb(15 23 42 / 6%); }
.sk-media { position: relative; }
.sk-img { display: flex; width: 280rpx; height: 280rpx; align-items: center; justify-content: center; background: #f3f4f6; color: var(--wot-color-theme-primary, #ff2237); font-size: 22rpx; }
.sk-img--empty { font-size: 22rpx; }
.sk-off { position: absolute; top: 12rpx; left: 12rpx; display: flex; height: 36rpx; padding: 0 12rpx; align-items: center; background: linear-gradient(135deg, var(--wot-color-theme-secondary, #ff6b7a), var(--wot-color-theme-primary, #ff2237)); border-radius: 999rpx; color: #fff; font-size: 19rpx; font-weight: 700; }
.sk-body { padding: 16rpx 18rpx 20rpx; }
.sk-name { display: -webkit-box; overflow: hidden; height: 68rpx; color: #1f2329; font-size: 25rpx; font-weight: 500; line-height: 34rpx; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.sk-progress { display: flex; margin-top: 12rpx; align-items: center; gap: 10rpx; }
.sk-track-bar { overflow: hidden; height: 12rpx; flex: 1; background: var(--wot-color-theme-background, #fff1ee); border-radius: 999rpx; }
.sk-bar { height: 100%; background: linear-gradient(90deg, var(--wot-color-theme-secondary, #ff6b7a), var(--wot-color-theme-primary, #ff2237)); border-radius: 999rpx; }
.sk-pct { flex: 0 0 auto; color: var(--wot-color-theme-primary, #ff2237); font-size: 19rpx; font-weight: 600; font-variant-numeric: tabular-nums; }
.sk-pct.is-low { font-weight: 700; }
.sk-price { display: flex; margin-top: 12rpx; align-items: baseline; gap: 8rpx; white-space: nowrap; }
.sk-sym { color: var(--wot-color-theme-primary, #ff2237); font-size: 22rpx; font-weight: 700; }
.sk-val { color: var(--wot-color-theme-primary, #ff2237); font-size: 42rpx; font-weight: 800; letter-spacing: -0.8rpx; line-height: 1; }
.sk-orig { color: #b6b9c0; font-size: 20rpx; text-decoration: line-through; }
.sk-btn { display: flex; height: 56rpx; margin-top: 16rpx; align-items: center; justify-content: center; background: linear-gradient(135deg, var(--wot-color-theme-secondary, #ff6b7a), var(--wot-color-theme-primary, #ff2237)); border-radius: 999rpx; color: #fff; font-size: 24rpx; font-weight: 700; letter-spacing: 0.8rpx; }
.sk-btn.is-pending { background: var(--wot-color-theme-background, #fff1ee); color: var(--wot-color-theme-primary, #ff2237); }
.sk-empty { padding: 32rpx 0; color: #909399; font-size: 24rpx; text-align: center; }
</style>

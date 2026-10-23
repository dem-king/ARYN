<script setup lang="ts">
import type { DiscountGoodsItem, DiscountProps } from '@/components/diy/retail-types'

import { computed } from 'vue'

import { followDecorationLink } from '@/components/diy/link-resolver'
import { loadDiscounts } from '@/components/diy/retail-data'
import RetailState from '@/components/diy/retail-state.vue'
import { retailCommonStyle } from '@/components/diy/retail-types'
import { formatRemainingTime, useCountdownTicker } from '@/composables/useCountdown'
import { useDiyStyle } from '@/composables/useDiyStyle'
import { useRetailData } from '@/composables/useRetailData'
import { navigateToUrl } from '@/utils/tab-bar'

/** 折扣会场页：全场型活动的固定落点（非 tabBar 页，走 navigateTo） */
const DISCOUNT_HALL_PATH = '/pages/promotion/discount'
/** 楼层头副标题默认文案：运营未配置时使用 */
const DEFAULT_SUBTITLE = '限时特惠 · 折扣价直接买'

const props = withDefaults(defineProps<{ showData?: Partial<DiscountProps> }>(), {
  showData: () => ({}),
})

/** 每活动商品数：仅 1-8 的整数生效，其余（缺省/越界/小数）统一回落 4 */
function normalizeGoodsLimit(value: unknown) {
  const limit = Number(value)
  return Number.isInteger(limit) && limit >= 1 && limit <= 8 ? limit : 4
}
const showData = computed<DiscountProps>(() => ({
  commonStyle: props.showData.commonStyle || retailCommonStyle,
  count: Number(props.showData.count) || 1,
  dataSource: props.showData.dataSource || { mode: 'automatic' },
  emptyStrategy: props.showData.emptyStrategy || 'placeholder',
  // 1-8 的整数才生效；存量页面缺字段/非法值统一回落默认 4（与画布预览同口径，不做 clamp）
  goodsLimit: normalizeGoodsLimit(props.showData.goodsLimit),
  invalidStrategy: props.showData.invalidStrategy || 'hide',
  showCountdown: props.showData.showCountdown !== false,
  subtitle: props.showData.subtitle || '',
  title: props.showData.title || '限时折扣',
}))
const dynamicStyles = useDiyStyle(computed(() => showData.value.commonStyle))
const { items, shouldRender, status } = useRetailData(showData, loadDiscounts)
const { now } = useCountdownTicker()

/** 楼层头副标题：空串回落到模块默认文案 */
const subtitleText = computed(() => showData.value.subtitle.trim() || DEFAULT_SUBTITLE)

/**
 * 折扣文案。打折 0.8 → 8折、0.95 → 9.5折、0.88 → 8.8折
 * （保留一位小数，整数去掉 .0）；减价给金额语义；固定价只表达「一口价」。
 */
function discountLabel(activity: { discountType: number, discountValue: number }) {
  switch (activity.discountType) {
    case 1: {
      const zhe = Math.round(activity.discountValue * 100) / 10
      return `${Number.isInteger(zhe) ? zhe : zhe.toFixed(1)}折`
    }
    case 2:
      return `减¥${activity.discountValue}`
    case 3:
      return '一口价'
    default:
      return '折扣'
  }
}

/** 形态 A：全场型（scope=1 且没有商品清单），用权益卡而不是灰字解释空清单 */
function isFullScope(activity: { goodsList: readonly unknown[], scope: number }) {
  return activity.scope === 1 && activity.goodsList.length === 0
}

/** 形态 A 说明：打折活动换算出等价的「下单立减 X%」，其余类型不编造比例 */
function fullDesc(activity: { discountType: number, discountValue: number }) {
  if (activity.discountType !== 1)
    return '全场商品参与 · 下单立减'
  const percent = Math.round((1 - activity.discountValue) * 100)
  return `全场商品参与 · 下单立减 ${percent}%`
}

/** 商品型活动按 goodsLimit 截断，避免一次渲染整份清单把楼层撑得过高 */
function goodsOf(activity: { goodsList: readonly DiscountGoodsItem[] }) {
  return activity.goodsList.slice(0, showData.value.goodsLimit)
}

/** 原价严格大于折后价才划线；相等或缺失时原价与省额整块隐藏 */
function hasOriginalPrice(goods: DiscountGoodsItem) {
  return goods.originalPrice > goods.discountPrice
}

function savedLabel(goods: DiscountGoodsItem) {
  const diff = goods.originalPrice - goods.discountPrice
  // 与会场页同口径：价差 ≤0 时整块隐藏，不出现「省¥0.00」
  if (!Number.isFinite(diff) || diff <= 0)
    return ''
  return `省¥${diff.toFixed(2)}`
}

/** 后端时间串兼容 `yyyy-MM-dd HH:mm:ss`（部分小程序内核直接 Date.parse 会 NaN） */
function toTimestamp(value: string) {
  if (!value)
    return Number.NaN
  const direct = Date.parse(value)
  if (!Number.isNaN(direct))
    return direct
  return Date.parse(value.replace(' ', 'T'))
}

/** 头部倒计时取「仍在进行中的活动里最早的结束时间」，全部结束显示「已结束」 */
const headerCountdown = computed(() => {
  if (items.value.length === 0)
    return ''
  const targets = items.value
    .map(activity => toTimestamp(activity.endTime))
    .filter(target => !Number.isNaN(target) && target > now.value)
  if (targets.length === 0)
    return '已结束'
  const remaining = formatRemainingTime(new Date(Math.min(...targets)).toISOString(), now.value)
  return remaining || '已结束'
})

/** 活动头倒计时：该活动自身的结束时间（已结束状态直接给结束文案） */
function blockCountdown(activity: { endTime: string, status?: number }) {
  if (activity.status === 2)
    return '已结束'
  const target = toTimestamp(activity.endTime)
  if (Number.isNaN(target))
    return '已结束'
  const text = formatRemainingTime(new Date(target).toISOString(), now.value)
  return text ? `剩 ${text}` : '已结束'
}

/** 全场型权益卡整卡与「去逛逛」都去折扣会场页 */
function openDiscountHall() {
  navigateToUrl(DISCOUNT_HALL_PATH)
}

function openGoods(spuId: string) {
  followDecorationLink({ params: {}, path: '', targetId: spuId, type: 'goods' })
}
</script>

<template>
  <view v-if="shouldRender" class="discount" :style="dynamicStyles">
    <!-- 楼层头（轻促销）：浅底渐变 + 主色字符图标 + 内联倒计时 -->
    <view class="promo-head--light">
      <view class="promo-icon">
        <text class="promo-icon-glyph">
          折
        </text>
      </view>
      <view class="promo-text">
        <text class="promo-title">
          {{ showData.title }}
        </text>
        <text class="promo-sub">
          {{ subtitleText }}
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
    <template v-else>
      <view v-for="activity in items" :key="activity.activityId">
        <!-- 形态 A：全场型活动 = 折扣大牌权益卡（整卡可点，进折扣会场） -->
        <view v-if="isFullScope(activity)" class="dc-full" @click="openDiscountHall">
          <view class="dc-ratio">
            <text class="dc-ratio-value">
              {{ discountLabel(activity) }}
            </text>
            <text class="dc-ratio-label">
              全场
            </text>
          </view>
          <view class="dc-full-main">
            <text class="dc-full-name">
              {{ activity.activityName || '限时折扣' }}
            </text>
            <text class="dc-full-desc">
              {{ fullDesc(activity) }}
            </text>
          </view>
          <view class="dc-full-btn">
            去逛逛
          </view>
        </view>

        <!-- 形态 B：商品型活动 = 活动头小牌 + 双列 4:3 商品卡 -->
        <view v-else class="dc-block">
          <view class="dc-block-head">
            <text class="dc-ratio-tag">
              {{ discountLabel(activity) }}
            </text>
            <text class="dc-block-name">
              {{ activity.activityName || '限时折扣' }}
            </text>
            <text v-if="showData.showCountdown" class="dc-block-cd">
              {{ blockCountdown(activity) }}
            </text>
          </view>
          <view class="dc-grid">
            <view
              v-for="goods in goodsOf(activity)"
              :key="goods.skuId"
              class="dc-card"
              hover-class="dc-card--pressed"
              @click="openGoods(goods.spuId)"
            >
              <view class="dc-media">
                <image
                  v-if="goods.goodsImage"
                  class="dc-img"
                  :src="resolveImageSrc(goods.goodsImage)"
                  mode="aspectFill"
                  lazy-load
                />
                <view v-else class="dc-img dc-img--empty">
                  <text>折扣</text>
                </view>
                <text class="dc-off">
                  {{ discountLabel(activity) }}
                </text>
              </view>
              <view class="dc-body">
                <text class="dc-name">
                  {{ goods.goodsName || '折扣商品' }}
                </text>
                <view class="dc-price">
                  <text class="dc-price-sym">¥</text>
                  <text class="dc-price-val">
                    {{ goods.discountPrice.toFixed(2) }}
                  </text>
                  <text v-if="hasOriginalPrice(goods)" class="dc-original">
                    ¥{{ goods.originalPrice.toFixed(2) }}
                  </text>
                  <text v-if="hasOriginalPrice(goods)" class="dc-save">
                    {{ savedLabel(goods) }}
                  </text>
                </view>
              </view>
            </view>
          </view>
        </view>
      </view>
    </template>
  </view>
</template>

<!--
  样式与管理端 page-design/components/discount/index.vue 逐值对齐。
  换算口径：小程序屏宽在 rpx 下恒为 750，画布正好 375px，故 1rpx = 0.5px，
  本文件所有 rpx 值都是对应 px 值的两倍；改任一端都要同步另一端。
  色彩角色：价格/折扣大牌/小微标走主题主色 token，浅底与描边走
  --wot-color-theme-background（页面底色），标题/说明/划线价用固定中性色。
-->
<style scoped lang="scss">
/* ---- 楼层头（轻促销：浅底渐变 + 主色字符图标 + 内联倒计时） ---- */
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

/* 字符图标：UnoCSS carbon 图标是 background-image 模式、无法染白，
   两端统一用汉字字符，管理端画布与实机视觉一致 */
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
  align-items: center;
  box-sizing: border-box;
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

/* ---- 形态 A：全场型权益卡 ---- */
.dc-full {
  box-sizing: border-box;
  display: flex;
  align-items: center;
  gap: 20rpx;
  height: 160rpx;
  padding: 24rpx;
  margin-top: 20rpx;
  background: linear-gradient(
    105deg,
    var(--wot-color-theme-background, #fff1ee),
    #fff 78%
  );
  border: 1rpx solid var(--wot-color-theme-background, #fff1ee);
  border-radius: 20rpx;
}

/* 折扣力度即主视觉：112rpx 渐变大牌 */
.dc-ratio {
  display: flex;
  flex: 0 0 auto;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 112rpx;
  height: 112rpx;
  color: #fff;
  background: linear-gradient(
    135deg,
    var(--wot-color-theme-secondary, #ff6b7a),
    var(--wot-color-theme-primary, #ff2237)
  );
  border-radius: 20rpx;
  box-shadow: 0 8rpx 20rpx rgb(15 23 42 / 14%);
}

.dc-ratio-value {
  font-size: 42rpx;
  font-weight: 800;
  line-height: 1;
  letter-spacing: -1rpx;
}

.dc-ratio-label {
  margin-top: 4rpx;
  font-size: 18rpx;
  font-weight: 600;
  line-height: 1;
  opacity: 0.92;
}

.dc-full-main {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}

.dc-full-name {
  overflow: hidden;
  font-size: 26rpx;
  font-weight: 700;
  color: #1f2329;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dc-full-desc {
  margin-top: 8rpx;
  font-size: 20rpx;
  color: #8a8f99;
}

/* 折扣是轻促销：CTA 用弱按钮（白底 + 主色描边 + 主色字），视觉弱于秒杀实心 CTA */
.dc-full-btn {
  box-sizing: border-box;
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  height: 56rpx;
  padding: 0 24rpx;
  font-size: 23rpx;
  font-weight: 700;
  color: var(--wot-color-theme-primary, #ff2237);
  white-space: nowrap;
  background: #fff;
  border: 1rpx solid var(--wot-color-theme-primary, #ff2237);
  border-radius: 999rpx;
}

/* ---- 形态 B：活动头小牌 + 双列商品卡 ---- */
.dc-block {
  margin-top: 28rpx;
}

.dc-block-head {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 16rpx;
}

.dc-ratio-tag {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  height: 44rpx;
  padding: 0 16rpx;
  font-size: 24rpx;
  font-weight: 800;
  line-height: 1;
  color: #fff;
  letter-spacing: 0.4rpx;
  background: linear-gradient(
    135deg,
    var(--wot-color-theme-secondary, #ff6b7a),
    var(--wot-color-theme-primary, #ff2237)
  );
  border-radius: 10rpx;
}

.dc-block-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  font-size: 25rpx;
  font-weight: 600;
  color: #1f2329;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dc-block-cd {
  flex: 0 0 auto;
  font-size: 20rpx;
  font-weight: 600;
  color: var(--wot-color-theme-primary, #ff2237);
  font-variant-numeric: tabular-nums;
}

.dc-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20rpx;
}

.dc-card {
  overflow: hidden;
  background: #fff;
  border: 1rpx solid #f1f2f4;
  border-radius: 20rpx;
  box-shadow: 0 8rpx 24rpx rgb(15 23 42 / 5%);
}

/* 按压态：轻量底色反馈（不做缩放动画） */
.dc-card--pressed {
  background: #fafafa;
}

.dc-media {
  position: relative;
}

/* 小程序 <image> 必须显式写宽高：基座自带 height:240px，
   只给宽度会让图片竖着撑开整张卡片（H5 端按比例自适应，不暴露此问题） */
.dc-img {
  display: block;
  width: 100%;
  height: 257rpx;
  aspect-ratio: 4 / 3;
  object-fit: cover;
  background: #f3f4f6;
}

.dc-img--empty {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 40rpx;
  color: var(--wot-color-theme-primary, #ff2237);
}

.dc-off {
  position: absolute;
  top: 12rpx;
  left: 12rpx;
  display: flex;
  align-items: center;
  height: 38rpx;
  padding: 0 14rpx;
  font-size: 21rpx;
  font-weight: 800;
  color: #fff;
  letter-spacing: 0.4rpx;
  background: linear-gradient(
    135deg,
    var(--wot-color-theme-secondary, #ff6b7a),
    var(--wot-color-theme-primary, #ff2237)
  );
  border-radius: 12rpx;
  box-shadow: 0 4rpx 12rpx rgb(15 23 42 / 16%);
}

.dc-body {
  padding: 14rpx 18rpx 18rpx;
}

.dc-name {
  display: -webkit-box;
  height: 64rpx;
  overflow: hidden;
  font-size: 24rpx;
  font-weight: 500;
  line-height: 32rpx;
  color: #1f2329;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.dc-price {
  display: flex;
  align-items: baseline;
  gap: 8rpx;
  margin-top: 10rpx;
  white-space: nowrap;
}

.dc-price-sym {
  font-size: 20rpx;
  font-weight: 700;
  color: var(--wot-color-theme-primary, #ff2237);
}

.dc-price-val {
  font-size: 38rpx;
  font-weight: 800;
  line-height: 1;
  color: var(--wot-color-theme-primary, #ff2237);
  letter-spacing: -0.6rpx;
}

.dc-original {
  font-size: 20rpx;
  color: #b6b9c0;
  text-decoration: line-through;
}

.dc-save {
  padding: 4rpx 10rpx;
  margin-left: auto;
  font-size: 19rpx;
  font-weight: 600;
  color: var(--wot-color-theme-primary, #ff2237);
  background: var(--wot-color-theme-background, #fff1ee);
  border-radius: 8rpx;
}
</style>

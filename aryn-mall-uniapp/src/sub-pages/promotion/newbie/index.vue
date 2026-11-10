<script setup lang="ts">
import type { AppNewbieFloorVO } from '@/api/promotion'
import { onLoad } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { getCurrentNewbieFloor } from '@/api/promotion'
import { shouldShowSubtitle } from '@/components/diy/subtitle-display'
import PriceText from '@/components/price-text/index.vue'
import { formatRemainingTime, useCountdownTicker } from '@/composables/useCountdown'
import { useNewbieGoodsActions } from '@/composables/useNewbieGoodsActions'
import { navigateToUrl } from '@/utils/tab-bar'

definePage({
  name: 'newbie-zone',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '新人专享',
  },
})

const { t } = useI18n()

/**
 * 独立专享页一次取全活动商品。
 * 首页楼层受「展示商品数」限制，本页不受楼层配置影响；后端上限 200。
 */
const GOODS_LIMIT = 200

const activity = ref<AppNewbieFloorVO | null>(null)
const loading = ref(true)
const refreshing = ref(false)
/** 已登录老客：活动对他不可用，整页显示「仅限新人」阻断态（分享链接/直达入口仍需兜底） */
const ineligible = computed(() => activity.value?.eligible === false)
const goodsList = computed(() => (ineligible.value ? [] : (activity.value?.goodsList ?? [])))
const { now } = useCountdownTicker()

const {
  pending,
  cardMainPrice,
  cardStrikePrice,
  cardZoneBadgeText,
  handleGrab,
  loadZonePrices,
  savedAmount,
} = useNewbieGoodsActions({
  limitPerUser: () => Number(activity.value?.limitPerUser) || 1,
  openGoods,
})

onLoad(() => {
  void loadActivity()
})

async function loadActivity() {
  if (!activity.value)
    loading.value = true
  try {
    const vo = await getCurrentNewbieFloor(GOODS_LIMIT)
    activity.value = vo && vo.activityId ? vo : null
    // 老客没有可展示的商品（也不该再为不可用的价格发查价请求）
    if (!ineligible.value)
      loadZonePrices(goodsList.value)
  }
  catch {
    uni.showToast({ title: t('promotion.common.loadFailed'), icon: 'none' })
  }
  finally {
    loading.value = false
  }
}

async function onRefresh() {
  refreshing.value = true
  try {
    await loadActivity()
  }
  finally {
    refreshing.value = false
  }
}

const longTerm = computed(() => activity.value?.longTerm === true)

const countdown = computed(() => {
  const endTime = activity.value?.endTime
  // 长期有效：结束时间是远期哨兵，只展示「长期有效」标签，不做倒计时
  if (!endTime || longTerm.value)
    return null
  const remaining = formatRemainingTime(endTime, now.value)
  return remaining && remaining !== '00:00:00'
    ? { prefix: t('diy.newbieFloor.countdownPrefix'), text: remaining }
    : { prefix: t('diy.newbieFloor.countdownEndedPrefix'), text: t('diy.newbieFloor.countdownEndedText') }
})

const perksText = computed(() => {
  const limit = Number(activity.value?.limitPerUser) || 1
  const registerDays = Number(activity.value?.registerWithinDays) || 0
  return registerDays > 0
    ? t('diy.newbieFloor.perks', { limit, registerDays })
    : t('promotion.newbie.perksNoRegister', { limit })
})

function openGoods(spuId: string) {
  if (!spuId)
    return
  uni.navigateTo({ url: `/sub-pages/product/goods-detail/index?id=${spuId}` })
}

function goHome() {
  navigateToUrl('/pages/home/index')
}
</script>

<template>
  <view class="newbie-page">
    <hr-navbar :title="t('promotion.newbie.title')" />

    <scroll-view
      scroll-y
      class="page-scroll"
      refresher-enabled
      :refresher-triggered="refreshing"
      @refresherrefresh="onRefresh"
    >
      <!-- 首屏骨架：活动头卡 + 2 列 × 2 行商品卡，形状与真实内容一致 -->
      <template v-if="loading">
        <view class="sk-hero">
          <view class="sk-line sk-line--lg" />
          <view class="sk-line sk-line--md" />
          <view class="sk-line sk-line--sm" />
        </view>
        <view class="goods-grid">
          <view v-for="n in 4" :key="n" class="goods-card sk-card">
            <view class="sk-img" />
            <view class="sk-body">
              <view class="sk-line sk-line--lg" />
              <view class="sk-line sk-line--md" />
              <view class="sk-line sk-line--sm" />
            </view>
          </view>
        </view>
      </template>

      <hall-state
        v-else-if="!activity"
        art="group"
        :title="t('promotion.newbie.emptyTitle')"
        :desc="t('promotion.newbie.emptyDesc')"
        :action-text="t('promotion.common.goHome')"
        @action="goHome"
      />

      <!-- 已登录老客：活动仅限新人，直接给结论，不再展示不可用的活动价 -->
      <hall-state
        v-else-if="ineligible"
        art="group"
        :title="t('promotion.newbie.ineligibleTitle')"
        :desc="t('promotion.newbie.ineligibleDesc')"
        :action-text="t('promotion.common.goHome')"
        @action="goHome"
      />

      <template v-else>
        <view class="hero">
          <view class="hero-glow" />
          <view class="hero-top">
            <view class="hero-mark">
              {{ t('diy.newbieFloor.tag') }}
            </view>
            <view class="hero-copy">
              <view class="hero-title">
                {{ activity.activityName || t('promotion.newbie.title') }}
              </view>
              <view class="hero-sub">
                {{ t('diy.newbieFloor.subtitle') }}
              </view>
            </view>
            <view v-if="countdown" class="hero-cd">
              <text class="hero-cd-label">{{ countdown.prefix }}</text>
              <text class="hero-cd-time">{{ countdown.text }}</text>
            </view>
            <view v-else-if="longTerm" class="hero-cd">
              <text class="hero-cd-time">{{ t('promotion.newbie.longTerm') }}</text>
            </view>
          </view>
          <view class="hero-perks">
            {{ perksText }}
          </view>
          <view class="hero-foot">
            <text class="hero-count">{{ t('promotion.newbie.goodsCount', { count: goodsList.length }) }}</text>
            <text class="hero-hint">{{ t('promotion.newbie.ruleHint') }}</text>
          </view>
        </view>

        <view class="goods-grid">
          <view
            v-for="goods in goodsList"
            :key="goods.skuId"
            class="goods-card"
            hover-class="goods-card--pressed"
            :hover-stay-time="120"
            @click="openGoods(goods.spuId)"
          >
            <view class="goods-media">
              <text v-if="cardZoneBadgeText(goods)" class="goods-badge">
                {{ cardZoneBadgeText(goods) }}
              </text>
              <image
                v-if="goods.goodsImage"
                class="goods-img"
                :src="resolveImageSrc(goods.goodsImage)"
                mode="aspectFill"
                lazy-load
              />
              <view v-else class="goods-img goods-img--empty">
                {{ t('diy.common.product') }}
              </view>
            </view>

            <view class="goods-body">
              <view class="goods-name">
                {{ goods.goodsName || t('diy.newbieFloor.fallbackGoodsName') }}
              </view>
              <view v-if="shouldShowSubtitle(goods.goodsName, goods.subTitle)" class="goods-sub">
                {{ goods.subTitle }}
              </view>
              <view class="goods-price-row">
                <price-text class="goods-price" :value="cardMainPrice(goods)" />
                <text v-if="cardStrikePrice(goods) !== null" class="goods-strike">
                  ¥{{ Number(cardStrikePrice(goods)).toFixed(2) }}
                </text>
              </view>
              <view class="goods-bottom">
                <text v-if="savedAmount(goods) > 0" class="goods-save">
                  {{ t('diy.common.saveAmount', { amount: savedAmount(goods).toFixed(2) }) }}
                </text>
                <view
                  class="goods-grab"
                  :class="{ 'goods-grab--pending': pending }"
                  hover-class="goods-grab--pressed"
                  :hover-stay-time="80"
                  @click.stop="handleGrab(goods)"
                >
                  {{ t('diy.common.grab') }}
                </view>
              </view>
            </view>
          </view>
        </view>

        <view class="page-foot">
          {{ t('promotion.newbie.footNote') }}
        </view>
      </template>
    </scroll-view>
  </view>
</template>

<style lang="scss" scoped>
.newbie-page {
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

/* ---- 活动头卡：白底 + 主色淡光晕，价格/倒计时走全站价格色 ---- */
.hero {
  position: relative;
  overflow: hidden;
  margin: 20rpx 24rpx;
  padding: 26rpx 24rpx 20rpx;
  background: var(--app-card);
  border-radius: 24rpx;
}

.hero-glow {
  position: absolute;
  top: -120rpx;
  right: -80rpx;
  width: 320rpx;
  height: 320rpx;
  background: radial-gradient(circle, var(--wot-color-theme-primary, #ff2237) 0%, rgb(255 255 255 / 0%) 68%);
  opacity: 0.12;
  pointer-events: none;
}

.hero-top {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 14rpx;
}

.hero-mark {
  display: flex;
  width: 44rpx;
  height: 44rpx;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--wot-color-theme-secondary, #ff7a45), var(--wot-color-theme-primary, #ff4d4f));
  border-radius: 10rpx;
  color: #fff;
  font-size: 26rpx;
  font-weight: 700;
  line-height: 1;
}

.hero-copy {
  overflow: hidden;
  min-width: 0;
  flex: 1;
}

.hero-title {
  overflow: hidden;
  color: var(--app-text);
  font-size: 34rpx;
  font-weight: 700;
  letter-spacing: 0.4rpx;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.hero-sub {
  margin-top: 4rpx;
  color: var(--wot-color-theme-primary, #ff2237);
  font-size: 22rpx;
  font-weight: 600;
}

.hero-cd {
  display: flex;
  flex: 0 0 auto;
  flex-direction: column;
  align-items: flex-end;
  gap: 2rpx;
  font-variant-numeric: tabular-nums;
}

.hero-cd-label {
  color: var(--app-text-3);
  font-size: 20rpx;
}

.hero-cd-time {
  color: var(--wot-color-theme-primary, #ff2237);
  font-size: 24rpx;
  font-weight: 600;
}

.hero-perks {
  position: relative;
  z-index: 1;
  margin-top: 16rpx;
  color: var(--app-text-2);
  font-size: 22rpx;
  line-height: 1.4;
}

.hero-foot {
  position: relative;
  z-index: 1;
  display: flex;
  margin-top: 16rpx;
  padding-top: 16rpx;
  align-items: center;
  justify-content: space-between;
  border-top: 1rpx solid var(--app-border);
  gap: 16rpx;
}

.hero-count {
  color: var(--app-text);
  font-size: 22rpx;
  font-weight: 600;
}

.hero-hint {
  overflow: hidden;
  color: var(--app-text-3);
  font-size: 20rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ---- 商品双列网格 ---- */
.goods-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16rpx;
  padding: 0 24rpx;
}

.goods-card {
  position: relative;
  display: flex;
  overflow: hidden;
  flex-direction: column;
  background: var(--app-card);
  border-radius: 20rpx;
  transition: transform 160ms ease;
}

.goods-card--pressed {
  transform: scale(0.98);
}

/* 方图：padding-top 撑比例，image 绝对铺满 */
.goods-media {
  position: relative;
  width: 100%;
  padding-top: 100%;
  background: var(--app-card-2);
}

.goods-img {
  position: absolute;
  top: 0;
  left: 0;
  display: block;
  width: 100%;
  height: 100%;
}

.goods-img--empty {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--wot-color-theme-primary, #ff2237);
  font-size: 24rpx;
}

.goods-badge {
  position: absolute;
  top: 0;
  left: 0;
  z-index: 1;
  padding: 6rpx 12rpx;
  background: linear-gradient(135deg, var(--wot-color-theme-secondary, #ff7a45), var(--wot-color-theme-primary, #ff4d4f));
  border-radius: 20rpx 0 12rpx;
  color: #fff;
  font-size: 20rpx;
  font-weight: 600;
}

.goods-body {
  display: flex;
  flex: 1;
  flex-direction: column;
  padding: 16rpx;
}

.goods-name {
  display: -webkit-box;
  overflow: hidden;
  min-height: 34rpx;
  color: var(--app-text);
  font-size: 25rpx;
  font-weight: 500;
  line-height: 34rpx;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

/* 子标题：单行省略；与商品名相同或为空时整行不渲染（存量商品常见同名回填） */
.goods-sub {
  overflow: hidden;
  margin-top: 2rpx;
  color: var(--app-text-3);
  font-size: 21rpx;
  line-height: 30rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.goods-price-row {
  display: flex;
  overflow: hidden;
  margin-top: 10rpx;
  align-items: baseline;
  gap: 8rpx;
  white-space: nowrap;
}

.goods-price {
  --price-symbol-size: 0.56em;

  color: var(--wot-color-theme-primary, #ff2237);
  font-size: 36rpx;
  font-weight: 700;
  letter-spacing: -0.6rpx;
  line-height: 1;
}

.goods-strike {
  overflow: hidden;
  color: var(--app-text-3);
  font-size: 20rpx;
  text-decoration: line-through;
  text-overflow: ellipsis;
}

.goods-bottom {
  display: flex;
  min-height: 64rpx;
  margin-top: auto;
  padding-top: 8rpx;
  align-items: center;
  justify-content: space-between;
  gap: 8rpx;
}

.goods-save {
  display: inline-flex;
  height: 34rpx;
  padding: 0 12rpx;
  align-items: center;
  background: var(--wot-color-theme-background, #fff1ee);
  border-radius: 999rpx;
  color: var(--wot-color-theme-primary, #ff2237);
  font-size: 19rpx;
  font-weight: 600;
  white-space: nowrap;
}

.goods-grab {
  display: flex;
  height: 52rpx;
  padding: 0 28rpx;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--wot-color-theme-secondary, #ff7a45), var(--wot-color-theme-primary, #ff4d4f));
  border-radius: 999rpx;
  box-shadow: 0 4rpx 12rpx rgb(255 77 79 / 22%);
  color: #fff;
  font-size: 22rpx;
  font-weight: 600;
  line-height: 1;
  transition: opacity 160ms ease;
}

.goods-grab--pending {
  opacity: 0.6;
}

.goods-grab--pressed {
  transform: scale(0.94);
}

.page-foot {
  padding: 32rpx 24rpx calc(48rpx + env(safe-area-inset-bottom));
  color: var(--app-text-3);
  font-size: 20rpx;
  text-align: center;
}

/* ---- 骨架 ---- */
@keyframes sk-pulse {
  0%,
  100% {
    opacity: 1;
  }

  50% {
    opacity: 0.55;
  }
}

.sk-hero,
.sk-card {
  animation: sk-pulse 1.4s ease-in-out infinite;
}

.sk-hero {
  display: flex;
  margin: 20rpx 24rpx;
  padding: 26rpx 24rpx;
  flex-direction: column;
  gap: 16rpx;
  background: var(--app-card);
  border-radius: 24rpx;
}

.sk-card {
  overflow: hidden;
  background: var(--app-card);
  border-radius: 20rpx;
}

.sk-img {
  width: 100%;
  padding-top: 100%;
  background: var(--app-card-2);
}

.sk-body {
  display: flex;
  padding: 16rpx;
  flex-direction: column;
  gap: 14rpx;
}

.sk-line {
  height: 24rpx;
  background: var(--app-card-2);
  border-radius: 8rpx;
}

.sk-line--lg {
  width: 72%;
  height: 28rpx;
}

.sk-line--md {
  width: 56%;
}

.sk-line--sm {
  width: 38%;
}
</style>

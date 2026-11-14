<script setup lang="ts">
import { onLoad, onShow } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { shouldShowSubtitle } from '@/components/diy/subtitle-display'
import PriceText from '@/components/price-text/index.vue'
import { formatRemainingTime, useCountdownTicker } from '@/composables/useCountdown'
import { useNewbieGoodsActions } from '@/composables/useNewbieGoodsActions'
import { useNewbieScenes } from '@/composables/useNewbieScenes'
import { useRequireLogin } from '@/composables/useRequireLogin'
import { useAuthStore } from '@/store/authStore'
import { useShoppingCartStore } from '@/store/shoppingCartStore'
import { navigateToUrl } from '@/utils/tab-bar'

definePage({
  name: 'newbie-zone',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '新人专享',
  },
})

const { t } = useI18n()
const authStore = useAuthStore()
const shoppingCartStore = useShoppingCartStore()
const { goLogin } = useRequireLogin()

/**
 * 独立专享页一次取全活动商品。
 * 首页楼层受「展示商品数」限制，本页不受楼层配置影响；后端上限 200。
 */
const GOODS_LIMIT = 200

const {
  activeScene,
  degraded,
  load: loadScenes,
  orgScene,
  personalScene,
  shipScene,
  switchScene,
} = useNewbieScenes(GOODS_LIMIT)

const activity = computed(() => personalScene.value)
const loading = ref(true)
const refreshing = ref(false)
/** 已登录老客：活动对他不可用，整页显示「仅限新人」阻断态（分享链接/直达入口仍需兜底） */
const ineligible = computed(() => activity.value?.eligible === false)
const goodsList = computed(() => (ineligible.value ? [] : (activity.value?.goodsList ?? [])))
const { now } = useCountdownTicker()

const firstOrderScene = computed(() =>
  activeScene.value === '2' ? shipScene.value : orgScene.value,
)
const firstOrderTiers = computed(() => firstOrderScene.value?.tiers ?? [])
/**
 * 活动名角标：只在「有独立活动名」时渲染。
 * 后端未命名时会与固定标题重复，卡面不该把同一句话印两遍。
 */
const firstOrderMarkText = computed(() => {
  const name = firstOrderScene.value?.activityName?.trim() ?? ''
  return name && name !== t('promotion.newbie.firstOrderTitle') ? name : ''
})
const firstOrderEligibilityText = computed(() => {
  if (!authStore.isLoggedIn)
    return t('promotion.newbie.firstOrderLogin')
  if (firstOrderScene.value?.eligible === true)
    return t('promotion.newbie.firstOrderEligible')
  if (firstOrderScene.value?.eligible === false)
    return t('promotion.newbie.firstOrderIneligible')
  return t('promotion.newbie.firstOrderPending')
})
const sceneTabs = computed(() => [
  { key: '1' as const, label: t('promotion.newbie.scenePersonal') },
  { key: '2' as const, label: t('promotion.newbie.sceneShip') },
  { key: '3' as const, label: t('promotion.newbie.sceneOrg') },
])

const {
  pending,
  cardMainPrice,
  cardStrikePrice,
  cardZoneBadgeText,
  handleGrab,
  loadZonePrices,
  limitPerUser,
  savedAmount,
} = useNewbieGoodsActions({
  limitPerUser: () => Number(activity.value?.limitPerUser) || 1,
  openGoods,
})

/** 排序：默认沿用活动配置顺序，省最多按「划线价 - 成交价」倒序 */
type SortMode = 'default' | 'save'
const sortMode = ref<SortMode>('default')
/** 规则折叠：默认收起，避免页脚免责话术放大不确定感 */
const rulesOpen = ref(false)

const sortedGoods = computed(() => {
  const list = goodsList.value
  if (sortMode.value !== 'save')
    return list
  return [...list].sort((a, b) => savedAmount(b) - savedAmount(a))
})

const cartCount = computed(() => shoppingCartStore.cartCount)
const cartBadge = computed(() => (cartCount.value > 99 ? '99+' : String(cartCount.value)))

onLoad(() => {
  void loadActivity()
})

onShow(() => {
  // 从购物车返回时角标要跟着变；未登录不请求，避免一次 401 跳转
  if (authStore.isLoggedIn)
    void shoppingCartStore.fetchCartCount().catch(() => {})
})

async function loadActivity() {
  loading.value = true
  try {
    await loadScenes()
    // 老客没有可展示的商品（也不该再为不可用的价格发查价请求）
    if (!ineligible.value)
      loadZonePrices(goodsList.value)
    if (degraded.value)
      uni.showToast({ title: t('promotion.common.loadFailed'), icon: 'none' })
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
  // 长期有效：结束时间是远期哨兵，右侧整块留白，不展示倒计时也不加「长期有效」标签
  if (!endTime || longTerm.value)
    return null
  const remaining = formatRemainingTime(endTime, now.value)
  return remaining && remaining !== '00:00:00'
    ? { prefix: t('diy.newbieFloor.countdownPrefix'), text: remaining }
    : { prefix: t('diy.newbieFloor.countdownEndedPrefix'), text: t('diy.newbieFloor.countdownEndedText') }
})

const registerDays = computed(() => Number(activity.value?.registerWithinDays) || 0)

/**
 * 头卡权益行：只说「价格规则 + 限购」，注册时效交给资格条，避免一行塞三段规则。
 * registerWithinDays=0（不限）时不再额外解释，规则区已经写全。
 */
const perksText = computed(() =>
  `${t('promotion.newbie.priceLine')} · ${t('promotion.newbie.ruleHint', { limit: limitPerUser() })}`,
)

/**
 * 资格条文案。
 * - eligible=true：直接给结论，不再让用户猜；
 * - 未登录：给出「登录后验证」的可操作入口；
 * - 已登录但后端未判定（null）：不编造结论，交给结算页兜底。
 */
const eligibilityText = computed(() => {
  if (ineligible.value)
    return ''
  // 游客先要求登录验证：后端对未登录也可能回 eligible=null，这里不拿活动态当资格结论
  if (!authStore.isLoggedIn)
    return t('promotion.newbie.loginHint')
  if (activity.value?.eligible === true)
    return registerDays.value > 0
      ? t('promotion.newbie.eligibleHint', { days: registerDays.value })
      : t('promotion.newbie.eligibleShort')
  return ''
})

const showLoginEntry = computed(() => !authStore.isLoggedIn && !ineligible.value)

const ineligibleDesc = computed(() => {
  const reason = activity.value?.ineligibleReason
  if (reason === 'ALREADY_PAID')
    return t('promotion.newbie.ineligiblePaidDesc')
  if (reason === 'REGISTER_TIMEOUT')
    return t('promotion.newbie.ineligibleTimeoutDesc', { days: registerDays.value })
  return t('promotion.newbie.ineligibleDesc')
})

const ruleLines = computed(() => {
  const limit = limitPerUser()
  return [
    registerDays.value > 0
      ? t('promotion.newbie.ruleEligibility', { days: registerDays.value })
      : t('promotion.newbie.ruleEligibilityNoDays'),
    t('promotion.newbie.ruleDiscount'),
    t('promotion.newbie.ruleLimit', { limit }),
    t('promotion.newbie.ruleFinal'),
  ]
})

/**
 * 底部悬浮玻璃操作条：三个场景共用同一条，位置、尺寸、材质完全一致。
 * 个人场景要求活动可用；船供/企业场景要求有对应首单活动，避免空态页挂一条无意义操作条。
 */
const showActionBar = computed(() => {
  if (loading.value)
    return false
  if (activeScene.value === '1')
    return !!activity.value && !ineligible.value
  return !!firstOrderScene.value?.activityId
})

/** 左入口：个人场景进个人购物车，船供/企业场景进共享购物车 */
const actionBarCartLabel = computed(() =>
  activeScene.value === '1'
    ? t('promotion.newbie.cartLabel')
    : t('catalog.sharedCart'),
)

/** 主 CTA：个人场景去购物车结算，船供/企业场景去采购 */
const actionBarCtaText = computed(() =>
  activeScene.value === '1'
    ? t('promotion.newbie.goCart')
    : t('promotion.newbie.firstOrderCta'),
)

function handleActionBarCart() {
  if (activeScene.value === '1')
    goCart()
  else
    goSharedCart()
}

function handleActionBarCta() {
  if (activeScene.value === '1')
    goCart()
  else
    goSharedCart()
}

/** 卡片底部一行：省额 + 限购，把结算时才会暴露的规则前置到卡面 */
function cardMetaText(goods: Parameters<typeof savedAmount>[0]) {
  const saved = savedAmount(goods)
  const limitText = t('promotion.newbie.limitTag', { limit: limitPerUser() })
  return saved > 0
    ? `${t('diy.common.saveAmount', { amount: saved.toFixed(2) })} · ${limitText}`
    : limitText
}

function openGoods(spuId: string) {
  if (!spuId)
    return
  uni.navigateTo({ url: `/sub-pages/product/goods-detail/index?id=${spuId}` })
}

function goHome() {
  navigateToUrl('/pages/home/index')
}

function goCart() {
  navigateToUrl('/pages/user/shopping-cart/index')
}

function goSharedCart() {
  uni.navigateTo({ url: '/sub-pages/order/shared-cart/list' })
}
</script>

<template>
  <view class="newbie-page">
    <hr-navbar :title="t('promotion.newbie.title')" />

    <view class="scene-tabs">
      <view
        v-for="tab in sceneTabs"
        :key="tab.key"
        class="scene-tab"
        :class="{ 'scene-tab--active': activeScene === tab.key }"
        @click="switchScene(tab.key)"
      >
        {{ tab.label }}
      </view>
    </view>

    <scroll-view
      scroll-y
      class="page-scroll"
      refresher-enabled
      :refresher-triggered="refreshing"
      @refresherrefresh="onRefresh"
    >
      <template v-if="activeScene !== '1'">
        <view v-if="firstOrderScene?.activityId" class="first-order-card">
          <view v-if="firstOrderMarkText" class="first-order-mark">
            {{ firstOrderMarkText }}
          </view>
          <view class="first-order-title">
            {{ t('promotion.newbie.firstOrderTitle') }}
          </view>
          <view class="first-order-desc">
            {{ t('promotion.newbie.firstOrderDesc') }}
          </view>
          <view class="first-order-tiers">
            <view v-for="(tier, index) in firstOrderTiers" :key="index" class="first-order-tier">
              {{ t('promotion.newbie.tierRule', {
                min: Number(tier.minAmount).toFixed(0),
                discount: Number(tier.discountAmount).toFixed(0),
              }) }}
            </view>
          </view>
          <view v-if="firstOrderScene?.maxDiscountAmount" class="first-order-cap">
            单笔最高减 ¥{{ Number(firstOrderScene?.maxDiscountAmount).toFixed(0) }}
          </view>
          <view class="first-order-elig" :class="{ 'first-order-elig--bad': firstOrderScene?.eligible === false }">
            {{ firstOrderEligibilityText }}
          </view>
          <view class="first-order-rule">
            {{ t('promotion.newbie.firstOrderRule') }}
          </view>
        </view>
        <hall-state
          v-else
          art="group"
          :title="activeScene === '2'
            ? t('promotion.newbie.sceneShipEmpty')
            : t('promotion.newbie.sceneOrgEmpty')"
          :desc="t('promotion.newbie.firstOrderPending')"
          :action-text="t('promotion.common.goHome')"
          @action="goHome"
        />
      </template>

      <!-- 个人场景内容：船供/企业 tab 只展示上面的首单卡，不重复渲染个人空态/商品 -->
      <template v-if="activeScene === '1'">
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
        :desc="ineligibleDesc"
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
          </view>

          <view class="hero-perks">
            {{ perksText }}
          </view>

          <!-- 资格条：把「我能不能享」从规则里提出来，放在最显眼的位置 -->
          <view v-if="eligibilityText" class="hero-elig">
            <text class="hero-elig-text">
              <!-- 游客还没验证资格，不打勾，避免「未验证」看起来像「已通过」 -->
              <text v-if="authStore.isLoggedIn" class="hero-elig-check">✓</text>
              {{ eligibilityText }}
            </text>
            <text v-if="showLoginEntry" class="hero-elig-action" @click="goLogin">
              {{ t('promotion.newbie.loginAction') }} ›
            </text>
          </view>
        </view>

        <view class="list-head">
          <text class="list-count">
            {{ t('promotion.newbie.goodsCount', { count: goodsList.length }) }}
          </text>
          <view class="sort-row">
            <text
              class="sort-chip"
              :class="{ 'sort-chip--active': sortMode === 'default' }"
              @click="sortMode = 'default'"
            >
              {{ t('promotion.newbie.sortDefault') }}
            </text>
            <text
              class="sort-chip"
              :class="{ 'sort-chip--active': sortMode === 'save' }"
              @click="sortMode = 'save'"
            >
              {{ t('promotion.newbie.sortSave') }}
            </text>
          </view>
        </view>

        <view class="goods-grid">
          <view
            v-for="goods in sortedGoods"
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
                <text class="goods-meta">
                  {{ cardMetaText(goods) }}
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

        <!-- 规则折叠：把资格、限购、超件计价说清楚，替代原来的免责页脚 -->
        <view class="rules">
          <view class="rules-head" @click="rulesOpen = !rulesOpen">
            <text class="rules-title">
              {{ t('promotion.newbie.rulesTitle') }}
            </text>
            <text class="rules-arrow" :class="{ 'rules-arrow--open': rulesOpen }">›</text>
          </view>
          <view v-if="rulesOpen" class="rules-body">
            <text v-for="(line, index) in ruleLines" :key="index" class="rules-line">
              {{ line }}
            </text>
          </view>
        </view>

      </template>
      </template>

      <!-- 悬浮操作条是 fixed 覆盖层：三个场景统一在滚动内容末尾留出胶囊等高余量 -->
      <view class="page-foot-space" />
    </scroll-view>

    <!-- 底部悬浮玻璃操作条：胶囊几何与 glass-tabbar 逐值一致，三个场景共用同一条 -->
    <view v-if="showActionBar" class="action-bar-veil" />
    <view v-if="showActionBar" class="action-bar">
      <view
        class="action-bar__entry"
        hover-class="action-bar__entry--pressed"
        :hover-stay-time="80"
        @click="handleActionBarCart"
      >
        <view class="action-bar__icon-box">
          <image class="action-bar__icon" src="/static/tabbar/cart.png" mode="aspectFit" />
          <text v-if="activeScene === '1' && cartCount > 0" class="action-bar__badge">
            {{ cartBadge }}
          </text>
        </view>
        <text class="action-bar__label">
          {{ actionBarCartLabel }}
        </text>
      </view>
      <view
        class="action-bar__cta"
        hover-class="action-bar__cta--pressed"
        :hover-stay-time="80"
        @click="handleActionBarCta"
      >
        {{ actionBarCtaText }}
      </view>
    </view>
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

/* ---- 活动头卡：品牌渐变 + 资格条，价格/倒计时走全站价格色 ---- */
.hero {
  position: relative;
  overflow: hidden;
  margin: 20rpx 24rpx;
  padding: 26rpx 24rpx 20rpx;
  background: linear-gradient(
    135deg,
    var(--wot-color-theme-primary, #ff2237) 0%,
    var(--wot-color-theme-secondary, #ff7a45) 100%
  );
  border-radius: 24rpx;
  color: #fff;
}

.hero-glow {
  position: absolute;
  top: -120rpx;
  right: -80rpx;
  width: 320rpx;
  height: 320rpx;
  background: radial-gradient(circle, #fff 0%, rgb(255 255 255 / 0%) 68%);
  opacity: 0.16;
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
  background: #fff;
  border-radius: 10rpx;
  color: var(--wot-color-theme-primary, #ff2237);
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
  color: #fff;
  font-size: 34rpx;
  font-weight: 700;
  letter-spacing: 0.4rpx;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.hero-sub {
  margin-top: 4rpx;
  color: rgb(255 255 255 / 88%);
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
  color: rgb(255 255 255 / 78%);
  font-size: 20rpx;
}

.hero-cd-time {
  padding: 2rpx 10rpx;
  background: rgb(0 0 0 / 18%);
  border-radius: 8rpx;
  color: #fff;
  font-size: 24rpx;
  font-weight: 600;
}

.hero-perks {
  position: relative;
  z-index: 1;
  margin-top: 14rpx;
  color: rgb(255 255 255 / 90%);
  font-size: 21rpx;
  line-height: 1.5;
}

.hero-elig {
  position: relative;
  z-index: 1;
  display: flex;
  margin-top: 14rpx;
  padding-top: 14rpx;
  align-items: center;
  justify-content: space-between;
  border-top: 1rpx solid rgb(255 255 255 / 26%);
  gap: 16rpx;
  color: rgb(255 255 255 / 92%);
  font-size: 21rpx;
}

.hero-elig-text {
  overflow: hidden;
  min-width: 0;
  flex: 1;
  text-overflow: ellipsis;
}

.hero-elig-check {
  margin-right: 6rpx;
  font-weight: 700;
}

.hero-elig-action {
  flex: 0 0 auto;
  color: #fff;
  font-size: 22rpx;
  font-weight: 600;
}

/* ---- 商品数 + 排序 ---- */
.list-head {
  display: flex;
  margin: 4rpx 24rpx 12rpx;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
}

.list-count {
  color: var(--app-text-3);
  font-size: 22rpx;
}

.sort-row {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8rpx;
}

.sort-chip {
  padding: 8rpx 18rpx;
  border-radius: 999rpx;
  color: var(--app-text-3);
  font-size: 21rpx;
  line-height: 1.5;
}

.sort-chip--active {
  background: var(--wot-color-theme-background, #fff1ee);
  color: var(--wot-color-theme-primary, #ff2237);
  font-weight: 600;
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
  background: linear-gradient(
    135deg,
    var(--wot-color-theme-primary, #ff2237) 0%,
    var(--wot-color-theme-secondary, #ff7a45) 100%
  );
  border-radius: 20rpx 0 12rpx;
  color: #fff;
  font-size: 20rpx;
  font-weight: 600;
}

.goods-body {
  display: flex;
  flex: 1;
  flex-direction: column;
  padding: 14rpx;
}

.goods-name {
  display: -webkit-box;
  overflow: hidden;
  min-height: 32rpx;
  color: var(--app-text);
  font-size: 25rpx;
  font-weight: 500;
  line-height: 32rpx;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

/* 子标题：单行省略；与商品名相同或为空时整行不渲染（存量商品常见同名回填） */
.goods-sub {
  overflow: hidden;
  margin-top: 2rpx;
  color: var(--app-text-3);
  font-size: 21rpx;
  line-height: 28rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.goods-price-row {
  display: flex;
  overflow: hidden;
  margin-top: 8rpx;
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

/* 省额并进卡面一行，与限购同排，替代原来的独立「省¥X」胶囊 */
.goods-bottom {
  display: flex;
  min-height: 52rpx;
  margin-top: auto;
  padding-top: 8rpx;
  align-items: center;
  justify-content: space-between;
  gap: 8rpx;
}

.goods-meta {
  overflow: hidden;
  min-width: 0;
  flex: 1;
  color: var(--app-text-3);
  font-size: 19rpx;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.goods-grab {
  display: flex;
  height: 52rpx;
  padding: 0 28rpx;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  background: linear-gradient(
    135deg,
    var(--wot-color-theme-primary, #ff2237) 0%,
    var(--wot-color-theme-secondary, #ff7a45) 100%
  );
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

/* ---- 规则折叠 ---- */
.rules {
  margin: 20rpx 24rpx 0;
  padding: 0 20rpx;
  background: var(--app-card);
  border-radius: 16rpx;
}

.rules-head {
  display: flex;
  height: 76rpx;
  align-items: center;
  justify-content: space-between;
  color: var(--app-text-2);
  font-size: 22rpx;
}

.rules-arrow {
  color: var(--app-text-3);
  font-size: 24rpx;
  transition: transform 160ms ease;
}

.rules-arrow--open {
  transform: rotate(90deg);
}

.rules-body {
  padding: 0 0 18rpx;
  border-top: 1rpx solid var(--app-border);
}

.rules-line {
  display: block;
  margin-top: 12rpx;
  color: var(--app-text-3);
  font-size: 20rpx;
  line-height: 1.6;
}

.page-foot-space {
  /* 悬浮操作条是 fixed 覆盖层：留出胶囊(52) + 贴底(16) + 一点呼吸位 */
  height: 84px;
}

/* ---- 底部悬浮玻璃操作条：与 glass-tabbar / GoodsFooter 同一套材质与几何 ---- */
/**
 * 底部渐进模糊蒙层：与 glass-tabbar 的蒙层逐值一致（68px = 贴底 16 + 胶囊 52），
 * 模糊由下往上渐弱，商品卡滚到胶囊两侧/下方时不再能看清。
 * 层级 8 在胶囊(9)之下，页面弹层滑出时保持清晰。
 */
.action-bar-veil {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  height: 68px;
  z-index: 8;
  pointer-events: none;
  background-color: rgba(255, 255, 255, 0.42);
  /* 与胶囊同一套材质数值，渐隐后仍是同一族玻璃 */
  -WEBKIT-backdrop-filter: blur(18px) saturate(160%);
  backdrop-filter: blur(18px) saturate(160%);
  -WEBKIT-mask-image: linear-gradient(to top, rgba(0, 0, 0, 1) 0%, rgba(0, 0, 0, 0.6) 45%, rgba(0, 0, 0, 0) 100%);
  mask-image: linear-gradient(to top, rgba(0, 0, 0, 1) 0%, rgba(0, 0, 0, 0.6) 45%, rgba(0, 0, 0, 0) 100%);
}

.wot-theme-dark .action-bar-veil {
  background-color: rgba(18, 18, 18, 0.5);
}

/**
 * 与悬浮玻璃底栏同一套材质与几何：高 52px、圆角 26px、贴底 16px、
 * 左右各留 20px、白 86% + blur 18px saturate 160%。
 * 本页没有 tabBar，这条操作条就是用户在这一页看到的「底部导航」，
 * 尺寸与材质必须和 tab 页底栏一致，切 tab 时底栏位置不能跳。
 * 层级 9：低于页面级浮层，高于滚动内容。
 */
.action-bar {
  position: fixed;
  left: 50%;
  transform: translateX(-50%);
  width: calc(100% - 40px);
  max-width: 480px;
  bottom: 16px;
  z-index: 9;
  display: flex;
  align-items: center;
  height: 52px;
  box-sizing: border-box;
  padding: 0 10px;
  overflow: hidden;
  border-radius: 26px;
  /* 与顶栏磨砂白同一数值（86%），上下两块玻璃看起来才是一套材质 */
  background-color: rgba(255, 255, 255, 0.86);
  /* 大写 WEBKIT 避开 autoprefixer 的过时前缀清理（iOS 17- 的 WKWebView 只认带前缀的写法） */
  -WEBKIT-backdrop-filter: blur(18px) saturate(160%);
  backdrop-filter: blur(18px) saturate(160%);
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.16);
}

.action-bar__entry {
  flex: none;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 68px;
  padding: 0 4px;
  box-sizing: border-box;
  transition: opacity 160ms ease;

  &--pressed {
    opacity: 0.72;
  }
}

.action-bar__icon-box {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  margin-bottom: 3px;
}

.action-bar__icon {
  width: 26px;
  height: 26px;
}

/* 角标与原生 tabBar / glass-tabbar 同款：主题色偏浅时白字会看不清，不跟随主题 */
.action-bar__badge {
  position: absolute;
  top: -4px;
  right: -10px;
  box-sizing: border-box;
  min-width: 15px;
  height: 15px;
  padding: 0 4px;
  border-radius: 8px;
  background-color: #ff2237;
  color: #fff;
  font-size: 9px;
  line-height: 15px;
  text-align: center;
}

.action-bar__label {
  color: var(--app-text-2);
  font-size: 11px;
  line-height: 15px;
  white-space: nowrap;
}

.action-bar__cta {
  flex: 1;
  min-width: 0;
  height: 36px;
  margin-left: 6px;
  background: linear-gradient(
    135deg,
    var(--wot-color-theme-primary, #ff2237) 0%,
    var(--wot-color-theme-secondary, #ff7a45) 100%
  );
  border-radius: 18px;
  box-shadow: 0 6rpx 16rpx rgb(255 77 79 / 24%);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  line-height: 36px;
  text-align: center;
  transition: transform 120ms ease, opacity 120ms ease;

  &--pressed {
    opacity: 0.88;
    transform: scale(0.99);
  }
}

/* 深色模式：与 glass-tabbar 同源（wot-theme-dark 挂在根节点） */
.wot-theme-dark .action-bar {
  background-color: rgba(26, 26, 26, 0.86);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.46);
}

.wot-theme-dark .action-bar__label {
  color: #a8adb5;
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

/* ---- 三场景 tab：个人 / 船供首单 / 企业首单 ---- */
.scene-tabs {
  display: flex;
  flex-shrink: 0;
  padding: 16rpx 24rpx 0;
  gap: 12rpx;
}

.scene-tab {
  flex: 1;
  padding: 14rpx 0;
  color: var(--app-text-2);
  font-size: 26rpx;
  text-align: center;
  background: var(--app-card);
  border-radius: 999rpx;
}

.scene-tab--active {
  color: #fff;
  font-weight: 600;
  background: var(--wot-color-theme-primary, #ff2237);
}

/* ---- 首单卡片：不伪造商品列表，只展示门槛与主体资格 ---- */
.first-order-card {
  margin: 24rpx;
  padding: 32rpx 28rpx;
  background: var(--app-card);
  border-radius: 24rpx;
}

/* 活动名角标：宽度随文案自适应。不能再复用 44rpx 方形单字标记，
   否则多字活动名会逐字折行、溢出压到下方标题。 */
.first-order-mark {
  display: inline-block;
  max-width: 100%;
  box-sizing: border-box;
  padding: 6rpx 16rpx;
  overflow: hidden;
  background: var(--wot-color-theme-background, #fff1ee);
  border-radius: 10rpx;
  color: var(--wot-color-theme-primary, #ff2237);
  font-size: 24rpx;
  font-weight: 700;
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.first-order-title {
  margin-top: 20rpx;
  color: var(--app-text-1);
  font-size: 40rpx;
  font-weight: 700;
}

.first-order-desc {
  margin-top: 10rpx;
  color: var(--app-text-2);
  font-size: 26rpx;
}

.first-order-tiers {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  margin-top: 24rpx;
}

.first-order-tier {
  padding: 16rpx 20rpx;
  color: var(--wot-color-theme-primary, #ff2237);
  font-size: 28rpx;
  font-weight: 600;
  background: rgba(255, 34, 55, 0.08);
  border-radius: 12rpx;
}

.first-order-cap {
  margin-top: 16rpx;
  color: var(--app-text-2);
  font-size: 24rpx;
}

.first-order-elig {
  margin-top: 20rpx;
  color: var(--app-text-1);
  font-size: 26rpx;
}

.first-order-elig--bad {
  color: var(--app-text-2);
}

.first-order-rule {
  margin-top: 16rpx;
  color: var(--app-text-3);
  font-size: 22rpx;
  text-align: center;
}
</style>

<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { getMemberLevelList, getMyBenefits } from '@/sub-pages/api/user/benefit'
import { Local } from '@/utils/storage'

definePage({
  name: 'member-center',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '会员中心',
  },
})

interface MemberBenefits {
  levelId?: string
  levelName?: string
  discountRate?: number
  freeShipping?: boolean
  pointsMultiplier?: number
  exclusiveCouponTemplateIds?: string[]
}

interface MemberLevel {
  id: string
  levelName?: string
  levelIcon?: string
}

interface AssetItem {
  key: string
  label: string
  value: string
  unit?: string
  onTap: () => void
}

interface BenefitItem {
  key: string
  label: string
  icon: string
  value: string
  /** 未解锁/未享有：值降级为灰色，避免「0 张 / 未享」抢过已生效权益 */
  inactive?: boolean
}

interface EntryItem {
  key: string
  icon: string
  title: string
  desc: string
  onTap: () => void
}

const BRAND_COLOR = 'var(--theme-color-primary, var(--wot-color-theme-primary, #FF2237))'

const { t } = useI18n()
const router = useRouter()
const authStore = useAuthStore()
const userStore = useUserStore()

const benefits = ref<MemberBenefits | null>(null)
const levels = ref<MemberLevel[]>([])

const isLoggedIn = computed(() => authStore.isLoggedIn)

const nickname = computed(() => {
  if (userStore.getUserNickname)
    return userStore.getUserNickname
  return isLoggedIn.value
    ? t('accountMember.memberUser')
    : t('accountMember.loginOrRegister')
})

const avatarUrl = computed(() => userStore.getUserAvatar || '/static/default-avatar.png')

/** 等级徽章取「等级列表」里的 3D 徽章图：benefits/pointsInfo 都不带图标，只能按 levelId 回查 */
const currentLevel = computed(() => levels.value.find(item => item.id === userStore.getLevelId))

const levelName = computed(() =>
  userStore.getLevelName || benefits.value?.levelName || currentLevel.value?.levelName || '',
)

const levelIcon = computed(() => currentLevel.value?.levelIcon || '')

/** 千分位：积分/券张数都可能是四位数，不分组时读音成本高 */
function formatCount(value?: null | number): string {
  const num = Number(value)
  const safe = Number.isFinite(num) ? Math.max(0, Math.trunc(num)) : 0
  return String(safe).replace(/\B(?=(\d{3})+(?!\d))/g, ',')
}

/** 储值余额固定两位小数（金额口径），整数部分同样千分位 */
function formatBalance(value?: null | number): string {
  const num = Number(value)
  const safe = Number.isFinite(num) ? Math.max(0, num) : 0
  const [integer, decimal] = safe.toFixed(2).split('.')
  return `${integer.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}.${decimal}`
}

const assetItems = computed<AssetItem[]>(() => [
  { key: 'point', label: t('accountMember.assets.points'), value: formatCount(userStore.getPoint), onTap: toPointsRecord },
  { key: 'balance', label: t('accountMember.assets.balance'), value: formatBalance(userStore.getBalance), unit: '¥', onTap: toBalanceRecord },
  { key: 'coupon', label: t('accountMember.assets.coupon'), value: formatCount(userStore.getCouponCount), onTap: toCouponList },
])

/** 30 天内即将过期积分：用 store 实时数据，过期提醒不占用首屏，仅在有值时插入一条窄条 */
const expiringTip = computed(() => {
  const info = userStore.pointsInfo
  const expiring = Number(info?.expiringPoints)
  const nearest = info?.nearestExpireTime
  if (!isLoggedIn.value || !Number.isFinite(expiring) || expiring <= 0 || !nearest)
    return ''
  return t('accountMember.expiringTip', {
    points: formatCount(expiring),
    date: String(nearest).slice(0, 10),
  })
})

const benefitItems = computed<BenefitItem[]>(() => {
  const data = benefits.value
  const rate = Number(data?.discountRate)
  const hasDiscount = Number.isFinite(rate) && rate > 0 && rate < 1
  const multiplier = Number(data?.pointsMultiplier)
  const couponCount = data?.exclusiveCouponTemplateIds?.length || 0
  return [
    {
      key: 'discount',
      label: t('accountMember.benefit.discount'),
      icon: 'i-carbon:percentage',
      value: hasDiscount
        ? t('accountMember.benefit.discountValue', {
            rate: Number((rate * 10).toFixed(1)),
            percent: Number(((1 - rate) * 100).toFixed(1)),
          })
        : t('accountMember.benefit.noDiscount'),
      inactive: !hasDiscount,
    },
    {
      key: 'shipping',
      label: t('accountMember.benefit.freeShipping'),
      icon: 'i-carbon:delivery-truck',
      value: data?.freeShipping
        ? t('accountMember.benefit.enjoyed')
        : t('accountMember.benefit.notEnjoyed'),
      inactive: !data?.freeShipping,
    },
    {
      key: 'multiplier',
      label: t('accountMember.benefit.pointsMultiplier'),
      icon: 'i-carbon:increase-level',
      value: t('accountMember.benefit.multiplierValue', {
        value: Number.isFinite(multiplier) && multiplier > 0 ? Number(multiplier) : 1,
      }),
    },
    {
      key: 'coupon',
      label: t('accountMember.benefit.exclusiveCoupon'),
      icon: 'i-carbon:ticket',
      value: t('accountMember.benefit.couponCount', { count: couponCount }),
      inactive: couponCount === 0,
    },
  ]
})

const entries = computed<EntryItem[]>(() => [
  {
    key: 'recharge',
    icon: 'i-carbon:add-filled',
    title: t('accountMember.entries.rechargeTitle'),
    desc: t('accountMember.entries.rechargeDesc'),
    onTap: toRecharge,
  },
  {
    key: 'points',
    icon: 'i-carbon:chart-line-data',
    title: t('accountMember.entries.pointsTitle'),
    desc: t('accountMember.entries.pointsDesc'),
    onTap: toPointsRecord,
  },
  {
    key: 'balance',
    icon: 'i-carbon:wallet',
    title: t('accountMember.entries.balanceTitle'),
    desc: t('accountMember.entries.balanceDesc'),
    onTap: toBalanceRecord,
  },
])

onShow(() => {
  // 未登录不点任何 /app/** 接口（均需登录，会触发全局 401 跳登录），直接渲染登录引导态
  if (!authStore.isLoggedIn) {
    benefits.value = null
    levels.value = []
    return
  }
  /**
   * 冷启动可能只恢复了 token：与个人中心同口径，补一次用户信息，
   * 否则页头会顶着「会员用户」/空头像渲染已登录页面。
   */
  if (!userStore.getUserInfo) {
    userStore.fetchUserInfo().catch(error => console.error('加载用户信息失败:', error))
  }
  else {
    // 已登录且已有用户信息时只刷新会变的数字，避免每次返回都整包拉用户信息
    userStore.refreshPointsInfo().catch(error => console.error('刷新积分信息失败:', error))
    userStore.refreshUserCouponCount().catch(error => console.error('刷新优惠券数量失败:', error))
    userStore.fetchMemberInfo()
  }
  loadBenefits()
  loadLevels()
})

async function loadBenefits() {
  try {
    const response: MemberBenefits = await getMyBenefits()
    benefits.value = response ?? null
  }
  catch (error) {
    console.error('加载会员权益失败:', error)
    benefits.value = null
  }
}

async function loadLevels() {
  try {
    const response = await getMemberLevelList()
    levels.value = Array.isArray(response) ? response : []
  }
  catch (error) {
    // 徽章只是装饰：拉不到等级列表时隐藏徽章，不影响权益与数字展示
    console.error('加载会员等级失败:', error)
    levels.value = []
  }
}

/** 会员能力都需要登录：未登录时统一落到登录页，并记录回跳路径 */
function requireLogin(): boolean {
  if (isLoggedIn.value)
    return true
  Local.set('redirectPath', '/sub-pages/user/member/index')
  router.replaceAll({ name: 'login' })
  return false
}

function toLogin() {
  requireLogin()
}

/** 页头整块可点：未登录去登录，已登录进等级权益（等级胶囊的可点区域顺势放大到 112rpx） */
function onProfileTap() {
  if (!isLoggedIn.value) {
    toLogin()
    return
  }
  toLevelBenefit()
}

function toSignIn() {
  if (requireLogin())
    router.push({ name: 'member-sign-in' })
}

function toPointsRecord() {
  if (requireLogin())
    router.push({ name: 'member-points-record' })
}

function toRecharge() {
  if (requireLogin())
    router.push({ name: 'member-recharge' })
}

function toBalanceRecord() {
  if (requireLogin())
    router.push({ name: 'member-balance-record' })
}

function toLevelBenefit() {
  if (requireLogin())
    router.push({ name: 'member-level-benefit' })
}

function toCouponList() {
  if (requireLogin())
    router.push({ name: 'coupon-user' })
}
</script>

<template>
  <hr-navbar
    :title="t('accountMember.title')"
    :bordered="false"
    :background-color="BRAND_COLOR"
    text-color="#ffffff"
  />
  <view class="mc-page">
    <!-- 会员身份页头：导航栏同色，渐变从主色收口到辅色，不写死紫/蓝紫 -->
    <view class="mc-hero">
      <view class="mc-hero__glow mc-hero__glow--top" />
      <view class="mc-hero__glow mc-hero__glow--side" />
      <view
        class="mc-profile"
        hover-class="mc-profile--pressed"
        @click="onProfileTap"
      >
        <image class="mc-profile__avatar" :src="avatarUrl" mode="aspectFill" />
        <view class="mc-profile__meta">
          <text class="mc-profile__name">
            {{ nickname }}
          </text>
          <view v-if="isLoggedIn && levelName" class="mc-level">
            <image v-if="levelIcon" class="mc-level__medal" :src="levelIcon" mode="aspectFit" />
            <text class="mc-level__name">
              {{ levelName }}
            </text>
            <text class="i-carbon:chevron-right mc-level__arrow" />
          </view>
          <text v-else-if="!isLoggedIn" class="mc-profile__hint">
            {{ t('accountMember.loginHint') }}
          </text>
        </view>
      </view>
    </view>

    <!-- 资产卡：上移压住渐变收口，积分/余额/优惠券一屏可见 -->
    <view v-if="isLoggedIn" class="mc-assets">
      <view
        v-for="item in assetItems"
        :key="item.key"
        class="mc-assets__item"
        hover-class="mc-assets__item--pressed"
        @click="item.onTap"
      >
        <view class="mc-assets__value">
          <text v-if="item.unit" class="mc-assets__unit">
            {{ item.unit }}
          </text>
          <text class="mc-assets__number">
            {{ item.value }}
          </text>
        </view>
        <text class="mc-assets__label">
          {{ item.label }}
        </text>
      </view>
    </view>
    <view
      v-else
      class="mc-assets mc-assets--guest"
      hover-class="mc-assets__item--pressed"
      @click="toLogin"
    >
      <view class="mc-assets__guest-icon">
        <text class="i-carbon:user-profile mc-assets__guest-glyph" />
      </view>
      <text class="mc-assets__guest-text">
        {{ t('accountMember.guestAssetsHint') }}
      </text>
      <text class="i-carbon:chevron-right mc-assets__guest-arrow" />
    </view>

    <!-- 积分过期提醒：有真实到期批次才出现 -->
    <view
      v-if="expiringTip"
      class="mc-notice"
      hover-class="mc-notice--pressed"
      @click="toPointsRecord"
    >
      <text class="i-carbon:warning-alt mc-notice__icon" />
      <text class="mc-notice__text">
        {{ expiringTip }}
      </text>
      <text class="mc-notice__action">
        {{ t('accountMember.viewRecords') }}
      </text>
    </view>

    <!-- 我的权益：2×2 权益块，值比标签重，未享有的值降级 -->
    <view class="mc-card">
      <view class="mc-card__head">
        <text class="mc-card__title">
          {{ t('accountMember.myBenefits') }}
        </text>
        <view
          v-if="isLoggedIn"
          class="mc-card__more"
          hover-class="mc-card__more--pressed"
          @click="toLevelBenefit"
        >
          <text>{{ t('accountMember.allLevelBenefits') }}</text>
          <text class="i-carbon:chevron-right mc-card__more-arrow" />
        </view>
      </view>
      <view v-if="isLoggedIn" class="mc-benefits">
        <view
          v-for="item in benefitItems"
          :key="item.key"
          class="mc-benefit"
          hover-class="mc-benefit--pressed"
          @click="toLevelBenefit"
        >
          <view class="mc-benefit__icon">
            <text :class="item.icon" class="mc-benefit__glyph" />
          </view>
          <view class="mc-benefit__body">
            <text class="mc-benefit__label">
              {{ item.label }}
            </text>
            <text class="mc-benefit__value" :class="{ 'mc-benefit__value--off': item.inactive }">
              {{ item.value }}
            </text>
          </view>
        </view>
      </view>
      <view v-else class="mc-benefits__guest">
        <text class="mc-benefits__guest-text">
          {{ t('accountMember.guestBenefitsHint') }}
        </text>
        <view class="mc-benefits__guest-btn" hover-class="mc-benefits__guest-btn--pressed" @click="toLogin">
          {{ t('accountMember.goLogin') }}
        </view>
      </view>
    </view>

    <!-- 每日签到：品牌渐变横幅，页面里唯一的高饱和运营块 -->
    <view class="mc-signin" hover-class="mc-signin--pressed" @click="toSignIn">
      <view class="mc-signin__icon">
        <text class="i-carbon:fire mc-signin__glyph" />
      </view>
      <view class="mc-signin__body">
        <text class="mc-signin__title">
          {{ t('accountMember.dailyCheckIn') }}
        </text>
        <text class="mc-signin__desc">
          {{ t('accountMember.checkInDesc') }}
        </text>
      </view>
      <view class="mc-signin__cta">
        {{ t('accountMember.goCheckIn') }}
      </view>
    </view>

    <!-- 常用功能 -->
    <view class="mc-card mc-card--entries">
      <view class="mc-card__head mc-card__head--entries">
        <text class="mc-card__title">
          {{ t('accountMember.quickAccess') }}
        </text>
      </view>
      <view
        v-for="(entry, index) in entries"
        :key="entry.key"
        class="mc-entry"
        :class="{ 'mc-entry--divided': index > 0 }"
        hover-class="mc-entry--pressed"
        @click="entry.onTap"
      >
        <view class="mc-entry__icon">
          <text :class="entry.icon" class="mc-entry__glyph" />
        </view>
        <view class="mc-entry__body">
          <text class="mc-entry__title">
            {{ entry.title }}
          </text>
          <text class="mc-entry__desc">
            {{ entry.desc }}
          </text>
        </view>
        <text class="i-carbon:chevron-right mc-entry__arrow" />
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
/* 文字色阶与卡片口径沿用个人中心：标题 #1f2329 / 正文 #4e5969 / 辅助 #9ca3af */
$mc-title: #1f2329;
$mc-text: #4e5969;
$mc-muted: #9ca3af;
/* 小号说明文字的灰比 #9ca3af 再深一档：22rpx 在浅底上要保证可读 */
$mc-label: #6b7280;
$mc-hairline: #eef0f3;
$mc-gutter: 20rpx;
$mc-shadow: 0 8rpx 24rpx rgba(31, 44, 65, 0.06);
/* 主题色：管理端可换肤，页面内不出现写死的主色（旧版紫色渐变即反例） */
$mc-primary: var(--theme-color-primary, var(--wot-color-theme-primary, #FF2237));
$mc-secondary: var(--theme-color-secondary, var(--wot-color-theme-secondary, #FF6B7A));

.mc-page {
  /* 底部留白叠加安全区，避免最后一张卡贴住 home indicator */
  padding-bottom: calc(32rpx + constant(safe-area-inset-bottom));
  padding-bottom: calc(32rpx + env(safe-area-inset-bottom));
}

/* ===================== 会员身份页头 ===================== */

.mc-hero {
  position: relative;
  overflow: hidden;
  padding: 24rpx 32rpx 104rpx;
  /* 顶部必须与导航栏同为纯主色，180deg 才能让接缝不可见（黑 scrim 会把橙色压成脏棕，已弃用） */
  background: linear-gradient(180deg, $mc-primary 0%, $mc-secondary 100%);
}

/* 用径向渐变做柔光，不用 blur：小程序端 filter 支持不可靠 */
.mc-hero__glow {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
}

.mc-hero__glow--top {
  top: 24rpx;
  right: -140rpx;
  width: 380rpx;
  height: 380rpx;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.2) 0%, rgba(255, 255, 255, 0) 70%);
}

.mc-hero__glow--side {
  bottom: -240rpx;
  left: -160rpx;
  width: 460rpx;
  height: 460rpx;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.14) 0%, rgba(255, 255, 255, 0) 72%);
}

.mc-profile {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
}

.mc-profile--pressed {
  opacity: 0.8;
}

.mc-profile__avatar {
  width: 112rpx;
  height: 112rpx;
  flex-shrink: 0;
  border: 4rpx solid rgba(255, 255, 255, 0.7);
  border-radius: 50%;
  background-color: #fff;
  box-shadow: 0 8rpx 20rpx rgba(31, 44, 65, 0.12);
}

.mc-profile__meta {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  min-width: 0;
  margin-left: 24rpx;
}

.mc-profile__name {
  max-width: 420rpx;
  overflow: hidden;
  color: #fff;
  font-size: 38rpx;
  font-weight: 700;
  line-height: 1.3;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.mc-profile__hint {
  margin-top: 8rpx;
  color: rgba(255, 255, 255, 0.85);
  font-size: 24rpx;
}

/* 等级胶囊：徽章图 + 等级名 + 箭头，压在半透明玻璃片上 */
.mc-level {
  display: flex;
  align-items: center;
  align-self: flex-start;
  margin-top: 12rpx;
  padding: 6rpx 18rpx 6rpx 8rpx;
  /* 图标类会 `color: inherit`，箭头颜色必须由父容器给 */
  color: rgba(255, 255, 255, 0.82);
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.2);
}

.mc-level__medal {
  width: 52rpx;
  height: 52rpx;
  margin-right: 8rpx;
}

.mc-level__name {
  color: #fff;
  font-size: 22rpx;
  line-height: 1.5;
}

.mc-level__arrow {
  margin-left: 2rpx;
  font-size: 22rpx;
}

/* ===================== 资产卡 ===================== */

.mc-assets {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: stretch;
  margin: -80rpx $mc-gutter 0;
  padding: 26rpx 0;
  border-radius: 24rpx;
  background: #fff;
  box-shadow: 0 12rpx 30rpx rgba(31, 44, 65, 0.08);
}

.mc-assets__item {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  /* 分栏竖线用边框，省一个伪元素节点 */
  border-left: 1rpx solid $mc-hairline;
}

.mc-assets__item:first-child {
  border-left: 0;
}

.mc-assets__item--pressed {
  opacity: 0.55;
}

.mc-assets__value {
  display: flex;
  align-items: baseline;
  justify-content: center;
}

.mc-assets__unit {
  margin-right: 2rpx;
  color: $mc-title;
  font-size: 24rpx;
  font-weight: 600;
}

.mc-assets__number {
  color: $mc-title;
  font-size: 42rpx;
  font-weight: 700;
  line-height: 1.1;
}

.mc-assets__label {
  margin-top: 8rpx;
  color: $mc-label;
  font-size: 24rpx;
}

.mc-assets--guest {
  align-items: center;
  padding: 28rpx;
  /* 尾部箭头 color:inherit，颜色给容器 */
  color: $mc-muted;
}

.mc-assets__guest-icon {
  display: flex;
  color: $mc-label;
  align-items: center;
  justify-content: center;
  width: 72rpx;
  height: 72rpx;
  flex-shrink: 0;
  border-radius: 24rpx;
  background: #f5f6f8;
}

.mc-assets__guest-glyph {
  font-size: 36rpx;
}

.mc-assets__guest-text {
  flex: 1;
  min-width: 0;
  margin: 0 20rpx;
  color: $mc-text;
  font-size: 26rpx;
}

.mc-assets__guest-arrow {
  font-size: 28rpx;
}

/* ===================== 积分过期提醒 ===================== */

.mc-notice {
  display: flex;
  align-items: center;
  /* 警告图标 color:inherit，颜色给容器；正文/动作各自覆盖 */
  color: #e07a00;
  margin: 20rpx $mc-gutter 0;
  min-height: 88rpx;
  padding: 12rpx 24rpx;
  border-radius: 20rpx;
  background: #fff7e8;
}

.mc-notice--pressed {
  opacity: 0.7;
}

.mc-notice__icon {
  font-size: 32rpx;
}

.mc-notice__text {
  flex: 1;
  min-width: 0;
  margin: 0 16rpx;
  color: #8a4b00;
  font-size: 24rpx;
  line-height: 1.4;
}

.mc-notice__action {
  flex-shrink: 0;
  /* #e07a00 on #fff7e8 只有 2.84:1，与正文同色可达 6.39:1 */
  color: #8a4b00;
  font-size: 24rpx;
  font-weight: 600;
}

/* ===================== 卡片与权益 ===================== */

.mc-card {
  margin: 20rpx $mc-gutter 0;
  padding: 8rpx 24rpx 20rpx;
  border-radius: 24rpx;
  background: #fff;
  box-shadow: $mc-shadow;
}

.mc-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 88rpx;
}

.mc-card__title {
  color: $mc-title;
  font-size: 30rpx;
  font-weight: 700;
}

.mc-card__more {
  display: flex;
  align-items: center;
  height: 88rpx;
  padding-left: 24rpx;
  color: $mc-label;
  font-size: 22rpx;
}

.mc-card__more--pressed {
  opacity: 0.6;
}

.mc-card__more-arrow {
  margin-left: 2rpx;
  font-size: 22rpx;
}

.mc-benefits {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
}

.mc-benefit {
  box-sizing: border-box;
  display: flex;
  align-items: center;
  width: calc(50% - 8rpx);
  margin-bottom: 16rpx;
  padding: 18rpx 20rpx;
  border-radius: 20rpx;
  background: #f7f8fa;
}

.mc-benefit--pressed {
  opacity: 0.6;
}

.mc-benefit:nth-last-child(-n + 2) {
  margin-bottom: 0;
}

.mc-benefit__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 64rpx;
  height: 64rpx;
  /* 图标类 color:inherit：颜色给父容器 */
  color: $mc-primary;
  flex-shrink: 0;
  border-radius: 20rpx;
  background: #fff;
  box-shadow: 0 2rpx 8rpx rgba(31, 44, 65, 0.04);
}

.mc-benefit__glyph {
  font-size: 34rpx;
}

.mc-benefit__body {
  display: flex;
  flex-direction: column;
  min-width: 0;
  margin-left: 16rpx;
}

.mc-benefit__label {
  color: $mc-label;
  font-size: 22rpx;
  line-height: 1.3;
}

.mc-benefit__value {
  margin-top: 4rpx;
  color: $mc-title;
  font-size: 28rpx;
  font-weight: 700;
  line-height: 1.25;
}

.mc-benefit__value--off {
  /* #9ca3af 在 #f7f8fa 上只有 2.39:1；降权靠字重而不是靠对比度不足 */
  color: $mc-label;
  font-weight: 500;
}

.mc-benefits__guest {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8rpx 0 16rpx;
}

.mc-benefits__guest-text {
  flex: 1;
  min-width: 0;
  margin-right: 20rpx;
  color: $mc-label;
  font-size: 24rpx;
  line-height: 1.4;
}

.mc-benefits__guest-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 88rpx;
  padding: 0 32rpx;
  flex-shrink: 0;
  border-radius: 999rpx;
  background: $mc-primary;
  color: #fff;
  font-size: 26rpx;
  font-weight: 600;
  line-height: 1.4;
}

.mc-benefits__guest-btn--pressed {
  opacity: 0.8;
}

/* ===================== 每日签到横幅 ===================== */

.mc-signin {
  display: flex;
  align-items: center;
  margin: 20rpx $mc-gutter 0;
  padding: 24rpx;
  border-radius: 24rpx;
  background: linear-gradient(135deg, $mc-primary 0%, $mc-secondary 100%);
  box-shadow: 0 10rpx 24rpx rgba(31, 44, 65, 0.1);
}

.mc-signin--pressed {
  opacity: 0.88;
}

.mc-signin__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 84rpx;
  height: 84rpx;
  color: #fff;
  flex-shrink: 0;
  border-radius: 26rpx;
  background: rgba(255, 255, 255, 0.2);
}

.mc-signin__glyph {
  font-size: 44rpx;
}

.mc-signin__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  margin-left: 20rpx;
}

.mc-signin__title {
  color: #fff;
  font-size: 32rpx;
  font-weight: 700;
}

.mc-signin__desc {
  margin-top: 4rpx;
  color: rgba(255, 255, 255, 0.92);
  font-size: 24rpx;
}

.mc-signin__cta {
  padding: 12rpx 28rpx;
  flex-shrink: 0;
  border-radius: 999rpx;
  background: #fff;
  color: $mc-primary;
  font-size: 24rpx;
  font-weight: 700;
  line-height: 1.3;
}

/* ===================== 常用功能 ===================== */

.mc-card--entries {
  padding-bottom: 8rpx;
}

.mc-card__head--entries {
  height: 80rpx;
}

.mc-entry {
  display: flex;
  align-items: center;
  padding: 22rpx 0;
  color: $mc-muted;
}

.mc-entry--divided {
  border-top: 1rpx solid $mc-hairline;
}

.mc-entry--pressed {
  opacity: 0.6;
}

.mc-entry__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 72rpx;
  height: 72rpx;
  color: $mc-primary;
  flex-shrink: 0;
  border-radius: 22rpx;
  background: #f5f6f8;
}

.mc-entry__glyph {
  font-size: 36rpx;
}

.mc-entry__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  margin-left: 20rpx;
}

.mc-entry__title {
  color: $mc-title;
  font-size: 28rpx;
  font-weight: 600;
}

.mc-entry__desc {
  margin-top: 4rpx;
  color: $mc-label;
  font-size: 22rpx;
}

.mc-entry__arrow {
  font-size: 26rpx;
}
</style>

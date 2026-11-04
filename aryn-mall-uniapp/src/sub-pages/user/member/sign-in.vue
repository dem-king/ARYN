<script setup lang="ts">
import type { SignInConfig } from '@/sub-pages/api/user/signIn'
import { onLoad, onPageScroll, onShow } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useFrostedNavbar } from '@/composables/useFrostedNavbar'
import { formatSignInDate, useSignInStatus } from '@/composables/useSignInStatus'
import { getSignInConfigPage, getSignInRecordPage, signIn as signInApi } from '@/sub-pages/api/user/signIn'

const { handlePageScroll } = useFrostedNavbar()

definePage({
  name: 'member-sign-in',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '每日签到',
  },
})

const { t } = useI18n()
const router = useRouter()
const userStore = useUserStore()
const globalLoading = useGlobalLoading()
const { success: showSuccess } = useGlobalToast()

// 与会员中心同源的主题色：管理端可换肤，页面内不写死主色
const BRAND_COLOR = 'var(--theme-color-primary, var(--wot-color-theme-primary, #FF2237))'

const isSigned = ref(false)
const consecutiveDay = ref(0)
const rewardPoint = ref(0)
const signInConfigs = ref<SignInConfig[]>([])
const signedDates = ref<string[]>([])
// 签到成功后的「+N」飘分动画：key 递增强制重建节点重播动画
const floatVisible = ref(false)
const floatKey = ref(0)

/**
 * 与会员中心共用「今日签到态」：首帧先用本地快照渲染，
 * 已签到用户进来不会先看到一瞬「立即签到」，再由当月记录接口校正。
 * 快照只含「今天已签」，所以同步补一条当日日期，避免首帧「CTA 已签到 +
 * 本月签到 0 天 + 日历未打勾」三处口径不一致。
 */
const signInStatus = useSignInStatus()
if (signInStatus.syncFromCache()) {
  isSigned.value = true
  consecutiveDay.value = signInStatus.consecutiveDay.value
  signedDates.value = [formatSignInDate()]
}

onLoad(() => {
  loadSignInConfig()
})

onShow(() => {
  // 先用已有数据渲染日历，再拉取当月记录后覆盖刷新
  refreshCalendar()
  loadSignInRecord()
  userStore.refreshPointsInfo()
})

async function loadSignInConfig() {
  try {
    const response = await getSignInConfigPage({ current: 1, size: 100 })
    signInConfigs.value = response.records || []
  }
  catch (error) {
    console.error('加载签到配置失败:', error)
  }
}

async function loadSignInRecord() {
  try {
    const now = new Date()
    const range = currentMonthRange(now)
    const response = await getSignInRecordPage({
      current: 1,
      size: 31,
      beginDate: range.begin,
      endDate: range.end,
    })
    const records = response.records || []
    signedDates.value = records.map(r => r.signDate)
    // 检查今天是否已签到
    const today = formatSignInDate(now)
    isSigned.value = signedDates.value.includes(today)
    if (records.length > 0) {
      consecutiveDay.value = records[0].consecutiveDay || 0
    }
    // 记录接口是签到态的唯一事实来源：顺手刷新本地快照，供会员中心首帧直接渲染
    if (isSigned.value)
      signInStatus.markSigned(consecutiveDay.value, now)
    refreshCalendar()
  }
  catch (error) {
    console.error('加载签到记录失败:', error)
  }
}

/** 当月首末日，用于按当前月拉取签到记录（后端按 sign_date 过滤） */
function currentMonthRange(now: Date): { begin: string, end: string } {
  const year = now.getFullYear()
  const month = now.getMonth()
  return {
    begin: formatSignInDate(new Date(year, month, 1)),
    end: formatSignInDate(new Date(year, month + 1, 0)),
  }
}

async function handleSignIn() {
  if (isSigned.value)
    return
  globalLoading.loading(t('accountMember.signIn.signingIn'))
  try {
    const result = await signInApi()
    isSigned.value = true
    consecutiveDay.value = result.consecutiveDay
    rewardPoint.value = result.rewardPoint
    const today = formatSignInDate(new Date())
    signedDates.value.unshift(today)
    // 写本地快照：返回会员中心时横幅立即变「已签到」，不必等接口往返
    signInStatus.markSigned(result.consecutiveDay)
    // 签到成功后立即刷新日历，否则当日格子要等下次 onShow 才打勾
    refreshCalendar()
    if (result.rewardPoint > 0) {
      floatKey.value++
      floatVisible.value = true
      setTimeout(() => {
        floatVisible.value = false
      }, 900)
    }
    showSuccess(result.rewardPoint > 0
      ? t('accountMember.signIn.successWithReward', { points: result.rewardPoint })
      : t('accountMember.signIn.successNoReward'))
    userStore.refreshPointsInfo()
  }
  catch (error) {
    // 错误提示由请求层全局拦截器统一弹出，这里只记录日志避免重复 toast
    console.error('签到失败:', error)
  }
  finally {
    globalLoading.close()
  }
}

function toPointsRecord() {
  router.push({ name: 'member-points-record' })
}

// 生成当月日历数据
interface CalendarDay {
  date: string
  day: number
  isSigned: boolean
  isToday: boolean
  isFuture: boolean
}

function getCalendarDays(): CalendarDay[] {
  const now = new Date()
  const year = now.getFullYear()
  const month = now.getMonth()
  const today = now.getDate()
  const daysInMonth = new Date(year, month + 1, 0).getDate()

  const days: CalendarDay[] = []
  for (let d = 1; d <= daysInMonth; d++) {
    const date = new Date(year, month, d)
    const dateStr = formatSignInDate(date)
    days.push({
      date: dateStr,
      day: d,
      isSigned: signedDates.value.includes(dateStr),
      isToday: d === today,
      isFuture: d > today,
    })
  }
  return days
}

const calendarDays = ref<CalendarDay[]>([])

function refreshCalendar() {
  calendarDays.value = getCalendarDays()
}

/** 当月 1 号的星期下标（0=周日），用于日历首行补位对齐 */
const leadingBlanks = computed(() => {
  const now = new Date()
  return new Date(now.getFullYear(), now.getMonth(), 1).getDay()
})

/** 日历标题「2026年10月」 */
const calendarTitle = computed(() => {
  const now = new Date()
  return t('accountMember.signIn.calendarMonth', { year: now.getFullYear(), month: now.getMonth() + 1 })
})

/** 奖励档位按连续天数升序，作为进度条刻度 */
const progressCells = computed(() => {
  return [...signInConfigs.value]
    .sort((a, b) => Number(a.consecutiveDay) - Number(b.consecutiveDay))
    .map(config => ({
      ...config,
      reached: consecutiveDay.value >= Number(config.consecutiveDay),
    }))
})

const litCount = computed(() => progressCells.value.filter(cell => cell.reached).length)

/** 今天待签的档位下标：未签时指向下一档；已签或超出配置范围则无 */
const pendingCellIndex = computed(() => {
  if (isSigned.value || litCount.value >= progressCells.value.length)
    return -1
  return litCount.value
})

/** 进度线覆盖宽度：从首档圆心近似推进到最近达成档（圆点自身状态已精确表意） */
const litLineWidth = computed(() => {
  const total = progressCells.value.length
  if (litCount.value <= 0 || total <= 0)
    return '0%'
  return `${Math.min(litCount.value / total, 1) * 100}%`
})

/** 进度提示：今日可得 / 再签 N 天可得 / 已达成展示连签天数 */
const progressHint = computed(() => {
  const cells = progressCells.value
  if (!cells.length)
    return ''
  if (!isSigned.value) {
    const next = cells[litCount.value]
    return next
      ? t('accountMember.signIn.todayRewardHint', { points: next.rewardPoint })
      : t('accountMember.signIn.consecutiveDays', { days: consecutiveDay.value })
  }
  const next = cells[litCount.value]
  if (!next)
    return t('accountMember.signIn.consecutiveDays', { days: consecutiveDay.value })
  const gap = Math.max(Number(next.consecutiveDay) - consecutiveDay.value, 1)
  return t('accountMember.signIn.nextRewardHint', { days: gap, points: next.rewardPoint })
})

// 顶栏磨砂玻璃：字面量注册滚动（小程序按页面源码生成滚动位标记，包进 composable 会静默失效）
onPageScroll(handlePageScroll)
</script>

<template>
  <hr-navbar
    :title="t('accountMember.signIn.title')"
    :bordered="false"
    :background-color="BRAND_COLOR"
    text-color="#ffffff"
  />
  <view class="si-page">
    <!-- 主题渐变 Hero：与导航栏同色，180deg 收口到辅色 -->
    <view class="si-hero">
      <view class="si-hero__glow si-hero__glow--a" />
      <view class="si-hero__glow si-hero__glow--b" />

      <view class="si-hero__top">
        <view class="si-hero__points">
          <text class="si-hero__points-label">
            {{ t('accountMember.signIn.currentPoints') }}
          </text>
          <view class="si-hero__points-value">
            {{ userStore.getPoint }}
          </view>
        </view>
        <view
          class="si-hero__detail"
          hover-class="si-hero__detail--pressed"
          :hover-stay-time="80"
          @click="toPointsRecord"
        >
          <text>{{ t('accountMember.signIn.pointsDetail') }}</text>
          <text class="i-carbon:chevron-right si-hero__detail-arrow" />
        </view>
      </view>

      <view class="si-hero__stats">
        <view class="si-stat">
          <view class="si-stat__value">
            {{ consecutiveDay }}
            <text class="si-stat__unit">
              {{ t('accountMember.signIn.unitDay') }}
            </text>
          </view>
          <text class="si-stat__label">
            {{ t('accountMember.signIn.streakLabel') }}
          </text>
        </view>
        <view class="si-stat__divider" />
        <view class="si-stat">
          <view class="si-stat__value">
            {{ signedDates.length }}
            <text class="si-stat__unit">
              {{ t('accountMember.signIn.unitDay') }}
            </text>
          </view>
          <text class="si-stat__label">
            {{ t('accountMember.signIn.monthSigned') }}
          </text>
        </view>
      </view>

      <view
        class="si-cta"
        :class="{ 'si-cta--signed': isSigned }"
        hover-class="si-cta--pressed"
        :hover-stay-time="80"
        @click="handleSignIn"
      >
        <view v-if="floatVisible" :key="floatKey" class="si-cta__float">
          +{{ rewardPoint }}
        </view>
        <text v-if="isSigned" class="i-carbon:checkmark si-cta__check" />
        <text>{{ isSigned ? t('accountMember.signIn.checkedToday') : t('accountMember.signIn.checkInNow') }}</text>
      </view>
    </view>

    <!-- 连签奖励进度卡：上浮压住渐变收口 -->
    <view v-if="progressCells.length" class="si-card si-card--float">
      <view class="si-card__head">
        <text class="si-card__title">
          {{ t('accountMember.signIn.rewards') }}
        </text>
        <text v-if="progressHint" class="si-progress__hint">
          {{ progressHint }}
        </text>
      </view>
      <scroll-view class="si-progress__scroll" :scroll-x="true" :show-scrollbar="false">
        <view class="si-progress__track">
          <view class="si-progress__line" />
          <view v-if="litCount > 0" class="si-progress__line si-progress__line--lit" :style="{ width: litLineWidth }" />
          <view
            v-for="(cell, index) in progressCells"
            :key="cell.id"
            class="si-progress__cell"
          >
            <text class="si-progress__reward" :class="{ 'is-reached': cell.reached }">
              {{ t('accountMember.signIn.rewardPoints', { points: cell.rewardPoint }) }}
            </text>
            <view
              class="si-progress__dot"
              :class="{ 'is-reached': cell.reached, 'is-pending': index === pendingCellIndex }"
            >
              <text v-if="cell.reached" class="i-carbon:checkmark si-progress__check" />
            </view>
            <text class="si-progress__day" :class="{ 'is-reached': cell.reached }">
              {{ t('accountMember.signIn.daysUnit', { days: cell.consecutiveDay }) }}
            </text>
          </view>
        </view>
      </scroll-view>
    </view>

    <!-- 签到日历 -->
    <view class="si-card">
      <view class="si-card__head">
        <text class="si-card__title">
          {{ t('accountMember.signIn.calendar') }}
        </text>
        <text class="si-calendar__month">
          {{ calendarTitle }}
        </text>
      </view>
      <view class="si-calendar__week">
        <text v-for="i in 7" :key="i" class="si-calendar__weekday">
          {{ t(`accountMember.signIn.weekday${i - 1}`) }}
        </text>
      </view>
      <view class="si-calendar__grid">
        <view v-for="blank in leadingBlanks" :key="`blank-${blank}`" class="si-calendar__cell" />
        <view v-for="item in calendarDays" :key="item.date" class="si-calendar__cell">
          <view
            class="si-calendar__day"
            :class="{
              'is-signed': item.isSigned,
              'is-today': item.isToday && !item.isSigned,
              'is-future': item.isFuture,
            }"
          >
            <text v-if="item.isSigned" class="i-carbon:checkmark si-calendar__check" />
            <text v-else>
              {{ item.day }}
            </text>
          </view>
        </view>
      </view>
    </view>

    <view class="si-safe" />
  </view>
</template>

<style lang="scss" scoped>
/* 主题色与会员中心同源：管理端可换肤，页面内不出现写死的主色 */
$si-primary: var(--theme-color-primary, var(--wot-color-theme-primary, #FF2237));
$si-secondary: var(--theme-color-secondary, var(--wot-color-theme-secondary, #FF6B7A));
$si-hairline: var(--app-border);
$si-ink: var(--app-text);
$si-ink-2: var(--app-text-2);
$si-ink-3: var(--app-text-3);
$si-gutter: 20rpx;
$si-shadow: 0 8rpx 24rpx rgba(31, 44, 65, 0.06);

.si-page {
  min-height: 100vh;
  background: var(--app-bg);
  padding-bottom: calc(32rpx + constant(safe-area-inset-bottom));
  padding-bottom: calc(32rpx + env(safe-area-inset-bottom));
}

/* ===================== Hero ===================== */

.si-hero {
  position: relative;
  overflow: hidden;
  padding: 24rpx 32rpx 148rpx;
  /* 顶部与导航栏同为纯主色，180deg 收口才能让接缝不可见 */
  background: linear-gradient(180deg, $si-primary 0%, $si-secondary 100%);
}

/* 径向柔光：小程序端 filter 支持不可靠，不用 blur */
.si-hero__glow {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
}

.si-hero__glow--a {
  top: 16rpx;
  right: -140rpx;
  width: 380rpx;
  height: 380rpx;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.2) 0%, rgba(255, 255, 255, 0) 70%);
}

.si-hero__glow--b {
  bottom: -220rpx;
  left: -160rpx;
  width: 440rpx;
  height: 440rpx;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.14) 0%, rgba(255, 255, 255, 0) 72%);
}

.si-hero__top {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}

.si-hero__points-label {
  color: rgba(255, 255, 255, 0.82);
  font-size: 24rpx;
}

.si-hero__points-value {
  margin-top: 6rpx;
  color: #fff;
  font-size: 64rpx;
  font-weight: 700;
  line-height: 1.15;
  /* 积分数变化宽度稳定，避免入口胶囊被推移 */
  font-variant-numeric: tabular-nums;
}

.si-hero__detail {
  position: relative;
  display: flex;
  align-items: center;
  margin-top: 8rpx;
  padding: 10rpx 14rpx 10rpx 22rpx;
  color: rgba(255, 255, 255, 0.9);
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.18);
  font-size: 24rpx;
  /* 视觉保持胶囊大小，热区外扩到 ~44px 触控标准 */
}

.si-hero__detail::after {
  content: '';
  position: absolute;
  inset: -16rpx -20rpx;
}

.si-hero__detail--pressed {
  opacity: 0.6;
}

.si-hero__detail-arrow {
  margin-left: 2rpx;
  font-size: 22rpx;
}

.si-hero__stats {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: stretch;
  margin-top: 28rpx;
  padding: 20rpx 0;
  border-radius: 20rpx;
  background: rgba(255, 255, 255, 0.14);
}

.si-stat {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.si-stat__value {
  color: #fff;
  font-size: 36rpx;
  font-weight: 700;
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
}

.si-stat__unit {
  margin-left: 2rpx;
  font-size: 22rpx;
  font-weight: 400;
}

.si-stat__label {
  margin-top: 4rpx;
  color: rgba(255, 255, 255, 0.78);
  font-size: 22rpx;
}

.si-stat__divider {
  width: 1rpx;
  background: rgba(255, 255, 255, 0.24);
}

/* ===================== CTA ===================== */

.si-cta {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 92rpx;
  margin-top: 28rpx;
  border-radius: 999rpx;
  background: #fff;
  color: $si-primary;
  font-size: 32rpx;
  font-weight: 600;
  transition: opacity 0.15s ease-out;
}

.si-cta--pressed {
  opacity: 0.85;
}

.si-cta--signed {
  background: rgba(255, 255, 255, 0.22);
  color: rgba(255, 255, 255, 0.9);
}

.si-cta__check {
  margin-right: 8rpx;
  font-size: 30rpx;
}

.si-cta__float {
  position: absolute;
  top: -12rpx;
  left: 50%;
  color: #fff;
  font-size: 44rpx;
  font-weight: 700;
  pointer-events: none;
  text-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.18);
  animation: si-float-up 0.9s ease-out forwards;
}

@keyframes si-float-up {
  0% {
    opacity: 0;
    transform: translate(-50%, 24rpx);
  }

  25% {
    opacity: 1;
  }

  100% {
    opacity: 0;
    transform: translate(-50%, -128rpx);
  }
}

/* ===================== 卡片 ===================== */

.si-card {
  position: relative;
  margin: $si-gutter;
  padding: 28rpx 24rpx 24rpx;
  border-radius: 24rpx;
  background: var(--app-card);
  box-shadow: $si-shadow;
}

.si-card--float {
  z-index: 2;
  /* 上浮压住渐变收口 */
  margin-top: -100rpx;
}

.si-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20rpx;
}

.si-card__title {
  color: $si-ink;
  font-size: 30rpx;
  font-weight: 700;
}

/* ===================== 连签进度 ===================== */

.si-progress__hint {
  max-width: 420rpx;
  overflow: hidden;
  color: $si-ink-2;
  font-size: 22rpx;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.si-progress__scroll {
  width: 100%;
}

.si-progress__track {
  position: relative;
  display: inline-flex;
  min-width: 100%;
  padding: 8rpx 0 4rpx;
}

.si-progress__line {
  position: absolute;
  top: 96rpx;
  left: 60rpx;
  right: 60rpx;
  height: 4rpx;
  border-radius: 2rpx;
  background: $si-hairline;
}

.si-progress__line--lit {
  right: auto;
  background: $si-primary;
  opacity: 0.85;
  transition: width 0.3s ease-out;
}

.si-progress__cell {
  position: relative;
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  min-width: 120rpx;
}

.si-progress__reward {
  color: $si-ink-3;
  font-size: 22rpx;
  font-weight: 500;
}

.si-progress__reward.is-reached {
  color: $si-primary;
}

.si-progress__dot {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56rpx;
  height: 56rpx;
  margin-top: 12rpx;
  border: 4rpx solid transparent;
  border-radius: 50%;
  background: var(--app-card-2);
  /* 图标类 color: inherit，勾的颜色由父容器给定 */
  color: #fff;
}

.si-progress__dot.is-reached {
  border-color: transparent;
  background: $si-primary;
}

.si-progress__dot.is-pending {
  border-color: $si-primary;
  background: var(--app-card);
}

.si-progress__check {
  font-size: 28rpx;
}

.si-progress__day {
  margin-top: 12rpx;
  color: $si-ink-2;
  font-size: 22rpx;
}

.si-progress__day.is-reached {
  color: $si-ink;
  font-weight: 600;
}

/* ===================== 日历 ===================== */

.si-calendar__month {
  color: $si-ink-2;
  font-size: 22rpx;
  font-variant-numeric: tabular-nums;
}

.si-calendar__week {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  margin-bottom: 8rpx;
}

.si-calendar__weekday {
  color: $si-ink-3;
  font-size: 22rpx;
  text-align: center;
}

.si-calendar__grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  row-gap: 20rpx;
}

.si-calendar__cell {
  display: flex;
  align-items: center;
  justify-content: center;
}

.si-calendar__day {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 64rpx;
  height: 64rpx;
  border: 3rpx solid transparent;
  border-radius: 50%;
  color: $si-ink;
  font-size: 26rpx;
  font-variant-numeric: tabular-nums;
}

.si-calendar__day.is-signed {
  border-color: transparent;
  background: $si-primary;
  color: #fff;
}

.si-calendar__day.is-today {
  border-color: $si-primary;
  color: $si-primary;
  font-weight: 700;
}

.si-calendar__day.is-future {
  color: $si-ink-3;
}

.si-calendar__check {
  font-size: 30rpx;
}

.si-safe {
  height: 1rpx;
}
</style>

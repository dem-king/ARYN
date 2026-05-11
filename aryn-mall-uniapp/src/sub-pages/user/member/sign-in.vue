<script setup lang="ts">
import { ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { signIn as signInApi, getSignInConfigPage, getSignInRecordPage } from '@/api/user/signIn'
import type { SignInConfig } from '@/api/user/signIn'

definePage({
  name: 'member-sign-in',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '每日签到',
  },
})

const userStore = useUserStore()
const globalLoading = useGlobalLoading()
const { success: showSuccess, error: showError } = useGlobalToast()

const isSigned = ref(false)
const consecutiveDay = ref(0)
const rewardPoint = ref(0)
const signInConfigs = ref<SignInConfig[]>([])
const signedDates = ref<string[]>([])

onLoad(() => {
  loadSignInConfig()
})

onShow(() => {
  loadSignInRecord()
  userStore.refreshPointsInfo()
})

async function loadSignInConfig() {
  try {
    const response = await getSignInConfigPage({ current: 1, size: 100, desc: 'sort_order' })
    signInConfigs.value = response.records || []
  }
  catch (error) {
    console.error('加载签到配置失败:', error)
  }
}

async function loadSignInRecord() {
  try {
    const now = new Date()
    const year = now.getFullYear()
    const month = now.getMonth() + 1
    const response = await getSignInRecordPage({
      current: 1,
      size: 31,
      desc: 'sign_date',
    })
    const records = response.records || []
    signedDates.value = records.map((r: any) => r.signDate)
    // 检查今天是否已签到
    const today = formatDate(now)
    isSigned.value = signedDates.value.includes(today)
    if (records.length > 0) {
      consecutiveDay.value = records[0].consecutiveDay || 0
    }
  }
  catch (error) {
    console.error('加载签到记录失败:', error)
  }
}

async function handleSignIn() {
  if (isSigned.value) return
  globalLoading.loading('签到中...')
  try {
    const result = await signInApi()
    isSigned.value = true
    consecutiveDay.value = result.consecutiveDay
    rewardPoint.value = result.rewardPoint
    const today = formatDate(new Date())
    signedDates.value.unshift(today)
    showSuccess(`签到成功！获得${result.rewardPoint}积分`)
    userStore.refreshPointsInfo()
  }
  catch (error: any) {
    showError(error?.message || '签到失败')
  }
  finally {
    globalLoading.close()
  }
}

function formatDate(date: Date): string {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

// 生成当月日历数据
function getCalendarDays(): { date: string, day: number, isSigned: boolean, isToday: boolean, isFuture: boolean }[] {
  const now = new Date()
  const year = now.getFullYear()
  const month = now.getMonth()
  const today = now.getDate()
  const daysInMonth = new Date(year, month + 1, 0).getDate()
  const todayStr = formatDate(now)

  const days: { date: string, day: number, isSigned: boolean, isToday: boolean, isFuture: boolean }[] = []
  for (let d = 1; d <= daysInMonth; d++) {
    const date = new Date(year, month, d)
    const dateStr = formatDate(date)
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

const calendarDays = ref<{ date: string, day: number, isSigned: boolean, isToday: boolean, isFuture: boolean }[]>([])

function refreshCalendar() {
  calendarDays.value = getCalendarDays()
}

// 监听签到记录变化刷新日历
onShow(() => {
  refreshCalendar()
})
</script>

<template>
  <hr-navbar title="每日签到" />
  <!-- 签到状态卡片 -->
  <view class="sign-card">
    <view class="card-bg" />
    <view class="card-content">
      <view class="text-center">
        <view class="text-14px text-white opacity-80">当前积分</view>
        <view class="text-36px font-bold text-white mt-1">{{ userStore.getPoint }}</view>
      </view>
      <view class="mt-4 flex items-center justify-center">
        <view v-if="consecutiveDay > 0" class="text-13px text-white opacity-80">
          已连续签到{{ consecutiveDay }}天
        </view>
      </view>
      <view class="mt-4">
        <wd-button
          :disabled="isSigned"
          :custom-class="isSigned ? 'signed-btn' : 'sign-btn'"
          block
          @click="handleSignIn"
        >
          {{ isSigned ? '今日已签到' : '立即签到' }}
        </wd-button>
      </view>
    </view>
  </view>

  <!-- 签到日历 -->
  <view class="m-2 rounded-xl bg-white p-4">
    <view class="text-14px font-bold mb-2">签到日历</view>
    <view class="calendar-grid">
      <view v-for="item in calendarDays" :key="item.date" class="calendar-item">
        <view
          class="calendar-day"
          :class="{
            'is-signed': item.isSigned,
            'is-today': item.isToday,
            'is-future': item.isFuture,
          }"
        >
          {{ item.day }}
        </view>
        <view v-if="item.isSigned" class="i-carbon:checkmark text-10px color-primary mt-1" />
      </view>
    </view>
  </view>

  <!-- 签到奖励规则 -->
  <view v-if="signInConfigs.length > 0" class="m-2 rounded-xl bg-white p-4">
    <view class="text-14px font-bold mb-2">签到奖励</view>
    <view v-for="config in signInConfigs" :key="config.id" class="flex items-center justify-between py-2 border-b border-gray-100 last:border-0">
      <view class="text-13px">连续签到{{ config.consecutiveDay }}天</view>
      <view class="text-13px color-primary">+{{ config.rewardPoint }}积分</view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.sign-card {
  position: relative;
  margin: 20rpx;
  border-radius: 20rpx;
  overflow: hidden;

  .card-bg {
    position: absolute;
    inset: 0;
    background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
  }

  .card-content {
    position: relative;
    padding: 40rpx 30rpx;
    z-index: 1;
  }
}

:deep(.sign-btn) {
  background: #fff !important;
  color: #f5576c !important;
  border-color: #fff !important;
}

:deep(.signed-btn) {
  background: rgba(255, 255, 255, 0.5) !important;
  color: #fff !important;
  border-color: transparent !important;
}

.calendar-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 8rpx;
}

.calendar-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 8rpx 0;
}

.calendar-day {
  width: 56rpx;
  height: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  font-size: 24rpx;

  &.is-signed {
    background: var(--wot-color-theme);
    color: #fff;
  }

  &.is-today {
    border: 2rpx solid var(--wot-color-theme);
  }

  &.is-future {
    color: #c0c0c0;
  }
}

.color-primary {
  color: var(--wot-color-theme);
}
</style>

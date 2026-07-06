<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad, onShow, onReachBottom } from '@dcloudio/uni-app'
import {
  getAppMemberLevels,
  createPaidOrder,
  getGrowthLogPage,
} from '@/api/user/member'
import type { MemberLevelInfo, GrowthLogItem } from '@/api/user/member'
import { getLevelBenefits } from '@/api/user/benefit'

definePage({
  name: 'member-center-new',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '会员中心',
  },
})

const userStore = useUserStore()
const globalLoading = useGlobalLoading()
const { success: showSuccess, error: showError } = useGlobalToast()

const levelList = ref<MemberLevelInfo[]>([])
const currentLevel = ref<MemberLevelInfo | null>(null)
const benefits = ref<any[]>([])
const growthLogs = ref<GrowthLogItem[]>([])
const growthLogPage = ref({ current: 1, size: 10, total: 0 })
const growthLogLoading = ref(false)
const paidLoading = ref(false)

// 成长值进度百分比
const growthPercent = computed(() => {
  const current = userStore.getGrowthValue || 0
  const next = currentLevel.value?.growthValue || 0
  if (next <= 0) return 100
  return Math.min(Math.round((current / next) * 100), 100)
})

// 当前成长值
const currentGrowthValue = computed(() => userStore.getGrowthValue || 0)

// 是否有付费等级
const paidLevels = computed(() =>
  levelList.value.filter((l) => l.isPaid === '1'),
)

onLoad(() => {
  loadLevelList()
  loadGrowthLogs()
})

onShow(() => {
  userStore.refreshPointsInfo()
})

onReachBottom(() => {
  loadMoreGrowthLogs()
})

async function loadLevelList() {
  globalLoading.loading('加载中...')
  try {
    const response = await getAppMemberLevels()
    levelList.value = response || []
    // 找到当前等级
    if (userStore.getLevelName) {
      currentLevel.value =
        levelList.value.find((l) => l.levelName === userStore.getLevelName) ||
        null
    }
    // 加载当前等级权益
    if (currentLevel.value) {
      loadBenefits(currentLevel.value.id)
    }
  }
  catch (error) {
    console.error('加载等级列表失败:', error)
  }
  finally {
    globalLoading.close()
  }
}

async function loadBenefits(levelId: string) {
  try {
    const response = await getLevelBenefits(levelId)
    benefits.value = response || []
  }
  catch (error) {
    console.error('加载权益失败:', error)
  }
}

async function loadGrowthLogs() {
  growthLogLoading.value = true
  try {
    const response = await getGrowthLogPage({
      current: growthLogPage.value.current,
      size: growthLogPage.value.size,
      desc: 'create_time',
    })
    growthLogs.value = response.records || []
    growthLogPage.value.total = response.total || 0
  }
  catch (error) {
    console.error('加载成长值记录失败:', error)
  }
  finally {
    growthLogLoading.value = false
  }
}

async function loadMoreGrowthLogs() {
  const loaded = growthLogPage.value.current * growthLogPage.value.size
  if (loaded >= growthLogPage.value.total) return
  growthLogLoading.value = true
  try {
    growthLogPage.value.current++
    const response = await getGrowthLogPage({
      current: growthLogPage.value.current,
      size: growthLogPage.value.size,
      desc: 'create_time',
    })
    growthLogs.value.push(...(response.records || []))
  }
  catch (error) {
    growthLogPage.value.current--
    console.error('加载更多成长值记录失败:', error)
  }
  finally {
    growthLogLoading.value = false
  }
}

async function handleOpenPaid(level: MemberLevelInfo) {
  paidLoading.value = true
  try {
    await createPaidOrder({ levelId: level.id })
    showSuccess('开通成功！')
    userStore.refreshPointsInfo()
    loadLevelList()
  }
  catch (error: any) {
    showError(error?.message || '开通失败')
  }
  finally {
    paidLoading.value = false
  }
}

function getSourceTypeLabel(type: string): string {
  const map: Record<string, string> = {
    ORDER: '消费',
    SIGN_IN: '签到',
    RECHARGE: '充值',
    BIRTHDAY: '生日礼包',
    MANUAL: '手动调整',
  }
  return map[type] || type
}
</script>

<template>
  <hr-navbar title="会员中心" />

  <!-- 当前等级卡片 -->
  <view class="level-card">
    <view class="card-bg" />
    <view class="card-content">
      <view class="flex items-center">
        <image
          v-if="currentLevel?.levelIcon"
          :src="currentLevel.levelIcon"
          class="h-80rpx w-80rpx"
          mode="aspectFit"
        />
        <view v-else class="level-icon-placeholder">
          {{ currentLevel?.levelName?.charAt(0) || 'V' }}
        </view>
        <view class="ml-20rpx">
          <view class="text-18px font-bold text-white">
            {{ userStore.getLevelName || '普通会员' }}
          </view>
          <view class="mt-1 flex items-center">
            <text class="text-12px text-white opacity-80">
              成长值 {{ currentGrowthValue }}
              <template v-if="currentLevel?.growthValue">
                / {{ currentLevel.growthValue }}
              </template>
            </text>
          </view>
        </view>
      </view>
      <!-- 成长值进度条 -->
      <view class="mt-4">
        <view class="progress-bar">
          <view class="progress-fill" :style="{ width: `${growthPercent}%` }" />
        </view>
        <view class="mt-1 flex justify-between">
          <text class="text-11px text-white opacity-70">当前成长值</text>
          <text class="text-11px text-white opacity-70">
            {{ growthPercent }}%
          </text>
        </view>
      </view>
    </view>
  </view>

  <!-- 等级权益列表 -->
  <view class="m-2 rounded-xl bg-white p-4">
    <view class="text-15px font-bold mb-3">等级权益</view>
    <view v-if="benefits.length === 0" class="text-center text-gray-400 py-4">
      暂无权益
    </view>
    <view v-for="benefit in benefits" :key="benefit.id" class="benefit-item">
      <view class="flex items-center">
        <text class="i-carbon:checkmark-filled text-16px color-primary mr-2" />
        <text class="text-14px">{{ benefit.benefitName }}</text>
      </view>
      <text v-if="benefit.benefitValue" class="text-13px color-primary font-bold">
        {{ benefit.benefitValue }}
      </text>
    </view>
    <!-- 专属折扣 -->
    <view
      v-if="currentLevel?.exclusiveDiscount && currentLevel.exclusiveDiscount < 100"
      class="benefit-item"
    >
      <view class="flex items-center">
        <text class="i-carbon:checkmark-filled text-16px color-primary mr-2" />
        <text class="text-14px">专属折扣</text>
      </view>
      <text class="text-13px color-primary font-bold">
        {{ currentLevel.exclusiveDiscount }}%
      </text>
    </view>
    <!-- 生日礼包 -->
    <view
      v-if="currentLevel?.birthdayGiftPoints && currentLevel.birthdayGiftPoints > 0"
      class="benefit-item"
    >
      <view class="flex items-center">
        <text class="i-carbon:checkmark-filled text-16px color-primary mr-2" />
        <text class="text-14px">生日礼包积分</text>
      </view>
      <text class="text-13px color-primary font-bold">
        {{ currentLevel.birthdayGiftPoints }}积分
      </text>
    </view>
  </view>

  <!-- 开通/续费付费会员 -->
  <view v-if="paidLevels.length > 0" class="m-2 rounded-xl bg-white p-4">
    <view class="text-15px font-bold mb-3">付费会员</view>
    <view
      v-for="level in paidLevels"
      :key="level.id"
      class="paid-level-card"
    >
      <view class="flex items-center justify-between">
        <view class="flex items-center">
          <image
            v-if="level.levelIcon"
            :src="level.levelIcon"
            class="h-48rpx w-48rpx"
            mode="aspectFit"
          />
          <view class="ml-2">
            <view class="text-14px font-bold">{{ level.levelName }}</view>
            <view class="text-12px text-gray-400 mt-1">
              ¥{{ level.price }}/{{ level.duration }}个月
            </view>
          </view>
        </view>
        <wd-button
          type="primary"
          size="small"
          :loading="paidLoading"
          @click="handleOpenPaid(level)"
        >
          开通
        </wd-button>
      </view>
    </view>
  </view>

  <!-- 成长值记录 -->
  <view class="m-2 rounded-xl bg-white p-4">
    <view class="text-15px font-bold mb-3">成长值记录</view>
    <view v-if="growthLogs.length === 0" class="text-center text-gray-400 py-4">
      暂无记录
    </view>
    <view
      v-for="log in growthLogs"
      :key="log.id"
      class="growth-log-item"
    >
      <view class="flex items-center justify-between">
        <view>
          <view class="text-14px">{{ log.sourceDesc || getSourceTypeLabel(log.sourceType) }}</view>
          <view class="text-12px text-gray-400 mt-1">{{ log.createTime }}</view>
        </view>
        <text
          class="text-15px font-bold"
          :class="log.growthValue > 0 ? 'color-primary' : 'text-red-500'"
        >
          {{ log.growthValue > 0 ? '+' : '' }}{{ log.growthValue }}
        </text>
      </view>
    </view>
    <view v-if="growthLogLoading" class="text-center text-gray-400 py-3">
      加载中...
    </view>
  </view>
</template>

<style lang="scss" scoped>
.level-card {
  position: relative;
  margin: 20rpx;
  border-radius: 20rpx;
  overflow: hidden;

  .card-bg {
    position: absolute;
    inset: 0;
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  }

  .card-content {
    position: relative;
    padding: 40rpx 30rpx;
    z-index: 1;
  }
}

.level-icon-placeholder {
  width: 80rpx;
  height: 80rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.25);
  color: #fff;
  font-size: 32rpx;
  font-weight: bold;
}

.progress-bar {
  height: 12rpx;
  background: rgba(255, 255, 255, 0.3);
  border-radius: 6rpx;
  overflow: hidden;

  .progress-fill {
    height: 100%;
    background: #fff;
    border-radius: 6rpx;
    transition: width 0.3s ease;
  }
}

.benefit-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16rpx 0;
  border-bottom: 1rpx solid #f5f5f5;

  &:last-child {
    border-bottom: none;
  }
}

.paid-level-card {
  padding: 20rpx 0;
  border-bottom: 1rpx solid #f5f5f5;

  &:last-child {
    border-bottom: none;
  }
}

.growth-log-item {
  padding: 16rpx 0;
  border-bottom: 1rpx solid #f5f5f5;

  &:last-child {
    border-bottom: none;
  }
}

.color-primary {
  color: var(--wot-color-theme);
}
</style>
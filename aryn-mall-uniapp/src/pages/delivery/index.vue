<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { getActiveTrip, getMyTaskPage, getTripStatusName, getTripStatusColor } from '@/api/delivery'
import type { DeliveryTrip } from '@/api/delivery'

definePage({
  name: 'delivery-index',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '配送工作台',
  },
})

const globalLoading = useGlobalLoading()
const router = useRouter()

const loading = ref(true)
const activeTrip = ref<DeliveryTrip | null>(null)
/** 待处理任务数（待取货+配货中+待送达） */
const pendingTaskCount = ref(0)
/** 今日已完成任务数 */
const todayDoneCount = ref(0)

onShow(() => {
  fetchData()
})

async function fetchData() {
  globalLoading.loading('加载中...')
  loading.value = true
  try {
    // 并行获取进行中出车单和待处理任务数
    const [trip, pending2, pending3, pending4] = await Promise.all([
      getActiveTrip().send().catch(() => null),
      getMyTaskPage({ current: 1, size: 1, status: '2' }).send().catch(() => ({ total: 0 })),
      getMyTaskPage({ current: 1, size: 1, status: '3' }).send().catch(() => ({ total: 0 })),
      getMyTaskPage({ current: 1, size: 1, status: '4' }).send().catch(() => ({ total: 0 })),
    ])
    activeTrip.value = trip as DeliveryTrip | null
    pendingTaskCount.value = ((pending2 as any)?.total ?? 0) + ((pending3 as any)?.total ?? 0) + ((pending4 as any)?.total ?? 0)
    // 获取今日已完成任务数（已送达+已签收）
    try {
      const [done5, done6] = await Promise.all([
        getMyTaskPage({ current: 1, size: 1, status: '5' }).send(),
        getMyTaskPage({ current: 1, size: 1, status: '6' }).send(),
      ])
      todayDoneCount.value = ((done5 as any)?.total ?? 0) + ((done6 as any)?.total ?? 0)
    }
    catch {
      todayDoneCount.value = 0
    }
  }
  finally {
    loading.value = false
    globalLoading.close()
  }
}

/** 跳转到出车单详情 */
function toTripDetail() {
  if (!activeTrip.value)
    return
  router.push({
    name: 'delivery-trip-detail',
    params: { id: activeTrip.value.id },
  })
}

/** 跳转到我的任务列表 */
function toTaskList() {
  router.push({ name: 'delivery-task-list' })
}

/** 跳转到个人中心 */
function toProfile() {
  router.push({ name: 'delivery-profile' })
}

/** 下拉刷新 */
function onRefresh() {
  fetchData()
}
</script>

<template>
  <hr-navbar title="配送工作台" />
  <view v-if="!loading" class="min-h-screen bg-gray-50 pb-100rpx">
    <!-- 顶部统计卡片 -->
    <view class="m-20rpx rounded-20rpx bg-gradient-to-r from-primary to-primary-light p-30rpx text-white">
      <view class="flex items-center justify-between">
        <view>
          <view class="text-24rpx opacity-80">
            待处理任务
          </view>
          <view class="mt-10rpx text-48rpx font-bold">
            {{ pendingTaskCount }}
          </view>
        </view>
        <view>
          <view class="text-24rpx opacity-80">
            今日已完成
          </view>
          <view class="mt-10rpx text-48rpx font-bold">
            {{ todayDoneCount }}
          </view>
        </view>
      </view>
    </view>

    <!-- 当前进行中的出车单 -->
    <view class="mx-20rpx mb-20rpx rounded-20rpx bg-white p-30rpx">
      <view class="mb-20rpx flex items-center justify-between">
        <text class="text-30rpx font-bold">
          当前出车单
        </text>
        <text
          v-if="activeTrip"
          class="rounded-8rpx px-16rpx py-4rpx text-24rpx text-white"
          :style="{ backgroundColor: getTripStatusColor(activeTrip.status) }"
        >
          {{ getTripStatusName(activeTrip.status) }}
        </text>
      </view>

      <view v-if="activeTrip" class="active-trip-card" @click="toTripDetail">
        <view class="flex items-center justify-between py-10rpx">
          <text class="text-26rpx text-gray-600">
            出车单号
          </text>
          <text class="text-26rpx">
            {{ activeTrip.tripNo }}
          </text>
        </view>
        <view class="flex items-center justify-between py-10rpx">
          <text class="text-26rpx text-gray-600">
            仓库
          </text>
          <text class="text-26rpx">
            {{ activeTrip.warehouseName }}
          </text>
        </view>
        <view class="flex items-center justify-between py-10rpx">
          <text class="text-26rpx text-gray-600">
            总件数
          </text>
          <text class="text-26rpx">
            {{ activeTrip.pickedItemCount }} / {{ activeTrip.totalItemCount }} 件
          </text>
        </view>
        <view class="flex items-center justify-between py-10rpx">
          <text class="text-26rpx text-gray-600">
            总单数
          </text>
          <text class="text-26rpx">
            {{ activeTrip.arrivedTaskCount }} / {{ activeTrip.taskCount }} 单
          </text>
        </view>
        <view class="mt-10rpx flex items-center justify-end">
          <text class="text-24rpx text-primary">
            查看详情
          </text>
          <text class="i-carbon:chevron-right ml-4rpx text-24rpx text-primary" />
        </view>
      </view>

      <view v-else class="py-60rpx text-center">
        <text class="i-carbon:truck text-80rpx text-gray-300" />
        <view class="mt-20rpx text-26rpx text-gray-400">
          暂无进行中的出车单
        </view>
      </view>
    </view>

    <!-- 快捷入口 -->
    <view class="mx-20rpx mb-20rpx rounded-20rpx bg-white p-30rpx">
      <text class="mb-20rpx block text-30rpx font-bold">
        快捷入口
      </text>
      <view class="grid grid-cols-2 gap-20rpx">
        <view class="quick-entry" @click="toTaskList">
          <text class="i-carbon:list text-60rpx text-primary" />
          <text class="mt-10rpx text-26rpx">
            我的任务
          </text>
        </view>
        <view class="quick-entry" @click="toTripDetail">
          <text class="i-carbon:truck text-60rpx text-primary" />
          <text class="mt-10rpx text-26rpx">
            出车单
          </text>
        </view>
      </view>
    </view>
  </view>

  <!-- 底部Tab -->
  <view class="delivery-tabbar fixed bottom-0 left-0 right-0 flex border-t border-gray-200 bg-white">
    <view class="tabbar-item" @click="fetchData">
      <text class="i-carbon:dashboard text-40rpx text-primary" />
      <text class="mt-4rpx text-22rpx text-primary">
        工作台
      </text>
    </view>
    <view class="tabbar-item" @click="toTaskList">
      <text class="i-carbon:list text-40rpx text-gray-500" />
      <text class="mt-4rpx text-22rpx text-gray-500">
        我的任务
      </text>
    </view>
    <view class="tabbar-item" @click="toProfile">
      <text class="i-carbon:user text-40rpx text-gray-500" />
      <text class="mt-4rpx text-22rpx text-gray-500">
        个人中心
      </text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.active-trip-card {
  border-radius: 12rpx;
  background-color: #f7f8fa;
  padding: 20rpx;
}

.quick-entry {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 30rpx 0;
  border-radius: 12rpx;
  background-color: #f7f8fa;
}

.delivery-tabbar {
  padding-bottom: env(safe-area-inset-bottom, 0);
}

.tabbar-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 16rpx 0;
}
</style>
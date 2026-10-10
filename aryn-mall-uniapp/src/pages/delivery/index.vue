<script setup lang="ts">
import type { DeliveryTripBrief } from '@/api/delivery'
import { onShow } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'
import {
  getDeliveryWorkbench,
  getTripStatusColor,
  getTripStatusName,
} from '@/api/delivery'
import { resolveDeliveryDestination } from '@/utils/delivery-navigation'

definePage({
  name: 'delivery-index',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '配送工作台',
  },
})

/**
 * 工作台 = 司机当天全部活的驾驶舱。
 *
 * 按「趟次 → 站点」组织：每趟一张卡，卡里直接列出这趟要送的每一单（按送货顺序），
 * 司机在首页就能一眼看全今天要送的货、按顺序装车出发。
 *
 * 数据来自 `trip/workbench`（一次返回全部在途趟次 + 统计）。不要退回
 * `trip/active`——它只给最新一趟，订单分批派下来时前面几趟在首页等于消失。
 */

const globalLoading = useGlobalLoading()
const { show: showToast } = useGlobalToast()
const router = useRouter()

const loading = ref(true)
/** 待处理单数（待取货+配货中+待送达） */
const pendingTaskCount = ref(0)
/** 今日已完成单数（今天送达或签收） */
const todayDoneCount = ref(0)
/** 在途趟次（按创建时间升序，含每趟的站点） */
const trips = ref<DeliveryTripBrief[]>([])
/** 展开的趟次ID；默认展开第一趟，其余折叠，避免整天多趟挤在一屏 */
const expandedTripId = ref<string>('')

onShow(() => {
  fetchData()
})

async function fetchData() {
  globalLoading.loading('加载中...')
  loading.value = true
  try {
    const res = await getDeliveryWorkbench().send()
    const workbench = (res ?? {}) as { pendingTaskCount?: number, todayDoneCount?: number, trips?: DeliveryTripBrief[] }
    pendingTaskCount.value = workbench.pendingTaskCount ?? 0
    todayDoneCount.value = workbench.todayDoneCount ?? 0
    trips.value = workbench.trips ?? []
    // 默认展开第一趟：司机进页面最先要知道「这车先送哪家」
    if (!trips.value.some(trip => trip.id === expandedTripId.value)) {
      expandedTripId.value = trips.value[0]?.id ?? ''
    }
  }
  catch (error: any) {
    // 工作台是司机唯一的活源入口，拉不到就必须说出来，
    // 否则空列表会被当成「今天没活」直接漏单
    showToast(error?.message || '获取配送任务失败，请下拉刷新')
  }
  finally {
    loading.value = false
    globalLoading.close()
  }
}

/** 今天还有几单没送（待送达） */
const waitingDeliverCount = computed(() => trips.value
  .flatMap(trip => trip.taskList ?? [])
  .filter(task => task.status === '4')
  .length)

/** 今天还有几件没取（按趟次汇总） */
const remainingPickCount = computed(() => trips.value
  .reduce((sum, trip) => sum + Math.max(0, (trip.totalItemCount ?? 0) - (trip.pickedItemCount ?? 0)), 0))

/** 趟次卡副标题：这趟车现在该干什么 */
function tripHint(trip: DeliveryTripBrief): string {
  const total = trip.totalItemCount ?? 0
  const picked = trip.pickedItemCount ?? 0
  if (trip.status === '1')
    return '还没开始配货，进详情核对清单'
  if (trip.status === '2') {
    const remaining = Math.max(0, total - picked)
    return remaining > 0 ? `配货中，还差 ${remaining} 件未确认` : '已配齐，可以出发送货'
  }
  if (trip.status === '3') {
    const arrived = trip.arrivedTaskCount ?? 0
    // 车已开出但还有新并入的货没配（任务停在「待取货/配货中」）：
    // 只报「还剩几单未送达」会让司机以为直接开过去就行，漏掉回车配货这一步
    const pendingPick = (trip.taskList ?? [])
      .filter(task => task.status === '2' || task.status === '3')
      .length
    if (pendingPick > 0)
      return `配送中，有 ${pendingPick} 单新加货待配货`
    return `配送中，还剩 ${Math.max(0, (trip.taskCount ?? 0) - arrived)} 单未送达`
  }
  return '已收车'
}

/** 站点目的地：收货地址为空时回落港口+泊位（与导航递交口径同源） */
function stopDestination(stop: { recipientAddress?: string, portName?: string, berth?: string }): string {
  return resolveDeliveryDestination(stop)
}

/** 站点在列表上的时间提示 */
function stopHint(stop: DeliveryTripBrief['taskList'][number]): string {
  if (stop.status === '5' || stop.status === '6') {
    return stop.arriveTime ? `已送达 ${stop.arriveTime}` : '已送达'
  }
  return stop.recipientPhone || ''
}

/**
 * 站点状态标签。
 *
 * 异常(8)/待退回(9) 必须显式标出来：这两类单在详情页没有「已送达」按钮
 * （后端也不允许），司机在首页看不到原因就会一直等它变待送达。
 */
function stopStatusLabel(status: string): string {
  if (status === '4')
    return '待送达'
  if (status === '2' || status === '3')
    return '待取货'
  if (status === '8')
    return '异常'
  if (status === '9')
    return '待退回'
  return ''
}

/** 站点状态标签颜色：待送达是下一步动作，异常类要醒目 */
function stopStatusColor(status: string): string {
  if (status === '4')
    return '#e6a23c'
  if (status === '8' || status === '9')
    return '#f56c6c'
  return '#909399'
}

/** 展开/收起趟次卡 */
function toggleTrip(tripId: string) {
  expandedTripId.value = expandedTripId.value === tripId ? '' : tripId
}

/** 进入出车单详情（配货/送货都在这里操作） */
function toTripDetail(tripId: string) {
  router.push({
    name: 'delivery-trip-detail',
    params: { id: tripId },
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
    <view class="to-primary-light m-20rpx rounded-20rpx from-primary bg-gradient-to-r p-30rpx text-white">
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
      <view v-if="trips.length > 0" class="mt-20rpx flex border-t border-white/20 pt-20rpx text-24rpx">
        <text class="flex-1">
          待送 {{ waitingDeliverCount }} 单
        </text>
        <text class="flex-1 text-right">
          待取 {{ remainingPickCount }} 件
        </text>
      </view>
    </view>

    <!-- 在途趟次：正常只有一张（一车一张单）；多张属历史遗留，提示司机拉合 -->
    <view v-if="trips.length === 0" class="mx-20rpx rounded-20rpx bg-white p-60rpx text-center">
      <text class="i-carbon:truck text-80rpx text-gray-300" />
      <view class="mt-20rpx text-26rpx text-gray-400">
        暂无进行中的出车单
      </view>
      <view class="mt-10rpx text-22rpx text-gray-400">
        有新任务派给你时会出现在这里
      </view>
    </view>

    <view v-if="trips.length > 1" class="mx-20rpx mb-20rpx rounded-20rpx bg-orange-50 p-24rpx">
      <view class="flex items-start">
        <text class="i-carbon:warning-alt mr-12rpx mt-4rpx flex-none text-32rpx text-orange-500" />
        <view class="flex-1">
          <view class="text-26rpx text-orange-700 font-bold">
            你有 {{ trips.length }} 张出车单
          </view>
          <view class="mt-6rpx text-24rpx text-orange-600">
            同一辆车的货应该装在一张单上才能统一排序。进任意一张的详情，用「加单」把其它单的订单拉过来即可合并。
          </view>
        </view>
      </view>
    </view>

    <view
      v-for="(trip, tripIndex) in trips"
      :key="trip.id"
      class="mx-20rpx mb-20rpx rounded-20rpx bg-white p-30rpx"
    >
      <!-- 趟次头部：编号 + 状态 -->
      <view class="flex items-start justify-between" @click="toggleTrip(trip.id)">
        <view class="flex-1">
          <view class="flex items-center">
            <text class="text-30rpx font-bold">
              {{ trips.length > 1 ? `第 ${tripIndex + 1} 趟` : '当前出车单' }}
            </text>
            <text
              class="ml-16rpx rounded-8rpx px-16rpx py-4rpx text-24rpx text-white"
              :style="{ backgroundColor: getTripStatusColor(trip.status) }"
            >
              {{ getTripStatusName(trip.status) }}
            </text>
          </view>
          <view class="mt-8rpx text-24rpx text-gray-500">
            {{ trip.tripNo }} · {{ trip.taskCount }} 单 / {{ trip.totalItemCount }} 件
          </view>
          <view class="mt-6rpx text-24rpx text-gray-400">
            {{ tripHint(trip) }}
          </view>
        </view>
        <text
          class="i-carbon:chevron-right ml-16rpx mt-10rpx flex-none text-32rpx text-gray-400 transition-transform"
          :class="{ 'rotate-90': expandedTripId === trip.id }"
        />
      </view>

      <!-- 送货顺序：司机按这个顺序装车、按这个顺序送 -->
      <view v-if="expandedTripId === trip.id" class="mt-20rpx">
        <view class="mb-10rpx flex items-center justify-between border-t border-gray-100 pt-20rpx">
          <text class="text-26rpx text-gray-600 font-bold">
            送货顺序
          </text>
          <text class="text-22rpx text-gray-400">
            装车与送达都按此顺序
          </text>
        </view>

        <view
          v-for="(stop, stopIndex) in trip.taskList"
          :key="stop.id"
          class="stop-row"
          :class="{ 'stop-row-done': stop.status === '5' || stop.status === '6' }"
        >
          <view class="stop-index" :class="{ 'stop-index-done': stop.status === '5' || stop.status === '6' }">
            {{ stop.status === '5' || stop.status === '6' ? '✓' : stopIndex + 1 }}
          </view>
          <view class="ml-20rpx flex-1 overflow-hidden">
            <view class="flex items-center">
              <text class="text-26rpx font-bold">
                {{ stop.recipientName || '未填收货人' }}
              </text>
              <text v-if="stop.vesselName" class="ml-10rpx text-22rpx text-gray-400">
                {{ stop.vesselName }}
              </text>
            </view>
            <view class="mt-4rpx truncate text-24rpx text-gray-500">
              {{ stopDestination(stop) || '暂无收货地址' }}
            </view>
            <view class="mt-4rpx text-22rpx text-gray-400">
              {{ stopHint(stop) }}
            </view>
          </view>
          <text
            class="ml-10rpx flex-none text-22rpx"
            :style="{ color: stopStatusColor(stop.status) }"
          >
            {{ stopStatusLabel(stop.status) }}
          </text>
        </view>

        <view class="mt-20rpx flex items-center gap-20rpx">
          <wd-button size="small" type="primary" block @click.stop="toTripDetail(trip.id)">
            {{ trip.status === '3' ? '继续送货' : '配货 / 出发' }}
          </wd-button>
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
        <view class="quick-entry" @click="onRefresh">
          <text class="i-carbon:renew text-60rpx text-primary" />
          <text class="mt-10rpx text-26rpx">
            刷新数据
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
.stop-row {
  display: flex;
  align-items: center;
  padding: 20rpx 0;
  border-bottom: 1px solid #f5f5f5;

  &:last-of-type {
    border-bottom: none;
  }
}

.stop-row-done {
  opacity: 0.55;
}

.stop-index {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 48rpx;
  height: 48rpx;
  border-radius: 50%;
  background-color: var(--wot-color-theme, #0084ff);
  color: #fff;
  font-size: 26rpx;
  font-weight: bold;
}

.stop-index-done {
  background-color: #07c160;
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

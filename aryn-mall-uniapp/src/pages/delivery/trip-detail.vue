<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'
import {
  arriveTask,
  departTrip,
  getPickList,
  getTripDetail,
  pickItem,
  sortTripTasks,
  startLoading,
  unpickItem,
} from '@/api/delivery'
import type { DeliveryTask, DeliveryTrip, PickGroup, PickItem, TripStatus } from '@/api/delivery'

definePage({
  name: 'delivery-trip-detail',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '出车单详情',
  },
})

const globalLoading = useGlobalLoading()
const { show: showToast } = useGlobalToast()

const loading = ref(true)
const trip = ref<DeliveryTrip | null>(null)
const pickGroups = ref<PickGroup[]>([])
/** 操作锁，防止重复提交 */
const submitting = ref(false)

/** 是否为配货视图（status=1待配货 或 2配货中） */
const isPickView = computed(() => {
  const status = trip.value?.status
  return status === '1' || status === '2'
})

/** 是否为送货视图（status=3配送中） */
const isDeliverView = computed(() => trip.value?.status === '3')

/** 已取件数 */
const pickedCount = computed(() => trip.value?.pickedItemCount ?? 0)

/** 总件数 */
const totalCount = computed(() => trip.value?.totalItemCount ?? 0)

/** 是否全部取完 */
const isAllPicked = computed(() => pickedCount.value >= totalCount.value && totalCount.value > 0)

/** 已送达单数 */
const arrivedCount = computed(() => trip.value?.arrivedTaskCount ?? 0)

/** 总单数 */
const totalTaskCount = computed(() => trip.value?.taskCount ?? 0)

/** 是否全部送达 */
const isAllArrived = computed(() => arrivedCount.value >= totalTaskCount.value && totalTaskCount.value > 0)

/** 送货视图按 sortNo 排序的任务列表 */
const sortedTaskList = computed(() => {
  if (!trip.value?.taskList)
    return []
  return [...trip.value.taskList].sort((a, b) => a.sortNo - b.sortNo)
})

onLoad((options) => {
  if (options?.id) {
    fetchTripDetail(options.id)
  }
})

/** 获取出车单详情 */
async function fetchTripDetail(id: string) {
  globalLoading.loading('加载中...')
  loading.value = true
  try {
    const res = await getTripDetail(id).send()
    trip.value = res as DeliveryTrip
    // 配货视图时加载取货清单
    if (isPickView.value) {
      await fetchPickList(id)
    }
  }
  catch (error) {
    console.error('获取出车单详情失败:', error)
  }
  finally {
    loading.value = false
    globalLoading.close()
  }
}

/** 获取取货清单 */
async function fetchPickList(tripId: string) {
  try {
    const res = await getPickList(tripId).send()
    pickGroups.value = res as PickGroup[]
  }
  catch (error) {
    console.error('获取取货清单失败:', error)
    pickGroups.value = []
  }
}

/** 开始配货（status: 1 -> 2） */
async function handleStartLoading() {
  if (!trip.value || submitting.value)
    return
  submitting.value = true
  globalLoading.loading('处理中...')
  try {
    await startLoading(trip.value.id).send()
    showToast('已开始配货')
    await fetchTripDetail(trip.value.id)
  }
  catch (error: any) {
    showToast(error?.msg || '操作失败')
  }
  finally {
    submitting.value = false
    globalLoading.close()
  }
}

/** 装货完毕出发（status: 2 -> 3） */
async function handleDepart() {
  if (!trip.value || submitting.value)
    return
  if (!isAllPicked.value) {
    const remaining = totalCount.value - pickedCount.value
    showToast(`还有${remaining}件商品未确认取货`)
    return
  }
  submitting.value = true
  globalLoading.loading('处理中...')
  try {
    await departTrip(trip.value.id).send()
    showToast('已出发配送')
    await fetchTripDetail(trip.value.id)
  }
  catch (error: any) {
    showToast(error?.msg || '操作失败')
  }
  finally {
    submitting.value = false
    globalLoading.close()
  }
}

/** 切换商品取货确认状态 */
async function handleTogglePick(item: PickItem) {
  if (!trip.value || submitting.value)
    return
  // status=1时不允许操作，需先开始配货
  if (trip.value.status === '1') {
    showToast('请先开始配货')
    return
  }
  submitting.value = true
  try {
    if (item.picked === '1') {
      await unpickItem(trip.value.id, item.id).send()
      item.picked = '0'
    }
    else {
      await pickItem(trip.value.id, item.id).send()
      item.picked = '1'
    }
    // 更新已取件数
    if (trip.value) {
      trip.value.pickedItemCount = pickGroups.value
        .reduce((sum, g) => sum + g.items.filter(i => i.picked === '1').length, 0)
    }
  }
  catch (error: any) {
    showToast(error?.msg || '操作失败')
  }
  finally {
    submitting.value = false
  }
}

/** 导航到指定位置 */
function handleNavigate(latitude?: number, longitude?: number, address?: string) {
  if (latitude && longitude) {
    uni.openLocation({
      latitude,
      longitude,
      name: address || '目的地',
      scale: 18,
      fail: () => {
        showToast('打开地图失败')
      },
    })
  }
  else {
    showToast('暂无坐标信息')
  }
}

/** 拨打电话 */
function handleCallPhone(phone: string) {
  if (!phone) {
    showToast('暂无电话号码')
    return
  }
  uni.makePhoneCall({
    phoneNumber: phone,
    fail: () => {
      showToast('拨打电话失败')
    },
  })
}

/** 送达某单 */
async function handleArriveTask(task: DeliveryTask) {
  if (submitting.value)
    return
  submitting.value = true
  globalLoading.loading('处理中...')
  try {
    await arriveTask(task.id).send()
    showToast('已确认送达')
    if (trip.value) {
      await fetchTripDetail(trip.value.id)
    }
  }
  catch (error: any) {
    showToast(error?.msg || '操作失败')
  }
  finally {
    submitting.value = false
    globalLoading.close()
  }
}

/** 上移任务 */
async function handleMoveUp(task: DeliveryTask, index: number) {
  if (index === 0 || submitting.value)
    return
  await handleSortTask(index, index - 1)
}

/** 下移任务 */
async function handleMoveDown(task: DeliveryTask, index: number) {
  if (index === sortedTaskList.value.length - 1 || submitting.value)
    return
  await handleSortTask(index, index + 1)
}

/** 调整任务顺序 */
async function handleSortTask(fromIndex: number, toIndex: number) {
  if (!trip.value || submitting.value)
    return
  const list = [...sortedTaskList.value]
  const [moved] = list.splice(fromIndex, 1)
  list.splice(toIndex, 0, moved)
  const taskSort = list.map((t, idx) => ({ taskId: t.id, sortNo: idx + 1 }))
  submitting.value = true
  globalLoading.loading('调整顺序中...')
  try {
    await sortTripTasks(trip.value.id, taskSort).send()
    // 更新本地排序
    if (trip.value.taskList) {
      trip.value.taskList.forEach((t) => {
        const found = taskSort.find(s => s.taskId === t.id)
        if (found)
          t.sortNo = found.sortNo
      })
    }
  }
  catch (error: any) {
    showToast(error?.msg || '调整顺序失败')
  }
  finally {
    submitting.value = false
    globalLoading.close()
  }
}
</script>

<template>
  <hr-navbar title="出车单详情" />
  <view v-if="!loading && trip" class="min-h-screen bg-gray-50 pb-160rpx">
    <!-- ============ 配货视图 ============ -->
    <template v-if="isPickView">
      <!-- 顶部进度条 -->
      <view class="m-20rpx rounded-20rpx bg-white p-30rpx">
        <view class="mb-20rpx flex items-center justify-between">
          <text class="text-30rpx font-bold">
            取货进度
          </text>
          <text class="text-26rpx text-primary">
            {{ pickedCount }} / {{ totalCount }} 件
          </text>
        </view>
        <view class="progress-bar">
          <view
            class="progress-bar-inner"
            :style="{ width: `${totalCount > 0 ? (pickedCount / totalCount) * 100 : 0}%` }"
          />
        </view>
      </view>

      <!-- 仓库地址卡片 -->
      <view
        class="mx-20rpx mb-20rpx rounded-20rpx bg-white p-30rpx"
        @click="handleNavigate(trip.warehouseLatitude, trip.warehouseLongitude, trip.warehouseName)"
      >
        <view class="flex items-center">
          <text class="i-carbon:building text-40rpx text-primary" />
          <view class="ml-20rpx flex-1">
            <text class="text-28rpx font-bold">
              {{ trip.warehouseName }}
            </text>
            <view class="mt-10rpx text-26rpx text-gray-500">
              {{ trip.warehouseAddress }}
            </view>
          </view>
          <text class="i-carbon:chevron-right text-28rpx text-gray-400" />
        </view>
      </view>

      <!-- 取货清单（按订单分组） -->
      <view
        v-for="group in pickGroups"
        :key="group.orderId"
        class="mx-20rpx mb-20rpx rounded-20rpx bg-white p-30rpx"
      >
        <!-- 订单号 -->
        <view class="mb-20rpx flex items-center justify-between border-b border-gray-100 pb-20rpx">
          <text class="text-28rpx font-bold">
            订单 {{ group.orderNo }}
          </text>
          <text class="text-24rpx text-gray-400">
            共{{ group.items.length }}件
          </text>
        </view>

        <!-- 商品列表 -->
        <view
          v-for="item in group.items"
          :key="item.id"
          class="pick-item"
          :class="{ 'pick-item-done': item.picked === '1' }"
          @click="handleTogglePick(item)"
        >
          <image :src="item.picUrl" class="h-120rpx w-120rpx flex-none rounded-lg" mode="aspectFill" />
          <view class="ml-20rpx flex flex-1 flex-col overflow-hidden">
            <wd-text :lines="2" size="26rpx" color="inherit" :text="item.spuName" />
            <view v-if="item.specsInfo" class="pt-6rpx">
              <wd-text size="24rpx" color="#909090" :text="item.specsInfo" />
            </view>
            <view class="pt-6rpx text-24rpx text-gray-500">
              数量：{{ item.quantity }}
            </view>
          </view>
          <!-- 确认状态 -->
          <view class="flex-none pl-20rpx">
            <text
              v-if="item.picked === '1'"
              class="i-carbon:checkmark-filled text-48rpx text-green-500"
            />
            <text
              v-else
              class="i-carbon:checkbox text-48rpx text-gray-300"
            />
          </view>
        </view>
      </view>
    </template>

    <!-- ============ 送货视图 ============ -->
    <template v-if="isDeliverView">
      <!-- 顶部进度 -->
      <view class="m-20rpx rounded-20rpx bg-white p-30rpx">
        <view class="mb-20rpx flex items-center justify-between">
          <text class="text-30rpx font-bold">
            送货进度
          </text>
          <text class="text-26rpx text-primary">
            {{ arrivedCount }} / {{ totalTaskCount }} 单
          </text>
        </view>
        <view class="progress-bar">
          <view
            class="progress-bar-inner"
            :style="{ width: `${totalTaskCount > 0 ? (arrivedCount / totalTaskCount) * 100 : 0}%` }"
          />
        </view>
      </view>

      <!-- 送货路线列表 -->
      <view
        v-for="(task, index) in sortedTaskList"
        :key="task.id"
        class="mx-20rpx mb-20rpx rounded-20rpx bg-white p-30rpx"
        :class="{ 'task-done': task.status === '5' || task.status === '6' }"
      >
        <!-- 卡片头部：序号 + 状态 -->
        <view class="mb-20rpx flex items-center justify-between">
          <view class="flex items-center">
            <view class="task-index">
              {{ index + 1 }}
            </view>
            <text class="ml-20rpx text-28rpx font-bold">
              {{ task.recipientName }}
            </text>
          </view>
          <text
            v-if="task.status === '5' || task.status === '6'"
            class="text-24rpx text-gray-400"
          >
            已送达
          </text>
        </view>

        <!-- 联系电话 -->
        <view class="mb-10rpx flex items-center">
          <text class="i-carbon:phone mr-10rpx text-28rpx text-gray-400" />
          <text
            class="text-26rpx text-primary"
            @click.stop="handleCallPhone(task.recipientPhone)"
          >
            {{ task.recipientPhone }}
          </text>
        </view>

        <!-- 收货地址 -->
        <view class="mb-20rpx flex items-start">
          <text class="i-carbon:location mr-10rpx mt-4rpx text-28rpx flex-none text-gray-400" />
          <text class="flex-1 text-26rpx">
            {{ task.recipientAddress }}
          </text>
        </view>

        <!-- 送达时间 -->
        <view v-if="task.arriveTime" class="mb-10rpx text-24rpx text-gray-400">
          送达时间：{{ task.arriveTime }}
        </view>

        <!-- 操作按钮 -->
        <view v-if="task.status !== '5' && task.status !== '6' && task.status !== '7'" class="flex items-center gap-20rpx border-t border-gray-100 pt-20rpx">
          <wd-button
            size="small"
            plain
            type="info"
            @click.stop="handleNavigate(task.latitude, task.longitude, task.recipientAddress)"
          >
            导航
          </wd-button>
          <wd-button
            size="small"
            type="primary"
            @click.stop="handleArriveTask(task)"
          >
            已送达
          </wd-button>
          <!-- 上移/下移按钮 -->
          <view class="ml-auto flex flex-col items-center">
            <text
              class="i-carbon:arrow-up text-32rpx"
              :class="index === 0 ? 'text-gray-300' : 'text-primary'"
              @click.stop="handleMoveUp(task, index)"
            />
            <text
              class="i-carbon:arrow-down mt-10rpx text-32rpx"
              :class="index === sortedTaskList.length - 1 ? 'text-gray-300' : 'text-primary'"
              @click.stop="handleMoveDown(task, index)"
            />
          </view>
        </view>
      </view>

      <!-- 全部送达提示 -->
      <view v-if="isAllArrived" class="mx-20rpx my-40rpx rounded-20rpx bg-green-50 p-40rpx text-center">
        <text class="i-carbon:checkmark-filled text-80rpx text-green-500" />
        <view class="mt-20rpx text-30rpx font-bold text-green-600">
          全部配送完成
        </view>
      </view>
    </template>

    <!-- ============ 已完成视图 ============ -->
    <template v-if="trip.status === '4'">
      <view class="mx-20rpx my-40rpx rounded-20rpx bg-white p-40rpx text-center">
        <text class="i-carbon:checkmark-filled text-80rpx text-green-500" />
        <view class="mt-20rpx text-30rpx font-bold">
          出车单已完成
        </view>
        <view class="mt-10rpx text-26rpx text-gray-500">
          完成时间：{{ trip.completeTime }}
        </view>
      </view>
    </template>
  </view>

  <!-- 底部操作按钮 -->
  <view
    v-if="!loading && trip"
    class="fixed bottom-0 left-0 right-0 bg-white p-20rpx"
    style="padding-bottom: max(env(safe-area-inset-bottom), 16rpx);"
  >
    <!-- status=1：开始配货 -->
    <wd-button
      v-if="trip.status === '1'"
      type="primary"
      block
      :loading="submitting"
      @click="handleStartLoading"
    >
      开始配货
    </wd-button>

    <!-- status=2：装货完毕出发 -->
    <wd-button
      v-if="trip.status === '2'"
      type="primary"
      block
      :disabled="!isAllPicked"
      :loading="submitting"
      @click="handleDepart"
    >
      {{ isAllPicked ? '装货完毕出发' : `还有${totalCount - pickedCount}件未确认` }}
    </wd-button>
  </view>
</template>

<style lang="scss" scoped>
.progress-bar {
  width: 100%;
  height: 16rpx;
  border-radius: 8rpx;
  background-color: #e5e6eb;
  overflow: hidden;

  .progress-bar-inner {
    height: 100%;
    border-radius: 8rpx;
    background: linear-gradient(90deg, #0084ff, #07c160);
    transition: width 0.3s ease;
  }
}

.pick-item {
  display: flex;
  align-items: center;
  padding: 20rpx 0;
  border-bottom: 1px solid #f5f5f5;

  &:last-child {
    border-bottom: none;
  }
}

.pick-item-done {
  opacity: 0.6;
}

.task-done {
  opacity: 0.7;
  background-color: #f7f8fa;
}

.task-index {
  display: flex;
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
</style>
<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { arriveTask, getTaskDetail, getTaskStatusColor, getTaskStatusName } from '@/api/delivery'
import type { DeliveryTask, DeliveryTaskStatus } from '@/api/delivery'

definePage({
  name: 'delivery-task-detail',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '任务详情',
  },
})

const globalLoading = useGlobalLoading()
const { show: showToast } = useGlobalToast()

const loading = ref(true)
const submitting = ref(false)
const task = ref<DeliveryTask | null>(null)

/** 配送状态时间线节点 */
const timelineNodes = ref<Array<{ name: string, time?: string, done: boolean, active: boolean }>>([])

onLoad((options) => {
  if (options?.id) {
    fetchTaskDetail(options.id)
  }
})

/** 获取任务详情 */
async function fetchTaskDetail(id: string) {
  globalLoading.loading('加载中...')
  loading.value = true
  try {
    const res = await getTaskDetail(id).send()
    task.value = res as DeliveryTask
    buildTimeline()
  }
  catch (error) {
    console.error('获取任务详情失败:', error)
  }
  finally {
    loading.value = false
    globalLoading.close()
  }
}

/** 构建配送状态时间线 */
function buildTimeline() {
  if (!task.value)
    return
  const status = task.value.status
  // 节点定义：待派单 -> 待取货 -> 配货中 -> 待送达 -> 已送达 -> 已签收
  const nodes = [
    { key: '1', name: '待派单' },
    { key: '2', name: '待取货' },
    { key: '3', name: '配货中' },
    { key: '4', name: '待送达' },
    { key: '5', name: '已送达' },
    { key: '6', name: '已签收' },
  ]
  const statusIndex = nodes.findIndex(n => n.key === status)
  timelineNodes.value = nodes.map((node, index) => ({
    name: node.name,
    done: index < statusIndex,
    active: index === statusIndex,
    time: index === 2 ? task.value?.pickUpTime : index === 4 ? task.value?.arriveTime : index === 5 ? task.value?.signTime : undefined,
  }))
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

/** 导航到收货地址 */
function handleNavigate() {
  if (!task.value)
    return
  const { latitude, longitude, recipientAddress } = task.value
  if (latitude && longitude) {
    uni.openLocation({
      latitude,
      longitude,
      name: recipientAddress || '目的地',
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

/** 确认送达 */
async function handleArrive() {
  if (!task.value || submitting.value)
    return
  submitting.value = true
  globalLoading.loading('处理中...')
  try {
    await arriveTask(task.value.id).send()
    showToast('已确认送达')
    await fetchTaskDetail(task.value.id)
  }
  catch (error: any) {
    showToast(error?.msg || '操作失败')
  }
  finally {
    submitting.value = false
    globalLoading.close()
  }
}
</script>

<template>
  <hr-navbar title="任务详情" />
  <view v-if="!loading && task" class="min-h-screen bg-gray-50 pb-160rpx">
    <!-- 任务头部 -->
    <view class="m-20rpx rounded-20rpx bg-white p-30rpx">
      <view class="flex items-center justify-between">
        <text class="text-30rpx font-bold">
          {{ task.taskNo }}
        </text>
        <text
          class="rounded-8rpx px-16rpx py-4rpx text-24rpx text-white"
          :style="{ backgroundColor: getTaskStatusColor(task.status as DeliveryTaskStatus) }"
        >
          {{ getTaskStatusName(task.status as DeliveryTaskStatus) }}
        </text>
      </view>
      <view class="mt-10rpx text-26rpx text-gray-500">
        订单号：{{ task.orderNo }}
      </view>
    </view>

    <!-- 订单明细列表 -->
    <view class="mx-20rpx mb-20rpx rounded-20rpx bg-white p-30rpx">
      <text class="mb-20rpx block text-28rpx font-bold">
        订单明细
      </text>
      <view
        v-for="item in task.itemList"
        :key="item.id"
        class="flex border-b border-gray-100 py-20rpx last:border-none"
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
      </view>
    </view>

    <!-- 收货信息卡片 -->
    <view class="mx-20rpx mb-20rpx rounded-20rpx bg-white p-30rpx">
      <text class="mb-20rpx block text-28rpx font-bold">
        收货信息
      </text>
      <view class="flex items-center py-10rpx">
        <text class="i-carbon:user mr-10rpx text-28rpx text-gray-400" />
        <text class="text-26rpx text-gray-600">
          收货人：
        </text>
        <text class="text-26rpx">
          {{ task.recipientName }}
        </text>
      </view>
      <view class="flex items-center py-10rpx">
        <text class="i-carbon:phone mr-10rpx text-28rpx text-gray-400" />
        <text class="text-26rpx text-gray-600">
          电话：
        </text>
        <text class="text-26rpx text-primary" @click="handleCallPhone(task.recipientPhone)">
          {{ task.recipientPhone }}
        </text>
      </view>
      <view class="flex items-start py-10rpx">
        <text class="i-carbon:location mr-10rpx mt-4rpx text-28rpx flex-none text-gray-400" />
        <text class="text-26rpx text-gray-600">
          地址：
        </text>
        <text class="flex-1 text-26rpx">
          {{ task.recipientAddress }}
        </text>
      </view>
      <view class="mt-20rpx flex gap-20rpx border-t border-gray-100 pt-20rpx">
        <wd-button size="small" plain type="info" @click="handleCallPhone(task.recipientPhone)">
          拨打电话
        </wd-button>
        <wd-button size="small" plain type="primary" @click="handleNavigate">
          导航前往
        </wd-button>
      </view>
    </view>

    <!-- 配送状态时间线 -->
    <view class="mx-20rpx mb-20rpx rounded-20rpx bg-white p-30rpx">
      <text class="mb-30rpx block text-28rpx font-bold">
        配送状态
      </text>
      <view class="timeline">
        <view
          v-for="(node, index) in timelineNodes"
          :key="index"
          class="timeline-item"
        >
          <!-- 节点圆点 -->
          <view class="timeline-dot-wrap">
            <view
              class="timeline-dot"
              :class="{
                'timeline-dot-done': node.done,
                'timeline-dot-active': node.active,
              }"
            />
            <view
              v-if="index < timelineNodes.length - 1"
              class="timeline-line"
              :class="{ 'timeline-line-done': node.done }"
            />
          </view>
          <!-- 节点内容 -->
          <view class="timeline-content">
            <text
              class="text-26rpx"
              :class="{
                'text-primary font-bold': node.active,
                'text-gray-400': !node.active && !node.done,
                'text-gray-600': node.done,
              }"
            >
              {{ node.name }}
            </text>
            <text v-if="node.time" class="mt-4rpx block text-22rpx text-gray-400">
              {{ node.time }}
            </text>
          </view>
        </view>
      </view>
    </view>
  </view>

  <!-- 底部操作按钮 -->
  <view
    v-if="!loading && task && task.status === '4'"
    class="fixed bottom-0 left-0 right-0 bg-white p-20rpx"
    style="padding-bottom: max(env(safe-area-inset-bottom), 16rpx);"
  >
    <wd-button
      type="primary"
      block
      :loading="submitting"
      @click="handleArrive"
    >
      确认送达
    </wd-button>
  </view>
</template>

<style lang="scss" scoped>
.timeline {
  padding-left: 10rpx;
}

.timeline-item {
  display: flex;
  min-height: 80rpx;
}

.timeline-dot-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 40rpx;
  flex-shrink: 0;
}

.timeline-dot {
  width: 24rpx;
  height: 24rpx;
  border-radius: 50%;
  background-color: #dcdfe6;
  flex-shrink: 0;
  z-index: 1;
}

.timeline-dot-done {
  background-color: #07c160;
}

.timeline-dot-active {
  background-color: var(--wot-color-theme, #0084ff);
  box-shadow: 0 0 0 8rpx rgba(0, 132, 255, 0.2);
}

.timeline-line {
  width: 4rpx;
  flex: 1;
  background-color: #dcdfe6;
  margin-top: 4rpx;
}

.timeline-line-done {
  background-color: #07c160;
}

.timeline-content {
  padding-left: 20rpx;
  padding-bottom: 30rpx;
  flex: 1;
}
</style>
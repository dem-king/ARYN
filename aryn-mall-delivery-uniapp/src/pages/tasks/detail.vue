<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useDeliveryStore } from '@/store/delivery'

const deliveryStore = useDeliveryStore()
const taskId = ref('')
const task = computed(() => deliveryStore.currentTask)
const fullAddress = computed(() => [task.value?.recipientProvince, task.value?.recipientCity, task.value?.recipientArea, task.value?.recipientAddress].filter(Boolean).join(''))

onLoad((query) => {
  taskId.value = String(query?.id || '')
  if (taskId.value)
    void load()
})

async function load() {
  try {
    await deliveryStore.loadTask(taskId.value)
  }
  catch (error) {
    if (deliveryStore.handleMutationFailure(error))
      uni.redirectTo({ url: '/pages/tasks/index' })
  }
}

function callRecipient() {
  if (task.value?.recipientPhone)
    uni.makePhoneCall({ phoneNumber: task.value.recipientPhone })
}

function navigate() {
  if (task.value?.latitude != null && task.value?.longitude != null) {
    uni.openLocation({ latitude: task.value.latitude, longitude: task.value.longitude, name: task.value.recipientName, address: fullAddress.value })
    return
  }
  uni.setClipboardData({ data: fullAddress.value, success: () => uni.showToast({ title: '地址已复制，可粘贴到地图', icon: 'none' }) })
}

async function beginPicking() {
  if (!task.value)
    return
  try {
    if (task.value.status === 'ASSIGNED')
      await deliveryStore.beginPicking(task.value)
    uni.navigateTo({ url: `/pages/tasks/picking?id=${taskId.value}` })
  }
  catch (error) {
    if (deliveryStore.handleMutationFailure(error))
      uni.redirectTo({ url: '/pages/tasks/index' })
  }
}

function openDelivered() {
  if (task.value)
    uni.navigateTo({ url: `/pages/tasks/delivered?id=${task.value.id}` })
}

function openException() {
  if (task.value)
    uni.navigateTo({ url: `/pages/tasks/exception?id=${task.value.id}` })
}
</script>

<template>
  <view v-if="task" class="page">
    <view class="status-panel"><text class="status">{{ task.status }}</text><text class="task-no">任务 {{ task.taskNo }}</text></view>
    <view class="section">
      <text class="label">收货信息</text>
      <text class="name">{{ task.recipientName }}　{{ task.recipientPhone }}</text>
      <text class="address">{{ fullAddress }}</text>
      <view class="contact-actions"><button @click="callRecipient">拨打电话</button><button @click="navigate">系统导航</button></view>
    </view>
    <view class="section">
      <text class="label">商品核对</text>
      <view v-for="(item, index) in task.items" :key="item.id" class="item"><text>{{ index + 1 }}. 订单商品 {{ item.orderItemId }}</text><text :class="item.checked === '1' ? 'checked' : 'unchecked'">{{ item.checked === '1' ? '已核对' : '待核对' }}</text></view>
    </view>
    <view v-if="task.exceptionSummary" class="section warning"><text class="label">异常记录</text><text>{{ task.exceptionSummary }}</text></view>
    <view class="action-bar">
      <button v-if="['ASSIGNED', 'PICKING'].includes(task.status || '')" class="primary" @click="beginPicking">进入配货</button>
      <button v-if="task.status === 'DELIVERING'" class="primary" @click="openDelivered">提交送达</button>
      <button v-if="['PICKING', 'DELIVERING'].includes(task.status || '')" class="danger" @click="openException">上报异常</button>
    </view>
  </view>
</template>

<style scoped lang="scss">
.page { box-sizing: border-box; min-height: 100vh; padding: 28rpx 28rpx 180rpx; }.status-panel { padding: 36rpx; color: #fff; background: linear-gradient(135deg, #2563eb, #1d4ed8); border-radius: 28rpx; }.status, .task-no, .label, .name, .address { display: block; }.status { font-size: 42rpx; font-weight: 760; }.task-no { margin-top: 12rpx; color: #dbeafe; font-size: 24rpx; }.section { margin-top: 22rpx; padding: 32rpx; background: #fff; border-radius: 26rpx; }.label { margin-bottom: 24rpx; color: #64748b; font-size: 24rpx; }.name { font-size: 30rpx; font-weight: 700; }.address { margin-top: 14rpx; color: #475569; font-size: 26rpx; line-height: 1.6; }.contact-actions { display: grid; grid-template-columns: 1fr 1fr; gap: 18rpx; margin-top: 28rpx; }.contact-actions button { color: #2563eb; font-size: 25rpx; background: #eff6ff; border-radius: 18rpx; }.item { display: flex; align-items: center; justify-content: space-between; min-height: 76rpx; font-size: 25rpx; border-top: 1px solid #f1f5f9; }.checked { color: #059669; }.unchecked { color: #d97706; }.warning { color: #92400e; background: #fffbeb; }.action-bar { position: fixed; right: 0; bottom: 0; left: 0; display: flex; gap: 16rpx; padding: 20rpx 28rpx calc(20rpx + env(safe-area-inset-bottom)); background: #fff; box-shadow: 0 -10rpx 40rpx rgb(15 23 42 / 8%); }.action-bar button { flex: 1; color: #fff; font-size: 27rpx; border-radius: 20rpx; }.primary { background: #2563eb; }.danger { background: #ef4444; }
</style>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useDeliveryStore } from '@/store/delivery'

const deliveryStore = useDeliveryStore()
const activeStatus = ref('')
const filters = [
  { label: '全部', value: '' },
  { label: '待取货', value: 'PICKING' },
  { label: '配送中', value: 'DELIVERING' },
  { label: '异常', value: 'EXCEPTION' },
  { label: '已送达', value: 'DELIVERED' },
]

function load(status = activeStatus.value) {
  activeStatus.value = status
  void deliveryStore.loadTasks(status || undefined)
}

onShow(() => load())

function openTask(id: string) {
  uni.navigateTo({ url: `/pages/tasks/detail?id=${id}` })
}
</script>

<template>
  <view class="page">
    <scroll-view scroll-x class="filters">
      <view class="filter-row">
        <text v-for="filter in filters" :key="filter.value" class="filter" :class="{ active: activeStatus === filter.value }" @click="load(filter.value)">{{ filter.label }}</text>
      </view>
    </scroll-view>
    <view class="list">
      <view v-for="task in deliveryStore.tasks" :key="task.id" class="card" @click="openTask(task.id)">
        <view class="row"><text class="number">{{ task.taskNo }}</text><text class="status">{{ task.status }}</text></view>
        <text class="name">{{ task.recipientName }} · {{ task.recipientPhone }}</text>
        <text class="address">{{ task.recipientProvince }}{{ task.recipientCity }}{{ task.recipientArea }}{{ task.recipientAddress }}</text>
        <text class="time">更新于 {{ task.updateTime || task.createTime }}</text>
      </view>
      <view v-if="!deliveryStore.loading && !deliveryStore.tasks.length" class="empty">该状态下暂无任务</view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.page { min-height: 100vh; }.filters { position: sticky; top: 0; z-index: 2; white-space: nowrap; background: #fff; }.filter-row { display: inline-flex; gap: 16rpx; padding: 22rpx 28rpx; }.filter { padding: 16rpx 24rpx; color: #64748b; font-size: 25rpx; background: #f1f5f9; border-radius: 999rpx; }.filter.active { color: #fff; background: #2563eb; }.list { display: grid; gap: 20rpx; padding: 28rpx 32rpx 60rpx; }.card { padding: 30rpx; background: #fff; border-radius: 26rpx; }.row { display: flex; align-items: center; justify-content: space-between; }.number, .name, .address, .time { display: block; }.number { font-size: 28rpx; font-weight: 700; }.status { color: #2563eb; font-size: 22rpx; }.name { margin-top: 24rpx; font-size: 29rpx; font-weight: 650; }.address { margin-top: 10rpx; color: #475569; font-size: 25rpx; line-height: 1.55; }.time { margin-top: 22rpx; color: #94a3b8; font-size: 22rpx; }.empty { padding: 100rpx 20rpx; color: #94a3b8; text-align: center; }
</style>

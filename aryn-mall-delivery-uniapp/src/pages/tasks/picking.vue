<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { canPickup, useDeliveryStore } from '@/store/delivery'

const store = useDeliveryStore()
const taskId = ref('')
const submitting = ref(false)
const task = computed(() => store.currentTask)
const allChecked = computed(() => canPickup(task.value?.items))

onLoad((query) => {
  taskId.value = String(query?.id || '')
  if (taskId.value)
    void store.loadTask(taskId.value)
})

async function toggle(itemId: string, checked: boolean) {
  if (!task.value)
    return
  try {
    await store.setItemChecked(task.value, itemId, checked)
  }
  catch (error) {
    if (store.handleMutationFailure(error))
      uni.redirectTo({ url: '/pages/tasks/index' })
  }
}

async function pickup() {
  if (!task.value || !allChecked.value)
    return
  submitting.value = true
  try {
    await store.confirmPickup(task.value)
    uni.showToast({ title: '取货成功', icon: 'success' })
    uni.redirectTo({ url: `/pages/tasks/detail?id=${taskId.value}` })
  }
  catch (error) {
    if (store.handleMutationFailure(error))
      uni.redirectTo({ url: '/pages/tasks/index' })
    else
      uni.showToast({ title: error instanceof Error ? error.message : '取货失败', icon: 'none' })
  }
  finally {
    submitting.value = false
  }
}
</script>

<template>
  <view class="page">
    <view class="notice">请逐件核对商品。全部确认后才能取货，核对结果会实时保存。</view>
    <view class="items">
      <view v-for="(item, index) in task?.items" :key="item.id" class="item" @click="toggle(item.id, item.checked !== '1')">
        <view><text class="title">商品 {{ index + 1 }}</text><text class="meta">订单项 {{ item.orderItemId }}</text></view>
        <view class="checkbox" :class="{ checked: item.checked === '1' }">{{ item.checked === '1' ? '✓' : '' }}</view>
      </view>
    </view>
    <view class="action-bar"><button class="primary" :disabled="!allChecked || submitting" :loading="submitting" @click="pickup">全部核对并确认取货</button></view>
  </view>
</template>

<style scoped lang="scss">
.page { box-sizing: border-box; min-height: 100vh; padding: 28rpx 28rpx 170rpx; }.notice { padding: 26rpx 28rpx; color: #1e40af; font-size: 24rpx; line-height: 1.6; background: #dbeafe; border-radius: 22rpx; }.items { display: grid; gap: 18rpx; margin-top: 24rpx; }.item { display: flex; align-items: center; justify-content: space-between; padding: 32rpx; background: #fff; border-radius: 24rpx; }.title, .meta { display: block; }.title { font-size: 29rpx; font-weight: 700; }.meta { margin-top: 10rpx; color: #64748b; font-size: 23rpx; }.checkbox { width: 54rpx; height: 54rpx; color: #fff; line-height: 54rpx; text-align: center; border: 3rpx solid #cbd5e1; border-radius: 16rpx; }.checkbox.checked { background: #059669; border-color: #059669; }.action-bar { position: fixed; right: 0; bottom: 0; left: 0; padding: 20rpx 28rpx calc(20rpx + env(safe-area-inset-bottom)); background: #fff; }.primary { color: #fff; font-size: 28rpx; background: #2563eb; border-radius: 20rpx; }.primary[disabled] { background: #94a3b8; }
</style>

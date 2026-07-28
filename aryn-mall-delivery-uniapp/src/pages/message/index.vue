<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app'
import { useMessageStore } from '@/store/message'

const store = useMessageStore()
onShow(() => void store.refresh())
</script>

<template>
  <view class="page">
    <view class="head"><text>未读 {{ store.unread }} 条</text><text class="action" @click="store.markAllRead">全部已读</text></view>
    <view class="list">
      <view v-for="notice in store.notices" :key="notice.recipientRecordId" class="notice" :class="{ unread: notice.readStatus !== 'READ' }" @click="store.markRead(notice)">
        <view class="title-row"><text class="title">{{ notice.title }}</text><text v-if="notice.readStatus !== 'READ'" class="dot" /></view>
        <text class="summary">{{ notice.summary || notice.content }}</text>
        <text class="time">{{ notice.receivedTime }}</text>
      </view>
      <view v-if="!store.notices.length" class="empty">暂无员工消息</view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.page { min-height: 100vh; }.head { display: flex; justify-content: space-between; padding: 26rpx 32rpx; color: #64748b; font-size: 24rpx; background: #fff; }.action { color: #2563eb; }.list { display: grid; gap: 2rpx; margin-top: 18rpx; }.notice { padding: 30rpx 34rpx; background: #fff; }.notice.unread { background: #f8fbff; }.title-row { display: flex; align-items: center; justify-content: space-between; }.title, .summary, .time { display: block; }.title { font-size: 29rpx; font-weight: 700; }.dot { width: 16rpx; height: 16rpx; background: #2563eb; border-radius: 999rpx; }.summary { margin-top: 14rpx; overflow: hidden; color: #475569; font-size: 25rpx; line-height: 1.55; text-overflow: ellipsis; white-space: nowrap; }.time { margin-top: 18rpx; color: #94a3b8; font-size: 22rpx; }.empty { padding: 120rpx 20rpx; color: #94a3b8; text-align: center; }
</style>

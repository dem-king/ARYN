<script setup lang="ts">
import { computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useAuthStore } from '@/store/auth'
import { useDeliveryStore } from '@/store/delivery'
import { useMessageStore } from '@/store/message'
import { useStaffWebSocket } from '@/composables/useStaffWebSocket'

const authStore = useAuthStore()
const deliveryStore = useDeliveryStore()
const messageStore = useMessageStore()

const picking = computed(() => deliveryStore.tasks.filter(item => ['ASSIGNED', 'PICKING'].includes(item.status || '')).length)
const delivering = computed(() => deliveryStore.tasks.filter(item => item.status === 'DELIVERING').length)
const exception = computed(() => deliveryStore.tasks.filter(item => item.status === 'EXCEPTION').length)
const currentTasks = computed(() => deliveryStore.tasks.filter(item => ['ASSIGNED', 'PICKING', 'DELIVERING'].includes(item.status || '')).slice(0, 3))

async function refresh() {
  await Promise.all([deliveryStore.loadTasks(), messageStore.refresh()])
}

const socket = useStaffWebSocket(refresh)

onShow(() => {
  void refresh()
  socket.connect()
})

function openTask(id: string) {
  uni.navigateTo({ url: `/pages/tasks/detail?id=${id}` })
}

function openTasks() {
  uni.switchTab({ url: '/pages/tasks/index' })
}

function openMessages() {
  uni.switchTab({ url: '/pages/message/index' })
}

async function signOut() {
  await authStore.signOut()
  uni.reLaunch({ url: '/pages/login/index' })
}
</script>

<template>
  <view class="page">
    <view class="hero">
      <view>
        <text class="greeting">你好，{{ authStore.user?.nickname || '配送员' }}</text>
        <text class="date">今日配送任务，请注意核对商品与收货信息</text>
      </view>
      <button class="ghost" @click="signOut">退出</button>
    </view>

    <view class="metrics">
      <view class="metric blue"><text class="metric-value">{{ picking }}</text><text>待取货</text></view>
      <view class="metric green"><text class="metric-value">{{ delivering }}</text><text>配送中</text></view>
      <view class="metric amber"><text class="metric-value">{{ exception }}</text><text>异常</text></view>
    </view>

    <view class="section-head">
      <text class="section-title">当前任务</text>
      <text class="link" @click="openTasks">查看全部</text>
    </view>
    <view v-if="currentTasks.length" class="task-list">
      <view v-for="task in currentTasks" :key="task.id" class="task-card" @click="openTask(task.id)">
        <view class="task-row"><text class="task-no">{{ task.taskNo }}</text><text class="status">{{ task.status }}</text></view>
        <text class="recipient">{{ task.recipientName }} · {{ task.recipientArea }}</text>
        <text class="address">{{ task.recipientAddress }}</text>
      </view>
    </view>
    <view v-else class="empty">当前没有待处理配送任务</view>

    <view class="message-strip" @click="openMessages">
      <view><text class="message-title">员工消息</text><text class="message-copy">派单与改派通知会在这里留存</text></view>
      <text class="badge">{{ messageStore.unread }}</text>
    </view>
  </view>
</template>

<style scoped lang="scss">
.page { min-height: 100vh; padding: 40rpx 32rpx 60rpx; box-sizing: border-box; background: linear-gradient(180deg, #eef5ff 0, #f5f7fb 360rpx); }
.hero, .task-row, .section-head, .message-strip { display: flex; align-items: center; justify-content: space-between; }
.greeting, .date, .metric-value, .recipient, .address, .message-title, .message-copy { display: block; }
.greeting { font-size: 44rpx; font-weight: 760; }
.date { margin-top: 12rpx; color: #64748b; font-size: 24rpx; }
.ghost { margin: 0; padding: 0 22rpx; color: #475569; font-size: 24rpx; line-height: 60rpx; background: rgb(255 255 255 / 70%); border-radius: 18rpx; }
.metrics { display: grid; grid-template-columns: repeat(3, 1fr); gap: 18rpx; margin-top: 44rpx; }
.metric { padding: 28rpx 20rpx; color: #475569; font-size: 24rpx; background: #fff; border-radius: 24rpx; }
.metric-value { margin-bottom: 10rpx; font-size: 48rpx; font-weight: 760; }
.blue .metric-value { color: #2563eb; } .green .metric-value { color: #059669; } .amber .metric-value { color: #d97706; }
.section-head { margin: 48rpx 4rpx 20rpx; }
.section-title { font-size: 32rpx; font-weight: 700; }
.link { color: #2563eb; font-size: 24rpx; }
.task-list { display: grid; gap: 18rpx; }
.task-card, .message-strip { padding: 30rpx; background: #fff; border-radius: 26rpx; box-shadow: 0 12rpx 40rpx rgb(30 64 175 / 6%); }
.task-no { font-size: 28rpx; font-weight: 700; }
.status { padding: 8rpx 16rpx; color: #2563eb; font-size: 22rpx; background: #eff6ff; border-radius: 999rpx; }
.recipient { margin-top: 24rpx; font-size: 28rpx; font-weight: 650; }
.address { margin-top: 8rpx; overflow: hidden; color: #64748b; font-size: 24rpx; text-overflow: ellipsis; white-space: nowrap; }
.empty { padding: 72rpx 24rpx; color: #94a3b8; text-align: center; background: #fff; border-radius: 26rpx; }
.message-strip { margin-top: 28rpx; }
.message-title { font-size: 28rpx; font-weight: 700; }.message-copy { margin-top: 8rpx; color: #64748b; font-size: 23rpx; }
.badge { min-width: 48rpx; height: 48rpx; color: #fff; line-height: 48rpx; text-align: center; background: #ef4444; border-radius: 999rpx; }
</style>

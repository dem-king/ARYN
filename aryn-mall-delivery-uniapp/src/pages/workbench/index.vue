<script setup lang="ts">
import { useAuthStore } from '@/store/auth'

const authStore = useAuthStore()
</script>

<template>
  <view class="page">
    <text class="title">你好，{{ authStore.user?.nickname || '配送员' }}</text>
    <text class="subtitle">工作台正在加载任务数据</text>
  </view>
</template>

<style scoped>
.page { padding: 48rpx 36rpx; }
.title, .subtitle { display: block; }
.title { font-size: 44rpx; font-weight: 700; }
.subtitle { margin-top: 16rpx; color: #64748b; font-size: 28rpx; }
</style>

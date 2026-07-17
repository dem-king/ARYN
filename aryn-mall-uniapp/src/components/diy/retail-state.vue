<script setup lang="ts">
import type { RetailLoadStatus } from '@/composables/useRetailData'

defineProps<{
  status: RetailLoadStatus
}>()
</script>

<template>
  <view v-if="status !== 'ready'" class="retail-state">
    <view v-if="status === 'loading'" class="retail-state__skeleton" />
    <text v-else>{{ status === 'empty' ? '暂无内容' : '暂时无法加载' }}</text>
  </view>
</template>

<style scoped lang="scss">
.retail-state {
  display: flex;
  min-height: 120rpx;
  align-items: center;
  justify-content: center;
  color: #909399;
  font-size: 24rpx;
}

.retail-state__skeleton {
  width: 100%;
  height: 88rpx;
  border-radius: 8rpx;
  background: #f2f3f5;
}
</style>

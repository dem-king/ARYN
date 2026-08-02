<script setup lang="ts">
import { ref, watch } from 'vue'
import { getOrderDeliveryProgress } from '@/api/delivery'
import type { DeliveryProgress } from '@/api/delivery'

interface Props {
  /** 订单ID */
  orderId: string
}

const props = defineProps<Props>()

const loading = ref(true)
const progress = ref<DeliveryProgress | null>(null)

/** 拨打配送员电话 */
function handleCallStaff(phone?: string) {
  if (!phone) {
    return uni.showToast({ title: '暂无电话号码', icon: 'none' })
  }
  uni.makePhoneCall({
    phoneNumber: phone,
    fail: () => {
      uni.showToast({ title: '拨打电话失败', icon: 'none' })
    },
  })
}

/** 加载配送进度 */
async function fetchProgress() {
  if (!props.orderId)
    return
  loading.value = true
  try {
    const res = await getOrderDeliveryProgress(props.orderId).send()
    progress.value = res as DeliveryProgress
  }
  catch (error) {
    console.error('获取配送进度失败:', error)
    progress.value = null
  }
  finally {
    loading.value = false
  }
}

watch(() => props.orderId, () => {
  fetchProgress()
}, { immediate: true })
</script>

<template>
  <view class="delivery-progress">
    <view v-if="loading" class="py-40rpx text-center text-26rpx text-gray-400">
      加载中...
    </view>

    <template v-else-if="progress">
      <!-- 配送员信息卡片 -->
      <view
        v-if="progress.staffName"
        class="staff-card mb-20rpx flex items-center justify-between rounded-20rpx bg-white p-30rpx"
      >
        <view class="flex items-center">
          <view class="staff-avatar">
            <text class="i-carbon:user-avatar text-40rpx text-white" />
          </view>
          <view class="ml-20rpx">
            <text class="text-28rpx font-bold">
              {{ progress.staffName }}
            </text>
            <view class="mt-4rpx text-24rpx text-gray-500">
              商城专属配送员
            </view>
          </view>
        </view>
        <view
          v-if="progress.staffPhone"
          class="staff-call"
          @click="handleCallStaff(progress.staffPhone)"
        >
          <text class="i-carbon:phone text-36rpx text-white" />
        </view>
      </view>

      <!-- 出车单号 -->
      <view v-if="progress.tripNo" class="mb-20rpx rounded-20rpx bg-white p-30rpx">
        <view class="flex items-center justify-between">
          <text class="text-26rpx text-gray-600">
            出车单号
          </text>
          <text class="text-26rpx">
            {{ progress.tripNo }}
          </text>
        </view>
      </view>

      <!-- 配送进度时间线 -->
      <view class="rounded-20rpx bg-white p-30rpx">
        <text class="mb-30rpx block text-28rpx font-bold">
          配送进度
        </text>
        <view class="timeline">
          <view
            v-for="(node, index) in progress.nodes"
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
              >
                <text
                  v-if="node.done"
                  class="i-carbon:checkmark text-20rpx text-white"
                />
              </view>
              <view
                v-if="index < progress.nodes.length - 1"
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
    </template>

    <view v-else class="py-40rpx text-center text-26rpx text-gray-400">
      暂无配送进度信息
    </view>
  </view>
</template>

<style lang="scss" scoped>
.delivery-progress {
  width: 100%;
}

.staff-card {
  background: linear-gradient(135deg, #0084ff 0%, #07c160 100%);
  color: #fff;

  :deep(.text-gray-500) {
    color: rgba(255, 255, 255, 0.8) !important;
  }
}

.staff-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 80rpx;
  height: 80rpx;
  border-radius: 50%;
  background-color: rgba(255, 255, 255, 0.25);
}

.staff-call {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  background-color: rgba(255, 255, 255, 0.25);
}

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
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32rpx;
  height: 32rpx;
  border-radius: 50%;
  background-color: #dcdfe6;
  flex-shrink: 0;
  z-index: 1;
  transition: all 0.3s ease;
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
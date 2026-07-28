<script setup lang="ts">
import type { MallDeliveryProgress } from '@/api/order/mallDelivery'

import { computed, onMounted, ref } from 'vue'
import {
  customerDeliveryTimeline,
  getMallDeliveryEvidenceAccess,
  getMallDeliveryProgress,
} from '@/api/order/mallDelivery'
import { orderReceiver } from '@/api/order/orderInfo'

const props = defineProps<{ orderId: string }>()
const emit = defineEmits<{ (event: 'received'): void }>()
const loading = ref(true)
const progress = ref<MallDeliveryProgress>()
const evidenceUrls = ref<Record<string, string>>({})
const timeline = computed(() =>
  progress.value ? customerDeliveryTimeline(progress.value) : [],
)

async function load() {
  loading.value = true
  try {
    progress.value = await getMallDeliveryProgress(props.orderId)
  }
  finally {
    loading.value = false
  }
}

async function previewEvidence(evidenceId: string) {
  const access = await getMallDeliveryEvidenceAccess(props.orderId, evidenceId)
  evidenceUrls.value[evidenceId] = access.accessUrl
  uni.previewImage({ current: access.accessUrl, urls: [access.accessUrl] })
}

function confirmReceived() {
  uni.showModal({
    title: '确认收货',
    content: '请确认商品已送达且状态正常。确认后订单将完成。',
    success: async ({ confirm }) => {
      if (!confirm)
        return
      await orderReceiver(props.orderId)
      useGlobalToast().success('确认收货成功')
      emit('received')
    },
  })
}

onMounted(load)
</script>

<template>
  <view class="m-2 rounded-xl bg-white p-4">
    <view class="mb-4 flex items-center justify-between">
      <view class="font-semibold">
        商城配送进度
      </view>
      <view v-if="progress?.assigneeName" class="text-24rpx text-gray-500">
        配送员 {{ progress.assigneeName }}
      </view>
    </view>
    <wd-loading v-if="loading" />
    <view v-else-if="progress">
      <view
        v-for="(item, index) in timeline"
        :key="`${item.title}-${index}`"
        class="timeline-item"
      >
        <view
          class="timeline-dot"
          :class="{ active: index === timeline.length - 1 }"
        />
        <view class="min-w-0 flex-1 pb-32rpx">
          <view class="text-28rpx">
            {{ item.title }}
          </view>
          <view v-if="item.time" class="mt-6rpx text-22rpx text-gray-400">
            {{
              item.time
            }}
          </view>
        </view>
      </view>
      <view
        v-if="progress.evidences?.length"
        class="mt-2 border-t border-gray-100 pt-3"
      >
        <view class="mb-2 text-26rpx">
          送达凭证
        </view>
        <view class="grid grid-cols-3 gap-2">
          <view
            v-for="evidence in progress.evidences"
            :key="evidence.id"
            class="evidence-tile"
            @click="previewEvidence(evidence.id)"
          >
            <image
              v-if="evidenceUrls[evidence.id]"
              :src="evidenceUrls[evidence.id]"
              mode="aspectFill"
            />
            <view
              v-else
              class="text-theme h-full flex items-center justify-center text-22rpx"
            >
              查看凭证
            </view>
          </view>
        </view>
      </view>
      <wd-button
        v-if="progress.status === 'DELIVERED'"
        block
        class="mt-4"
        type="primary"
        @click="confirmReceived"
      >
        确认收货
      </wd-button>
    </view>
  </view>
</template>

<style scoped lang="scss">
.timeline-item {
  position: relative;
  display: flex;
  gap: 20rpx;
}
.timeline-item:not(:last-child)::before {
  position: absolute;
  top: 18rpx;
  bottom: -2rpx;
  left: 7rpx;
  width: 2rpx;
  content: "";
  background: #e5e7eb;
}
.timeline-dot {
  z-index: 1;
  width: 16rpx;
  height: 16rpx;
  margin-top: 10rpx;
  border-radius: 50%;
  background: #cbd5e1;
}
.timeline-dot.active {
  background: var(--wot-color-theme);
  box-shadow: 0 0 0 8rpx rgb(7 193 96 / 12%);
}
.evidence-tile {
  height: 180rpx;
  overflow: hidden;
  border-radius: 12rpx;
  background: #f5f7fa;
}
.evidence-tile image {
  width: 100%;
  height: 100%;
}
</style>

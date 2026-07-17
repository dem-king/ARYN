<script setup lang="ts">
import { computed } from 'vue'

import { followDecorationLink } from '@/components/diy/link-resolver'
import { loadLimitedActivities } from '@/components/diy/retail-data'
import { retailCommonStyle } from '@/components/diy/retail-types'
import type { LimitedActivityProps } from '@/components/diy/retail-types'
import RetailState from '@/components/diy/retail-state.vue'
import { formatRemainingTime, useCountdownTicker } from '@/composables/useCountdown'
import { useDiyStyle } from '@/composables/useDiyStyle'
import { useRetailData } from '@/composables/useRetailData'

const props = withDefaults(defineProps<{ showData?: Partial<LimitedActivityProps> }>(), {
  showData: () => ({}),
})
const showData = computed<LimitedActivityProps>(() => ({
  commonStyle: props.showData.commonStyle || retailCommonStyle,
  count: Number(props.showData.count) || 3,
  dataSource: props.showData.dataSource || { mode: 'automatic', sort: 'start-time' },
  emptyStrategy: props.showData.emptyStrategy || 'placeholder',
  invalidStrategy: props.showData.invalidStrategy || 'hide',
  showCountdown: props.showData.showCountdown !== false,
  title: props.showData.title || '限时活动',
}))
const dynamicStyles = useDiyStyle(computed(() => showData.value.commonStyle))
const { items, shouldRender, status } = useRetailData(showData, loadLimitedActivities)
const { now } = useCountdownTicker()

function remaining(endTime: string) {
  return formatRemainingTime(endTime, now.value)
}

function openActivity(id: string) {
  followDecorationLink({ params: {}, path: '', targetId: id, type: 'activity' })
}
</script>

<template>
  <view v-if="shouldRender" class="limited-activity" :style="dynamicStyles">
    <view class="section-title">{{ showData.title }}</view>
    <RetailState v-if="status !== 'ready'" :status="status" />
    <view v-else class="activity-list">
      <view v-for="item in items" :key="item.id" class="activity-item" @click="openActivity(item.id)">
        <image v-if="item.imageUrl" class="activity-image" :src="item.imageUrl" mode="aspectFill" />
        <view v-else class="activity-image activity-image--empty">活动</view>
        <view class="activity-content">
          <view class="activity-name">{{ item.name || '限时活动' }}</view>
          <view v-if="showData.showCountdown" class="activity-time">
            {{ item.status === 'ended' || !remaining(item.endTime) ? '活动已结束' : `剩余 ${remaining(item.endTime)}` }}
          </view>
          <view class="activity-price-row">
            <text class="activity-price">￥{{ item.activityPrice.toFixed(2) }}</text>
            <text v-if="item.originalPrice" class="activity-original">￥{{ item.originalPrice.toFixed(2) }}</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.section-title { margin-bottom: 12rpx; color: #1f2329; font-size: 30rpx; font-weight: 600; }
.activity-list { display: flex; flex-direction: column; gap: 14rpx; }
.activity-item { display: flex; overflow: hidden; min-height: 140rpx; gap: 18rpx; border: 1rpx solid #ffe1dc; border-radius: 8rpx; background: #fff; }
.activity-image { display: flex; width: 150rpx; min-height: 140rpx; flex: 0 0 auto; align-items: center; justify-content: center; background: #fff1ee; color: #e5484d; font-size: 22rpx; }
.activity-content { min-width: 0; flex: 1; padding: 16rpx 16rpx 16rpx 0; }
.activity-name { overflow: hidden; color: #303133; font-size: 27rpx; font-weight: 500; text-overflow: ellipsis; white-space: nowrap; }
.activity-time { margin-top: 10rpx; color: #e5484d; font-size: 22rpx; }
.activity-price-row { display: flex; margin-top: 14rpx; align-items: baseline; gap: 10rpx; }
.activity-price { color: #e5484d; font-size: 30rpx; font-weight: 700; }
.activity-original { color: #a8abb2; font-size: 21rpx; text-decoration: line-through; }
</style>

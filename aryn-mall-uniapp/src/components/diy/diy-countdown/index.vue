<script setup lang="ts">
import { computed } from 'vue'

import { retailCommonStyle } from '@/components/diy/retail-types'
import type { CountdownProps } from '@/components/diy/retail-types'
import { formatRemainingTime, useCountdownTicker } from '@/composables/useCountdown'
import { useDiyStyle } from '@/composables/useDiyStyle'

const props = withDefaults(defineProps<{ showData?: Partial<CountdownProps> }>(), {
  showData: () => ({}),
})
const showData = computed<CountdownProps>(() => ({
  commonStyle: props.showData.commonStyle || retailCommonStyle,
  completedText: props.showData.completedText || '活动已结束',
  count: Number(props.showData.count) || 1,
  dataSource: props.showData.dataSource || { mode: 'manual' },
  emptyStrategy: props.showData.emptyStrategy || 'placeholder',
  invalidStrategy: props.showData.invalidStrategy || 'placeholder',
  targetTime: props.showData.targetTime || '',
  title: props.showData.title || '距活动结束',
}))
const dynamicStyles = useDiyStyle(computed(() => showData.value.commonStyle))
const { now } = useCountdownTicker()
const remaining = computed(() => formatRemainingTime(showData.value.targetTime, now.value))
const invalid = computed(() => !showData.value.targetTime || Number.isNaN(Date.parse(showData.value.targetTime)))
const shouldRender = computed(() => !invalid.value || showData.value.invalidStrategy !== 'hide')
</script>

<template>
  <view v-if="shouldRender" class="countdown" :style="dynamicStyles">
    <text class="countdown-title">{{ showData.title }}</text>
    <text class="countdown-value">{{ invalid || !remaining ? showData.completedText : remaining }}</text>
  </view>
</template>

<style scoped lang="scss">
.countdown { display: flex; min-height: 76rpx; align-items: center; justify-content: space-between; gap: 18rpx; }
.countdown-title { color: #303133; font-size: 28rpx; font-weight: 500; }
.countdown-value { color: #e5484d; font-family: monospace; font-size: 32rpx; font-weight: 700; }
</style>

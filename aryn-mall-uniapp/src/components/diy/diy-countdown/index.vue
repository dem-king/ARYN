<script setup lang="ts">
import type { CountdownProps } from '@/components/diy/retail-types'

import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { retailCommonStyle } from '@/components/diy/retail-types'
import { formatRemainingTime, useCountdownTicker } from '@/composables/useCountdown'
import { useDiyStyle } from '@/composables/useDiyStyle'
import { resolveTargetTimestamp } from '@/utils/datetime'

const props = withDefaults(defineProps<{ showData?: Partial<CountdownProps> }>(), {
  showData: () => ({}),
})
const { t } = useI18n()
const showData = computed<CountdownProps>(() => ({
  commonStyle: props.showData.commonStyle || retailCommonStyle,
  completedText: props.showData.completedText || t('diy.countdown.completedText'),
  count: Number(props.showData.count) || 1,
  dataSource: props.showData.dataSource || { mode: 'manual' },
  emptyStrategy: props.showData.emptyStrategy || 'placeholder',
  invalidStrategy: props.showData.invalidStrategy || 'placeholder',
  targetTime: props.showData.targetTime || '',
  title: props.showData.title || t('diy.countdown.title'),
}))
const dynamicStyles = useDiyStyle(computed(() => showData.value.commonStyle))
const { now } = useCountdownTicker()
const remaining = computed(() => formatRemainingTime(showData.value.targetTime, now.value))
const invalid = computed(() => !showData.value.targetTime || resolveTargetTimestamp(showData.value.targetTime) === null)
const shouldRender = computed(() => !invalid.value || showData.value.invalidStrategy !== 'hide')
</script>

<template>
  <view v-if="shouldRender" class="countdown" :style="dynamicStyles">
    <text class="countdown-title">
      {{ showData.title }}
    </text>
    <text class="countdown-value">
      {{ invalid || !remaining ? showData.completedText : remaining }}
    </text>
  </view>
</template>

<style scoped lang="scss">
.countdown { display: flex; min-height: 76rpx; align-items: center; justify-content: space-between; gap: 18rpx; }
.countdown-title { color: #303133; font-size: 28rpx; font-weight: 500; }
.countdown-value { color: var(--wot-color-theme-primary, #ff2237); font-family: monospace; font-size: 32rpx; font-weight: 700; }
</style>

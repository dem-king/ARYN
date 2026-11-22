<script setup lang="ts">
import type { DeliverySchedule } from '@/api/order/delivery-group'
import { computed } from 'vue'
import { useI18n } from '@/sub-pages/locales'

const props = defineProps<{ schedule?: DeliverySchedule, loading?: boolean, failed?: boolean, fetchedAt: number, checkout?: boolean }>()
defineEmits<{ refresh: [] }>()
const { t } = useI18n()
const closed = computed(() => !!props.schedule?.cutoff && Date.parse(props.schedule.cutoff.replace(' ', 'T')) <= props.fetchedAt)
const pending = computed(() => !props.schedule || props.schedule.needsScheduling)
</script>

<template>
  <view class="schedule-card">
    <view class="schedule-heading"><text class="schedule-title">{{ t('deliverySchedule.title') }}</text><text class="schedule-refresh" @click="$emit('refresh')">{{ t('deliverySchedule.refresh') }}</text></view>
    <text v-if="loading" class="schedule-line">{{ t('common.loading') }}</text>
    <text v-else-if="failed" class="schedule-line">{{ t('deliverySchedule.failed') }}</text>
    <template v-else>
      <text class="schedule-state">{{ pending ? t('deliverySchedule.pending') : closed ? t('deliverySchedule.closed') : t(checkout ? 'deliverySchedule.expected' : 'deliverySchedule.grouped') }}</text>
      <text v-if="schedule?.windowStart && !pending" class="schedule-line">{{ schedule.windowStart }} ~ {{ schedule.windowEnd }}</text>
      <text v-if="schedule?.cutoff && !pending" class="schedule-line">{{ t('deliverySchedule.cutoff', { time: schedule.cutoff }) }}</text>
      <text v-if="checkout" class="schedule-line">{{ t('deliverySchedule.checkoutHint') }}</text>
      <text class="schedule-note">{{ t('deliverySchedule.independent') }}</text>
    </template>
  </view>
</template>

<style scoped>
.schedule-card { margin: 20rpx 24rpx; padding: 26rpx; border: 1rpx solid var(--wot-color-border, #e5e7eb); border-left: 6rpx solid #0f766e; border-radius: 16rpx; background: var(--wot-color-bg, #fff); }
.schedule-heading { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14rpx; }
.schedule-title { font-size: 28rpx; font-weight: 600; }
.schedule-refresh { padding: 8rpx; font-size: 24rpx; color: #0f766e; }
.schedule-state { display: block; font-size: 28rpx; font-weight: 600; color: #0f766e; }
.schedule-line, .schedule-note { display: block; margin-top: 12rpx; font-size: 24rpx; line-height: 1.6; color: var(--wot-color-content, #64748b); }
</style>

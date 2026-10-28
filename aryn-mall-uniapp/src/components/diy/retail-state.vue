<script setup lang="ts">
import type { RetailLoadStatus } from '@/composables/useRetailData'

import { useI18n } from 'vue-i18n'

defineProps<{
  status: RetailLoadStatus
}>()

const { t } = useI18n()
</script>

<template>
  <view v-if="status !== 'ready'" class="retail-state">
    <view v-if="status === 'loading'" class="retail-state__skeleton" />
    <text v-else>{{ status === 'empty' ? t('diy.retailState.empty') : t('diy.retailState.invalid') }}</text>
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

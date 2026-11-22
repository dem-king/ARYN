<script setup lang="ts">
import type { GoodsUseScenario } from '@/utils/goods-use-scenarios'

defineProps<{ scenarios?: GoodsUseScenario[] | null }>()

const iconClassMap: Record<GoodsUseScenario['icon'], string> = {
  snowflake: 'i-carbon:snowflake',
  restaurant: 'i-carbon:restaurant',
}
</script>

<template>
  <view v-if="scenarios?.length" class="use-scenarios">
    <view
      v-for="scenario in scenarios"
      :key="scenario.key"
      class="use-scenario dark:bg-[var(--wot-dark-background4)]"
      :class="`use-scenario--${scenario.tone}`"
    >
      <view class="use-scenario__head">
        <text class="use-scenario__icon" :class="iconClassMap[scenario.icon]" />
        <text class="use-scenario__title">{{ scenario.title }}</text>
      </view>
      <text class="use-scenario__content">{{ scenario.content }}</text>
    </view>
  </view>
</template>

<style scoped>
.use-scenarios {
  display: flex;
  gap: 12rpx;
  margin: 16rpx 24rpx 0;
}

.use-scenario {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 10rpx;
  padding: 20rpx 18rpx;
  border-radius: 14rpx;
  background: var(--use-scenario-bg);
}

.use-scenario--blue {
  --use-scenario-bg: #f1f6ff;
  --use-scenario-icon: #3b82f6;
  --use-scenario-title: #2458c9;
}

.use-scenario--orange {
  --use-scenario-bg: #fff7ed;
  --use-scenario-icon: #ea8a1f;
  --use-scenario-title: #b45309;
}

.use-scenario__head {
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.use-scenario__icon {
  width: 28rpx;
  height: 28rpx;
  flex: none;
  color: var(--use-scenario-icon);
  font-size: 28rpx;
}

.use-scenario__title {
  color: var(--use-scenario-title);
  font-size: 26rpx;
  font-weight: 600;
  line-height: 1.3;
}

.use-scenario__content {
  color: var(--app-text-2);
  font-size: 24rpx;
  line-height: 1.55;
  overflow-wrap: anywhere;
}
</style>
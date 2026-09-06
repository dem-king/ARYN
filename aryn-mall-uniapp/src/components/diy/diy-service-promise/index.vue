<script setup lang="ts">
import { computed } from 'vue'

import type { ServicePromiseProps } from '@/components/diy/retail-types'
import { useDiyStyle } from '@/composables/useDiyStyle'

const props = defineProps({
  showData: {
    type: Object,
    default: () => ({}),
  },
})

const componentProps = computed(() => props.showData as unknown as ServicePromiseProps)
const items = computed(() => componentProps.value.items || [])
const dynamicStyles = useDiyStyle(computed(() => componentProps.value.commonStyle))
</script>

<template>
  <view class="diy-service-promise" :style="dynamicStyles">
    <view v-if="componentProps.title" class="promise-title">
      {{ componentProps.title }}
    </view>
    <view v-if="items.length === 0" class="promise-empty">
      暂无服务承诺，请在装修中配置
    </view>
    <view v-else class="promise-row">
      <view v-for="item in items" :key="item.id" class="promise-item">
        <image v-if="item.iconUrl" class="promise-icon" :src="item.iconUrl" />
        <view class="promise-text">
          <view class="promise-name">
            {{ item.title }}
          </view>
          <view v-if="item.description" class="promise-desc">
            {{ item.description }}
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.diy-service-promise {
  padding: 20rpx;
}

.promise-title {
  margin-bottom: 16rpx;
  font-size: 32rpx;
  font-weight: 600;
}

.promise-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16rpx;
}

.promise-item {
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 16rpx;
  background: #fff;
  border-radius: 12rpx;
}

.promise-icon {
  width: 48rpx;
  height: 48rpx;
}

.promise-name {
  font-size: 26rpx;
  font-weight: 600;
}

.promise-desc {
  margin-top: 4rpx;
  font-size: 22rpx;
  color: #999;
}

.promise-empty {
  padding: 40rpx;
  color: #999;
  font-size: 26rpx;
  text-align: center;
}
</style>

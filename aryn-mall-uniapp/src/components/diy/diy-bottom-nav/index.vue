<script setup lang="ts">
import { computed } from 'vue'

import { followDecorationLink } from '@/components/diy/link-resolver'
import type { BottomNavProps } from '@/components/diy/retail-types'
import { useDiyStyle } from '@/composables/useDiyStyle'

const props = defineProps({
  showData: {
    type: Object,
    default: () => ({}),
  },
})

const componentProps = computed(() => props.showData as unknown as BottomNavProps)
const items = computed(() => componentProps.value.items || [])
const dynamicStyles = useDiyStyle(computed(() => componentProps.value.commonStyle))
</script>

<template>
  <view class="diy-bottom-nav" :style="dynamicStyles">
    <view
      v-if="items.length === 0"
      class="bottomnav-empty"
    >
      暂无导航项，请在装修中配置
    </view>
    <view
      v-else
      class="bottomnav-bar"
      :style="{
        backgroundColor: componentProps.backgroundColor || '#ffffff',
        color: componentProps.textColor || '#333333',
      }"
    >
      <view
        v-for="item in items"
        :key="item.id"
        class="bottomnav-item"
        @click="followDecorationLink(item.link)"
      >
        <image v-if="item.iconUrl" class="bottomnav-icon" :src="item.iconUrl" />
        <text
          class="bottomnav-text"
          :style="{ color: componentProps.activeColor || '#ff5000' }"
        >
          {{ item.text }}
        </text>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.diy-bottom-nav {
  width: 100%;
}

.bottomnav-bar {
  display: flex;
  padding: 12rpx 0 calc(12rpx + env(safe-area-inset-bottom));
}

.bottomnav-item {
  display: grid;
  flex: 1;
  gap: 4rpx;
  justify-items: center;
}

.bottomnav-icon {
  width: 44rpx;
  height: 44rpx;
}

.bottomnav-text {
  font-size: 22rpx;
}

.bottomnav-empty {
  padding: 24rpx;
  color: #999;
  font-size: 24rpx;
  text-align: center;
}
</style>

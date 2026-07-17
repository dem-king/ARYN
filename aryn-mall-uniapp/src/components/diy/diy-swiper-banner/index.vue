<script setup lang="ts">
import { computed } from 'vue'

import { followDecorationLink } from '@/components/diy/link-resolver'

const props = defineProps({
  showData: {
    type: Object,
    default: () => ({}),
  },
})

const imageList = computed(() => props.showData.imageList || [])
const interval = computed(() => props.showData.interval || 3000)
const indicatorDots = computed(() => props.showData.indicatorDots !== false)
const indicatorColor = computed(() => props.showData.indicatorColor || 'rgba(0, 0, 0, 0.3)')
const indicatorActiveColor = computed(() => props.showData.indicatorActiveColor || '#fff')
const height = computed(() => props.showData.height || 180)
const borderRadius = computed(() => props.showData.borderRadius || 0)

function handleClick(item: any) {
  followDecorationLink(item.link || item.linkUrl)
}
</script>

<template>
  <view class="swiper-banner" :style="{ borderRadius: `${borderRadius}px`, overflow: 'hidden' }">
    <swiper
      circular
      autoplay
      :interval="interval"
      :indicator-dots="indicatorDots"
      :indicator-color="indicatorColor"
      :indicator-active-color="indicatorActiveColor"
      :style="{ height: `${height}px` }"
    >
      <swiper-item
        v-for="(item, index) in imageList"
        :key="index"
        @click="handleClick(item)"
      >
        <image
          :src="item.url"
          mode="scaleToFill"
          :style="{ width: '100%', height: `${height}px`, display: 'block' }"
        />
      </swiper-item>
    </swiper>
  </view>
</template>

<style lang="scss" scoped>
.swiper-banner {
  margin: 0 12px;
}
</style>

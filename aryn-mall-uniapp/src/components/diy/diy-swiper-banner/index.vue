<script setup lang="ts">
import { computed } from 'vue'

import { followDecorationLink } from '@/components/diy/link-resolver'
import { useMallThemeStore } from '@/store/mallThemeStore'
import { getCommonStyle } from '@/utils/common-style'

const props = defineProps({
  showData: {
    type: Object,
    default: () => ({}),
  },
})

/** 全站自动动效总开关（商城主题配置）；关闭时停止自动切换、保留手动滑动 */
const mallThemeStore = useMallThemeStore()

const imageList = computed(() => props.showData.imageList || [])
const interval = computed(() => props.showData.interval || 3000)
const indicatorDots = computed(() => props.showData.indicatorDots !== false)
const indicatorColor = computed(() => props.showData.indicatorColor || 'rgba(0, 0, 0, 0.3)')
const indicatorActiveColor = computed(() => props.showData.indicatorActiveColor || '#fff')
const height = computed(() => props.showData.height || 180)
const borderRadius = computed(() => props.showData.borderRadius || 0)

/**
 * 通用样式（外边距/内边距/背景/圆角）挂根节点，与设置面板「通用样式」同源；
 * 老数据没有 commonStyle 时不绑内联样式，保留 CSS 的 12px 左右留白兜底。
 */
const dynamicStyles = computed(() => {
  const commonStyle = props.showData.commonStyle
  return commonStyle ? getCommonStyle(commonStyle) : {}
})

function handleClick(item: any) {
  followDecorationLink(item.link || item.linkUrl)
}
</script>

<template>
  <view class="swiper-banner" :style="dynamicStyles">
    <view
      class="swiper-banner-media"
      :style="{ borderRadius: `${borderRadius}px`, overflow: 'hidden' }"
    >
      <swiper
        circular
        :autoplay="mallThemeStore.autoMotionEnabled"
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
            :src="resolveImageSrc(item.url)"
            mode="scaleToFill"
            lazy-load
            :style="{ width: '100%', height: `${height}px`, display: 'block' }"
          />
        </swiper-item>
      </swiper>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.swiper-banner {
  margin: 0 12px;
}
</style>

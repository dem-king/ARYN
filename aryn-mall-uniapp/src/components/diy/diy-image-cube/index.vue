<script setup lang="ts">
import type { ImageCubeItem, ImageCubeProps } from '@/components/diy/retail-types'

import { computed } from 'vue'

import { followDecorationLink } from '@/components/diy/link-resolver'
import { useDiyStyle } from '@/composables/useDiyStyle'

const props = defineProps({
  showData: {
    type: Object,
    default: () => ({}),
  },
})

const componentProps = computed(() => props.showData as unknown as ImageCubeProps)
const dynamicStyles = useDiyStyle(computed(() => componentProps.value.commonStyle))

/** 布局 → 修饰符类名（'1+2' 等带符号值不能直接进 class） */
const layoutClass = computed(() => {
  const layout = componentProps.value.layout || '4'
  return layout.replace('+', '')
})

const items = computed(() => (componentProps.value.items ?? []) as ImageCubeItem[])

/** 一左多右布局时首格是大图（跨行列） */
function isBig(index: number) {
  const layout = componentProps.value.layout
  return index === 0 && (layout === '1+2' || layout === '1+3')
}

function onTap(item: ImageCubeItem) {
  if (item.url)
    followDecorationLink(item.link)
}
</script>

<template>
  <view
    class="cube-grid"
    :class="`cube-grid--${layoutClass}`"
    :style="dynamicStyles"
  >
    <view
      v-for="(item, index) in items"
      :key="item.id || index"
      class="cube-cell"
      :class="{ 'cube-cell--big': isBig(index) }"
      @click="onTap(item)"
    >
      <image
        v-if="item.url"
        class="cube-cell__img"
        :src="resolveImageSrc(item.url)"
        mode="aspectFill"
        lazy-load
      />
      <view v-else class="cube-cell__placeholder" />
    </view>
  </view>
</template>

<style lang="scss" scoped>
/* 格距 8rpx 与管理端预览 4px 同源（1rpx = 0.5px） */
.cube-grid {
  display: grid;
  gap: 8rpx;
}

/* 大格 376rpx、小格 184rpx、三分格 120rpx，均与管理端预览逐值换算 */
.cube-grid--1 {
  grid-template-columns: 1fr;
  grid-auto-rows: 376rpx;
}

.cube-grid--2h {
  grid-template-columns: 1fr;
  grid-auto-rows: 184rpx;
}

.cube-grid--2v {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  grid-auto-rows: 376rpx;
}

.cube-grid--4 {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  grid-auto-rows: 184rpx;
}

.cube-grid--12 {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  grid-template-rows: repeat(2, 184rpx);
}

.cube-grid--13 {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  grid-template-rows: repeat(3, 120rpx);
}

.cube-cell {
  overflow: hidden;
}

.cube-cell--big {
  grid-row: span 2;
}

.cube-grid--13 .cube-cell--big {
  grid-row: span 3;
}

.cube-cell__img {
  width: 100%;
  height: 100%;
  display: block;
}

.cube-cell__placeholder {
  width: 100%;
  height: 100%;
  background: #f2f3f5;
}
</style>

<script lang="ts" setup>
import { computed } from 'vue'
import { useDiyStyle } from '@/composables/useDiyStyle'

const props = defineProps<{
  showData: Record<string, unknown>
}>()
const getGapStyle = computed(() => {
  const height = Number(props.showData.height) || 0
  const direction = typeof props.showData.gapBgColorDirection === 'string'
    ? props.showData.gapBgColorDirection
    : 'to right'
  const startColor = typeof props.showData.gapBgStartColor === 'string'
    ? props.showData.gapBgStartColor
    : ''
  const endColor = typeof props.showData.gapBgEndColor === 'string'
    ? props.showData.gapBgEndColor
    : startColor
  return {
    height: `${height}px`,
    background: `linear-gradient(${direction},${startColor},${endColor})`,
  }
})
const dynamicStyles = useDiyStyle(computed(() => props.showData.commonStyle))
</script>

<template>
  <view :style="dynamicStyles">
    <view class="gap" :style="getGapStyle" />
  </view>
</template>

<style scoped lang="scss"></style>

<script lang="ts" setup>
import { computed } from 'vue'
import { useDiyStyle } from '@/composables/useDiyStyle'

interface ShowData {
  commonStyle?: any
  height: number// 高度
  gapBgColorDirection: string
  gapBgStartColor: string
  gapBgEndColor: string
}
const props = defineProps<{
  showData: ShowData
}>()
const getGapStyle = computed(() => {
  return {
    height: `${props.showData.height}px`,
    background: `linear-gradient(${props.showData.gapBgColorDirection || 'to right'},${props.showData.gapBgStartColor || ''},${props.showData.gapBgEndColor || props.showData.gapBgStartColor || ''})`,
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

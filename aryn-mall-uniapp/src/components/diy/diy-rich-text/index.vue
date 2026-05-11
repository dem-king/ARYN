<script setup lang="ts">
// @ts-expect-error: mp-html type declaration issue
import mpHtml from 'mp-html/dist/uni-app/components/mp-html/mp-html'
import { useDiyStyle } from '@/composables/useDiyStyle'

const props = defineProps({
  showData: {
    type: Object,
    default: () => { },
  },
})
const dynamicStyles = useDiyStyle(computed(() => props.showData.commonStyle))
</script>

<template>
  <view :style="dynamicStyles">
    <mp-html :content="showData.content" />
  </view>
</template>

<style></style>

<script setup lang="ts">
// @ts-expect-error: mp-html type declaration issue
import mpHtml from 'mp-html/dist/uni-app/components/mp-html/mp-html'
import { computed } from 'vue'

import { useDiyStyle } from '@/composables/useDiyStyle'

import { sanitizeCustomHtmlHtml } from './sanitize-html'

const props = defineProps<{
  showData: Record<string, unknown>
}>()

const rawHtml = computed(() =>
  typeof props.showData.html === 'string' ? props.showData.html : '',
)

const safeHtml = computed(() => sanitizeCustomHtmlHtml(rawHtml.value))

const hasContent = computed(() => safeHtml.value.trim().length > 0)

const containerStyle = computed(() => {
  const style: Record<string, string> = {}
  const height = Number(props.showData.height)
  if (Number.isFinite(height) && height > 0) {
    style.height = `${height}px`
    style.overflow = 'hidden'
  }
  return style
})

const dynamicStyles = useDiyStyle(computed(() => props.showData.commonStyle))
</script>

<template>
  <view :style="dynamicStyles">
    <view v-if="hasContent" class="custom-html-container" :style="containerStyle">
      <mp-html :content="safeHtml" />
    </view>
    <view v-else class="custom-html-placeholder">暂无内容</view>
  </view>
</template>

<style scoped lang="scss">
.custom-html-container {
  width: 100%;
}

.custom-html-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24rpx 0;
  font-size: 26rpx;
  color: #999;
}
</style>

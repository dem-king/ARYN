<script setup lang="ts">
import type { NoticeBarScrollDirection } from 'wot-design-uni/components/wd-notice-bar/types'
import { useDiyStyle } from '@/composables/useDiyStyle'

interface NoticeItem {
  content?: unknown
  type?: unknown
  url?: unknown
}
const props = defineProps<{
  showData: Record<string, unknown>
}>()

function getString(value: unknown, fallback = '') {
  return typeof value === 'string' ? value : fallback
}

const noticeData = computed(() => {
  const direction: NoticeBarScrollDirection
    = props.showData.direction === 'vertical' ? 'vertical' : 'horizontal'
  const contentList = Array.isArray(props.showData.contentList)
    ? props.showData.contentList as NoticeItem[]
    : []
  return {
    color: getString(props.showData.color),
    contentList,
    direction,
    speed: Number(props.showData.speed) || 50,
    titleColor: getString(props.showData.titleColor),
    titleSize: Number(props.showData.titleSize) || 18,
    titleStyle: getString(props.showData.titleStyle),
    titleText: getString(props.showData.titleText),
    titleType: getString(props.showData.titleType),
    titleUrl: getString(props.showData.titleUrl),
  }
})
const getNoticeText = computed(() => {
  return noticeData.value.contentList.map(v => getString(v.content))
})
const dynamicStyles = useDiyStyle(computed(() => props.showData.commonStyle))

// 跳转
function toPage(text: string, index: number) {
  const item = noticeData.value.contentList[index]
  if (item?.type === 1 && typeof item.url === 'string') {
    toJumpUrl(item.url)
  }
}
</script>

<template>
  <view :style="dynamicStyles">
    <wd-notice-bar
      custom-class="dark:text-white!"
      :text="getNoticeText" :direction="noticeData.direction" :color="noticeData.color"
      :speed="noticeData.speed" background-color="transparent !important;" custom-style="padding: 0 !important;" @click="toPage"
    >
      <template #prefix>
        <image
          v-if="noticeData.titleType === '1'" :src="noticeData.titleUrl" mode="scaleToFill"
          style="width: 18px; height: 18px;"
        />
        <view
          v-else-if="noticeData.titleType === '2'"
          class="dark:text-white!" :style="{
            color: noticeData.titleColor,
            fontSize: `${noticeData.titleSize}px`,
            fontWeight: noticeData.titleStyle === '1' ? 'bold' : 'normal',
          }"
        >
          {{ noticeData.titleText }}
        </view>
        <wd-icon v-else name="notification" :size="noticeData.titleSize" custom-class="dark:text-white!" :color="noticeData.titleColor" :style="{ fontWeight: noticeData.titleStyle === '1' ? 'bold' : 'normal' }" />
      </template>
    </wd-notice-bar>
  </view>
</template>

<style lang="scss" scoped>

</style>

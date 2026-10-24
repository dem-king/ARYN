<script setup lang="ts">
import type { NoticeBarScrollDirection } from 'wot-design-uni/components/wd-notice-bar/types'
import { computed, ref } from 'vue'

import { followDecorationLink } from '@/components/diy/link-resolver'
import { useDiyStyle } from '@/composables/useDiyStyle'

import type { NoticeContentItem } from './notice-link'
import { hasNoticeLink, resolveNoticeLink } from './notice-link'

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
    ? props.showData.contentList as NoticeContentItem[]
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
const hasLink = computed(() => hasNoticeLink(noticeData.value.contentList))

const baseStyle = useDiyStyle(computed(() => props.showData.commonStyle))
const dynamicStyles = computed(() => ({
  ...baseStyle.value,
  '--wot-notice-bar-line-height': '20px',
}))

// 横向滚动时 wd-notice-bar 始终展示首条；竖向滚动用 next 同步当前条目
const activeIndex = ref(0)
function rememberIndex(payload?: { index?: number }) {
  if (typeof payload?.index === 'number')
    activeIndex.value = payload.index
}
function rememberNext(index: number) {
  activeIndex.value = index
}

function openNotice() {
  const item = noticeData.value.contentList[activeIndex.value]
  followDecorationLink(resolveNoticeLink(item))
}
</script>

<template>
  <view :style="dynamicStyles" @click="openNotice">
    <wd-notice-bar
      :text="getNoticeText" :direction="noticeData.direction" :color="noticeData.color"
      :speed="noticeData.speed" background-color="transparent !important;" custom-class="dark:text-white!"
      custom-style="padding: 0 !important;" @click="rememberIndex" @next="rememberNext"
    >
      <template #prefix>
        <view class="notice-prefix">
          <image
            v-if="noticeData.titleType === '1' && noticeData.titleUrl" :src="noticeData.titleUrl" mode="scaleToFill"
            :style="{ width: `${noticeData.titleSize}px`, height: `${noticeData.titleSize}px` }"
          />
          <view
            v-else-if="noticeData.titleType === '2'"
            class="dark:text-white!" :style="{
              color: noticeData.titleColor,
              fontSize: `${noticeData.titleSize}px`,
              fontWeight: noticeData.titleStyle === '1' ? 'bold' : 'normal',
              lineHeight: '20px',
            }"
          >
            {{ noticeData.titleText }}
          </view>
          <wd-icon
            v-else name="notification" :size="noticeData.titleSize" custom-class="dark:text-white!"
            :color="noticeData.titleColor"
          />
        </view>
      </template>
      <template #suffix>
        <view v-if="hasLink" aria-hidden="true" class="notice-arrow">
          ›
        </view>
      </template>
    </wd-notice-bar>
  </view>
</template>

<style lang="scss" scoped>
.notice-prefix {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  margin-right: 6px;
}

.notice-arrow {
  flex-shrink: 0;
  margin-left: 6px;
  font-size: 14px;
  line-height: 20px;
  opacity: 0.5;
}
</style>

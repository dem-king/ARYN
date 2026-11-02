<script setup lang="ts">
import type { NoticeBarScrollDirection } from 'wot-design-uni/components/wd-notice-bar/types'
import type { NoticeContentItem } from './notice-link'

import { computed, ref } from 'vue'
import MarqueeText from '@/components/diy/diy-activity-strip/marquee-text.vue'
import { followDecorationLink } from '@/components/diy/link-resolver'
import { useDiyStyle } from '@/composables/useDiyStyle'

import { useMallThemeStore } from '@/store/mallThemeStore'
import { hasNoticeLink, resolveNoticeLink } from './notice-link'
import { resolveNoticePrefix } from './notice-prefix'

const props = defineProps<{
  showData: Record<string, unknown>
}>()

const mallThemeStore = useMallThemeStore()

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
    titleText: getString(props.showData.titleText).trim(),
    titleType: getString(props.showData.titleType),
    titleUrl: getString(props.showData.titleUrl),
  }
})
// 前缀渲染类型：配置不足以渲染时回落铃铛（见 notice-prefix.ts，
// 存量数据存在「切了文字前缀但文案为空」的组合，否则公告条会留一段空白前缀）
const prefixKind = computed(() => resolveNoticePrefix(noticeData.value))
// 前缀颜色留空时跟随正文颜色，与管理端画布 notice/index.vue 同口径
const prefixColor = computed(() => noticeData.value.titleColor || noticeData.value.color)

const getNoticeText = computed(() => {
  return noticeData.value.contentList.map(v => getString(v.content))
})
const hasLink = computed(() => hasNoticeLink(noticeData.value.contentList))
/**
 * 是否有可展示的公告文案：空公告整块不渲染。
 *
 * 公告条自带背景色与圆角，运营把文案清空后如果照常渲染，页面上会留下一条
 * 没有任何文字的色块——用户看到的就是「这里有个多余的空背景」。
 */
const hasContent = computed(() => getNoticeText.value.some(text => text.trim().length > 0))
/**
 * 单条公告：横向走无缝跑马灯（ticker），纵向保持静态。
 *
 * `wd-notice-bar` 的横向滚动只把文案整条滚出可视区、空等一段时间再跳回来，
 * 单条时中段会只剩一个铃铛，像内容丢了；可公告条本身是运营配了滚动速度的
 * 组件，也不能因为只有一条就完全静止。MarqueeText 的 ticker 模式按容器
 * 宽度重复文案、无缝循环：既不出现空白，又始终有滚动动效。
 */
const singleNotice = computed(() => getNoticeText.value.length <= 1)
const singleTicker = computed(() => singleNotice.value && noticeData.value.direction === 'horizontal')

const baseStyle = useDiyStyle(computed(() => props.showData.commonStyle))
const dynamicStyles = computed(() => ({
  ...baseStyle.value,
  '--wot-notice-bar-line-height': '20px',
}))

// 横向轮播时 wd-notice-bar 始终展示首条；竖向轮播用 next 同步当前条目
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
  <view v-if="hasContent" class="notice-base" :style="dynamicStyles" @click="openNotice">
    <view class="notice">
      <!-- 前缀（铃铛 / 图片 / 文字）与箭头在行内渲染，不再走 wd-notice-bar 的插槽，
           原因见下方 wd-notice-bar 处注释；结构与页面设计器画布 notice/index.vue 一致 -->
      <image
        v-if="prefixKind === 'image'" class="notice-icon notice-icon--img" :src="noticeData.titleUrl"
        mode="scaleToFill" :style="{ width: `${noticeData.titleSize}px`, height: `${noticeData.titleSize}px` }"
      />
      <view
        v-else-if="prefixKind === 'text'" class="notice-icon notice-icon--text" :style="{
          color: prefixColor,
          fontSize: `${noticeData.titleSize}px`,
          fontWeight: noticeData.titleStyle === '1' ? 'bold' : 'normal',
        }"
      >
        {{ noticeData.titleText }}
      </view>
      <view v-else class="notice-icon">
        <wd-icon
          name="notification" :size="noticeData.titleSize" :color="prefixColor"
          custom-class="dark:text-white!"
        />
      </view>
      <view class="notice-body" :style="{ color: noticeData.color }">
        <MarqueeText
          v-if="singleTicker" ticker :text="getNoticeText[0] || ''" :speed="noticeData.speed"
          :motion-enabled="mallThemeStore.autoMotionEnabled"
        />
        <!--
          多条横向 / 竖向：交给 wd-notice-bar 逐条轮播。

          **禁止给它传任何插槽**：小程序端插槽是编译期静态登记的——父模板只要声明了
          默认插槽，产物就把 'd' 写进 wd-notice-bar 的 `u-s`，子组件 `$slots.d` 恒为真，
          永远渲染 `<slot>`，内建文案 fallback（`{{ currentText }}`）被顶掉。多条公告时
          上面的单条跑马灯分支不渲染，插槽为空，整条公告就只剩铃铛和箭头。
          H5 的插槽按 v-if 条件注册，所以浏览器上看不出问题（跨端差异只在小程序出现）。
        -->
        <wd-notice-bar
          v-else :text="getNoticeText" :direction="noticeData.direction" :color="noticeData.color"
          :speed="noticeData.speed" :scrollable="!singleNotice" :wrapable="singleNotice"
          background-color="transparent !important;" custom-class="dark:text-white!"
          custom-style="padding: 0 !important;" @click="rememberIndex" @next="rememberNext"
        />
      </view>
      <view
        v-if="hasLink" aria-hidden="true" class="notice-arrow"
        :style="{ color: noticeData.color }"
      >
        ›
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
/* 行高 / 字号与 wd-notice-bar、页面设计器画布三端同口径：20px / 12px */
.notice-base {
  min-height: 20px;
}

.notice {
  display: flex;
  align-items: center;
  min-height: 20px;
  font-size: 12px;
  line-height: 20px;
}

.notice-icon {
  flex-shrink: 0;
  margin-right: 6px;
  line-height: 20px;
}

.notice-icon--img {
  display: block;
}

.notice-icon--text {
  line-height: 20px;
}

/* 跑马灯 / 轮播都靠 flex:1 + min-width:0 拿到可用宽度，否则单行文本会把整行撑开 */
.notice-body {
  flex: 1;
  min-width: 0;
}

.notice-arrow {
  flex-shrink: 0;
  margin-left: 6px;
  font-size: 14px;
  line-height: 20px;
  opacity: 0.5;
}
</style>

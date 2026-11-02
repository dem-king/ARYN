<script setup lang="ts">
/**
 * 单行跑马灯文本（活动入口条、公告条共用）。
 *
 * 入口条卡片窄，真实活动名/钩子放不进一行时不再换行或省略：
 * 文本超出容器宽度时自动横向滚动，短文案保持静止。
 * 公告条用 `ticker` 模式：文案不超宽也持续滚动，并按容器宽度重复文案，
 * 避免「滚出去后整条空白」的观感（运营配置了滚动速度即预期有动效）。
 *
 * 跨端测量走 `uni.createSelectorQuery`：小程序没有 DOM，H5 也没有必要
 * 单独写一套。测量节点用 `position:absolute + visibility:hidden` 的
 * shrink-to-fit 容器拿文本真实宽度，避免被父级 overflow 裁掉后测不准。
 *
 * 自动滚动受全站「自动动效」总开关约束（由父组件传入 motionEnabled）：
 * 关闭时只显示单行省略。WCAG 2.2.2 要求自动移动内容可暂停，
 * 因此触摸/悬停即暂停，松手 5 秒后恢复。
 */
import { computed, getCurrentInstance, onBeforeUnmount, onMounted, ref, watch } from 'vue'

import {
  isMarqueeNeeded,
  MARQUEE_GAP_PX,
  MARQUEE_MIN_COPIES,
  MARQUEE_SPEED_PX_PER_SEC,
  marqueeCopies,
  marqueeDuration,
  marqueeTrackWidth,
} from './marquee'

const props = withDefaults(defineProps<{
  /** 全站自动动效开关：关闭时退化为单行省略 */
  motionEnabled?: boolean
  /** 卡片排布变化（如 3 卡 → 2 卡）时重新测量 */
  layoutKey?: number | string
  /** 两份文本之间的留白（px） */
  gap?: number
  /** 滚动速度（px/s） */
  speed?: number
  /**
   * 恒定跑马灯：文案不超出容器也持续滚动，并按容器宽度重复文案铺满轨道。
   * 入口条默认关闭（短活动名静止），公告条开启（运营配了滚动速度就应有动效）。
   */
  ticker?: boolean
  text: string
}>(), {
  motionEnabled: true,
  layoutKey: '',
  gap: MARQUEE_GAP_PX,
  speed: MARQUEE_SPEED_PX_PER_SEC,
  ticker: false,
})

/** 首帧后再测，等卡片完成栅格布局；测不到时短暂重试 */
const MEASURE_DELAY_MS = 40
const MEASURE_RETRY_DELAY_MS = 120
const MEASURE_RETRY_LIMIT = 3
/** 触摸/悬停暂停后，留出读完一句的时间再自动恢复 */
const RESUME_DELAY_MS = 5000

const instance = getCurrentInstance()
const scrolling = ref(false)
const paused = ref(false)
const duration = ref(0)
const trackWidth = ref(0)
const copies = ref(MARQUEE_MIN_COPIES)

const trackStyle = computed(() => ({
  animationDuration: `${duration.value}s`,
  width: `${trackWidth.value}px`,
}))
const itemStyle = computed(() => ({ paddingRight: `${props.gap}px` }))

let measureTimer: ReturnType<typeof setTimeout> | undefined
let resumeTimer: ReturnType<typeof setTimeout> | undefined
let measureAttempt = 0
/** 文本/排布快速变化时只认最后一次测量结果，避免旧查询回填错误滚动态 */
let measureSequence = 0

interface MarqueeRect {
  width?: number
}

/** 一次查询同时拿容器与测量节点宽度（px） */
function measureRects(): Promise<{ content: number, viewport: number }> {
  return new Promise((resolve) => {
    const query = uni.createSelectorQuery().in(instance?.proxy ?? instance)
    query.select('.marquee').boundingClientRect()
    query.select('.marquee__measure').boundingClientRect()
    query.exec((result) => {
      const rects = Array.isArray(result) ? result : []
      const viewport = Number((rects[0] as MarqueeRect | null)?.width || 0)
      const content = Number((rects[1] as MarqueeRect | null)?.width || 0)
      resolve({ content, viewport })
    })
  })
}

/** 重置滚动态并重新排队测量：文本/开关/排布变化都走这里 */
function scheduleMeasure() {
  measureSequence += 1
  const sequence = measureSequence
  if (measureTimer)
    clearTimeout(measureTimer)
  if (resumeTimer)
    clearTimeout(resumeTimer)
  scrolling.value = false
  paused.value = false
  measureAttempt = 0
  if (!props.motionEnabled || !props.text.trim())
    return
  measureTimer = setTimeout(() => {
    void runMeasure(sequence)
  }, MEASURE_DELAY_MS)
}

async function runMeasure(sequence: number) {
  if (sequence !== measureSequence)
    return
  const { content, viewport } = await measureRects()
  if (sequence !== measureSequence)
    return
  if (!content || !viewport) {
    // 首次上屏 / 卡片高度未定时可能量到 0：短暂重试，仍失败就静态省略
    if (measureAttempt < MEASURE_RETRY_LIMIT) {
      measureAttempt += 1
      measureTimer = setTimeout(() => {
        void runMeasure(sequence)
      }, MEASURE_RETRY_DELAY_MS)
    }
    return
  }
  measureAttempt = 0
  if (!props.ticker && !isMarqueeNeeded(content, viewport))
    return
  const copyCount = props.ticker
    ? marqueeCopies(content, viewport, props.gap)
    : MARQUEE_MIN_COPIES
  copies.value = copyCount
  trackWidth.value = marqueeTrackWidth(content, props.gap, copyCount)
  duration.value = marqueeDuration(content, props.gap, props.speed, copyCount)
  scrolling.value = true
}

function pauseMarquee() {
  if (!scrolling.value)
    return
  if (resumeTimer)
    clearTimeout(resumeTimer)
  paused.value = true
}

function scheduleResume() {
  if (!scrolling.value)
    return
  if (resumeTimer)
    clearTimeout(resumeTimer)
  resumeTimer = setTimeout(() => {
    paused.value = false
  }, RESUME_DELAY_MS)
}

onMounted(scheduleMeasure)

watch(() => props.text, scheduleMeasure)
watch(() => props.motionEnabled, scheduleMeasure)
watch(() => props.layoutKey, scheduleMeasure)
watch(() => props.ticker, scheduleMeasure)

onBeforeUnmount(() => {
  if (measureTimer)
    clearTimeout(measureTimer)
  if (resumeTimer)
    clearTimeout(resumeTimer)
})
</script>

<template>
  <view
    class="marquee"
    :class="{ 'is-scrolling': scrolling }"
    @touchstart="pauseMarquee"
    @touchend="scheduleResume"
    @touchcancel="scheduleResume"
    @mouseenter="pauseMarquee"
    @mouseleave="scheduleResume"
  >
    <view
      v-if="scrolling"
      class="marquee__track"
      :class="{ 'is-paused': paused }"
      :style="trackStyle"
    >
      <text
        v-for="copy in copies" :key="copy" class="marquee__item" :style="itemStyle"
        :aria-hidden="copy > 1 ? 'true' : undefined"
      >
        {{ text }}
      </text>
    </view>
    <view v-else class="marquee__static">
      {{ text }}
    </view>
    <!-- 真实文本宽度的测量节点：绝对定位 + 隐藏，不参与卡片布局 -->
    <view v-if="motionEnabled" class="marquee__measure" aria-hidden="true">
      <text class="marquee__measure-text">
        {{ text }}
      </text>
    </view>
  </view>
</template>

<style scoped lang="scss">
.marquee {
  position: relative;
  width: 100%;
  min-width: 0;
  overflow: hidden;
}

.marquee__static {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 重复文本：轨道宽度由 JS 按「(文本宽 + 间距) × 份数」写入；
   位移 -50% 正好整数份，份数为偶数时接缝不跳字 */
.marquee__track {
  display: flex;
  flex-wrap: nowrap;
  align-items: center;
  animation-name: strip-marquee;
  animation-timing-function: linear;
  animation-iteration-count: infinite;
  animation-delay: 0.6s;
  will-change: transform;
}

.marquee__track.is-paused {
  animation-play-state: paused;
}

.marquee__item {
  display: block;
  flex: 0 0 auto;
  white-space: nowrap;
}

.marquee__measure {
  position: absolute;
  top: 0;
  left: 0;
  display: inline-block;
  visibility: hidden;
  white-space: nowrap;
  pointer-events: none;
}

.marquee__measure-text {
  display: inline;
  white-space: nowrap;
}

/* 滚动时两侧做 8px 淡出，避免文字在硬边缘突然出现/消失（卡片恒为白底） */
.marquee.is-scrolling::before,
.marquee.is-scrolling::after {
  position: absolute;
  top: 0;
  bottom: 0;
  z-index: 1;
  width: 8px;
  content: '';
  pointer-events: none;
}

.marquee.is-scrolling::before {
  left: 0;
  background: linear-gradient(to right, #fff, rgba(255, 255, 255, 0));
}

.marquee.is-scrolling::after {
  right: 0;
  background: linear-gradient(to left, #fff, rgba(255, 255, 255, 0));
}

@keyframes strip-marquee {
  from {
    transform: translateX(0);
  }

  to {
    transform: translateX(-50%);
  }
}
</style>

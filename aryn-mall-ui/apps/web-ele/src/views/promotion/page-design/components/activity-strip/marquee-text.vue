<script setup lang="ts">
/**
 * 活动入口条/公告条单行跑马灯（管理端预览）。
 *
 * 与 C 端 `diy-activity-strip/marquee-text.vue` 同口径：
 * · 只有文本真实宽度超过容器才滚动，短文案保持静止（ticker 模式除外）；
 * · 双份文本 + translateX(-50%) 无缝循环，轨道宽度显式计算；
 * · 速度按 px/s 折算，最长循环不会因文本更长而变快；
 * · 长文案不换行、不省略，触摸/悬停可暂停（WCAG 2.2.2）；
 * · 尊重系统「减少动态效果」偏好。
 *
 * `ticker` 模式对齐 C 端同名 prop（公告条消费）：运营配置了滚动速度即预期
 * 有动效，文案不超宽也持续滚动，并按容器宽度重复偶数份文案铺满轨道，
 * 避免「滚出去后整条空白」的观感。
 *
 * 管理端只有浏览器环境，直接用 getBoundingClientRect + ResizeObserver；
 * 画布换列/缩放时重新测量，避免文本在容器变窄后仍按旧宽度静止。
 */
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  ref,
  watch,
} from 'vue';

const props = withDefaults(
  defineProps<{
    /** 两份文本之间的留白（px） */
    gap?: number;
    /** 滚动速度（px/s） */
    speed?: number;
    text: string;
    /** 恒定跑马灯：文案不超宽也持续滚动（公告条这类配了速度的场景） */
    ticker?: boolean;
  }>(),
  {
    ticker: false,
    speed: 28,
    gap: 12,
  },
);
/** 与 C 端 marquee.ts 保持同值，改一端必须同步另一端 */
const GAP_PX = 12;
const SPEED_PX_PER_SEC = 28;
const MIN_DURATION_SEC = 2.4;
const OVERFLOW_EPSILON_PX = 1;

/** 轨道重复份数：溢出模式恒 2 份，ticker 模式按容器宽算偶数份 */
const copies = ref(2);

const rootRef = ref<HTMLElement | null>(null);
const measureRef = ref<HTMLElement | null>(null);
const scrolling = ref(false);
const duration = ref(0);
const trackWidth = ref(0);

const trackStyle = computed(() => ({
  animationDuration: `${duration.value}s`,
  width: `${trackWidth.value}px`,
}));
const itemStyle = computed(() => ({
  paddingRight: `${props.gap > 0 ? props.gap : GAP_PX}px`,
}));

const reduceMotion =
  typeof window !== 'undefined' &&
  typeof window.matchMedia === 'function' &&
  window.matchMedia('(prefers-reduced-motion: reduce)').matches;

let resizeObserver: ResizeObserver | undefined;
let resizeFrame = 0;
let measureFrame = 0;
let observedWidth = 0;

function measure() {
  const root = rootRef.value;
  const measureEl = measureRef.value;
  if (!root || !measureEl) {
    scrolling.value = false;
    return;
  }
  const viewport = root.getBoundingClientRect().width;
  const content = measureEl.getBoundingClientRect().width;
  if (!viewport || !content) {
    scrolling.value = false;
    return;
  }
  // ticker 模式（公告条）：不超宽也持续滚动，按容器宽重复偶数份文案铺满轨道，
  // 位移 -50% 正好整数份，接缝不跳字（与 C 端 marqueeCopies/marqueeDuration 同式）
  if (props.ticker) {
    const gap = props.gap > 0 ? props.gap : GAP_PX;
    const speed = props.speed > 0 ? props.speed : SPEED_PX_PER_SEC;
    const unit = content + gap;
    const needed = Math.ceil((viewport * 2) / unit);
    copies.value = Math.max(2, needed % 2 === 0 ? needed : needed + 1);
    trackWidth.value = unit * copies.value;
    duration.value = Math.max(
      MIN_DURATION_SEC,
      Math.round(((unit * copies.value) / 2 / speed) * 100) / 100,
    );
    scrolling.value = true;
    return;
  }
  if (content <= viewport + OVERFLOW_EPSILON_PX) {
    scrolling.value = false;
    return;
  }
  copies.value = 2;
  const distance = content + GAP_PX;
  trackWidth.value = distance * 2;
  duration.value = Math.max(
    MIN_DURATION_SEC,
    Math.round((distance / SPEED_PX_PER_SEC) * 100) / 100,
  );
  scrolling.value = true;
}

function scheduleMeasure() {
  scrolling.value = false;
  if (reduceMotion) return;
  nextTick(() => {
    cancelAnimationFrame(measureFrame);
    measureFrame = requestAnimationFrame(measure);
  });
}

onMounted(() => {
  scheduleMeasure();
  if (typeof ResizeObserver === 'undefined' || !rootRef.value) return;
  resizeObserver = new ResizeObserver((entries) => {
    const width = entries[0]?.contentRect.width ?? 0;
    if (Math.abs(width - observedWidth) < 0.5) return;
    observedWidth = width;
    cancelAnimationFrame(resizeFrame);
    resizeFrame = requestAnimationFrame(scheduleMeasure);
  });
  resizeObserver.observe(rootRef.value);
});

watch(
  () => [props.text, props.ticker, props.speed, props.gap],
  scheduleMeasure,
);

onBeforeUnmount(() => {
  resizeObserver?.disconnect();
  cancelAnimationFrame(resizeFrame);
  cancelAnimationFrame(measureFrame);
});
</script>

<template>
  <div
    ref="rootRef"
    class="strip-marquee"
    :class="{ 'is-scrolling': scrolling }"
  >
    <div v-if="scrolling" class="strip-marquee__track" :style="trackStyle">
      <span
        v-for="copy in copies"
        :key="copy"
        class="strip-marquee__item"
        :style="itemStyle"
        :aria-hidden="copy > 1 ? 'true' : undefined"
      >
        {{ text }}
      </span>
    </div>
    <span v-else class="strip-marquee__static">{{ text }}</span>
    <!-- 文本真实宽度测量节点：绝对定位 + 隐藏，不参与卡片布局 -->
    <span ref="measureRef" class="strip-marquee__measure" aria-hidden="true">
      {{ text }}
    </span>
  </div>
</template>

<style scoped lang="scss">
@keyframes strip-marquee {
  from {
    transform: translateX(0);
  }

  to {
    transform: translateX(-50%);
  }
}

.strip-marquee {
  position: relative;
  width: 100%;
  min-width: 0;
  overflow: hidden;
}

.strip-marquee__static {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 双份文本：轨道宽度按「(文本宽 + 间距) × 2」写入，位移 -50% 正好一份 */
.strip-marquee__track {
  display: flex;
  flex-wrap: nowrap;
  align-items: center;
  animation-name: strip-marquee;
  animation-timing-function: linear;
  animation-delay: 0.6s;
  animation-iteration-count: infinite;
  will-change: transform;
}

/* 桌面端悬停即暂停，方便阅读；移出后从当前进度继续 */
.strip-marquee:hover .strip-marquee__track {
  animation-play-state: paused;
}

.strip-marquee__item {
  display: block;
  flex: 0 0 auto;
  white-space: nowrap;
}

.strip-marquee__measure {
  position: absolute;
  top: 0;
  left: 0;
  display: inline-block;
  visibility: hidden;
  white-space: nowrap;
  pointer-events: none;
}

/* 滚动时两侧做 8px 淡出，避免文字在硬边缘突然出现/消失（卡片恒为白底） */
.strip-marquee.is-scrolling::before,
.strip-marquee.is-scrolling::after {
  position: absolute;
  top: 0;
  bottom: 0;
  z-index: 1;
  width: 8px;
  pointer-events: none;
  content: '';
}

.strip-marquee.is-scrolling::before {
  left: 0;
  background: linear-gradient(to right, #fff, rgb(255 255 255 / 0%));
}

.strip-marquee.is-scrolling::after {
  right: 0;
  background: linear-gradient(to left, #fff, rgb(255 255 255 / 0%));
}
</style>

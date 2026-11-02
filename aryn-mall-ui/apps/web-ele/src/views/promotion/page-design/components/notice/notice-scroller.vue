<script lang="ts" setup>
/**
 * wd-notice-bar 的画布仿真（多条横滚 / 竖向轮播 / 静态省略与换行）。
 *
 * C 端公告在「多条」时交给 wot-design-uni 的 wd-notice-bar 渲染：
 * · 横向逐条滚动——首条从原位向左滚出，其余条目从右缘外进入、向左滚出，
 *   循环往复，速度为 px/s；
 * · 竖向逐行上滚——轨道是「全部条目 + 末尾补首条」，按行高步进位移，
 *   到末尾后瞬时回卷实现无缝循环；
 * · 条目间停顿 1s（组件默认 delay），单条竖向不动画、按配置换行。
 *
 * 画布此前只静态显示首条，运营配置的滚动方式/速度在预览里完全不可见。
 * 这里按 wd-notice-bar 的状态机逐步移植（transitionend 驱动 + 20ms 后换条），
 * 测量走 getBoundingClientRect，与实机节奏一致。
 */
import type { CSSProperties } from 'vue';

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
    direction?: 'horizontal' | 'vertical';
    scrollable?: boolean;
    /** 滚动速度（px/s），与 wd-notice-bar speed 同口径 */
    speed?: number;
    texts: string[];
    wrapable?: boolean;
  }>(),
  {
    direction: 'horizontal',
    speed: 50,
    scrollable: true,
    wrapable: false,
  },
);

/** 条目间停顿（秒）：wd-notice-bar 的 delay 默认 1s，C 端未覆盖该值 */
const DELAY_SEC = 1;
/** 换条前的缓冲：与 wd-notice-bar animationEnd 的 setTimeout 同值 */
const NEXT_DELAY_MS = 20;
/** wd-notice-bar speed 默认 50（0/非法值时回落） */
const FALLBACK_SPEED = 50;

const wrapRef = ref<HTMLElement | null>(null);
const contentRef = ref<HTMLElement | null>(null);
const currentIndex = ref(0);
const verticalIndex = ref(0);
const transitionState = ref<CSSProperties>({
  transitionProperty: 'unset',
  transitionDelay: 'unset',
  transitionDuration: 'unset',
  transform: 'none',
});

const isHorizontal = computed(() => props.direction === 'horizontal');
const isVertical = computed(() => props.direction === 'vertical');
const isWrap = computed(() => props.wrapable && !props.scrollable);
const isEllipse = computed(() => !props.scrollable && !props.wrapable);
const currentText = computed(() => props.texts[currentIndex.value] ?? '');
/** 竖向轨道：全部条目 + 末尾补首条（滚到最后无缝接回第一条） */
const loopTexts = computed(() =>
  props.texts.length > 1 ? [...props.texts, props.texts[0] ?? ''] : props.texts,
);

const contentStyle = computed(() => transitionState.value);

let nextTimer: ReturnType<typeof setTimeout> | undefined;

function safeSpeed() {
  return props.speed > 0 ? props.speed : FALLBACK_SPEED;
}

function setTransition(duration: number, delay: number, translate: number) {
  transitionState.value = {
    transitionProperty: 'all',
    transitionDelay: `${delay}s`,
    transitionDuration: `${duration}s`,
    transform: `${isVertical.value ? 'translateY' : 'translateX'}(${translate}px)`,
  };
}

function measureWidths(): { content: number; wrap: number } {
  return {
    wrap: wrapRef.value?.getBoundingClientRect().width ?? 0,
    content: contentRef.value?.getBoundingClientRect().width ?? 0,
  };
}

function measureContentHeight(): number {
  return contentRef.value?.getBoundingClientRect().height ?? 0;
}

/** 竖向步进：位移一行行高，时长按 px/s 折算（与 wd-notice-bar 同式） */
function verticalAnimate(contentHeight: number) {
  const line = contentHeight / (props.texts.length + 1);
  setTransition(
    line / safeSpeed(),
    DELAY_SEC,
    -line * (currentIndex.value + 1),
  );
}

function advanceIndex() {
  currentIndex.value =
    currentIndex.value >= props.texts.length - 1 ? 0 : currentIndex.value + 1;
}

function scroll() {
  if (isHorizontal.value) {
    const { content } = measureWidths();
    if (!content) return;
    if (props.scrollable) {
      setTransition(content / safeSpeed(), DELAY_SEC, -content);
    }
  } else {
    const contentHeight = measureContentHeight();
    if (!contentHeight) return;
    if (props.texts.length > 1) {
      verticalAnimate(contentHeight);
    }
  }
}

function onTransitionEnd(event: TransitionEvent) {
  if (event.target !== contentRef.value || event.propertyName !== 'transform') {
    return;
  }
  if (isHorizontal.value) {
    // 瞬移到右缘外（duration 0 不触发 transitionend），换条后再滚入
    setTransition(
      0,
      0,
      (wrapRef.value?.getBoundingClientRect().width ?? 0) + 1,
    );
  } else {
    verticalIndex.value += 1;
    if (verticalIndex.value >= props.texts.length) {
      verticalIndex.value = 0;
      setTransition(0, 0, 0);
    }
  }
  nextTimer = setTimeout(() => {
    advanceIndex();
    if (isHorizontal.value) {
      const { wrap, content } = measureWidths();
      if (!wrap || !content) return;
      setTransition((wrap + content) / safeSpeed(), DELAY_SEC, -content);
    } else {
      const contentHeight = measureContentHeight();
      if (!contentHeight) return;
      verticalAnimate(contentHeight);
    }
  }, NEXT_DELAY_MS);
}

function stop() {
  if (nextTimer) clearTimeout(nextTimer);
  nextTimer = undefined;
  currentIndex.value = 0;
  verticalIndex.value = 0;
  transitionState.value = {
    transitionProperty: 'unset',
    transitionDelay: 'unset',
    transitionDuration: 'unset',
    transform: 'none',
  };
}

function start() {
  stop();
  nextTick(() => {
    scroll();
  });
}

onMounted(start);

watch(
  () => [
    props.texts,
    props.direction,
    props.speed,
    props.scrollable,
    props.wrapable,
  ],
  start,
);

onBeforeUnmount(stop);
</script>

<template>
  <div
    class="notice-scroller"
    :class="{
      'notice-scroller--ellipse': isEllipse,
      'notice-scroller--wrap': isWrap,
    }"
  >
    <div ref="wrapRef" class="notice-scroller__wrap">
      <div
        ref="contentRef"
        class="notice-scroller__content"
        :style="contentStyle"
        @transitionend="onTransitionEnd"
      >
        <template v-if="isVertical && !isWrap">
          <div
            v-for="(text, index) in loopTexts"
            :key="index"
            class="notice-scroller__line"
          >
            {{ text }}
          </div>
        </template>
        <template v-else>{{ currentText }}</template>
      </div>
    </div>
  </div>
</template>

<!--
  盒模型与 wd-notice-bar index.scss 逐值对齐：wrap 高度 = 行高 20px、
  overflow hidden 裁切；滚动时内容绝对定位收缩到文本真实宽度（否则量到
  的是容器宽，速度换算全错）；ellipse/wrap 两种静态态与库同类名同表现。
-->
<style scoped lang="scss">
.notice-scroller {
  display: flex;
  align-items: center;
  width: 100%;
  min-width: 0;
}

.notice-scroller__wrap {
  position: relative;
  flex: 1;
  min-width: 0;
  height: 20px;
  overflow: hidden;
  line-height: 20px;
}

.notice-scroller__content {
  position: absolute;
  white-space: nowrap;
}

.notice-scroller--ellipse .notice-scroller__content {
  position: static;
  overflow: hidden;
  text-overflow: ellipsis;
}

.notice-scroller--wrap .notice-scroller__wrap {
  height: auto;
}

.notice-scroller--wrap .notice-scroller__content {
  position: static;
  white-space: normal;
}

.notice-scroller__line {
  height: 20px;
  line-height: 20px;
}
</style>

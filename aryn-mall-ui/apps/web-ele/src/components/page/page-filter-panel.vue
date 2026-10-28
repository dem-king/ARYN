<script lang="ts" setup>
import type { FormInstance } from 'element-plus';

import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  onUpdated,
  ref,
} from 'vue';

import { Refresh, Search } from '@element-plus/icons-vue';
import { ElButton, ElForm, ElFormItem } from 'element-plus';

withDefaults(
  defineProps<{
    /** 表单数据对象，直接透传给 ElForm */
    model: Record<string, any>;
    /** 与 RightToolbar 的搜索按钮联动 */
    visible?: boolean;
  }>(),
  {
    visible: true,
  },
);

const emit = defineEmits<{
  reset: [];
  search: [];
}>();

/** 折叠态下保留展示的查询条件个数（须与样式中 nth-of-type(n + 4) 联动） */
const COLLAPSED_VISIBLE_COUNT = 3;

/** 过渡时长与缓动，须与样式中条件项的动画保持一致 */
const EXPAND_DURATION = 260;
const COLLAPSE_FADE_DURATION = 120;
const COLLAPSE_HEIGHT_DURATION = 200;
const TRANSITION_EASING = 'cubic-bezier(0.25, 0.8, 0.35, 1)';
/** 定时器兜底清理，避免 transitionend 丢失时残留内联高度 */
const TRANSITION_CLEANUP_DELAY = 40;

const formRef = ref<FormInstance>();
/** 默认折叠只展示前 N 个条件，避免查询项一多就把列表挤到首屏之外 */
const collapsed = ref(true);
/** 实际生效的折叠态：收起时先让多余条件淡出，动画结束后才切到它 */
const appliedCollapsed = ref(true);
/** 过渡阶段，驱动条件项的淡入/淡出 */
const phase = ref<'collapse' | 'expand' | null>(null);

let transitionToken = 0;
/** slot 内查询条件个数（不含操作按钮项） */
const filterCount = ref(0);
const hasMoreFilters = computed(
  () => filterCount.value > COLLAPSED_VISIBLE_COUNT,
);
/** 第一行塞不下按钮组时改为整块预留右侧空间，避免按钮压住查询项 */
const reserved = ref(false);

let resizeObserver: ResizeObserver | undefined;

const getPanelEl = () => formRef.value?.$el as HTMLElement | undefined;

/** 条件个数只能数 DOM：slot 由各页面自由编写，组件侧拿不到稳定的 vnode 清单 */
const syncLayout = () => {
  const el = getPanelEl();
  if (!el) {
    return;
  }
  // 减 1 排除操作按钮项（搜索/重置/展开收起所在的 ElFormItem）
  filterCount.value = Math.max(
    el.querySelectorAll(':scope > .el-form-item').length - 1,
    0,
  );

  // 面板被 v-show 隐藏时量不到尺寸，保留上一轮布局结果
  if (!el.clientWidth) {
    return;
  }

  const actions = el.querySelector<HTMLElement>(
    ':scope > .hx-filter-panel__actions',
  );
  if (!actions) {
    return;
  }
  const actionsWidth = Math.ceil(actions.getBoundingClientRect().width);
  // 第一行末尾的占位宽度必须等于按钮组实际宽度，否则按钮会压到查询项上
  el.style.setProperty('--hx-filter-actions-width', `${actionsWidth}px`);

  const firstRowItems = [
    ...el.querySelectorAll<HTMLElement>(
      ':scope > .el-form-item:not(.hx-filter-panel__actions)',
    ),
  ].slice(0, COLLAPSED_VISIBLE_COUNT);

  let filtersWidth = 0;
  for (const item of firstRowItems) {
    filtersWidth +=
      item.getBoundingClientRect().width +
      (Number.parseFloat(getComputedStyle(item).marginRight) || 0);
  }

  // 用 clientWidth 减基础左右内边距判断，预留态自身的内边距不参与计算，避免来回切换
  const availableWidth = el.clientWidth - 32;
  reserved.value = filtersWidth + actionsWidth + 8 > availableWidth;
};

onMounted(() => {
  syncLayout();
  // 图标尺寸与字体要在首帧之后才稳定，补两次测量避免占位宽度偏小
  requestAnimationFrame(syncLayout);
  void document.fonts?.ready.then(syncLayout);

  // 只在宽度变化时重算：高度动画期间每帧都会触发，没必要跟着量
  let observedWidth = 0;
  resizeObserver = new ResizeObserver(([entry]) => {
    const width = entry?.contentRect.width ?? 0;
    if (width === observedWidth) {
      return;
    }
    observedWidth = width;
    syncLayout();
  });
  const el = getPanelEl();
  if (el) {
    resizeObserver.observe(el);
  }
});
onUpdated(syncLayout);

const measureHeight = (el: HTMLElement) =>
  Math.round(el.getBoundingClientRect().height * 100) / 100;

const resetPanelStyle = (el: HTMLElement) => {
  el.style.height = '';
  el.style.overflow = '';
  el.style.transition = '';
};

const wait = (ms: number) =>
  new Promise<void>((resolve) => setTimeout(resolve, ms));

/** 新一次切换会让进行中的过渡在下一个检查点直接退出，不再写任何样式 */
const stopTransition = () => {
  transitionToken += 1;
};

const prefersReducedMotion = () =>
  window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;

/**
 * 折叠时先淡出多余条件再压缩高度，展开时先钉住旧高度、一边长高一边让条件浮现。
 * 原先直接切 display 会让面板高度瞬变，下方的列表跟着硬跳一下。
 */
const runTransition = async (token: number) => {
  const el = getPanelEl();
  if (!el) {
    appliedCollapsed.value = collapsed.value;
    return;
  }

  // 先量当前视觉高度（可能是被中断的过渡的中途值），再清掉旧过渡把高度钉住
  const startHeight = measureHeight(el);
  el.style.transition = '';
  el.style.height = `${startHeight}px`;
  el.style.overflow = 'hidden';

  if (!startHeight) {
    resetPanelStyle(el);
    appliedCollapsed.value = collapsed.value;
    return;
  }

  const collapsing = collapsed.value;

  if (collapsing) {
    phase.value = 'collapse';
    await wait(COLLAPSE_FADE_DURATION);
    if (token !== transitionToken) {
      return;
    }
  }

  appliedCollapsed.value = collapsing;
  await nextTick();
  if (token !== transitionToken) {
    return;
  }

  // 解除固定高度量出目标值后立刻回填；以下同步执行，浏览器不会画出中间态
  el.style.height = '';
  const endHeight = measureHeight(el);
  el.style.height = `${startHeight}px`;
  void el.offsetHeight;

  phase.value = collapsing ? null : 'expand';

  const duration = collapsing ? COLLAPSE_HEIGHT_DURATION : EXPAND_DURATION;
  el.style.transition = `height ${duration}ms ${TRANSITION_EASING}`;
  el.style.height = `${endHeight}px`;

  await wait(duration + TRANSITION_CLEANUP_DELAY);
  if (token !== transitionToken) {
    return;
  }

  phase.value = null;
  resetPanelStyle(el);
};

const toggleCollapsed = () => {
  collapsed.value = !collapsed.value;

  stopTransition();
  const el = getPanelEl();
  if (!el || !hasMoreFilters.value || prefersReducedMotion()) {
    phase.value = null;
    if (el) {
      resetPanelStyle(el);
    }
    appliedCollapsed.value = collapsed.value;
    return;
  }

  void runTransition(transitionToken);
};

onBeforeUnmount(() => {
  resizeObserver?.disconnect();
  stopTransition();
  const el = getPanelEl();
  if (el) {
    resetPanelStyle(el);
  }
});

/** 重置后立即触发查询，避免用户还要再点一次搜索 */
const handleReset = () => {
  formRef.value?.resetFields();
  emit('reset');
};
</script>

<template>
  <ElForm
    ref="formRef"
    v-show="visible"
    :inline="true"
    label-width="96px"
    :model="model"
    class="hx-filter-panel"
    :class="{
      'hx-filter-panel--collapsed': appliedCollapsed && hasMoreFilters,
      'hx-filter-panel--expanding': phase === 'expand',
      'hx-filter-panel--collapsing': phase === 'collapse',
      'hx-filter-panel--reserved': reserved,
    }"
    @submit.prevent
  >
    <slot></slot>
    <ElFormItem class="hx-filter-panel__actions">
      <ElButton
        v-if="hasMoreFilters"
        class="hx-filter-panel__toggle"
        link
        type="primary"
        @click="toggleCollapsed"
      >
        {{ collapsed ? '展开' : '收起' }}
        <svg
          class="hx-filter-panel__toggle-icon"
          :class="{ 'is-expanded': !collapsed }"
          viewBox="0 0 16 16"
          width="12"
          height="12"
          aria-hidden="true"
          focusable="false"
        >
          <path d="M4 6.5 8 10.5l4-4" stroke-width="1.6" />
        </svg>
      </ElButton>
      <ElButton type="primary" :icon="Search" @click="emit('search')">
        搜索
      </ElButton>
      <ElButton :icon="Refresh" @click="handleReset">重置</ElButton>
    </ElFormItem>
  </ElForm>
</template>

<style scoped lang="scss">
@keyframes hx-filter-item-in {
  from {
    opacity: 0;
    transform: translateY(-4px);
  }

  to {
    opacity: 1;
    transform: none;
  }
}

@media (width <= 1400px) {
  .hx-filter-panel :deep(.el-form-item) {
    margin-right: 12px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .hx-filter-panel--expanding
    > :deep(.el-form-item:not(.hx-filter-panel__actions)):nth-of-type(n + 4),
  .hx-filter-panel--collapsing
    > :deep(.el-form-item:not(.hx-filter-panel__actions)):nth-of-type(n + 4) {
    transition: none;
    animation: none;
  }

  .hx-filter-panel__toggle-icon {
    transition: none;
  }
}

.hx-filter-panel {
  /* 滚动发生在 .hx-layout-container-auto，固定搜索区不随列表滚走 */
  position: sticky;
  top: 0;
  z-index: 10;

  /* 过渡期间需要给面板写死像素高度，border-box 才能与 offsetHeight 对齐 */
  box-sizing: border-box;
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  padding: 16px 16px 0;
  margin-bottom: 14px;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;

  /* 玻璃拟态：表格内容从搜索区下方滚过时呈磨砂效果。
     不支持背景模糊的环境保持上面那行不透明底色，避免文字叠影 */
  @supports (backdrop-filter: blur(1px)) {
    background: color-mix(
      in srgb,
      var(--el-fill-color-lighter) 62%,
      transparent
    );
    box-shadow: var(--el-box-shadow-light);
    backdrop-filter: blur(10px) saturate(1.4);
  }

  /* 第一行末尾的按钮组占位符：排序位于前 3 个条件（order 0）之后、
     第 4 个及以后的条件（order 2）之前，因此只有第一行给右上角按钮让位，
     展开后其余条件行仍用满整行宽度 */
  &::after {
    flex: 0 0 calc(var(--hx-filter-actions-width, 232px) + 8px);
    order: 1;
    height: 0;
    content: '';
  }

  :deep(.el-form-item) {
    flex: none;
    margin-right: 18px;
    margin-bottom: 16px;
  }

  :deep(.el-form-item__label) {
    font-weight: 500;
    color: var(--el-text-color-regular);
    white-space: nowrap;
  }

  /* 统一常规控件宽度，日期区间单独放宽 */
  :deep(.el-form-item__content > .el-input),
  :deep(.el-form-item__content > .el-select) {
    width: 200px;
  }

  :deep(.el-form-item__content > .el-date-editor) {
    width: 360px;
  }
}

/* 窄屏兜底：占位符会被挤到下一行、按钮可能压住查询项时，改为整块预留右侧空间 */
.hx-filter-panel--reserved {
  padding-right: calc(var(--hx-filter-actions-width, 232px) + 24px);

  &::after {
    display: none;
  }
}

/* 折叠态：只保留前 3 个查询条件（nth-of-type 按元素序号计，操作按钮项
   位于所有条件之后，不影响前 3 个的序号，但仍需显式排除） */
.hx-filter-panel--collapsed {
  > :deep(.el-form-item:not(.hx-filter-panel__actions)):nth-of-type(n + 4) {
    display: none;
  }
}

/* 第 4 个及以后的条件排到按钮占位符之后，另起整行铺满 */
.hx-filter-panel
  > :deep(.el-form-item:not(.hx-filter-panel__actions)):nth-of-type(n + 4) {
  order: 2;
}

/* 展开：多余条件随高度增长淡入，避免整行瞬间出现 */
.hx-filter-panel--expanding {
  > :deep(.el-form-item:not(.hx-filter-panel__actions)):nth-of-type(n + 4) {
    animation: hx-filter-item-in 260ms cubic-bezier(0.25, 0.8, 0.35, 1) both;
  }
}

/* 收起：先淡出多余条件（120ms，与脚本中的 COLLAPSE_FADE_DURATION 一致），
   再由脚本压缩面板高度 */
.hx-filter-panel--collapsing {
  > :deep(.el-form-item:not(.hx-filter-panel__actions)):nth-of-type(n + 4) {
    opacity: 0;
    transform: translateY(-4px);
    transition:
      opacity 120ms ease-out,
      transform 120ms ease-out;
  }
}

.hx-filter-panel__actions {
  /* 面板自身是 sticky 定位元素，可作按钮组的绝对定位包含块 */
  position: absolute;
  top: 16px;
  right: 16px;
  margin: 0 !important;
}

/* 展开/收起：文字后跟人字箭头，展开时箭头旋转 180° 指向上方 */
.hx-filter-panel__toggle-icon {
  width: 12px;
  height: 12px;
  margin-left: 4px;
  overflow: visible;
  fill: none;
  stroke: currentcolor;
  stroke-linecap: round;
  stroke-linejoin: round;
  transition: transform 240ms cubic-bezier(0.25, 0.8, 0.35, 1);

  &.is-expanded {
    transform: rotate(180deg);
  }
}
</style>

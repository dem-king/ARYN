<script setup lang="ts">
import type { ComponentPublicInstance } from 'vue';

import type { DecorationSection } from '../schema/types';

import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  ref,
  watch,
} from 'vue';

import { CopyDocument, Delete } from '@element-plus/icons-vue';
import { ElButton, ElTooltip } from 'element-plus';

import { getComponentDefinition } from '../registry/component-registry';
import { buildSectionStyle } from '../schema/section-style';

const props = defineProps<{
  sections: DecorationSection[];
  selectedId?: string;
}>();

const emit = defineEmits<{
  duplicate: [id: string];
  move: [id: string, toIndex: number, sectionId: string];
  remove: [id: string];
  select: [id?: string];
  selectSection: [sectionId: string];
}>();

const draggedId = ref('');

/**
 * 没有组件的区块不渲染：删空组件后区块只剩一张空样式卡（背景/圆角/内边距），
 * 运营看到的是「组件删了还留着一块灰条占位」。与 C 端 diy 渲染器、
 * 预览画布同口径：空区块在哪儿都不显示。
 */
const visibleSections = computed(() =>
  props.sections.filter((section) => section.components.length > 0),
);

/**
 * 区块样式拆成两层渲染：
 *
 * - 外层 `.canvas-section` 只承接 margin 与吸顶定位，**永不裁切**；
 * - 内层 `.canvas-section__body` 承接背景/圆角/内边距/overflow:hidden。
 *
 * 圆角区块必须内联 overflow:hidden 裁掉子组件的直角背景（与 C 端 diy
 * 渲染器同构，见 section-style.ts），悬浮操作条若挂进这层，探出组件
 * 盒子的部分会被整块剪掉（历史上表现为「选中的组件没有删除按钮」）。
 * 拆层后操作条挂外层，裁切够不到它。样式数值口径仍由 buildSectionStyle
 * 统一出，与 C 端 preview-parity 守卫不冲突。
 */
const OUTER_STYLE_KEYS = new Set([
  'marginBottom',
  'marginLeft',
  'marginRight',
  'marginTop',
  'position',
  'top',
  'zIndex',
]);

function splitSectionStyle(section: DecorationSection) {
  const full = buildSectionStyle(section.style, { stickyZIndex: 4 });
  const outer: Record<string, number | string | undefined> = {};
  const body: Record<string, number | string | undefined> = {};
  for (const [key, value] of Object.entries(full)) {
    (OUTER_STYLE_KEYS.has(key) ? outer : body)[key] = value;
  }
  return { body, outer };
}

function sectionOuterStyle(section: DecorationSection) {
  return splitSectionStyle(section).outer;
}

function sectionBodyStyle(section: DecorationSection) {
  return splitSectionStyle(section).body;
}

const selectedPlacement = computed(() =>
  props.sections.find((section) =>
    section.components.some((component) => component.id === props.selectedId),
  ),
);

/**
 * 组件悬浮操作条（复制/删除）悬浮在选中组件盒子的**正上方**，
 * 不压住组件内容（船舶工作台这类 44px 细条组件曾被按钮盖掉半条）。
 * 位置量出来而不是写死：组件在区块内的偏移取决于前面兄弟组件的高度，
 * 且预览数据异步到位后高度还会变，所以要跟着布局重算。
 */
const componentEls = new Map<string, HTMLElement>();
const actionsStyle = ref<Record<string, number | string>>({});

/** 操作条约 32px 高 + 4px 间隙，整体悬在组件盒子上沿之上 */
const ACTIONS_FLOAT_OFFSET = 36;

function setComponentRef(
  componentId: string,
  el: ComponentPublicInstance | Element | null,
) {
  if (el instanceof HTMLElement) {
    componentEls.set(componentId, el);
  } else {
    componentEls.delete(componentId);
  }
}

const resizeObserver =
  typeof ResizeObserver === 'undefined'
    ? undefined
    : new ResizeObserver(() => measureActions());
let observedTargets: readonly Element[] = [];

function measureActions() {
  const el = props.selectedId ? componentEls.get(props.selectedId) : undefined;
  const sectionEl = el?.closest<HTMLElement>('.canvas-section');
  if (!el || !sectionEl) {
    actionsStyle.value = {};
    return;
  }

  // 画布被 zoom 缩放：getBoundingClientRect 是缩放后的视觉坐标，
  // 按区块布局宽/视觉宽折算回未缩放坐标——操作条也在缩放树内，
  // 用同一坐标系定位，缩放前后位置自洽。
  const sectionRect = sectionEl.getBoundingClientRect();
  const itemRect = el.getBoundingClientRect();
  const scale = sectionRect.width / sectionEl.offsetWidth || 1;
  actionsStyle.value = {
    top: `${(itemRect.top - sectionRect.top) / scale - ACTIONS_FLOAT_OFFSET}px`,
    right: `${(sectionRect.right - itemRect.right) / scale + 8}px`,
  };

  const targets = [el, sectionEl];
  if (
    observedTargets.length !== targets.length ||
    observedTargets.some((target, index) => target !== targets[index])
  ) {
    resizeObserver?.disconnect();
    for (const target of targets) {
      resizeObserver?.observe(target);
    }
    observedTargets = targets;
  }
}

function duplicateSelected() {
  if (props.selectedId) emit('duplicate', props.selectedId);
}

function removeSelected() {
  if (props.selectedId) emit('remove', props.selectedId);
}

watch([() => props.selectedId, () => props.sections], () => {
  nextTick(measureActions);
});

onMounted(measureActions);

onBeforeUnmount(() => {
  resizeObserver?.disconnect();
  componentEls.clear();
});
</script>

<template>
  <div
    v-for="section in visibleSections"
    :key="section.id"
    class="canvas-section"
    :style="sectionOuterStyle(section)"
  >
    <div
      class="canvas-section__body"
      :class="{ 'canvas-section-scroll': section.style.horizontalScroll }"
      :style="sectionBodyStyle(section)"
      @click.self="emit('selectSection', section.id)"
    >
      <div
        v-for="(component, index) in section.components"
        :key="component.id"
        :ref="(el) => setComponentRef(component.id, el)"
        class="canvas-component"
        :class="{
          active: selectedId === component.id,
          scroll: section.style.horizontalScroll,
        }"
        draggable="true"
        tabindex="0"
        @click.stop="emit('select', component.id)"
        @dragover.prevent
        @dragstart="draggedId = component.id"
        @drop.stop="emit('move', draggedId, index, section.id)"
        @keydown.enter="emit('select', component.id)"
      >
        <component
          :is="getComponentDefinition(component.type)?.preview"
          v-if="getComponentDefinition(component.type)?.preview"
          :show-data="component.props"
        />
        <div v-else class="unknown-component">
          未识别组件：{{ component.type }}
        </div>
      </div>
    </div>

    <!--
      操作条悬浮在选中组件盒子正上方，挂在未裁切的外层区块上：
      既不被圆角区块的 overflow:hidden 剪掉，也不盖住组件内容。
      top/right 由 measureActions() 按选中组件的实时位置内联写入。
    -->
    <div
      v-if="selectedPlacement?.id === section.id"
      class="component-actions"
      :style="actionsStyle"
    >
      <ElTooltip content="复制组件" placement="left">
        <ElButton
          :icon="CopyDocument"
          aria-label="复制组件"
          circle
          size="small"
          @click.stop="duplicateSelected"
        />
      </ElTooltip>
      <ElTooltip content="删除组件" placement="left">
        <ElButton
          :icon="Delete"
          aria-label="删除组件"
          circle
          size="small"
          type="danger"
          @click.stop="removeSelected"
        />
      </ElTooltip>
    </div>
  </div>
</template>

<style scoped>
.canvas-section {
  position: relative;
}

.canvas-section__body {
  background-repeat: no-repeat;
  background-position: top center;
  background-size: 100% auto;
}

.canvas-section-scroll {
  display: flex;
  gap: 8px;
  overflow-x: auto;
}

/**
 * 组件盒子跟随区块内容宽（`width: 100%`），不再钉死 375px。
 *
 * 区块带 margin/padding 时（如「套用卡片样式」的 12px 留白 + 内边距），
 * 固定 375px 会溢出内容盒并被区块的圆角裁切 `overflow: hidden` 剪掉右侧，
 * 画布比实机宽，运营按画布调的满宽到手机上就变窄。
 */
.canvas-component {
  position: relative;
  width: 100%;
  min-height: 24px;
  cursor: pointer;
}

/* 横滑区块的组件固定 290px，与 C 端 .diy-item-scroll 的 580rpx 对齐 */
.canvas-component.scroll {
  flex: 0 0 290px;
  width: 290px;
}

.canvas-component:hover::after,
.canvas-component.active::after {
  position: absolute;
  inset: 0;
  z-index: 5;
  pointer-events: none;
  content: '';
  border: 2px solid var(--el-color-primary);
}

/**
 * 组件操作条（复制/删除）悬浮在选中组件盒子的**正上方**，挂在未裁切的
 * 外层 `.canvas-section` 上，top/right 由 measureActions() 内联写入：
 *
 * - 最早定位在盒子右侧外 44px（`right: -44px`）：区块一有圆角就内联
 *   overflow:hidden，溢出的按钮被整块剪掉，运营看到「没有删除按钮」；
 * - 后来改挂盒子内部右上角（top/right 4px）：不再被裁，但压住组件内容，
 *   船舶工作台这类 44px 细条组件几乎被按钮盖掉半条。
 *
 * 现在区块拆成「外层定位 + 内层裁切」两层，操作条挂外层、悬在盒子上方，
 * 两个历史问题都不存在。横滑区块里组件随行滚动，操作条不跟滚（选中后
 * 操作即走，极少边滚边选），定位仍以选中瞬间的位置为准。
 */
.component-actions {
  position: absolute;
  z-index: 8;
  display: flex;
  gap: 6px;
  padding: 4px;
  background: var(--el-bg-color);
  border-radius: 999px;
  box-shadow: 0 4px 12px rgb(15 23 42 / 18%);
}

.unknown-component {
  padding: 16px;
  color: var(--el-text-color-secondary);
  text-align: center;
  background: var(--el-fill-color-light);
  border: 1px dashed var(--el-border-color);
}
</style>

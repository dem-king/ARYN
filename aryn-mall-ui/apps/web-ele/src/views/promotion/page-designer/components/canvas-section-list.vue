<script setup lang="ts">
import type { DecorationSection } from '../schema/types';

import { computed, ref } from 'vue';

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

function sectionStyle(section: DecorationSection) {
  // 吸顶层级 4：编辑器里要给组件悬浮操作按钮（z-index 5/8）让位
  return buildSectionStyle(section.style, { stickyZIndex: 4 });
}
</script>

<template>
  <div
    v-for="section in visibleSections"
    :key="section.id"
    class="canvas-section"
    :class="{ 'canvas-section-scroll': section.style.horizontalScroll }"
    :style="sectionStyle(section)"
    @click.self="emit('selectSection', section.id)"
  >
    <div
      v-for="(component, index) in section.components"
      :key="component.id"
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
      <div v-if="selectedId === component.id" class="component-actions">
        <ElTooltip content="复制组件" placement="left">
          <ElButton
            :icon="CopyDocument"
            aria-label="复制组件"
            circle
            size="small"
            @click.stop="emit('duplicate', component.id)"
          />
        </ElTooltip>
        <ElTooltip content="删除组件" placement="left">
          <ElButton
            :icon="Delete"
            aria-label="删除组件"
            circle
            size="small"
            type="danger"
            @click.stop="emit('remove', component.id)"
          />
        </ElTooltip>
      </div>
    </div>
  </div>
</template>

<style scoped>
.canvas-section {
  position: relative;
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
 * 组件操作条挂在组件盒子**内部**的右上角。
 *
 * 原先定位在盒子右侧外 44px（`right: -44px`）：区块一有圆角就会内联
 * `overflow: hidden`（裁掉子组件的直角背景），溢出的按钮被整块剪掉，
 * 运营看到的是「选中的组件没有删除按钮」。挂到盒子内就与裁切无关了。
 */
.component-actions {
  position: absolute;
  top: 4px;
  right: 4px;
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

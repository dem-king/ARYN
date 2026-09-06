<script setup lang="ts">
import type { DecorationSection, PageSettings } from '../schema/types';

import { computed, ref } from 'vue';

import { CopyDocument, Delete } from '@element-plus/icons-vue';
import { ElButton, ElEmpty, ElTooltip } from 'element-plus';

import { getComponentDefinition } from '../registry/component-registry';

const props = defineProps<{
  page: PageSettings;
  pageName: string;
  sections: DecorationSection[];
  selectedId?: string;
  zoom: number;
}>();

const emit = defineEmits<{
  duplicate: [id: string];
  move: [id: string, toIndex: number, sectionId: string];
  remove: [id: string];
  select: [id?: string];
  selectSection: [sectionId: string];
}>();

const draggedId = ref('');
const canvasStyle = computed(() => ({
  backgroundColor: props.page.backgroundColor,
  backgroundImage: props.page.backgroundImage
    ? `url(${props.page.backgroundImage})`
    : undefined,
  transform: `scale(${props.zoom})`,
}));

function sectionStyle(section: DecorationSection) {
  const style: Record<string, number | string | undefined> = {
    backgroundColor: section.style.backgroundColor || undefined,
    backgroundImage: section.style.backgroundImage
      ? `url(${section.style.backgroundImage})`
      : undefined,
    paddingBottom: `${section.style.paddingY}px`,
    paddingTop: `${section.style.paddingY}px`,
  };
  if (section.style.sticky) {
    style.position = 'sticky';
    style.top = '0';
    style.zIndex = '4';
  }
  return style;
}
</script>

<template>
  <div class="canvas-stage" @click.self="emit('select', undefined)">
    <div class="phone-shell" :style="canvasStyle">
      <div
        v-if="page.navigation.visible"
        class="phone-navigation"
        :style="{
          backgroundColor: page.navigation.backgroundColor,
          color: page.navigation.textColor,
        }"
      >
        {{ page.navigation.title || pageName }}
      </div>
      <ElEmpty
        v-if="sections.every((section) => section.components.length === 0)"
        :image-size="72"
        description="从左侧添加组件"
      />
      <div
        v-for="section in sections"
        v-else
        :key="section.id"
        class="canvas-section"
        :class="[
          {
            'canvas-section-scroll': section.style.horizontalScroll,
          },
        ]"
        :style="sectionStyle(section)"
        @click.self="emit('selectSection', section.id)"
      >
        <div
          v-for="(component, index) in section.components"
          :key="component.id"
          class="canvas-component"
          :class="[
            {
              active: selectedId === component.id,
              scroll: section.style.horizontalScroll,
            },
          ]"
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
            <ElTooltip content="复制组件" placement="right">
              <ElButton
                :icon="CopyDocument"
                aria-label="复制组件"
                circle
                size="small"
                @click.stop="emit('duplicate', component.id)"
              />
            </ElTooltip>
            <ElTooltip content="删除组件" placement="right">
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
    </div>
  </div>
</template>

<style scoped>
.canvas-stage {
  display: flex;
  justify-content: center;
  min-width: 520px;
  min-height: 100%;
  padding: 32px 72px 100px;
  overflow: auto;
  background: var(--el-fill-color-light);
}

.phone-shell {
  width: 375px;
  min-height: 667px;
  overflow: visible;
  background: #fff;
  background-repeat: no-repeat;
  background-position: top center;
  background-size: 100% auto;
  border: 1px solid var(--el-border-color);
  box-shadow: 0 12px 32px rgb(15 23 42 / 12%);
  transform-origin: top center;
}

.phone-navigation {
  display: flex;
  align-items: end;
  justify-content: center;
  height: 84px;
  padding: 0 44px 10px;
  font-size: 16px;
  font-weight: 600;
  text-align: center;
}

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

.canvas-component {
  position: relative;
  width: 375px;
  min-height: 24px;
  cursor: pointer;
}

.canvas-component.scroll {
  flex: 0 0 300px;
  width: 300px;
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

.component-actions {
  position: absolute;
  top: 0;
  right: -44px;
  z-index: 8;
  display: grid;
  gap: 6px;
}

.unknown-component {
  padding: 16px;
  color: var(--el-text-color-secondary);
  text-align: center;
  background: var(--el-fill-color-light);
  border: 1px dashed var(--el-border-color);
}
</style>

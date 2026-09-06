<script setup lang="ts">
import type { DecorationSection } from '../schema/types';

import { ref } from 'vue';

import { Bottom, DCaret, Delete, Plus, Top } from '@element-plus/icons-vue';
import {
  ElButton,
  ElCheckbox,
  ElEmpty,
  ElIcon,
  ElOption,
  ElSelect,
  ElTooltip,
} from 'element-plus';

defineProps<{
  activeSectionId?: string;
  sections: DecorationSection[];
  selectedId?: string;
  selectedIds: string[];
}>();

const emit = defineEmits<{
  batchDuplicate: [];
  batchRemove: [];
  move: [id: string, toIndex: number, toSectionId: string];
  moveSection: [sectionId: string, toIndex: number];
  remove: [id: string];
  removeSection: [sectionId: string];
  select: [id: string];
  selectSection: [sectionId: string];
  toggleSelect: [id: string, checked: boolean];
}>();

const draggedId = ref('');

function sectionName(section: DecorationSection, index: number) {
  return section.name || `区块 ${index + 1}`;
}
</script>

<template>
  <div class="page-outline">
    <div class="outline-actions">
      <ElButton
        :disabled="selectedIds.length === 0"
        size="small"
        @click="emit('batchDuplicate')"
      >
        复制所选
      </ElButton>
      <ElButton
        :disabled="selectedIds.length === 0"
        size="small"
        type="danger"
        @click="emit('batchRemove')"
      >
        删除所选
      </ElButton>
    </div>
    <ElEmpty
      v-if="sections.every((section) => section.components.length === 0)"
      :image-size="56"
      description="暂无组件"
    />
    <div
      v-for="(section, sectionIndex) in sections"
      v-else
      :key="section.id"
      class="outline-section"
      :class="[{ active: activeSectionId === section.id }]"
    >
      <div
        class="outline-section-header"
        role="button"
        tabindex="0"
        @click.stop="emit('selectSection', section.id)"
      >
        <span class="section-label">{{
          sectionName(section, sectionIndex)
        }}</span>
        <span class="section-count">{{ section.components.length }}</span>
        <ElTooltip content="上移区块">
          <ElButton
            :disabled="sectionIndex === 0"
            :icon="Top"
            aria-label="上移区块"
            circle
            size="small"
            text
            @click.stop="emit('moveSection', section.id, sectionIndex - 1)"
          />
        </ElTooltip>
        <ElTooltip content="下移区块">
          <ElButton
            :disabled="sectionIndex === sections.length - 1"
            :icon="Bottom"
            aria-label="下移区块"
            circle
            size="small"
            text
            @click.stop="emit('moveSection', section.id, sectionIndex + 1)"
          />
        </ElTooltip>
        <ElTooltip
          v-if="sections.length > 1"
          content="删除区块（含区块内组件）"
        >
          <ElButton
            :icon="Delete"
            aria-label="删除区块"
            circle
            size="small"
            text
            type="danger"
            @click.stop="emit('removeSection', section.id)"
          />
        </ElTooltip>
      </div>
      <div
        v-for="(component, index) in section.components"
        :key="component.id"
        class="outline-item"
        :class="[{ active: selectedId === component.id }]"
        draggable="true"
        tabindex="0"
        @click.stop="emit('select', component.id)"
        @dragover.prevent
        @dragstart="draggedId = component.id"
        @drop.stop="emit('move', draggedId, index, section.id)"
        @keydown.enter="emit('select', component.id)"
      >
        <ElCheckbox
          :model-value="selectedIds.includes(component.id)"
          class="outline-check"
          @click.stop
          @change="emit('toggleSelect', component.id, $event as boolean)"
        />
        <ElIcon class="drag-handle"><DCaret /></ElIcon>
        <span class="outline-label">{{ component.type }}</span>
        <ElSelect
          class="outline-move"
          :model-value="section.id"
          size="small"
          title="移动到区块"
          @click.stop
          @change="
            emit(
              'move',
              component.id,
              section.components.length - 1,
              $event as string,
            )
          "
        >
          <ElOption
            v-for="target in sections"
            :key="target.id"
            :label="sectionName(target, sections.indexOf(target))"
            :value="target.id"
          />
        </ElSelect>
        <ElTooltip content="删除组件">
          <ElButton
            :icon="Delete"
            aria-label="删除组件"
            circle
            text
            type="danger"
            @click.stop="emit('remove', component.id)"
          />
        </ElTooltip>
      </div>
      <div v-if="section.components.length === 0" class="outline-empty-section">
        <ElIcon><Plus /></ElIcon>
        空区块，从组件库添加
      </div>
    </div>
  </div>
</template>

<style scoped>
.page-outline {
  padding: 8px;
}

.outline-actions {
  display: flex;
  gap: 8px;
  padding: 4px 4px 10px;
}

.outline-section {
  margin-bottom: 8px;
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}

.outline-section.active {
  border-color: var(--el-color-primary-light-5);
}

.outline-section-header {
  display: flex;
  gap: 4px;
  align-items: center;
  padding: 6px 8px;
  cursor: pointer;
  background: var(--el-fill-color-light);
}

.outline-section-header:hover {
  background: var(--el-color-primary-light-9);
}

.section-label {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  font-weight: 600;
  white-space: nowrap;
}

.section-count {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.outline-item {
  display: grid;
  grid-template-columns: 24px 24px minmax(0, 1fr) 96px 32px;
  gap: 4px;
  align-items: center;
  min-height: 40px;
  padding: 4px 6px;
  cursor: pointer;
  border-top: 1px solid var(--el-border-color-lighter);
  border-left: 3px solid transparent;
}

.outline-item:hover,
.outline-item:focus-visible,
.outline-item.active {
  outline: 0;
  background: var(--el-color-primary-light-9);
  border-left-color: var(--el-color-primary);
}

.outline-check {
  height: auto;
}

.outline-move {
  width: 92px;
}

.drag-handle {
  color: var(--el-text-color-placeholder);
  cursor: grab;
}

.outline-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.outline-empty-section {
  padding: 12px;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  text-align: center;
}
</style>

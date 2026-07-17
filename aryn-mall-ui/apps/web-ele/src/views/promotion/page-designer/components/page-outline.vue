<script setup lang="ts">
import type { DecorationComponent } from '../schema/types';

import { ref } from 'vue';

import { DCaret, Delete } from '@element-plus/icons-vue';
import { ElButton, ElEmpty, ElIcon, ElTooltip } from 'element-plus';

defineProps<{
  components: DecorationComponent[];
  selectedId?: string;
}>();

const emit = defineEmits<{
  move: [id: string, toIndex: number];
  remove: [id: string];
  select: [id: string];
}>();

const draggedId = ref('');
</script>

<template>
  <div class="page-outline">
    <ElEmpty
      v-if="components.length === 0"
      :image-size="56"
      description="暂无组件"
    />
    <div
      v-for="(component, index) in components"
      v-else
      :key="component.id"
      class="outline-item"
      :class="[{ active: selectedId === component.id }]"
      draggable="true"
      tabindex="0"
      @click="emit('select', component.id)"
      @dragover.prevent
      @dragstart="draggedId = component.id"
      @drop="emit('move', draggedId, index)"
      @keydown.enter="emit('select', component.id)"
    >
      <ElIcon class="drag-handle"><DCaret /></ElIcon>
      <span class="outline-label">{{ component.type }}</span>
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
  </div>
</template>

<style scoped>
.page-outline {
  padding: 8px;
}

.outline-item {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr) 32px;
  gap: 6px;
  align-items: center;
  min-height: 42px;
  padding: 4px 6px;
  cursor: pointer;
  border: 1px solid transparent;
  border-radius: 4px;
}

.outline-item:hover,
.outline-item:focus-visible,
.outline-item.active {
  outline: 0;
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-7);
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
</style>

<script setup lang="ts" generic="Id extends string">
import { ref } from 'vue';

import { ArrowDown, ArrowUp, Rank } from '@element-plus/icons-vue';
import { ElButton, ElIcon } from 'element-plus';

const props = defineProps<{ labelFor: (id: Id) => string; order: Id[] }>();
const emit = defineEmits<{ reorder: [order: Id[]] }>();

const dragged = ref<number>();
const target = ref<number>();

function move(from: number, to: number) {
  if (from === to || to < 0 || to >= props.order.length) return;
  const order = [...props.order];
  const item = order[from];
  if (item === undefined) return;
  order.splice(from, 1);
  order.splice(to, 0, item);
  emit('reorder', order);
}

function start(event: DragEvent, index: number) {
  dragged.value = index;
  event.dataTransfer?.setData('text/plain', String(index));
  if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move';
}

function clear() {
  dragged.value = undefined;
  target.value = undefined;
}

function drop(index: number) {
  if (dragged.value !== undefined) move(dragged.value, index);
  clear();
}
</script>

<template>
  <ul class="module-order-list">
    <li
      v-for="(id, index) in order"
      :key="id"
      class="module-order-row"
      :class="{
        'module-order-row--target': target === index && dragged !== index,
      }"
      @dragover.prevent="target = dragged === undefined ? undefined : index"
      @drop.prevent="drop(index)"
    >
      <span
        class="module-order-handle"
        draggable="true"
        aria-hidden="true"
        @dragstart="start($event, index)"
        @dragend="clear"
      >
        <ElIcon><Rank /></ElIcon>
      </span>
      <span class="module-order-label">{{ labelFor(id) }}</span>
      <slot :id="id"></slot>
      <span class="module-order-actions">
        <ElButton
          text
          size="small"
          :icon="ArrowUp"
          :disabled="index === 0"
          :aria-label="`上移${labelFor(id)}`"
          :title="`上移${labelFor(id)}`"
          @click="move(index, index - 1)"
        />
        <ElButton
          text
          size="small"
          :icon="ArrowDown"
          :disabled="index === order.length - 1"
          :aria-label="`下移${labelFor(id)}`"
          :title="`下移${labelFor(id)}`"
          @click="move(index, index + 1)"
        />
      </span>
    </li>
  </ul>
</template>

<style scoped>
.module-order-list {
  display: grid;
  gap: 6px;
  padding: 0;
  margin: 0;
  list-style: none;
}

.module-order-row {
  display: flex;
  gap: 4px;
  align-items: center;
  min-height: 36px;
  padding: 4px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
}

.module-order-row--target {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary);
}

.module-order-handle {
  display: grid;
  place-items: center;
  min-width: 23px;
  height: 30px;
  color: var(--el-text-color-secondary);
  cursor: grab;
}

.module-order-label {
  flex: 1;
  min-width: 0;
  font-size: 12px;
}

.module-order-actions {
  display: flex;
}

.module-order-actions :deep(.el-button) {
  width: 24px;
  height: 30px;
  padding: 0;
  margin: 0;
}
</style>

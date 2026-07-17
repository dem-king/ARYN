<script setup lang="ts">
import type {
  DecorationComponent,
  PaletteComponent,
} from '../use-home-decoration';

import { computed } from 'vue';

import { CircleClose, DCaret } from '@element-plus/icons-vue';
import { ElCollapse, ElCollapseItem, ElIcon } from 'element-plus';
import draggable from 'vuedraggable';

const props = defineProps<{
  componentBase: PaletteComponent[];
  componentHome: PaletteComponent[];
  componentTool: PaletteComponent[];
  dragOptions: Record<string, unknown>;
  selectedId: null | string;
}>();

const emit = defineEmits<{
  add: [item: PaletteComponent];
  delete: [index: number];
  dragStart: [event: DragEvent, item: PaletteComponent];
  drop: [event: DragEvent];
  select: [component: DecorationComponent];
}>();

const active = defineModel<string[]>('active', { required: true });
const components = defineModel<DecorationComponent[]>('components', {
  required: true,
});

const sections = computed(() => [
  { items: props.componentHome, name: '3', title: '首页组件' },
  { items: props.componentBase, name: '1', title: '基础组件' },
  { items: props.componentTool, name: '2', title: '工具组件' },
]);
</script>

<template>
  <div class="component-panel">
    <div class="left-panel">
      <ElCollapse v-model="active">
        <ElCollapseItem
          v-for="section in sections"
          :key="section.name"
          :name="section.name"
          :title="section.title"
        >
          <div class="left-group">
            <button
              v-for="component in section.items"
              :key="component.type"
              class="group-item"
              draggable="true"
              type="button"
              @click="emit('add', component)"
              @dragstart="emit('dragStart', $event, component)"
            >
              <img :src="component.icon" class="icon" alt="" />
              {{ component.name }}
            </button>
          </div>
        </ElCollapseItem>
      </ElCollapse>
    </div>

    <div class="selected-component">
      <div class="title">已选组件 ({{ components.length }})</div>
      <draggable
        v-model="components"
        v-bind="dragOptions"
        animation="700"
        item-key="id"
        @dragover.prevent
        @drop="emit('drop', $event)"
      >
        <template #item="{ element, index }">
          <div
            class="selected-item"
            :class="{ 'selected-active': selectedId === element.id }"
            @click.stop="emit('select', element)"
          >
            <div class="selected-left">
              <ElIcon><DCaret /></ElIcon>
              <div class="selected-title">{{ element.title }}</div>
            </div>
            <button
              class="selected-del"
              type="button"
              @click.stop="emit('delete', index)"
            >
              <ElIcon><CircleClose /></ElIcon>
            </button>
          </div>
        </template>
      </draggable>
    </div>
  </div>
</template>

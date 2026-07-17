<script setup lang="ts">
import type { DecorationComponent } from '../use-home-decoration';

import { Bottom, Delete, Top } from '@element-plus/icons-vue';
import { ElIcon, ElLink } from 'element-plus';
import draggable from 'vuedraggable';

import { componentsMap } from '../../page-design/componentsMap';

defineProps<{
  dragOptions: Record<string, unknown>;
  selectedId: null | string;
}>();

const emit = defineEmits<{
  delete: [index: number];
  down: [index: number];
  drop: [event: DragEvent];
  select: [component: DecorationComponent];
  up: [index: number];
}>();

const components = defineModel<DecorationComponent[]>('components', {
  required: true,
});
</script>

<template>
  <div class="middle">
    <div class="middle-content">
      <div class="content-warp">
        <div class="top-mobile-nav">
          <img
            class="top-mobile-image"
            src="/static/mobile-top-nav.svg"
            alt=""
          />
          <div class="top-mobile-title">首页装修</div>
        </div>
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
              class="mobile-item"
              :class="{ active: selectedId === element.id }"
              @click.stop="emit('select', element)"
            >
              <component
                :is="componentsMap[element.type]"
                :key="element.id"
                :show-data="element.formData"
              />
              <div class="preview-title">{{ element.title }}</div>
              <div v-if="selectedId === element.id" class="preview-btn-group">
                <ElLink class="btn-item" @click.stop="emit('delete', index)">
                  <ElIcon><Delete /></ElIcon>
                </ElLink>
                <ElLink
                  class="btn-item"
                  :disabled="index <= 0"
                  @click.stop="emit('up', index)"
                >
                  <ElIcon><Top /></ElIcon>
                </ElLink>
                <ElLink
                  class="btn-item"
                  :disabled="index === components.length - 1"
                  @click.stop="emit('down', index)"
                >
                  <ElIcon><Bottom /></ElIcon>
                </ElLink>
              </div>
            </div>
          </template>
        </draggable>
      </div>
    </div>
  </div>
</template>

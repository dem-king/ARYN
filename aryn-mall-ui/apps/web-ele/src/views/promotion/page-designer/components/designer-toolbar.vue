<script setup lang="ts">
import type { DraftSaveStatus } from '../composables/use-draft-save';

import { computed } from 'vue';

import {
  ArrowLeft,
  Check,
  Collection,
  Discount,
  Minus,
  Picture,
  Plus,
  RefreshLeft,
  RefreshRight,
  Upload,
  View,
} from '@element-plus/icons-vue';
import { ElButton, ElInput, ElSpace, ElTag, ElTooltip } from 'element-plus';

const props = defineProps<{
  canRedo: boolean;
  canUndo: boolean;
  pageName: string;
  saveStatus: DraftSaveStatus;
  zoom: number;
}>();

const emit = defineEmits<{
  assets: [];
  back: [];
  preview: [];
  publish: [];
  redo: [];
  save: [];
  template: [];
  theme: [];
  undo: [];
  'update:pageName': [value: string];
  'update:zoom': [value: number];
}>();

const statusLabel = computed(() => {
  const labels: Record<DraftSaveStatus, string> = {
    conflict: '版本冲突',
    dirty: '未保存',
    error: '保存失败',
    idle: '就绪',
    saved: '已保存',
    saving: '保存中',
  };
  return labels[props.saveStatus];
});
</script>

<template>
  <header class="designer-toolbar">
    <div class="toolbar-group toolbar-leading">
      <ElTooltip content="返回页面管理">
        <ElButton
          :icon="ArrowLeft"
          aria-label="返回"
          circle
          text
          @click="emit('back')"
        />
      </ElTooltip>
      <ElInput
        :model-value="pageName"
        aria-label="页面名称"
        class="page-name"
        maxlength="40"
        @update:model-value="emit('update:pageName', $event)"
      />
      <ElTag
        :type="
          saveStatus === 'error' || saveStatus === 'conflict'
            ? 'danger'
            : 'info'
        "
        effect="plain"
        size="small"
      >
        {{ statusLabel }}
      </ElTag>
    </div>

    <ElSpace :size="4" class="toolbar-group">
      <ElTooltip content="撤销">
        <ElButton
          :disabled="!canUndo"
          :icon="RefreshLeft"
          aria-label="撤销"
          circle
          text
          @click="emit('undo')"
        />
      </ElTooltip>
      <ElTooltip content="重做">
        <ElButton
          :disabled="!canRedo"
          :icon="RefreshRight"
          aria-label="重做"
          circle
          text
          @click="emit('redo')"
        />
      </ElTooltip>
      <span class="toolbar-divider"></span>
      <ElTooltip content="缩小画布">
        <ElButton
          :disabled="zoom <= 0.75"
          :icon="Minus"
          aria-label="缩小画布"
          circle
          text
          @click="emit('update:zoom', Math.max(0.75, zoom - 0.05))"
        />
      </ElTooltip>
      <span class="zoom-value">{{ Math.round(zoom * 100) }}%</span>
      <ElTooltip content="放大画布">
        <ElButton
          :disabled="zoom >= 1.15"
          :icon="Plus"
          aria-label="放大画布"
          circle
          text
          @click="emit('update:zoom', Math.min(1.15, zoom + 0.05))"
        />
      </ElTooltip>
    </ElSpace>

    <ElSpace :size="8" class="toolbar-group toolbar-actions">
      <ElButton :icon="Collection" @click="emit('template')">模板</ElButton>
      <ElButton :icon="Discount" @click="emit('theme')">主题</ElButton>
      <ElButton :icon="Picture" @click="emit('assets')">素材</ElButton>
      <ElButton :icon="View" @click="emit('preview')">预览</ElButton>
      <ElButton :icon="Check" @click="emit('save')">保存草稿</ElButton>
      <ElButton :icon="Upload" type="primary" @click="emit('publish')">
        发布
      </ElButton>
    </ElSpace>
  </header>
</template>

<style scoped>
.designer-toolbar {
  display: grid;
  grid-template-columns: minmax(300px, 1fr) auto minmax(300px, 1fr);
  gap: 16px;
  align-items: center;
  height: 56px;
  padding: 0 16px;
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-light);
}

.toolbar-group {
  display: flex;
  align-items: center;
  min-width: 0;
}

.toolbar-leading {
  gap: 8px;
}

.toolbar-actions {
  justify-self: end;
}

.page-name {
  width: min(260px, 40vw);
}

.toolbar-divider {
  width: 1px;
  height: 20px;
  margin: 0 4px;
  background: var(--el-border-color);
}

.zoom-value {
  width: 42px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  text-align: center;
}
</style>

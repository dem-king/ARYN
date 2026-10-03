<script setup lang="ts">
import type { DraftSaveStatus } from '../composables/use-draft-save';

import { computed } from 'vue';

import {
  ArrowLeft,
  Check,
  Collection,
  Discount,
  Grid,
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
  /** 当前页型是否为内嵌页型（商详/分类/个人中心）——决定骨架开关是否可用 */
  shellAvailable: boolean;
  /** 是否展示原生页面骨架（仅内嵌页型可切换） */
  showPageShell: boolean;
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
  'update:showPageShell': [value: boolean];
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
      <ElTooltip
        v-if="shellAvailable"
        :content="
          showPageShell
            ? '隐藏原生页面骨架，只看装修块'
            : '显示原生页面骨架，确认装修块在页面中的位置与宽度'
        "
      >
        <ElButton
          :icon="Grid"
          :type="showPageShell ? 'primary' : 'default'"
          plain
          @click="emit('update:showPageShell', !showPageShell)"
        >
          页面结构
        </ElButton>
      </ElTooltip>
      <ElButton :icon="Collection" @click="emit('template')">模板</ElButton>
      <ElTooltip
        content="只作用于当前装修页面；修改配色方案或设置全商城主题请到「商城装修 → 商城主题」"
        placement="bottom"
      >
        <ElButton :icon="Discount" @click="emit('theme')">本页主题</ElButton>
      </ElTooltip>
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

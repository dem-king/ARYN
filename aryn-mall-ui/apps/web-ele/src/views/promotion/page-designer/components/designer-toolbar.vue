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
import {
  ElButton,
  ElInput,
  ElRadioButton,
  ElRadioGroup,
  ElSlider,
  ElSpace,
  ElTag,
  ElTooltip,
} from 'element-plus';

const props = defineProps<{
  canRedo: boolean;
  canUndo: boolean;
  pageName: string;
  saveStatus: DraftSaveStatus;
  /**
   * 页面导航开启「沉浸式渐显」且非内嵌页型时，才允许切换画布导航的
   * 「顶部 / 滚动后」预览态；否则整组控件不渲染，避免出现改了没反应的死控件。
   */
  scrollPreviewAvailable: boolean;
  /**
   * 画布预览的滚动距离（0–260px，本地 UI 态）：只影响导航栏样式，
   * 不进 document、不触发 patchPage / 撤销记录。
   */
  scrollTop: number;
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
  'update:scrollTop': [value: number];
  'update:showPageShell': [value: boolean];
  'update:zoom': [value: number];
}>();

/**
 * 画布「滚动后」预览态的最大滚动距离。
 *
 * 取面板「渐显距离」上限 260：这样把距离调到 260 时点「滚动后」也能看到
 * 完全显现态；距离更小（默认 140）时 260 处同样是 progress=1，不会过度。
 */
const SCROLL_PREVIEW_MAX = 260;

/** 「滚动后」快捷档位取满程（完全显现），与滑杆微调互不冲突 */
const scrollPreviewMode = computed(() =>
  props.scrollTop > 0 ? 'scrolled' : 'top',
);

function onScrollPreviewModeChange(
  value: boolean | number | string | undefined,
) {
  emit('update:scrollTop', value === 'scrolled' ? SCROLL_PREVIEW_MAX : 0);
}

function onScrollTopInput(value: number | number[]) {
  const next = Array.isArray(value) ? (value[0] ?? 0) : value;
  emit(
    'update:scrollTop',
    Math.min(Math.max(Math.round(next), 0), SCROLL_PREVIEW_MAX),
  );
}

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

    <ElSpace :size="4" class="toolbar-group toolbar-center">
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
      <template v-if="scrollPreviewAvailable">
        <span class="toolbar-divider"></span>
        <ElTooltip
          content="预览画布导航栏滚动后的外观：只改预览，不修改草稿、不产生撤销记录"
        >
          <ElRadioGroup
            :model-value="scrollPreviewMode"
            size="small"
            @change="onScrollPreviewModeChange"
          >
            <ElRadioButton value="top">顶部</ElRadioButton>
            <ElRadioButton value="scrolled">滚动后</ElRadioButton>
          </ElRadioGroup>
        </ElTooltip>
        <!-- 细调滑杆是低频操作：窄窗口下先收起它，保留「顶部 / 滚动后」快捷切换 -->
        <span class="scroll-preview-fine">
          <ElSlider
            :max="SCROLL_PREVIEW_MAX"
            :min="0"
            :model-value="scrollTop"
            :show-tooltip="false"
            class="scroll-preview-slider"
            size="small"
            @input="onScrollTopInput"
          />
          <span class="scroll-preview-value">{{ scrollTop }}px</span>
        </span>
      </template>
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
          aria-label="页面结构"
          class="toolbar-tool"
          plain
          @click="emit('update:showPageShell', !showPageShell)"
        >
          <span class="toolbar-label toolbar-label--collapsible">页面结构</span>
        </ElButton>
      </ElTooltip>
      <ElTooltip content="套用页面模板，或把当前页面存为模板">
        <ElButton
          :icon="Collection"
          aria-label="模板"
          class="toolbar-tool"
          @click="emit('template')"
        >
          <span class="toolbar-label toolbar-label--collapsible">模板</span>
        </ElButton>
      </ElTooltip>
      <ElTooltip
        content="只作用于当前装修页面；修改配色方案或设置全商城主题请到「商城装修 → 商城主题」"
        placement="bottom"
      >
        <ElButton
          :icon="Discount"
          aria-label="本页主题"
          class="toolbar-tool"
          @click="emit('theme')"
        >
          <span class="toolbar-label toolbar-label--collapsible">本页主题</span>
        </ElButton>
      </ElTooltip>
      <ElTooltip content="素材库：查看与管理图片素材">
        <ElButton
          :icon="Picture"
          aria-label="素材"
          class="toolbar-tool"
          @click="emit('assets')"
        >
          <span class="toolbar-label toolbar-label--collapsible">素材</span>
        </ElButton>
      </ElTooltip>
      <ElButton :icon="View" @click="emit('preview')">预览</ElButton>
      <ElButton :icon="Check" @click="emit('save')">保存草稿</ElButton>
      <ElButton :icon="Upload" type="primary" @click="emit('publish')">
        发布
      </ElButton>
    </ElSpace>
  </header>
</template>

<style scoped>


/* 1450px 以下：收起预览滑杆与 px 数值（细调低频），保留分段切换 */
@media (max-width: 1449px) {
  .scroll-preview-fine {
    display: none;
  }
}

/*
 * 1320px 以下：次要工具（页面结构/模板/本页主题/素材）收成纯图标按钮，
 * 名称由 Tooltip 与 aria-label 兜底；预览、保存草稿、发布三个主操作
 * 始终保留文字，保证核心链路可发现。
 */
@media (max-width: 1319px) {
  .toolbar-actions :deep(.el-button.toolbar-tool) {
    padding-right: 8px;
    padding-left: 8px;
  }

  .toolbar-label--collapsible {
    display: none;
  }
}

.designer-toolbar {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
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

/* 中列占满剩余轨道并在内部居中；内容过多时裁切而不是压到两侧按钮 */
.toolbar-center {
  justify-content: center;
  justify-self: stretch;
  overflow: hidden;
}

/* 子项一律不收缩：滑杆/分段控件被压扁会出现竖排、文字截断等破版 */
.toolbar-center :deep(.el-space__item) {
  flex: none;
}

.toolbar-center :deep(.el-radio-group) {
  flex-wrap: nowrap;
}

.toolbar-actions {
  justify-self: end;
}

/*
 * 页面名用 clamp 连续收缩（240 → 180px）：断点只负责结构性收起，
 * 名字宽度不单独占一个断点，窄窗口下不会突然「跳一下」。
 */
.page-name {
  width: clamp(180px, 15.5vw, 240px);
}

.toolbar-divider {
  width: 1px;
  height: 20px;
  background: var(--el-border-color);
}

.zoom-value {
  width: 42px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  text-align: center;
}

/* 滑杆 + 数值作为一个整体：窄窗口下整组收起，不影响「顶部 / 滚动后」切换 */
.scroll-preview-fine {
  display: inline-flex;
  gap: 4px;
  align-items: center;

  /* 与左侧分段控件拉开一点距离，避免滑杆圆点贴着「滚动后」按钮 */
  margin-left: 8px;
}

.scroll-preview-slider {
  width: 90px;
}

.scroll-preview-value {
  width: 38px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

/*
 * 顶栏是一行「分组自适应」布局：
 *   [返回 + 页面名 + 保存状态]  [撤销/重做 + 缩放 + 导航预览态]  [页面工具 + 保存/发布]
 *
 * 三组宽度差异很大（左组约 283–340、中列含滑杆约 444、右组文字态约 576–692），
 * 且「导航预览」与「页面结构」按页型互斥、不会同时出现。历史写法用
 * `justify-self: center` 让中列按内容宽度居中：轨道不够宽时中列自身会溢出
 * 轨道而不是被裁切，直接压住左右两组；ElRadioGroup 还会被压成两行、上下溢出
 * 到工具栏外（2026-10-27 用户反馈截图）。现在改为：
 *   1. 中列拉伸填满剩余轨道，内容在其中居中；内容过多时裁在自己轨道内，
 *      绝不会越界到两侧按钮上；
 *   2. 中列子项禁止收缩、ElRadioGroup 禁止换行，任何宽度下都不会竖排；
 *   3. 空间不足时分两级收起低优先级信息（滑杆 → 次要工具文字），保证
 *      `.page-designer` 1180px 最小设计宽度下仍能一行放下全部控件。
 */
</style>

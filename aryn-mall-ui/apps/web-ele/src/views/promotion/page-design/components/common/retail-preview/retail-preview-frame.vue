<script setup lang="ts">
import type { DiyCommonStyle } from '@vben/types';

import type { RetailPreviewStatus } from './use-retail-preview';

import { computed } from 'vue';

import { Box, EditPen, Loading, WarningFilled } from '@element-plus/icons-vue';
import { ElIcon } from 'element-plus';

const props = defineProps<{
  commonStyle: DiyCommonStyle;
  message?: string;
  status: RetailPreviewStatus;
  title?: string;
}>();

const frameStyle = computed(() => {
  const style = props.commonStyle;
  return {
    background: style.bgPicUrl
      ? `url(${style.bgPicUrl}) center / cover no-repeat`
      : `linear-gradient(${style.bgColorDirection || 'to right'}, ${style.bgStartColor || '#ffffff'}, ${style.bgEndColor || style.bgStartColor || '#ffffff'})`,
    borderBottomLeftRadius: `${style.styleLbRadius}px`,
    borderBottomRightRadius: `${style.styleRbRadius}px`,
    borderTopLeftRadius: `${style.styleLtRadius}px`,
    borderTopRightRadius: `${style.styleRtRadius}px`,
    margin: `${style.styleTopMargin}px ${style.styleRightMargin}px ${style.styleBottomMargin}px ${style.styleLeftMargin}px`,
    padding: `${style.styleTopPadding}px ${style.styleRightPadding}px ${style.styleBottomPadding}px ${style.styleLeftPadding}px`,
  };
});

const stateMeta = computed(() => {
  const states = {
    empty: { icon: Box, label: props.message || '暂无可预览数据' },
    error: { icon: WarningFilled, label: props.message || '数据加载失败' },
    invalid: { icon: WarningFilled, label: props.message || '组件配置无效' },
    loading: { icon: Loading, label: props.message || '正在加载预览数据' },
    placeholder: {
      icon: EditPen,
      label: props.message || '配置后预览实时数据',
    },
  } as const;
  return props.status === 'data' ? undefined : states[props.status];
});
</script>

<template>
  <section class="retail-frame" :style="frameStyle">
    <header v-if="title" class="retail-frame__header">
      <strong>{{ title }}</strong>
      <slot name="header-extra"></slot>
    </header>
    <div
      v-if="stateMeta"
      class="retail-frame__state"
      :class="`is-${status}`"
      aria-live="polite"
      role="status"
    >
      <ElIcon :class="{ 'is-loading': status === 'loading' }">
        <component :is="stateMeta.icon" />
      </ElIcon>
      <span>{{ stateMeta.label }}</span>
    </div>
    <slot v-else></slot>
  </section>
</template>

<style scoped>
.retail-frame {
  min-height: 116px;
  overflow: hidden;
  color: #172033;
  border: 1px solid rgb(17 24 39 / 8%);
}

.retail-frame__header {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  min-height: 28px;
  margin-bottom: 10px;
  font-size: 15px;
}

.retail-frame__state {
  display: grid;
  gap: 8px;
  place-items: center;
  align-content: center;
  min-height: 84px;
  font-size: 13px;
  color: #64748b;
  text-align: center;
  background: rgb(248 250 252 / 88%);
  border: 1px dashed #cbd5e1;
}

.retail-frame__state.is-error,
.retail-frame__state.is-invalid {
  color: #9a3412;
  background: #fff7ed;
  border-color: #fdba74;
}

.retail-frame__state .el-icon {
  font-size: 22px;
}
</style>

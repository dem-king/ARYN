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
  /** 组件类型标识（如「秒杀」「拼团」）：占位态视觉几乎一致，用标签区分楼层类型 */
  typeLabel?: string;
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
  <section
    class="retail-frame"
    :class="{ 'is-state': status !== 'data' }"
    :style="frameStyle"
  >
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
      <span v-if="typeLabel" class="retail-frame__tag">{{ typeLabel }}</span>
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
  overflow: hidden;
  color: #172033;
  border: 1px solid rgb(17 24 39 / 8%);
}

/**
 * min-height 只给占位态（加载/空/错误/待配置），保证占位卡片有视觉重量；
 * 真实数据态按内容自然高：船舶工作台 44px、倒计时 38px、营销入口 42px
 * 这类细条组件与小程序实机 1rpx=0.5px 等高，统一撑到 116px 会在组件
 * 下方多出一大块空白（编辑器画布里看起来「组件高度太高」）。
 */
.retail-frame.is-state {
  min-height: 116px;
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

.retail-frame__tag {
  padding: 1px 10px;
  font-size: 11px;
  border: 1px solid currentcolor;
  border-radius: 999px;
  opacity: 0.8;
}
</style>

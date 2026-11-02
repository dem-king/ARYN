<script lang="ts" setup>
import type { DiyCommonStyle } from '@vben/types';

import { computed } from 'vue';

import { commonStyleRadiusOverflow } from '../common/common-style-clip';
import { sanitizeCustomHtml } from './sanitize-custom-html';

interface CustomHtmlProps {
  commonStyle?: DiyCommonStyle | null;
  height?: number;
  html?: string;
}

const props = defineProps<{
  showData: CustomHtmlProps;
}>();

const defaultCommonStyle: DiyCommonStyle = {
  styleTopMargin: 0,
  styleBottomMargin: 0,
  styleLeftMargin: 0,
  styleRightMargin: 0,
  styleTopPadding: 0,
  styleBottomPadding: 0,
  styleLeftPadding: 0,
  styleRightPadding: 0,
  styleLtRadius: 0,
  styleRtRadius: 0,
  styleLbRadius: 0,
  styleRbRadius: 0,
  bgColorDirection: 'to right',
  bgStartColor: '',
  bgEndColor: '',
  bgPicUrl: '',
};

/** 高度只做下限：与 C 端一致，内容超出时撑开而不是被裁掉 */
const containerHeight = computed(() => {
  const height = Number(props.showData?.height);
  return Number.isFinite(height) && height > 0 ? height : 300;
});

/** 渲染前统一净化，避免画布内执行脚本 */
const sanitizedHtml = computed(() =>
  sanitizeCustomHtml(props.showData?.html ?? ''),
);

/** 与 C 端 `useDiyStyle(commonStyle)` 同口径，保证画布与真机看到的一致 */
const dynamicStyles = computed(() => {
  const commonStyle = props.showData.commonStyle ?? defaultCommonStyle;
  const bgStart = commonStyle.bgStartColor;
  const bgEnd = commonStyle.bgEndColor;
  const hasGradient = Boolean(bgStart || bgEnd);
  return {
    marginTop: `${commonStyle.styleTopMargin}px`,
    marginLeft: `${commonStyle.styleLeftMargin}px`,
    marginRight: `${commonStyle.styleRightMargin}px`,
    marginBottom: `${commonStyle.styleBottomMargin}px`,
    paddingTop: `${commonStyle.styleTopPadding}px`,
    paddingLeft: `${commonStyle.styleLeftPadding}px`,
    paddingRight: `${commonStyle.styleRightPadding}px`,
    paddingBottom: `${commonStyle.styleBottomPadding}px`,
    borderTopLeftRadius: `${commonStyle.styleLtRadius}px`,
    borderTopRightRadius: `${commonStyle.styleRtRadius}px`,
    borderBottomLeftRadius: `${commonStyle.styleLbRadius}px`,
    borderBottomRightRadius: `${commonStyle.styleRbRadius}px`,
    ...commonStyleRadiusOverflow(commonStyle),
    ...(commonStyle.bgPicUrl
      ? {
          background: `url(${commonStyle.bgPicUrl})`,
          backgroundRepeat: 'no-repeat',
          backgroundPosition: 'center',
          backgroundSize: '100% 100%',
        }
      : {
          background: hasGradient
            ? `linear-gradient(${commonStyle.bgColorDirection || 'to right'}, ${bgStart || bgEnd}, ${bgEnd || bgStart})`
            : 'transparent',
        }),
  };
});
</script>

<template>
  <div :style="dynamicStyles">
    <div
      class="custom-html-base"
      :style="{ minHeight: `${containerHeight}px` }"
    >
      <!-- 内容经 sanitizeCustomHtml 净化后渲染 -->
      <!-- eslint-disable vue/no-v-html -->
      <div
        v-if="sanitizedHtml"
        class="custom-html-body"
        v-html="sanitizedHtml"
      ></div>
      <!-- eslint-enable vue/no-v-html -->
      <div v-else class="custom-html-empty">自定义HTML</div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.custom-html-base {
  display: flex;
  flex-direction: column;
}

.custom-html-body {
  /* 长串 URL/英文不会断行，运营粘贴的表格容易撑破预览画布 */
  overflow-wrap: anywhere;

  :deep(img) {
    max-width: 100%;
    height: auto;
  }

  :deep(a) {
    color: var(--el-color-primary);
  }

  :deep(table) {
    max-width: 100%;
    font-size: 12px;
  }
}

.custom-html-empty {
  display: grid;
  flex: 1;
  place-items: center;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}
</style>

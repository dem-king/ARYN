<script lang="ts" setup>
import type { DiyCommonStyle } from '@vben/types';

import { computed } from 'vue';

import { sanitizeRichTextHtml } from './sanitize-rich-text';

interface ShowData {
  commonStyle?: DiyCommonStyle | null;
  content: string;
}
const props = defineProps<{
  showData: ShowData;
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
const dynamicStyles = computed(() => {
  const commonStyle = props.showData.commonStyle ?? defaultCommonStyle;
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
    ...(commonStyle.bgPicUrl && {
      background: `url(${commonStyle.bgPicUrl})`,
      backgroundRepeat: 'no-repeat',
      backgroundPosition: 'center',
      backgroundSize: '100% 100%',
    }),
    ...(!commonStyle.bgPicUrl && {
      background: `linear-gradient(${commonStyle.bgColorDirection || 'to right'},
				${commonStyle.bgStartColor || ''},
				${commonStyle.bgEndColor || commonStyle.bgStartColor || ''})`,
    }),
  };
});
const sanitizedContent = computed(() =>
  sanitizeRichTextHtml(props.showData.content),
);
</script>

<template>
  <div class="rich-text-base" :style="dynamicStyles">
    <!-- Content is sanitized by sanitizeRichTextHtml before rendering. -->
    <!-- eslint-disable-next-line vue/no-v-html -->
    <div v-html="sanitizedContent"></div>
  </div>
</template>

<style scoped lang="scss">
.rich-text-base {
  overflow-wrap: break-word;

  :deep(img) {
    width: 100%;
    height: auto;
  }
}
</style>

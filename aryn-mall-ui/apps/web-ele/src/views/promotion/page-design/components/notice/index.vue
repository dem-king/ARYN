<script lang="ts" setup>
import type { DiyCommonStyle } from '@vben/types';

import { computed } from 'vue';

import { Bell } from '@element-plus/icons-vue';
import { ElIcon } from 'element-plus';

interface ShowData {
  commonStyle?: DiyCommonStyle | null;
  color: string;
  contentList: any[];
  direction?: string;
  titleColor?: string;
  titleSize?: number;
  titleStyle?: string;
  titleText?: string;
  titleType?: string;
  titleUrl?: string;
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
      background: `linear-gradient(${commonStyle.bgColorDirection || 'to right'}, ${commonStyle.bgStartColor || ''}, ${commonStyle.bgEndColor || commonStyle.bgStartColor || ''})`,
    }),
  };
});

/**
 * 公告文案：小程序端把所有 contentList 条目交给 wd-notice-bar 连续滚动，
 * 这里按首条呈现（画布不做滚动动画），无内容时给出与小程序一致的占位提示。
 */
const contentList = computed(() => props.showData.contentList || []);
const texts = computed(() =>
  contentList.value
    .map((item: any) => item?.content)
    .filter((text: unknown): text is string => typeof text === 'string'),
);
/** 有链接才显示右侧箭头；兼容旧数据 { type: 1, url } 与当前 { link } */
const hasLink = computed(() =>
  contentList.value.some((item: any) => {
    if (item?.link) return true;
    return (
      (item?.type === 1 || item?.type === '1') &&
      typeof item?.url === 'string' &&
      item.url.length > 0
    );
  }),
);
/** 前缀图标：与小程序一致，未配自定义标题时用默认铃铛，继承正文色 */
const titleType = computed(() => props.showData.titleType || '');
const titleColor = computed(
  () => props.showData.titleColor || props.showData.color || '',
);
const titleSize = computed(() => Number(props.showData.titleSize) || 18);
</script>

<template>
  <div class="base" :style="dynamicStyles">
    <div class="notice">
      <img
        v-if="titleType === '1' && showData.titleUrl"
        class="notice-icon notice-icon--img"
        :src="showData.titleUrl"
        alt=""
        :style="{ width: `${titleSize}px`, height: `${titleSize}px` }"
      />
      <span
        v-else-if="titleType === '2'"
        class="notice-icon notice-icon--text"
        :style="{
          color: titleColor,
          fontSize: `${titleSize}px`,
          fontWeight: showData.titleStyle === '1' ? 'bold' : 'normal',
        }"
      >
        {{ showData.titleText }}
      </span>
      <ElIcon
        v-else
        class="notice-icon"
        :style="{
          color: titleColor,
          fontSize: `${titleSize}px`,
          fontWeight: showData.titleStyle === '1' ? 'bold' : 'normal',
        }"
      >
        <Bell />
      </ElIcon>
      <span
        v-if="texts.length > 0"
        class="notice-text"
        :style="{ color: showData.color }"
      >
        {{ texts[0] }}
      </span>
      <span v-else class="notice-text" :style="{ color: showData.color }">
        请填写内容，如果过长，将会在手机上滚动显示
      </span>
      <span
        v-if="hasLink"
        aria-hidden="true"
        class="notice-arrow"
        :style="{ color: showData.color }"
      >
        ›
      </span>
    </div>
  </div>
</template>

<!--
  样式与小程序 diy-notice 逐值对齐：公告用 wd-notice-bar 渲染，
  行高 20px、字号 12px、前缀图标 16px，背景由通用样式提供。
-->
<style scoped lang="scss">
.base {
  min-height: 20px;
}

.notice {
  display: flex;
  align-items: center;
  min-height: 20px;
}

.notice-icon {
  flex: 0 0 auto;
  margin-right: 6px;
  line-height: 20px;
}

.notice-icon--img {
  display: block;
}

.notice-icon--text {
  line-height: 20px;
}

.notice-text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
  line-height: 20px;
  white-space: nowrap;
}

.notice-arrow {
  flex: 0 0 auto;
  margin-left: 6px;
  font-size: 14px;
  line-height: 20px;
  opacity: 0.5;
}
</style>

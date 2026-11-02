<script lang="ts" setup>
import type { DiyCommonStyle } from '@vben/types';

import { computed } from 'vue';

import { Bell } from '@element-plus/icons-vue';
import { ElIcon } from 'element-plus';

// 跑马灯与 C 端同源复用：diy-notice 就引用 diy-activity-strip 的 MarqueeText
import MarqueeText from '../activity-strip/marquee-text.vue';
import NoticeScroller from './notice-scroller.vue';

interface ShowData {
  commonStyle?: DiyCommonStyle | null;
  color: string;
  contentList: any[];
  direction?: string;
  speed?: number | string;
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
 * 公告文案：与小程序 diy-notice 同口径，所有条目（含空白）都进入滚动队列。
 * 空公告时画布保留占位提示（编辑器需要保持区块可选中），实机则整块不渲染。
 */
const contentList = computed(() => props.showData.contentList || []);
const texts = computed(() =>
  contentList.value.map((item: any) =>
    typeof item?.content === 'string' ? item.content : '',
  ),
);
const hasContent = computed(() =>
  texts.value.some((text) => text.trim().length > 0),
);
/** 滚动方式与速度：0/非法速度回落 50（与 C 端 `Number(speed) || 50` 同口径） */
const direction = computed<'horizontal' | 'vertical'>(() =>
  props.showData.direction === 'vertical' ? 'vertical' : 'horizontal',
);
const speed = computed(() => Number(props.showData.speed) || 50);
/**
 * 单条横向走无缝跑马灯（与小程序一致）：wd-notice-bar 的横向滚动会把文案
 * 整条滚出可视区、空等一段时间再跳回来，单条时中段只剩一个铃铛；可公告条
 * 是运营配了滚动速度的组件，也不能因为只有一条就完全静止。多条与竖向交给
 * notice-scroller 按 wd-notice-bar 同算法逐条滚动。
 */
const singleNotice = computed(() => texts.value.length <= 1);
const singleTicker = computed(
  () => singleNotice.value && direction.value === 'horizontal',
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
const titleColor = computed(
  () => props.showData.titleColor || props.showData.color || '',
);
const titleSize = computed(() => Number(props.showData.titleSize) || 18);
/** 文字前缀的文案（裁掉首尾空白，空白视同未配置） */
const titleText = computed(() => (props.showData.titleText ?? '').trim());
/**
 * 前缀渲染类型：与小程序 `diy-notice/notice-prefix.ts` 同口径。
 *
 * 存量数据存在「titleType='2' 但 titleText 为空」的组合（运营把前缀切成文字后
 * 又清空了文案），原样渲染会在公告条左侧留一段空白——画布和小程序都必须回落
 * 铃铛，运营在编辑器里看到的才是实机效果。
 */
const prefixKind = computed<'icon' | 'image' | 'text'>(() => {
  const titleType = String(props.showData.titleType ?? '').trim();
  const titleUrl = (props.showData.titleUrl ?? '').trim();
  if (titleType === '1' && titleUrl) return 'image';
  if (titleType === '2' && titleText.value) return 'text';
  return 'icon';
});
</script>

<template>
  <div class="base" :style="dynamicStyles">
    <div class="notice">
      <img
        v-if="prefixKind === 'image'"
        class="notice-icon notice-icon--img"
        :src="showData.titleUrl"
        alt=""
        :style="{ width: `${titleSize}px`, height: `${titleSize}px` }"
      />
      <span
        v-else-if="prefixKind === 'text'"
        class="notice-icon notice-icon--text"
        :style="{
          color: titleColor,
          fontSize: `${titleSize}px`,
          fontWeight: showData.titleStyle === '1' ? 'bold' : 'normal',
        }"
      >
        {{ titleText }}
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
      <div class="notice-body" :style="{ color: showData.color }">
        <!-- 单条横向：无缝跑马灯（与小程序 diy-notice 同走 MarqueeText ticker） -->
        <MarqueeText
          v-if="hasContent && singleTicker"
          ticker
          :text="texts[0] || ''"
          :speed="speed"
        />
        <!-- 多条横向 / 竖向：wd-notice-bar 同算法逐条滚动（见 notice-scroller.vue） -->
        <NoticeScroller
          v-else-if="hasContent"
          :texts="texts"
          :direction="direction"
          :speed="speed"
          :scrollable="!singleNotice"
          :wrapable="singleNotice && !singleTicker"
        />
        <span v-else class="notice-text">
          请填写内容，如果过长，将会在手机上滚动显示
        </span>
      </div>
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
  样式与小程序 diy-notice 逐值对齐：滚动行为见 MarqueeText（单条横向）
  与 notice-scroller（多条/竖向，wd-notice-bar 同算法），
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

.notice-body {
  flex: 1;
  min-width: 0;
  font-size: 12px;
  line-height: 20px;
}

.notice-text {
  display: block;
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

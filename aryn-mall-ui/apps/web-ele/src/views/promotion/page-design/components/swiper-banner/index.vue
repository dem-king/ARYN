<script lang="ts" setup>
import type { DiyCommonStyle } from '@vben/types';

import { computed } from 'vue';

import { ElCarousel, ElCarouselItem } from 'element-plus';

import { commonStyleRadiusOverflow } from '../common/common-style-clip';

interface ShowData {
  commonStyle?: DiyCommonStyle | null;
  imageList: any[];
  interval: number;
  indicatorDots: boolean;
  indicatorColor: string;
  indicatorActiveColor: string;
  height: number;
  borderRadius: number;
}

const props = defineProps<{ showData: ShowData }>();

const dynamicStyles = computed(() => {
  // 老数据没有 commonStyle：不绑内联样式，保留 CSS 的 12px 留白兜底（与 C 端同口径）
  if (!props.showData.commonStyle) return {};
  const commonStyle = props.showData.commonStyle;
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
    // 通用圆角 > 0 时裁切：轮播图铺满根节点，直角会盖住圆角（与 C 端 getCommonStyle 同口径）
    ...commonStyleRadiusOverflow(commonStyle),
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

const hasImages = computed(() => {
  return (
    props.showData.imageList &&
    props.showData.imageList.length > 0 &&
    props.showData.imageList[0].url
  );
});
</script>

<template>
  <div class="swiper-banner-box" :style="dynamicStyles">
    <div
      v-if="hasImages"
      class="swiper-banner-view"
      :style="{
        height: `${showData.height}px`,
        borderRadius: `${showData.borderRadius}px`,
        overflow: 'hidden',
      }"
    >
      <ElCarousel
        :height="`${showData.height}px`"
        :interval="showData.interval"
        :indicator-position="showData.indicatorDots ? '' : 'none'"
      >
        <ElCarouselItem
          v-for="(item, index) in showData.imageList"
          :key="index"
        >
          <img :src="item.url" class="banner-image" />
        </ElCarouselItem>
      </ElCarousel>
    </div>
    <div
      v-else
      class="swiper-banner-empty"
      :style="{
        height: `${showData.height}px`,
        borderRadius: `${showData.borderRadius}px`,
      }"
    >
      <svg
        viewBox="0 0 24 24"
        width="32"
        height="32"
        fill="none"
        stroke="#999"
        stroke-width="1.5"
      >
        <rect x="2" y="4" width="20" height="16" rx="2" />
        <circle cx="8" cy="10" r="2" />
        <path d="M22 16l-5-5-5 5" />
        <path d="M2 16l4-4 5 5" />
      </svg>
      <span class="empty-text">轮播图</span>
    </div>
  </div>
</template>

<!--
  样式与小程序 diy-swiper-banner 逐值对齐：外层左右内外边距 12px，
  高度与圆角由运营配置直接生效。
-->
<style scoped lang="scss">
.swiper-banner-box {
  margin: 0 12px;

  .swiper-banner-view {
    width: 100%;

    .banner-image {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
  }

  .swiper-banner-empty {
    display: flex;
    flex-direction: column;
    gap: 8px;
    align-items: center;
    justify-content: center;
    width: 100%;
    background-color: #f5f7fa;

    .empty-text {
      font-size: 12px;
      color: #909399;
    }
  }

  :deep(.el-carousel__item) {
    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
  }
}
</style>

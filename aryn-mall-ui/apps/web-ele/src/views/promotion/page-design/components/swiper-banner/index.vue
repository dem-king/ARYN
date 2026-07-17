<script lang="ts" setup>
import type { DiyCommonStyle } from '@vben/types';

import { computed } from 'vue';

import { ElCarousel, ElCarouselItem } from 'element-plus';

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

<style scoped lang="scss">
.swiper-banner-box {
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
      font-size: 14px;
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

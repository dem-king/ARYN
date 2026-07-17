<script lang="ts" setup>
import type { DiyCommonStyle } from '@vben/types';

import { computed } from 'vue';

import { Picture } from '@element-plus/icons-vue';
import { ElCarousel, ElCarouselItem, ElIcon, ElImage } from 'element-plus';

interface ShowData {
  commonStyle?: DiyCommonStyle | null;
  commonImageStyle: DiyCommonStyle | null;
  imageList: any;
  type: string; // 1.一行一个图片 2.轮播海报
  height: number;
  interval: number; // 轮播间隔时间
  indicatorDots?: boolean;
  swiperType: string; // 轮播图类型 1.横向滚动；2.卡片式轮播图；3.两个两个轮播
  imgRadius: number; // 图片圆角
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

  const paddingTop = commonStyle.styleTopPadding ?? 0;
  const paddingBottom = commonStyle.styleBottomPadding ?? 0;
  const marginTop = commonStyle.styleTopMargin ?? 0;
  const marginBottom = commonStyle.styleBottomMargin ?? 0;

  const baseHeight = props.showData?.height ?? 0;
  const imageCount = props.showData.imageList?.length || 1;
  const totalHeight =
    props.showData.type === '1' ? baseHeight * imageCount : baseHeight;

  return {
    marginTop: `${marginTop}px`,
    marginLeft: `${commonStyle.styleLeftMargin}px`,
    marginRight: `${commonStyle.styleRightMargin}px`,
    marginBottom: `${marginBottom}px`,
    paddingTop: `${paddingTop}px`,
    paddingLeft: `${commonStyle.styleLeftPadding}px`,
    paddingRight: `${commonStyle.styleRightPadding}px`,
    paddingBottom: `${paddingBottom}px`,
    borderTopLeftRadius: `${commonStyle.styleLtRadius}px`,
    borderTopRightRadius: `${commonStyle.styleRtRadius}px`,
    borderBottomLeftRadius: `${commonStyle.styleLbRadius}px`,
    borderBottomRightRadius: `${commonStyle.styleRbRadius}px`,
    minHeight: `${Math.max(totalHeight, 0)}px`,
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
const dynamicImageStyles = computed(() => {
  const commonStyle = props.showData.commonImageStyle ?? defaultCommonStyle;

  const paddingTop = commonStyle.styleTopPadding ?? 0;
  const paddingBottom = commonStyle.styleBottomPadding ?? 0;
  const marginTop = commonStyle.styleTopMargin ?? 0;
  const marginBottom = commonStyle.styleBottomMargin ?? 0;
  return {
    height: `${Math.max(props.showData.height, 0)}px`,
    marginTop: `${marginTop}px`,
    marginLeft: `${commonStyle.styleLeftMargin}px`,
    marginRight: `${commonStyle.styleRightMargin}px`,
    marginBottom: `${marginBottom}px`,
    paddingTop: `${paddingTop}px`,
    paddingLeft: `${commonStyle.styleLeftPadding}px`,
    paddingRight: `${commonStyle.styleRightPadding}px`,
    paddingBottom: `${paddingBottom}px`,
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
</script>

<template>
  <div class="image-box" :style="dynamicStyles">
    <div
      v-if="
        showData.imageList &&
        showData.imageList.length > 0 &&
        showData.imageList[0].url
      "
      class="image-view-box"
    >
      <!-- 一行一个图片 -->
      <div v-if="showData.type === '1'" class="single-image-list">
        <div
          v-for="(item, index) in showData.imageList"
          :key="index"
          class="single-image-item"
          :style="dynamicImageStyles"
        >
          <ElImage
            :style="{ borderRadius: `${showData.imgRadius}px` }"
            :src="item.url"
          />
        </div>
      </div>
      <!-- 轮播图 -->
      <ElCarousel
        v-else
        :height="`${showData?.height}px`"
        :interval="showData.interval * 1000"
        :type="showData.swiperType === '2' ? 'card' : ''"
        :indicator-position="showData.indicatorDots ? '' : 'none'"
      >
        <ElCarouselItem
          v-for="(item, index) in showData.imageList"
          :key="index"
        >
          <ElImage
            :src="item.url"
            :style="{ borderRadius: `${showData.imgRadius}px` }"
          />
        </ElCarouselItem>
      </ElCarousel>
    </div>
    <div v-else class="image-view-item empty-image">
      <ElIcon>
        <Picture />
      </ElIcon>
      <div class="image-title">建议宽度为750像素</div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.image-box {
  .single-image-list {
    .single-image-item {
      box-sizing: border-box; // ✅ 保证 padding、border 不撑开宽度
      overflow: hidden;

      &:last-child {
        margin-bottom: 0;
      }

      .el-image {
        box-sizing: border-box;
        width: 100%;
        height: 100%;
        object-fit: cover; // ✅ 确保图片不变形
      }
    }
  }

  .empty-image {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    min-height: 200px;
    color: #909399;
    background-color: #f5f7fa;

    .el-icon {
      margin-bottom: 8px;
      font-size: 32px;
    }

    .image-title {
      font-size: 14px;
    }
  }

  :deep(.el-carousel__item) {
    .el-image {
      box-sizing: border-box;
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
  }
}
</style>

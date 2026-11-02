<script lang="ts" setup>
import type { DiyCommonStyle } from '@vben/types';

import { computed } from 'vue';

import { Picture } from '@element-plus/icons-vue';
import { ElCarousel, ElCarouselItem, ElIcon, ElImage } from 'element-plus';

import { commonStyleRadiusOverflow } from '../common/common-style-clip';

interface ShowData {
  commonStyle?: DiyCommonStyle | null;
  commonImageStyle: DiyCommonStyle | null;
  imageList: any;
  type: string; // 1.一行一个图片 2.轮播海报
  height: number;
  interval: number; // 轮播间隔时间
  indicatorDots?: boolean;
  /** 指示点颜色 / 选中颜色（C 端 uni swiper 同名属性，运营可配） */
  indicatorColor?: string;
  indicatorActiveColor?: string;
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
    // 圆角 > 0 时裁切子内容：图片贴满根节点，直角会盖住圆角（与 C 端 getCommonStyle 同口径）
    ...commonStyleRadiusOverflow(commonStyle),
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

/**
 * 轮播指示点颜色：C 端 `diy-image` 的 uni swiper 读
 * `indicator-color` / `indicator-active-color`（运营可配），管理端 ElCarousel
 * 用 CSS 变量覆盖内部按钮，避免两端轮播点颜色不一致。
 * 缺省值与 C 端组件同口径。
 */
const indicatorVars = computed(() => ({
  '--image-indicator': props.showData.indicatorColor || 'rgba(0, 0, 0, 0.3)',
  '--image-indicator-active': props.showData.indicatorActiveColor || '#ffffff',
}));

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
    // 图片级通用样式同理：图片铺满格位，圆角需裁切（与 C 端 commonImageStyle 同口径）
    ...commonStyleRadiusOverflow(commonStyle),
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
            fit="fill"
            :src="item.url"
            :style="{ borderRadius: `${showData.imgRadius}px` }"
          />
        </div>
      </div>
      <!-- 轮播图 -->
      <ElCarousel
        v-else
        :height="`${showData?.height}px`"
        :indicator-position="showData.indicatorDots ? '' : 'none'"
        :interval="showData.interval * 1000"
        :style="indicatorVars"
        :type="showData.swiperType === '2' ? 'card' : ''"
      >
        <ElCarouselItem
          v-for="(item, index) in showData.imageList"
          :key="index"
        >
          <ElImage
            fit="fill"
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
/*
  图片缩放口径：C 端 diy-image 的 `<image>` 未指定 mode（默认 scaleToFill），
  轮播项显式 `mode="scaleToFill"` —— 两端都是「拉伸铺满格位、不裁切」。
  管理端预览必须用等价的 object-fit: fill；用 cover 会裁掉图片两侧，
  与运营在真机上看到的效果不一致。
*/
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
        object-fit: fill;
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
      object-fit: fill;
    }
  }

  /* 指示点：ElCarousel 默认 30×2 指示条 → 对齐 C 端 uni swiper 的 8px 圆点，
     颜色取运营配置（见 script 的 indicatorVars）。 */
  :deep(.el-carousel__indicator .el-carousel__button) {
    width: 8px;
    height: 8px;
    background-color: var(--image-indicator);
    border-radius: 50%;
    opacity: 1;
  }

  :deep(.el-carousel__indicator.is-active .el-carousel__button) {
    background-color: var(--image-indicator-active);
    opacity: 1;
  }
}
</style>

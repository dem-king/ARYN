<script lang="ts" setup>
import type { DiyCommonStyle } from '@vben/types';

import { computed } from 'vue';

interface ShowData {
  commonStyle?: DiyCommonStyle | null;
  fontColor: string;
  bgColor: string;
  navList: any;
  type: string;
  showNum: number;
  imgSize: number;
  imgRadius: number;
  scrollShow: boolean;
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
    color: props.showData.fontColor,
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
  <div class="tab-nav" :style="dynamicStyles">
    <!--文本-->
    <div v-if="showData.type === '2'" class="nav-list">
      <div
        v-for="(item, index) in showData.navList"
        :key="index"
        class="nav-item-item text-overFlow-1"
      >
        <span :style="{ color: showData.fontColor }">{{ item.title }}</span>
      </div>
    </div>
    <!--图片-->
    <div
      v-else-if="showData.type === '3' || showData.type === '1'"
      class="nav-item-image"
      :class="{ 'scroll-view': showData.scrollShow }"
    >
      <div
        v-for="(item, index) in showData.navList"
        :key="index"
        class="nav-item-image-li"
        :class="{
          'is-nav-cell4': !showData.scrollShow && showData.showNum === 4,
          'is-nav-cell5': !showData.scrollShow && showData.showNum === 5,
        }"
      >
        <div class="nav-item-content">
          <img
            :src="item.url"
            :style="{
              width: `${showData.imgSize}px`,
              height: `${showData.imgSize}px`,
              borderRadius: `${showData.imgRadius}px`,
            }"
          />
          <p
            class="text-overFlow-1"
            :style="{ color: showData.fontColor }"
            v-if="showData.type === '3'"
          >
            <span v-if="item.title">{{ item.title }}</span>
          </p>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.tab-nav {
  .text-overFlow-1 {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .nav-list {
    display: flex;
    padding-bottom: 10px;

    .nav-item-item {
      position: relative;
      flex: 1;
      vertical-align: top;
      color: var(--el-text-color-secondary);
      text-align: center;
    }
  }

  .nav-item-image {
    .nav-item-image-li {
      display: inline-block;
      margin-top: 2px;
      vertical-align: bottom;

      &.is-nav-cell4 {
        width: 25%;
      }

      &.is-nav-cell5 {
        width: 20%;
      }

      .nav-item-content {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
      }

      img {
        transition: all 0.28s;
      }

      p {
        margin-top: 5px;
        font-size: 12px;
        color: hsl(var(--foreground));
        text-align: center;
      }
    }

    &.scroll-view {
      width: 100%;
      overflow-x: auto;
      white-space: nowrap;
      -webkit-overflow-scrolling: touch;

      .nav-item-image-li {
        display: inline-flex;
        justify-content: center;
        min-width: 80px;
        padding: 0 8px;
      }
    }
  }
}
</style>

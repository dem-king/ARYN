<script lang="ts" setup>
import type { DiyCommonStyle } from '@vben/types';

import { computed } from 'vue';

interface ShowData {
  commonStyle?: DiyCommonStyle | null;
  navList: any[];
  showNum: number;
  imgSize: number;
  imgRadius: number;
  fontColor: string;
  scrollShow: boolean;
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

const hasNavList = computed(() => {
  return props.showData.navList && props.showData.navList.length > 0;
});

const placeholderList = computed(() => {
  return Array.from({ length: props.showData.showNum }, (_, i) => i);
});

const itemWidth = computed(() => {
  return `${100 / props.showData.showNum}%`;
});
</script>

<template>
  <div class="category-nav-box" :style="dynamicStyles">
    <div
      class="category-nav-grid"
      :class="{ 'scroll-mode': showData.scrollShow }"
    >
      <template v-if="hasNavList">
        <div
          v-for="(item, index) in showData.navList"
          :key="index"
          class="nav-item"
          :style="{ width: itemWidth }"
        >
          <div class="nav-icon" v-if="item.url">
            <img
              :src="item.url"
              :style="{
                width: `${showData.imgSize}px`,
                height: `${showData.imgSize}px`,
                borderRadius: `${showData.imgRadius}px`,
              }"
            />
          </div>
          <div
            class="nav-icon nav-icon-placeholder"
            v-else
            :style="{
              width: `${showData.imgSize}px`,
              height: `${showData.imgSize}px`,
              borderRadius: `${showData.imgRadius}px`,
            }"
          >
            <svg
              viewBox="0 0 24 24"
              :width="showData.imgSize * 0.6"
              :height="showData.imgSize * 0.6"
              fill="none"
              stroke="#ccc"
              stroke-width="1.5"
            >
              <rect x="3" y="3" width="18" height="18" rx="2" />
            </svg>
          </div>
          <span class="nav-text" :style="{ color: showData.fontColor }">
            {{ item.text }}
          </span>
        </div>
      </template>
      <template v-else>
        <div
          v-for="i in placeholderList"
          :key="i"
          class="nav-item"
          :style="{ width: itemWidth }"
        >
          <div
            class="nav-icon nav-icon-placeholder"
            :style="{
              width: `${showData.imgSize}px`,
              height: `${showData.imgSize}px`,
              borderRadius: `${showData.imgRadius}px`,
            }"
          >
            <svg
              viewBox="0 0 24 24"
              :width="showData.imgSize * 0.6"
              :height="showData.imgSize * 0.6"
              fill="none"
              stroke="#ccc"
              stroke-width="1.5"
            >
              <rect x="3" y="3" width="18" height="18" rx="2" />
            </svg>
          </div>
          <span class="nav-text" :style="{ color: showData.fontColor }">
            导航
          </span>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped lang="scss">
.category-nav-box {
  .category-nav-grid {
    display: flex;
    flex-wrap: wrap;

    &.scroll-mode {
      flex-wrap: nowrap;
      overflow-x: auto;
    }

    .nav-item {
      display: flex;
      flex-direction: column;
      gap: 6px;
      align-items: center;
      padding: 4px 0;

      .nav-icon {
        display: flex;
        align-items: center;
        justify-content: center;

        img {
          object-fit: cover;
        }
      }

      .nav-icon-placeholder {
        background-color: #f5f7fa;
      }

      .nav-text {
        font-size: 12px;
        line-height: 1.2;
        text-align: center;
        word-break: break-all;
      }
    }
  }
}
</style>

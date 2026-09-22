<script lang="ts" setup>
import type { DiyCommonStyle } from '@vben/types';

import { computed, ref } from 'vue';

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
  displayMode?: 'grid' | 'pager' | 'scroll';
  pageRows?: number;
  indicatorDots?: boolean;
  indicatorColor?: string;
  indicatorActiveColor?: string;
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

const navType = computed(() => String(props.showData.type ?? '3'));
const navList = computed<any[]>(() => props.showData.navList || []);
const showNum = computed(() => (Number(props.showData.showNum) === 5 ? 5 : 4));

// 显示方式归一化：旧数据只有 scrollShow 字段，由它推导 grid/scroll
const displayMode = computed<'grid' | 'pager' | 'scroll'>(() => {
  const mode = props.showData.displayMode;
  if (mode === 'grid' || mode === 'scroll' || mode === 'pager') return mode;
  return props.showData.scrollShow ? 'scroll' : 'grid';
});

// ---- 分页模式 ----
const pageRows = computed(() =>
  Number(props.showData.pageRows) === 2 ? 2 : 3,
);
const pageSize = computed(() => showNum.value * pageRows.value);
const pages = computed(() => {
  const list = navList.value;
  const grouped: any[][] = [];
  for (let i = 0; i < list.length; i += pageSize.value)
    grouped.push(list.slice(i, i + pageSize.value));
  return grouped.length > 0 ? grouped : [[]];
});
const showIndicator = computed(
  () => props.showData.indicatorDots !== false && pages.value.length > 1,
);
const indicatorColor = computed(
  () => props.showData.indicatorColor || 'rgba(0, 0, 0, 0.2)',
);
const indicatorActiveColor = computed(
  () => props.showData.indicatorActiveColor || '#1989fa',
);
const activePage = ref(0);
function onPagerScroll(event: Event) {
  const el = event.target as HTMLElement;
  activePage.value = Math.round(el.scrollLeft / el.clientWidth);
}

// 单行高度（px）= 图片高度 + 图文模式下文字块高度（上边距 5px + 行高约 17px）
const rowHeight = computed(() => {
  const imgSize = Number(props.showData.imgSize) || 40;
  return imgSize + (navType.value === '3' ? 22 : 0);
});
// 每行底部间距 6px；分页指示点预留 16px
const pagerHeight = computed(
  () => pageRows.value * (rowHeight.value + 6) + (showIndicator.value ? 16 : 0),
);
</script>

<template>
  <div class="tab-nav" :style="dynamicStyles">
    <!--文本-->
    <div v-if="navType === '2'" class="nav-list">
      <div
        v-for="(item, index) in navList"
        :key="index"
        class="nav-item-item text-overFlow-1"
      >
        <span :style="{ color: showData.fontColor }">{{ item.title }}</span>
      </div>
    </div>
    <!--图片 / 图文：横向滚动-->
    <div
      v-else-if="displayMode === 'scroll'"
      class="nav-item-image scroll-view"
    >
      <div
        v-for="(item, index) in navList"
        :key="index"
        class="nav-item-image-li"
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
            v-if="navType === '3'"
          >
            <span v-if="item.title">{{ item.title }}</span>
          </p>
        </div>
      </div>
    </div>
    <!--图片 / 图文：分页滑动-->
    <div
      v-else-if="displayMode === 'pager'"
      class="nav-pager"
      :style="{ height: `${pagerHeight}px` }"
    >
      <div class="nav-pager-track" @scroll="onPagerScroll">
        <div
          v-for="(page, pageIndex) in pages"
          :key="pageIndex"
          class="nav-pager-page"
          :class="showNum === 5 ? 'is-cell5' : 'is-cell4'"
        >
          <div
            v-for="(item, index) in page"
            :key="pageIndex * pageSize + index"
            class="nav-item-image-li"
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
                v-if="navType === '3'"
              >
                <span v-if="item.title">{{ item.title }}</span>
              </p>
            </div>
          </div>
        </div>
      </div>
      <div v-if="showIndicator" class="nav-pager-dots">
        <span
          v-for="index in pages.length"
          :key="index"
          class="nav-pager-dot"
          :style="{
            backgroundColor:
              index - 1 === activePage ? indicatorActiveColor : indicatorColor,
          }"
        ></span>
      </div>
    </div>
    <!--图片 / 图文：平铺-->
    <div v-else class="nav-item-image">
      <div
        v-for="(item, index) in navList"
        :key="index"
        class="nav-item-image-li"
        :class="{
          'is-nav-cell4': showNum === 4,
          'is-nav-cell5': showNum === 5,
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
            v-if="navType === '3'"
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
      color: #666;
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
        color: #fff;
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

  // 分页滑动
  .nav-pager {
    position: relative;
    width: 100%;

    &-track {
      display: flex;
      height: 100%;
      overflow-x: auto;
      overscroll-behavior-x: contain;
      scroll-snap-type: x mandatory;
      scrollbar-width: none;

      &::-webkit-scrollbar {
        display: none;
      }
    }

    &-page {
      display: flex;
      flex: 0 0 100%;
      flex-wrap: wrap;
      align-content: flex-start;
      width: 100%;
      scroll-snap-align: start;

      .nav-item-image-li {
        padding-bottom: 6px;
        margin-top: 2px;

        .nav-item-content {
          display: flex;
          flex-direction: column;
          align-items: center;
          justify-content: center;
        }

        p {
          margin-top: 5px;
          font-size: 12px;
          color: #fff;
          text-align: center;
        }
      }

      &.is-cell4 .nav-item-image-li {
        width: 25%;
      }

      &.is-cell5 .nav-item-image-li {
        width: 20%;
      }
    }

    &-dots {
      position: absolute;
      bottom: 0;
      left: 0;
      display: flex;
      gap: 5px;
      justify-content: center;
      width: 100%;
      pointer-events: none;
    }

    &-dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      transition: all 0.2s;
    }
  }
}
</style>

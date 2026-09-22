<script setup lang="ts">
import { computed, shallowRef } from 'vue'

import { followDecorationLink } from '@/components/diy/link-resolver'
import { useDiyStyle } from '@/composables/useDiyStyle'

const props = defineProps({
  showData: {
    type: Object,
    default: () => ({}),
  },
})

const current = shallowRef(0)

const baseStyle = useDiyStyle(computed(() => props.showData.commonStyle))
const dynamicStyles = computed(() => {
  return {
    ...baseStyle.value,
    color: props.showData.fontColor,
  }
})
const dynamicTabNavStyles = useDiyStyle(
  computed(() => props.showData.commonTabNavStyle),
)
const imageStyle = computed(() => ({
  width: `${props.showData.imgSize}px`,
  height: `${props.showData.imgSize}px`,
  borderRadius: `${props.showData.imgRadius}px`,
}))

// 导航类型：1 图片 / 2 文字 / 3 图文
const navType = computed(() => String(props.showData.type ?? '3'))
const navList = computed<any[]>(() => props.showData.navList || [])
// 一行显示个数：仅支持 4 / 5
const showNum = computed(() =>
  Number(props.showData.showNum) === 5 ? 5 : 4,
)

// 显示方式归一化：旧数据只有 scrollShow 字段，由它推导 grid/scroll
const displayMode = computed<'grid' | 'pager' | 'scroll'>(() => {
  const mode = props.showData.displayMode
  if (mode === 'grid' || mode === 'scroll' || mode === 'pager')
    return mode
  return props.showData.scrollShow ? 'scroll' : 'grid'
})

// ---- 分页模式 ----
const pageRows = computed(() => (Number(props.showData.pageRows) === 2 ? 2 : 3))
const pageSize = computed(() => showNum.value * pageRows.value)
const pages = computed(() => {
  const list = navList.value
  const grouped: any[][] = []
  for (let i = 0; i < list.length; i += pageSize.value)
    grouped.push(list.slice(i, i + pageSize.value))
  return grouped.length ? grouped : [[]]
})
const indicatorDots = computed(() => props.showData.indicatorDots !== false)
const indicatorColor = computed(
  () => props.showData.indicatorColor || 'rgba(0, 0, 0, 0.2)',
)
const indicatorActiveColor = computed(
  () => props.showData.indicatorActiveColor || '#1989fa',
)

// 单行高度（px）= 图片高度 + 图文模式下文字块高度（上边距约 5px + 行高约 17px）
const rowHeight = computed(() => {
  const imgSize = Number(props.showData.imgSize) || 40
  return imgSize + (navType.value === '3' ? 22 : 0)
})
// 每行底部间距 6px；分页指示点预留 16px
const pagerHeight = computed(
  () =>
    pageRows.value * (rowHeight.value + 6)
    + (indicatorDots.value && pages.value.length > 1 ? 16 : 0),
)
</script>

<template>
  <view :style="dynamicStyles">
    <!-- 文字导航 -->
    <wd-tabs v-if="navType === '2'" v-model="current">
      <block v-for="item in navList" :key="item">
        <wd-tab :title="item.title" :name="item.title" />
      </block>
    </wd-tabs>

    <!-- 图片 / 图文导航 -->
    <view v-else class="hx-grid">
      <!-- 横向滚动 -->
      <scroll-view
        v-if="displayMode === 'scroll'"
        scroll-x
        class="hx-grid-scroll"
        :enhanced="true"
        :show-scrollbar="false"
      >
        <view class="scroll-content">
          <view
            v-for="(item, index) in navList"
            :key="index"
            class="scroll-item"
            :style="dynamicTabNavStyles"
            @click="followDecorationLink(item.link)"
          >
            <view
              class="grid-item-inner"
              :class="[showData.showType === '1' ? 'row-direction' : 'column-direction']"
            >
              <image :src="item.url" :style="imageStyle" />
              <view v-if="navType === '3' && item.title" class="grid-text">
                {{ item.title }}
              </view>
            </view>
          </view>
        </view>
      </scroll-view>

      <!-- 分页滑动：每页 showNum × pageRows 个，左右滑动翻页 + 指示点 -->
      <swiper
        v-else-if="displayMode === 'pager'"
        class="hx-grid-pager"
        :indicator-dots="indicatorDots && pages.length > 1"
        :indicator-color="indicatorColor"
        :indicator-active-color="indicatorActiveColor"
        :style="{ height: `${pagerHeight}px` }"
      >
        <swiper-item
          v-for="(page, pageIndex) in pages"
          :key="pageIndex"
        >
          <view class="hx-grid-page">
            <view
              v-for="(item, index) in page"
              :key="pageIndex * pageSize + index"
              class="hx-grid-item"
              :style="{ width: `${100 / showNum}%` }"
              @click="followDecorationLink(item.link)"
            >
              <view
                class="grid-item-inner column-direction"
                :style="dynamicTabNavStyles"
              >
                <image :src="item.url" :style="imageStyle" />
                <view v-if="navType === '3' && item.title" class="grid-text">
                  {{ item.title }}
                </view>
              </view>
            </view>
          </view>
        </swiper-item>
      </swiper>

      <!-- 固定宫格平铺 -->
      <view v-else class="hx-grid-list">
        <view
          v-for="(item, index) in navList"
          :key="index"
          class="hx-grid-item"
          :style="{ width: `${100 / showNum}%` }"
          @click="followDecorationLink(item.link)"
        >
          <view
            :style="dynamicTabNavStyles"
            class="grid-item-inner"
            :class="[showData.showType === '1' ? 'row-direction' : 'column-direction']"
          >
            <image :src="item.url" :style="imageStyle" />
            <view v-if="navType === '3' && item.title" class="grid-text">
              {{ item.title }}
            </view>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.hx-grid {
  width: 100%;

  .grid-item-inner {
    display: flex;
    align-items: center;
    justify-content: center;

    &.row-direction {
      flex-direction: row;
      gap: 8rpx;
    }

    &.column-direction {
      flex-direction: column;
    }

    image {
      display: block;
    }

    .grid-text {
      font-size: 12px;
      margin-top: 10rpx;
      text-align: center;
    }
  }

  &-scroll {
    width: 100%;
    overflow-x: auto;
    white-space: nowrap;

    .scroll-content {
      display: flex;
      white-space: nowrap;
    }

    .scroll-item {
      flex-shrink: 0;
      display: inline-flex;
      padding: 0 8rpx;

      .grid-item-inner {
        display: flex;
        align-items: center;
        justify-content: center;

        &.row-direction {
          flex-direction: row;
          gap: 8rpx;
        }

        &.column-direction {
          flex-direction: column;
        }

        image {
          display: block;
        }

        .grid-text {
          font-size: 12px;
          margin-top: 10rpx;
          text-align: center;
        }
      }
    }
  }

  &-pager {
    width: 100%;
  }

  .hx-grid-page {
    display: flex;
    flex-wrap: wrap;
    align-content: flex-start;
    height: 100%;

    .hx-grid-item {
      display: flex;
      align-items: center;
      justify-content: center;
      padding-bottom: 6px;
    }
  }

  &-list {
    display: flex;
    flex-wrap: wrap;

    .hx-grid-item {
      display: flex;
      align-items: center;
      justify-content: center;

      .grid-item-inner {
        display: flex;
        align-items: center;
        justify-content: center;

        &.row-direction {
          flex-direction: row;
          gap: 8rpx;
        }

        &.column-direction {
          flex-direction: column;
        }

        image {
          display: block;
        }

        .grid-text {
          font-size: 12px;
          margin-top: 10rpx;
          text-align: center;
        }
      }
    }
  }
}
</style>

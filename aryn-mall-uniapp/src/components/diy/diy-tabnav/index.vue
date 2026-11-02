<script setup lang="ts">
import { computed, getCurrentInstance, nextTick, onMounted, shallowRef, watch } from 'vue'

import { followDecorationLink } from '@/components/diy/link-resolver'
import { useDiyStyle } from '@/composables/useDiyStyle'

const props = defineProps({
  showData: {
    type: Object,
    default: () => ({}),
  },
})

const current = shallowRef(0)
// 滑动中的实时高度（px）：仅在「非小程序端」使用；小程序端高度由 WXS 在视图层直接写
const liveHeight = shallowRef<number | null>(null)
// 分页器宽度（px）：把 transition 事件的 dx 换算成翻页进度（小程序端经 data-width 下发 WXS）
const pagerWidth = shallowRef(0)
const instance = getCurrentInstance()

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

// 单行高度（px）= 图片高度 + 图文模式下文字块高度（上边距 6px + 13px 字号行高约 18px）
const rowHeight = computed(() => {
  const imgSize = Number(props.showData.imgSize) || 56
  return imgSize + (navType.value === '3' ? 24 : 0)
})
// 高度自适应：按「当前页」实际行数收缩——铺满 pageRows 行占满，铺不满自动缩小
// （swiper 各页等高，需按行数给容器高度）。高度只在滑停后结算页码时派生，
// 手势期间的实际高度由 WXS（小程序端）/ JS 插值（H5/App）按照翻页位移驱动。
function rowsOfPage(pageIndex: number): number {
  const total = navList.value.length
  if (!total)
    return pageRows.value
  const page = pages.value[Math.min(pageIndex, pages.value.length - 1)] || []
  return Math.min(pageRows.value, Math.ceil(page.length / showNum.value))
}
// 每行底部间距 8px；分页指示点预留 16px
function heightForRows(rows: number): number {
  return rows * (rowHeight.value + 8)
    + (indicatorDots.value && pages.value.length > 1 ? 16 : 0)
}
const actualRows = computed(() => rowsOfPage(current.value))
const pagerHeight = computed(() => heightForRows(actualRows.value))
// 每页高度（px），经 data-heights 下发给小程序端 WXS：手势期间在视图层按 dx 线性插值
const pagerHeights = computed(() =>
  pages.value.map((_page, pageIndex) => heightForRows(rowsOfPage(pageIndex))),
)
const pagerHeightsAttr = computed(() => pagerHeights.value.join(','))

/** 量取分页器宽度：dx → 翻页进度的换算基准（WXS / JS 两条通道共用同一口径） */
function measurePagerWidth() {
  if (displayMode.value !== 'pager')
    return
  nextTick(() => {
    uni.createSelectorQuery()
      .in(instance?.proxy ?? instance)
      .select('.hx-grid-pager')
      .boundingClientRect((raw) => {
        const rect = (Array.isArray(raw) ? raw[0] : raw) as { width?: number } | null
        if (rect?.width)
          pagerWidth.value = Math.round(rect.width)
      })
      .exec()
  })
}
onMounted(measurePagerWidth)
// 装修数据异步到达或展示模式切换后 swiper 才挂载，需要补测一次
watch([displayMode, () => pages.value.length], measurePagerWidth)

// 非小程序端的高度提交节流（约 30 次/秒）：配合 40ms 线性 CSS 过渡，高度在两次
// 提交之间继续连续运动；既避免逐帧重渲染整个组件，也不会出现台阶式跳动。
const LIVE_HEIGHT_INTERVAL_MS = 33
let lastAppliedAt = 0

/**
 * 非小程序端（H5 / App）的实时高度插值：让收缩/展开与翻页位移同步。
 *
 * transition 事件在手指拖动与惯性动画期间持续触发，dx 为内容轨实时位移
 * （0 ~ ±swiper 宽）。进度 = |dx| / swiper 宽，高度在前后两页行数对应高度之间
 * 线性取值，滑到哪页高度就落到哪页；再用 40ms 线性过渡把 30Hz 提交补间成连续运动。
 *
 * 小程序端不走这里：模板里 @transition 绑的是 `tabnavWxs.onTransition`，微信会把它
 * 编译成 WXS 视图层事件（bindtransition="{{tabnavWxs.onTransition}}"），高度由 WXS
 * 直接 setStyle —— 逐帧 setData 会带着整页楼层重排，真机表现为滑动掉帧、收缩滞后。
 */
function onLivePagerTransition(e: any) {
  const dx = Number(e?.detail?.dx)
  if (!Number.isFinite(dx) || pagerWidth.value <= 0)
    return
  // 位移归零（手势取消回弹到原点）：结束插值，高度交还给 pagerHeight
  if (Math.abs(dx) < 1) {
    if (liveHeight.value !== null)
      liveHeight.value = null
    lastAppliedAt = 0
    return
  }
  const target = current.value + (dx >= 0 ? 1 : -1)
  if (target < 0 || target >= pages.value.length)
    return
  const from = heightForRows(rowsOfPage(current.value))
  const to = heightForRows(rowsOfPage(target))
  if (from === to) {
    if (liveHeight.value !== null)
      liveHeight.value = null
    return
  }
  const progress = Math.min(Math.abs(dx) / pagerWidth.value, 1)
  const next = Math.round(from + (to - from) * progress)
  if (next === liveHeight.value)
    return
  const now = Date.now()
  if (now - lastAppliedAt < LIVE_HEIGHT_INTERVAL_MS)
    return
  liveHeight.value = next
  lastAppliedAt = now
}

/**
 * 非小程序端的 transition 入口（模板统一写 @transition="tabnavWxs.onTransition"）：
 * 小程序端该表达式被编译成 WXS 事件绑定、不执行到这里；H5/App 端回落到 JS 插值。
 */
const tabnavWxs = { onTransition: onLivePagerTransition }

/**
 * 滑停后结算页码，并把高度收口到目标页行数。
 *
 * 小程序端高度已由 WXS 逐帧跟随到目标页高度，这里只同步 `current`
 * （供中途重渲染时 :style 取到正确高度，并给下一次手势提供 data-current）；
 * 非小程序端把 liveHeight 交还给 pagerHeight，由 240ms 弹性过渡收尾。
 */
function onPagerAnimationFinish(e: any) {
  const next = Number(e?.detail?.current)
  if (Number.isFinite(next) && next !== current.value)
    current.value = next
  liveHeight.value = null
  lastAppliedAt = 0
}

/**
 * 小程序端（微信）分页高度跟随：视图层 WXS，基础库 2.4.4+。见下方 script module 块。
 */
</script>

<script module="tabnavWxs" lang="wxs">
/* eslint-disable */
// 小程序端金刚区分页高度跟随（视图层 WXS，不支持类型标注；@ts-ignore 让 vue-tsc 跳过，
// eslint-disable 让 ESLint 跳过 —— WXS 是 ES5 方言，不能用 let/const 与箭头函数）。
//
// 为什么不用 JS 逐帧写高度：swiper 的 transition 事件在小程序端每秒可达 60~120 次，
// 每次经 setData 改高度都会让金刚区下方所有楼层重新排版，真机表现为整页掉帧、
// 收缩明显滞后于手指。WXS 事件在视图层执行，setStyle 不经过 setData，因此可以
// 逐帧改高度而不碰逻辑层 —— 收缩/展开与翻页位移严格同步。
//
// 数据全部来自模板下发的 data-*：heights 每页高度、current 当前页、width 分页器宽度；
// 行数/行高推导只在 JS 端 heightForRows 一处，WXS 只做线性插值，避免两端公式漂移。
// @ts-ignore WXS 无类型标注（仅 vue-tsc 需要）
function toInt(value) {
  var n = parseInt(value, 10)
  if (!n) {
    return 0
  }
  return n
}

// @ts-ignore WXS 无类型标注（仅 vue-tsc 需要）
function applyHeight(ownerInstance, height) {
  var pager = ownerInstance.selectComponent('.hx-grid-pager')
  if (pager) { pager.setStyle({ height: Math.round(height) + 'px' }) }
}

// @ts-ignore WXS 无类型标注（仅 vue-tsc 需要）
function onTransition(event, ownerInstance) {
  var dataset = event.currentTarget.dataset
  var dx = event.detail ? event.detail.dx : 0
  // 注意：WXS 基础库没有 String 构造器（也没有 JSON），不要用 String(...) 包一层
  var heights = (dataset.heights || '').split(',')
  var width = toInt(dataset.width)
  var cur = toInt(dataset.current)
  if (!width || cur >= heights.length) {
    return
  }
  var target = dx >= 0 ? cur + 1 : cur - 1
  if (target < 0 || target >= heights.length) { target = cur }
  var from = toInt(heights[cur])
  var to = toInt(heights[target])
  var progress = Math.abs(dx) / width
  if (progress > 1) { progress = 1 }
  applyHeight(ownerInstance, from + (to - from) * progress)
}

module.exports = { onTransition: onTransition }
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

      <!-- 分页滑动：每页 showNum × pageRows 个，左右滑动翻页 + 指示点。
           高度跟随翻页位移逐帧收缩/展开：小程序端由 WXS（视图层 setStyle）驱动，
           不经过 setData；H5/App 端由 tabnavWxs 这个 JS 垫片回落成本地插值。 -->
      <swiper
        v-else-if="displayMode === 'pager'"
        class="hx-grid-pager"
        :class="{ 'hx-grid-pager--live': liveHeight !== null }"
        :indicator-dots="indicatorDots && pages.length > 1"
        :indicator-color="indicatorColor"
        :indicator-active-color="indicatorActiveColor"
        :duration="300"
        :style="{ height: `${liveHeight ?? pagerHeight}px` }"
        :data-heights="pagerHeightsAttr"
        :data-current="current"
        :data-width="pagerWidth"
        @transition="tabnavWxs.onTransition"
        @animationfinish="onPagerAnimationFinish"
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
      font-size: 13px;
      margin-top: 12rpx;
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
          font-size: 13px;
          margin-top: 12rpx;
          text-align: center;
        }
      }
    }
  }

  &-pager {
    width: 100%;
    // 高度随当前页行数收缩/展开。小程序端由 WXS 逐帧写高度（视图层 setStyle），
    // 再加 CSS 过渡会变成「追赶」拖影，故小程序端不启用过渡；
    // H5/App 端高度走 JS 提交（30Hz）+ 此处过渡补间，滑停后 240ms 弹性收尾。
    /* #ifndef MP-WEIXIN */
    transition: height 240ms cubic-bezier(0.34, 1.35, 0.64, 1);
    /* #endif */

    // 非小程序端手势进行中：40ms 线性过渡把 30Hz 高度提交补成连续运动
    &--live {
      /* #ifndef MP-WEIXIN */
      transition: height 40ms linear;
      /* #endif */
    }
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
      padding-bottom: 8px;
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
          font-size: 13px;
          margin-top: 12rpx;
          text-align: center;
        }
      }
    }
  }
}
</style>

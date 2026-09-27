<script setup lang="ts">
import { computed } from 'vue'
import { followDecorationLink } from '@/components/diy/link-resolver'
import { shouldShowOriginalPrice } from '@/components/diy/price-display'
import { loadGoodsGroup } from '@/components/diy/retail-data'
import { retailCommonStyle } from '@/components/diy/retail-types'
import RetailState from '@/components/diy/retail-state.vue'
import { useDiyStyle } from '@/composables/useDiyStyle'
import { useRetailData } from '@/composables/useRetailData'

interface GoodsScrollProps {
  commonStyle?: any
  title?: string
  subtitle?: string
  // scroll：手指左右滑动；pager：自动轮播
  displayMode?: 'pager' | 'scroll'
  // 一屏展示几个商品（卡片宽度按此均分）
  perView?: 3 | 4
  // 自动轮播间隔（毫秒）
  interval?: number
  count?: number
  emptyStrategy?: 'hide' | 'placeholder'
  invalidStrategy?: 'hide' | 'placeholder'
  dataSource?: {
    mode?: 'manual' | 'rule'
    targetIds?: string[]
    categoryId?: string
    sort?: string
  }
  showSales?: boolean
  /** 是否展示划线原价（仅当商品原价高于售价时可见） */
  showOriginalPrice?: boolean
}

const props = withDefaults(defineProps<{ showData?: GoodsScrollProps }>(), {
  showData: () => ({}),
})

const showData = computed(() => ({
  title: props.showData.title || '省心午餐',
  subtitle: props.showData.subtitle || '',
  displayMode: props.showData.displayMode === 'pager' ? 'pager' : 'scroll',
  perView: Number(props.showData.perView) === 4 ? 4 : 3,
  interval: Number(props.showData.interval) || 3000,
  count: Number(props.showData.count) || 8,
  emptyStrategy: props.showData.emptyStrategy || 'placeholder',
  invalidStrategy: props.showData.invalidStrategy || 'hide',
  // 展开后再兜底 mode，保证 mode 一定存在（服务端数据缺失时回落到按销量排行）
  dataSource: {
    ...(props.showData.dataSource || {}),
    mode: props.showData.dataSource?.mode || 'rule',
  },
  // 划线原价的运营开关：缺省视为关闭（存量装修数据里没有该字段）
  showOriginalPrice: Boolean(props.showData.showOriginalPrice),
  showSales: props.showData.showSales !== false,
  commonStyle: props.showData.commonStyle || retailCommonStyle,
}))

const dynamicStyles = useDiyStyle(computed(() => showData.value.commonStyle))
// 与商品排行/活动等零售组件一致：加载中出骨架屏，空态与失败态按后台选的兜底策略决定是否渲染
const { items: goodsList, shouldRender, status } = useRetailData(showData, loadGoodsGroup)

// 横向留白与卡片间距（rpx），对齐原型设计令牌的 12px 页边距与 10px 卡间距。
// 留白放在内层而不是根节点：根节点的 padding 会被装修的 commonStyle 以内联样式
// 覆盖成 0，挂根上会导致卡片直接顶到屏幕边缘。
const PAGE_PADDING = 24
const CARD_GAP = 20

// 卡片宽度按 rpx 定死：屏宽在 rpx 下恒为 750，减去左右留白与卡片间距后按一屏个数均分。
// 向下取整，保证一屏 perView 张卡片连间距不会超出屏宽（swiper 里溢出会错位）。
const cardWidthRpx = computed(() => {
  const perView = showData.value.perView
  return Math.floor((750 - PAGE_PADDING * 2 - CARD_GAP * (perView - 1)) / perView)
})

// 卡片是 border-box，1rpx 上下左右边框都会占位，尺寸计算必须计入，
// 否则方图会差 2rpx、卡片底部被裁 2rpx
const CARD_BORDER_RPX = 2

/**
 * 图片高度：与卡片内容区同宽的方图。
 *
 * 这里必须显式给 rpx 高度，不能只写 CSS `aspect-ratio`——小程序 `<image>` 的
 * 基座样式自带显式 height，`aspect-ratio` 只在 height 为 auto 时才参与计算，
 * 结果图片会按默认高度竖着撑开，把下面的名称与价格挤出可视区。
 */
const imageHeightRpx = computed(() => cardWidthRpx.value - CARD_BORDER_RPX)

// 文字区高度（rpx）：上下内边距 24 + 两行名称 72 + 价格行间距 8 与行高 46，
// 加上可选的销量行（行高 28 + 间距 6）。取值与下方 .gs-name/.gs-bottom/.gs-sales
// 的显式行高一一对应，任一处改动都要同步这里，否则 swiper 会裁掉底部。
const cardBodyHeightRpx = computed(
  () => 24 + 72 + 8 + 46 + (showData.value.showSales ? 34 : 0),
)

const cardHeightRpx = computed(
  () => imageHeightRpx.value + cardBodyHeightRpx.value + CARD_BORDER_RPX,
)

const cardStyle = computed(() => ({
  height: `${Math.ceil(cardHeightRpx.value)}rpx`,
  width: `${cardWidthRpx.value}rpx`,
}))
const imageStyle = computed(() => ({ height: `${imageHeightRpx.value}rpx` }))

// <swiper> 不随内容自适应高度，必须显式绑定，否则卡片底部会被裁掉
const swiperHeight = computed(() => `${Math.ceil(cardHeightRpx.value + 2)}rpx`)

// 自动轮播：按每页 perView 个切片
const pages = computed(() => {
  const size = showData.value.perView
  const list = goodsList.value
  const out: any[][] = []
  for (let i = 0; i < list.length; i += size) out.push(list.slice(i, i + size))
  return out
})

function openGoods(id: string) {
  followDecorationLink({ params: {}, path: '', targetId: id, type: 'goods' })
}
</script>

<template>
  <view v-if="shouldRender" class="goods-scroll" :style="dynamicStyles">
    <!-- 标题栏：色条 + 标题，副标题做成胶囊标签 -->
    <view class="gs-head">
      <view class="gs-bar" />
      <text class="gs-title">{{ showData.title }}</text>
      <text v-if="showData.subtitle" class="gs-subtitle">{{ showData.subtitle }}</text>
    </view>

    <RetailState v-if="status !== 'ready'" :status="status" />

    <!-- 手动横滑模式 -->
    <scroll-view
      v-else-if="showData.displayMode === 'scroll'"
      class="gs-scroll"
      scroll-x
      :scroll-with-animation="true"
    >
      <view class="gs-track" :class="{ 'gs-track--4': showData.perView === 4 }">
        <view
          v-for="item in goodsList"
          :key="item.id"
          class="gs-card"
          :style="cardStyle"
          @click="openGoods(item.id)"
        >
          <image
            v-if="item.imageUrl"
            class="gs-img"
            :style="imageStyle"
            :src="resolveImageSrc(item.imageUrl)"
            mode="aspectFill"
            lazy-load
          />
          <view v-else class="gs-img gs-img--empty" :style="imageStyle">商品</view>
          <view class="gs-body">
            <view class="gs-name">{{ item.name }}</view>
            <view class="gs-bottom">
              <text class="gs-price">
                <text class="gs-price-symbol">¥</text>{{ item.price.toFixed(2) }}
                <!--
                  划线原价嵌在 .gs-price 内，跟随该行一起省略号截断：
                  .gs-bottom 是定高 46rpx 的价格行，卡片总高由 cardBodyHeightRpx 算出，
                  另起一行会把卡片撑破（swiper 会裁掉底部）。
                -->
                <text
                  v-if="showData.showOriginalPrice && shouldShowOriginalPrice(item.price, item.originalPrice)"
                  class="gs-price-original"
                >¥{{ item.originalPrice.toFixed(2) }}</text>
              </text>
              <view class="gs-btn">抢</view>
            </view>
            <text v-if="showData.showSales" class="gs-sales">已售 {{ item.sales }}</text>
          </view>
        </view>
      </view>
    </scroll-view>

    <!-- 自动轮播模式：每页 perView 个商品 -->
    <swiper
      v-else
      class="gs-swiper"
      circular
      :autoplay="true"
      :interval="showData.interval"
      :duration="400"
      :indicator-dots="false"
      :style="{ height: swiperHeight }"
    >
      <swiper-item v-for="(page, idx) in pages" :key="idx">
        <view class="gs-page" :class="{ 'gs-page--4': showData.perView === 4 }">
          <view
            v-for="item in page"
            :key="item.id"
            class="gs-card"
            :style="cardStyle"
            @click="openGoods(item.id)"
          >
            <image
              v-if="item.imageUrl"
              class="gs-img"
              :style="imageStyle"
              :src="resolveImageSrc(item.imageUrl)"
              mode="aspectFill"
              lazy-load
            />
            <view v-else class="gs-img gs-img--empty" :style="imageStyle">商品</view>
            <view class="gs-body">
              <view class="gs-name">{{ item.name }}</view>
              <view class="gs-bottom">
                <text class="gs-price">
                  <text class="gs-price-symbol">¥</text>{{ item.price.toFixed(2) }}
                  <!-- 划线原价嵌在 .gs-price 内，跟随该行省略号截断（卡片高度已被算死） -->
                  <text
                    v-if="showData.showOriginalPrice && shouldShowOriginalPrice(item.price, item.originalPrice)"
                    class="gs-price-original"
                  >¥{{ item.originalPrice.toFixed(2) }}</text>
                </text>
                <view class="gs-btn">抢</view>
              </view>
              <text v-if="showData.showSales" class="gs-sales">已售 {{ item.sales }}</text>
            </view>
          </view>
        </view>
      </swiper-item>
    </swiper>
  </view>
</template>

<style lang="scss" scoped>
.gs-head {
  display: flex;
  align-items: center;
  padding: 8rpx 24rpx 0;
  margin-bottom: 16rpx;
}

.gs-bar {
  width: 6rpx;
  height: 28rpx;
  margin-right: 10rpx;
  border-radius: 3rpx;
  background: linear-gradient(180deg, #0b63e5, #17a2c7);
}

.gs-title {
  color: #14202e;
  font-size: 32rpx;
  font-weight: 700;
}

.gs-subtitle {
  margin-left: 12rpx;
  padding: 2rpx 12rpx;
  border-radius: 999rpx;
  background: #fff1f0;
  color: #e5484d;
  font-size: 20rpx;
}

.gs-scroll {
  width: 100%;
  margin-bottom: 16rpx;
}

// 轨道 nowrap 保证小程序里能横向滚动（与 diy-tabnav 的 .scroll-content 一致）；
// 卡片内文案各自显式复位 white-space，否则商品名会不换行、被硬切成半截。
.gs-track {
  display: flex;
  padding: 0 24rpx;
  gap: 20rpx;
  white-space: nowrap;
}

.gs-page {
  display: flex;
  padding: 0 24rpx;
  gap: 20rpx;
}

.gs-swiper {
  margin-bottom: 16rpx;
}

.gs-card {
  overflow: hidden;
  box-sizing: border-box;
  flex: 0 0 auto;
  border: 1rpx solid #eef0f3;
  border-radius: 16rpx;
  background: #fff;
  box-shadow: 0 2rpx 12rpx rgba(11, 99, 229, 0.06);
}

.gs-img {
  display: block;
  width: 100%;
  background: #f3f4f6;
}

.gs-img--empty {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #a8abb2;
  font-size: 24rpx;
}

.gs-body {
  padding: 12rpx;
}

.gs-name {
  display: -webkit-box;
  overflow: hidden;
  height: 72rpx;
  color: #303133;
  font-size: 26rpx;
  line-height: 36rpx;
  /* 复位横滑轨道的 nowrap，否则商品名不换行会被硬切 */
  white-space: normal;
  word-break: normal;
  overflow-wrap: break-word;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.gs-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 46rpx;
  margin-top: 8rpx;
  gap: 8rpx;
}

/* min-width:0 + 省略号：一屏 4 个时价格与「抢」按钮会贴到一起，
   超长价格（如 ¥159.90）必须能收缩，否则会把按钮挤出卡片 */
.gs-price {
  overflow: hidden;
  min-width: 0;
  color: #e5484d;
  font-size: 30rpx;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.gs-price-symbol {
  font-size: 22rpx;
}

/* 划线原价：灰字小一号，仅作价格锚点，不与售价抢视觉层级 */
.gs-price-original {
  margin-left: 6rpx;
  color: #999;
  font-size: 20rpx;
  font-weight: normal;
  text-decoration: line-through;
}

.gs-btn {
  flex: 0 0 auto;
  padding: 4rpx 20rpx;
  border-radius: 999rpx;
  background: linear-gradient(90deg, #ff7a45, #ff4d4f);
  box-shadow: 0 2rpx 8rpx rgba(255, 77, 79, 0.28);
  color: #fff;
  font-size: 22rpx;
  line-height: 34rpx;
}

/* 一屏 4 个时卡片内容区仅约 66px 宽，须同时收窄价格字号、按钮留白与间距，
   才能让「¥13.90 + 抢」这类常规价格完整放下、不触发省略号 */
.gs-track--4 .gs-price,
.gs-page--4 .gs-price {
  font-size: 24rpx;
}

.gs-track--4 .gs-price-symbol,
.gs-page--4 .gs-price-symbol {
  font-size: 18rpx;
}

/* 一屏 4 个时内容区仅约 66px 宽，原价也要跟着收窄才放得下 */
.gs-track--4 .gs-price-original,
.gs-page--4 .gs-price-original {
  margin-left: 4rpx;
  font-size: 18rpx;
}

.gs-track--4 .gs-btn,
.gs-page--4 .gs-btn {
  padding: 2rpx 10rpx;
  font-size: 20rpx;
}

.gs-track--4 .gs-bottom,
.gs-page--4 .gs-bottom {
  gap: 4rpx;
}

.gs-sales {
  display: block;
  height: 28rpx;
  margin-top: 6rpx;
  color: #909399;
  font-size: 20rpx;
  line-height: 28rpx;
}
</style>

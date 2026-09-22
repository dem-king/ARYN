<script setup lang="ts">
import type { GoodsWaterfallProps } from '@/components/diy/retail-types'

import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import {
  loadGoodsWaterfall,
  loadGoodsWaterfallPage,
} from '@/components/diy/retail-data'
import QuickCartButton from '@/components/quick-cart-button/index.vue'
import { useDiyStyle } from '@/composables/useDiyStyle'

const props = defineProps({
  showData: {
    type: Object,
    default: () => ({}),
  },
})

const componentProps = computed(() => props.showData as unknown as GoodsWaterfallProps)

// 手选模式（manual）只渲染运营勾选的固定商品；自动模式由 z-paging 分页追加，实现无限滚动
const isManual = computed(() => componentProps.value.dataSource?.mode === 'manual')

const goodsList = ref<any[]>([])
const pagingRef = ref()
const dynamicStyles = useDiyStyle(computed(() => componentProps.value.commonStyle))

// 每页大小：后台若配置了 count 则作为每页条数，否则默认 10
const pageSize = computed(() => Math.max(1, Number(componentProps.value.count) || 10))

async function queryList(pageNo: number, pageSize: number) {
  try {
    if (isManual.value) {
      // 手选：一次性取全量，回传 total=条数，z-paging 据此判定无下一页
      const items = await loadGoodsWaterfall(componentProps.value)
      pagingRef.value?.complete(items, items.length)
      return
    }
    const { list, total } = await loadGoodsWaterfallPage(
      componentProps.value,
      pageNo,
      pageSize,
    )
    pagingRef.value?.complete(list, total)
  }
  catch {
    // 当前页加载失败：置空并提示，不阻断整页；z-paging 会停在当前列表
    pagingRef.value?.complete([])
  }
}

function openGoods(id: string) {
  uni.navigateTo({ url: `/sub-pages/product/goods-detail/index?id=${id}` })
}

// 首页页面滚动到底时自动加载下一页（z-paging use-page-scroll 在嵌套装修组件里不一定自动监听，这里兜底）
function onHomeReachBottom() {
  pagingRef.value?.pageReachBottom()
}
onMounted(() => {
  uni.$on('home-reach-bottom', onHomeReachBottom)
})
onBeforeUnmount(() => {
  uni.$off('home-reach-bottom', onHomeReachBottom)
})
</script>

<template>
  <view class="diy-waterfall" :style="dynamicStyles">
    <view v-if="componentProps.title" class="waterfall-title">
      {{ componentProps.title }}
    </view>
    <!--
      use-page-scroll：复用首页页面滚动，滚到底部附近自动加载下一页（等同小象超市“猜你喜欢”）。
      refresher-enabled=false：首页整体已有下拉刷新，避免与 z-paging 下拉冲突。
    -->
    <z-paging
      ref="pagingRef"
      v-model="goodsList"
      use-page-scroll
      :refresher-enabled="false"
      :default-page-size="pageSize"
      @query="queryList"
    >
      <view class="waterfall-columns" :style="{ columnCount: componentProps.columns || 2 }">
        <view
          v-for="item in goodsList"
          :key="item.id"
          class="waterfall-card"
          @click="openGoods(item.id)"
        >
          <image class="waterfall-pic" :src="item.imageUrl" mode="widthFix" />
          <view class="waterfall-name">{{ item.name }}</view>
          <view class="waterfall-meta">
            <view class="waterfall-meta-text">
              <text v-if="componentProps.showPrice !== false" class="waterfall-price">
                ¥{{ item.price }}
              </text>
              <text v-if="componentProps.showSales" class="waterfall-sales">
                已售{{ item.sales }}
              </text>
            </view>
            <!-- 快捷加购：单规格直接加购，多规格唤起规格弹层 -->
            <quick-cart-button v-if="item.stock > 0" :spu-id="item.id" size="40rpx" />
          </view>
        </view>
      </view>
      <template #empty>
        <view class="waterfall-empty">暂无商品</view>
      </template>
    </z-paging>
  </view>
</template>

<style lang="scss" scoped>
.diy-waterfall {
  padding: 20rpx;
}

.waterfall-title {
  margin-bottom: 16rpx;
  font-size: 32rpx;
  font-weight: 600;
}

.waterfall-columns {
  column-gap: 16rpx;
}

.waterfall-card {
  display: block;
  margin-bottom: 16rpx;
  overflow: hidden;
  background: #fff;
  border-radius: 12rpx;
  break-inside: avoid;
}

.waterfall-pic {
  width: 100%;
}

.waterfall-name {
  padding: 12rpx;
  font-size: 26rpx;
  display: -webkit-box;
  overflow: hidden;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.waterfall-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8rpx;
  padding: 0 12rpx 12rpx;
}

.waterfall-meta-text {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.waterfall-price {
  color: #ff5000;
  font-size: 30rpx;
  font-weight: 600;
}

.waterfall-sales {
  color: #999;
  font-size: 22rpx;
}

.waterfall-empty {
  padding: 40rpx;
  color: #999;
  font-size: 26rpx;
  text-align: center;
}
</style>

<script setup lang="ts">
import type { GoodsGroupProps } from '@/components/diy/retail-types'

import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { followDecorationLink } from '@/components/diy/link-resolver'
import {
  loadGoodsGroup,
  loadGoodsGroupPage,
} from '@/components/diy/retail-data'
import { retailCommonStyle } from '@/components/diy/retail-types'
import QuickCartButton from '@/components/quick-cart-button/index.vue'
import { useDiyStyle } from '@/composables/useDiyStyle'

const props = withDefaults(defineProps<{
  showData?: Partial<GoodsGroupProps>
}>(), {
  showData: () => ({}),
})

const showData = computed<GoodsGroupProps>(() => ({
  // 兼容后台序列化后 columns 可能为字符串的情况，统一按数值判断
  columns: Number(props.showData.columns) === 3 ? 3 : 2,
  commonStyle: props.showData.commonStyle || retailCommonStyle,
  count: Number(props.showData.count) || 6,
  dataSource: props.showData.dataSource || { mode: 'rule', sort: 'sales' },
  emptyStrategy: props.showData.emptyStrategy || 'placeholder',
  invalidStrategy: props.showData.invalidStrategy || 'hide',
  showSales: props.showData.showSales !== false,
  title: props.showData.title || '热卖商品',
}))
const dynamicStyles = useDiyStyle(computed(() => showData.value.commonStyle))
// 与后台预览一致的 grid 列数，避免 flex 宽度计算在小程序下偏移
const gridStyle = computed(() => ({
  gridTemplateColumns: `repeat(${showData.value.columns}, minmax(0, 1fr))`,
}))

// 手选（manual）只渲染运营勾选的固定商品；自动规则模式由 z-paging 分页追加，实现无限滚动
const isManual = computed(() => showData.value.dataSource?.mode === 'manual')
const goodsList = ref<any[]>([])
const pagingRef = ref()
// 每页大小：后台 count 作为每页条数，默认 10
const pageSize = computed(() => Math.max(1, Number(showData.value.count) || 10))

async function queryList(pageNo: number, pageSize: number) {
  try {
    if (isManual.value) {
      const items = await loadGoodsGroup(showData.value)
      pagingRef.value?.complete(items, items.length)
      return
    }
    const { list, total } = await loadGoodsGroupPage(showData.value, pageNo, pageSize)
    pagingRef.value?.complete(list, total)
  }
  catch {
    pagingRef.value?.complete([])
  }
}

function openGoods(id: string) {
  followDecorationLink({ params: {}, path: '', targetId: id, type: 'goods' })
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
  <view class="goods-group" :style="dynamicStyles">
    <view class="section-title">{{ showData.title }}</view>
    <!--
      use-page-scroll：复用首页页面滚动，滚到底自动加载下一页。
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
      <view class="goods-grid" :style="gridStyle">
        <view
          v-for="item in goodsList"
          :key="item.id"
          class="goods-card"
          @click="openGoods(item.id)"
        >
          <image v-if="item.imageUrl" class="goods-image" :src="item.imageUrl" mode="aspectFill" />
          <view v-else class="goods-image goods-image--empty">商品</view>
          <view class="goods-name">{{ item.name }}</view>
          <view class="goods-meta">
            <view class="goods-meta-text">
              <text class="goods-price">
                ￥{{ item.price.toFixed(2) }}
              </text>
              <text v-if="showData.showSales" class="goods-sales">
                已售 {{ item.sales }}
              </text>
            </view>
            <!-- 快捷加购：单规格直接加购，多规格唤起规格弹层 -->
            <quick-cart-button v-if="item.stock > 0" :spu-id="item.id" size="40rpx" />
          </view>
          <view v-if="item.stock <= 0" class="stock-label">暂时缺货</view>
        </view>
      </view>
      <template #empty>
        <view class="goods-empty">暂无商品</view>
      </template>
    </z-paging>
  </view>
</template>

<style lang="scss" scoped>
.section-title { margin-bottom: 18rpx; color: #1f2329; font-size: 30rpx; font-weight: 600; }
.goods-grid { display: grid; gap: 16rpx; }
.goods-card { overflow: hidden; box-sizing: border-box; border: 1rpx solid #eef0f3; border-radius: 8rpx; background: #fff; }
.goods-image { display: flex; width: 100%; aspect-ratio: 1; align-items: center; justify-content: center; background: #f3f4f6; color: #a8abb2; font-size: 24rpx; }
.goods-name { overflow: hidden; margin: 14rpx 14rpx 8rpx; color: #303133; font-size: 26rpx; text-overflow: ellipsis; white-space: nowrap; }
.goods-meta { display: flex; margin: 0 14rpx 14rpx; align-items: center; justify-content: space-between; gap: 8rpx; }
.goods-meta-text { min-width: 0; display: flex; flex-direction: column; }
.goods-price { color: #e5484d; font-size: 28rpx; font-weight: 600; }
.goods-sales { color: #909399; font-size: 20rpx; }
.stock-label { margin: -4rpx 14rpx 14rpx; color: #909399; font-size: 20rpx; }
.goods-empty { padding: 40rpx; color: #999; font-size: 26rpx; text-align: center; }
</style>

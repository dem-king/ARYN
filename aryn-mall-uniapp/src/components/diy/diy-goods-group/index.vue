<script setup lang="ts">
import { computed } from 'vue'

import { followDecorationLink } from '@/components/diy/link-resolver'
import { loadGoodsGroup } from '@/components/diy/retail-data'
import { retailCommonStyle } from '@/components/diy/retail-types'
import type { GoodsGroupProps } from '@/components/diy/retail-types'
import RetailState from '@/components/diy/retail-state.vue'
import { useDiyStyle } from '@/composables/useDiyStyle'
import { useRetailData } from '@/composables/useRetailData'

const props = withDefaults(defineProps<{
  showData?: Partial<GoodsGroupProps>
}>(), {
  showData: () => ({}),
})

const showData = computed<GoodsGroupProps>(() => ({
  columns: props.showData.columns === 3 ? 3 : 2,
  commonStyle: props.showData.commonStyle || retailCommonStyle,
  count: Number(props.showData.count) || 6,
  dataSource: props.showData.dataSource || { mode: 'rule', sort: 'sales' },
  emptyStrategy: props.showData.emptyStrategy || 'placeholder',
  invalidStrategy: props.showData.invalidStrategy || 'hide',
  showSales: props.showData.showSales !== false,
  title: props.showData.title || '热卖商品',
}))
const dynamicStyles = useDiyStyle(computed(() => showData.value.commonStyle))
const itemStyle = computed(() => ({
  width: `calc(${100 / showData.value.columns}% - 8rpx)`,
}))
const { items, shouldRender, status } = useRetailData(showData, loadGoodsGroup)

function openGoods(id: string) {
  followDecorationLink({ params: {}, path: '', targetId: id, type: 'goods' })
}
</script>

<template>
  <view v-if="shouldRender" class="goods-group" :style="dynamicStyles">
    <view class="section-title">{{ showData.title }}</view>
    <RetailState v-if="status !== 'ready'" :status="status" />
    <view v-else class="goods-grid">
      <view
        v-for="item in items"
        :key="item.id"
        class="goods-card"
        :style="itemStyle"
        @click="openGoods(item.id)"
      >
        <image v-if="item.imageUrl" class="goods-image" :src="item.imageUrl" mode="aspectFill" />
        <view v-else class="goods-image goods-image--empty">商品</view>
        <view class="goods-name">{{ item.name }}</view>
        <view class="goods-meta">
          <text class="goods-price">￥{{ item.price.toFixed(2) }}</text>
          <text v-if="showData.showSales" class="goods-sales">已售 {{ item.sales }}</text>
        </view>
        <view v-if="item.stock <= 0" class="stock-label">暂时缺货</view>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.section-title { margin-bottom: 18rpx; color: #1f2329; font-size: 30rpx; font-weight: 600; }
.goods-grid { display: flex; flex-wrap: wrap; gap: 16rpx; }
.goods-card { overflow: hidden; box-sizing: border-box; border: 1rpx solid #eef0f3; border-radius: 8rpx; background: #fff; }
.goods-image { display: flex; width: 100%; aspect-ratio: 1; align-items: center; justify-content: center; background: #f3f4f6; color: #a8abb2; font-size: 24rpx; }
.goods-name { overflow: hidden; margin: 14rpx 14rpx 8rpx; color: #303133; font-size: 26rpx; text-overflow: ellipsis; white-space: nowrap; }
.goods-meta { display: flex; margin: 0 14rpx 14rpx; align-items: baseline; justify-content: space-between; gap: 8rpx; }
.goods-price { color: #e5484d; font-size: 28rpx; font-weight: 600; }
.goods-sales { color: #909399; font-size: 20rpx; }
.stock-label { margin: -4rpx 14rpx 14rpx; color: #909399; font-size: 20rpx; }
</style>

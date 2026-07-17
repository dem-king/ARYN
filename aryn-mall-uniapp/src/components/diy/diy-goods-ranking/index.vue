<script setup lang="ts">
import { computed } from 'vue'

import { followDecorationLink } from '@/components/diy/link-resolver'
import { loadGoodsRanking } from '@/components/diy/retail-data'
import { retailCommonStyle } from '@/components/diy/retail-types'
import type { GoodsRankingProps } from '@/components/diy/retail-types'
import RetailState from '@/components/diy/retail-state.vue'
import { useDiyStyle } from '@/composables/useDiyStyle'
import { useRetailData } from '@/composables/useRetailData'

const props = withDefaults(defineProps<{ showData?: Partial<GoodsRankingProps> }>(), {
  showData: () => ({}),
})
const showData = computed<GoodsRankingProps>(() => ({
  commonStyle: props.showData.commonStyle || retailCommonStyle,
  count: Number(props.showData.count) || 5,
  dataSource: props.showData.dataSource || { metric: 'sales', mode: 'ranking' },
  emptyStrategy: props.showData.emptyStrategy || 'placeholder',
  invalidStrategy: props.showData.invalidStrategy || 'hide',
  showRankNumber: props.showData.showRankNumber !== false,
  title: props.showData.title || '畅销排行',
}))
const dynamicStyles = useDiyStyle(computed(() => showData.value.commonStyle))
const { items, shouldRender, status } = useRetailData(showData, loadGoodsRanking)

function openGoods(id: string) {
  followDecorationLink({ params: {}, path: '', targetId: id, type: 'goods' })
}
</script>

<template>
  <view v-if="shouldRender" class="goods-ranking" :style="dynamicStyles">
    <view class="section-title">{{ showData.title }}</view>
    <RetailState v-if="status !== 'ready'" :status="status" />
    <view v-else class="ranking-list">
      <view v-for="(item, index) in items" :key="item.id" class="ranking-item" @click="openGoods(item.id)">
        <view v-if="showData.showRankNumber" class="rank-number" :class="{ 'rank-number--top': index < 3 }">{{ index + 1 }}</view>
        <image v-if="item.imageUrl" class="ranking-image" :src="item.imageUrl" mode="aspectFill" />
        <view v-else class="ranking-image ranking-image--empty">商品</view>
        <view class="ranking-content">
          <view class="ranking-name">{{ item.name }}</view>
          <view class="ranking-stats">销量 {{ item.sales }} · 库存 {{ item.stock }}</view>
          <view class="ranking-price">￥{{ item.price.toFixed(2) }}</view>
        </view>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.section-title { margin-bottom: 12rpx; color: #1f2329; font-size: 30rpx; font-weight: 600; }
.ranking-list { display: flex; flex-direction: column; }
.ranking-item { display: flex; min-height: 132rpx; align-items: center; gap: 16rpx; border-bottom: 1rpx solid #eef0f3; }
.ranking-item:last-child { border-bottom: 0; }
.rank-number { width: 36rpx; color: #909399; font-size: 26rpx; text-align: center; }
.rank-number--top { color: #e5484d; font-weight: 700; }
.ranking-image { display: flex; width: 104rpx; height: 104rpx; flex: 0 0 auto; align-items: center; justify-content: center; border-radius: 8rpx; background: #f3f4f6; color: #a8abb2; font-size: 22rpx; }
.ranking-content { min-width: 0; flex: 1; }
.ranking-name { overflow: hidden; color: #303133; font-size: 26rpx; text-overflow: ellipsis; white-space: nowrap; }
.ranking-stats { margin-top: 8rpx; color: #909399; font-size: 21rpx; }
.ranking-price { margin-top: 6rpx; color: #e5484d; font-size: 27rpx; font-weight: 600; }
</style>

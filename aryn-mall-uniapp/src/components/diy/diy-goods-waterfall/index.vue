<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import { loadGoodsWaterfall } from '@/components/diy/retail-data'
import type { GoodsWaterfallProps } from '@/components/diy/retail-types'
import { useDiyStyle } from '@/composables/useDiyStyle'

const props = defineProps({
  showData: {
    type: Object,
    default: () => ({}),
  },
})

const componentProps = computed(() => props.showData as unknown as GoodsWaterfallProps)
const goodsList = ref<any[]>([])
const loading = ref(true)
const dynamicStyles = useDiyStyle(computed(() => componentProps.value.commonStyle))

async function loadData() {
  loading.value = true
  try {
    goodsList.value = await loadGoodsWaterfall(componentProps.value)
  }
  catch {
    // 数据源失效时降级为空态，不阻断整页渲染
    goodsList.value = []
  }
  finally {
    loading.value = false
  }
}

onMounted(loadData)

function openGoods(id: string) {
  uni.navigateTo({ url: `/sub-pages/product/goods-detail/index?id=${id}` })
}
</script>

<template>
  <view class="diy-waterfall" :style="dynamicStyles">
    <view v-if="componentProps.title" class="waterfall-title">
      {{ componentProps.title }}
    </view>
    <view v-if="loading" class="waterfall-empty">加载中…</view>
    <view v-else-if="goodsList.length === 0" class="waterfall-empty">
      暂无商品
    </view>
    <view
      v-else
      class="waterfall-columns"
      :style="{ columnCount: componentProps.columns || 2 }"
    >
      <view
        v-for="item in goodsList"
        :key="item.id"
        class="waterfall-card"
        @click="openGoods(item.id)"
      >
        <image class="waterfall-pic" :src="item.picUrl" mode="widthFix" />
        <view class="waterfall-name">{{ item.name }}</view>
        <view class="waterfall-meta">
          <text v-if="componentProps.showPrice !== false" class="waterfall-price">
            ¥{{ item.price }}
          </text>
          <text v-if="componentProps.showSales" class="waterfall-sales">
            已售{{ item.sales }}
          </text>
        </view>
      </view>
    </view>
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
  padding: 0 12rpx 12rpx;
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

<script setup lang="ts">
import type { GoodsRecommendProps } from '@/components/diy/retail-types'

import { computed, ref, watch } from 'vue'

import { shouldShowOriginalPrice } from '@/components/diy/price-display'
import { loadGoodsRecommend } from '@/components/diy/retail-data'
import { useDiyStyle } from '@/composables/useDiyStyle'

const props = defineProps({
  showData: {
    type: Object,
    default: () => ({}),
  },
  /** 当前商品 ID（商详页装修上下文，automatic 模式按其分类取数） */
  goodsId: {
    type: String,
    default: '',
  },
})

const componentProps = computed(() => props.showData as unknown as GoodsRecommendProps)
const dynamicStyles = useDiyStyle(computed(() => componentProps.value.commonStyle))

const goodsList = ref<any[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    goodsList.value = await loadGoodsRecommend(componentProps.value, props.goodsId || undefined)
  }
  catch {
    // 推荐位加载失败静默隐藏，不阻塞商详主内容
    goodsList.value = []
  }
  finally {
    loading.value = false
  }
}

// goodsId 变化（切换商品）时重取；同商品命中 loadGoodsRecommend 内的 TTL 缓存
watch(
  () => [componentProps.value, props.goodsId] as const,
  () => load(),
  { immediate: true, deep: true },
)

function openGoods(id: string) {
  uni.navigateTo({ url: `/sub-pages/product/goods-detail/index?id=${id}` })
}

function originalPriceText(item: any) {
  return `¥${item.originalPrice}`
}
</script>

<template>
  <view v-if="goodsList.length > 0" class="gr-box" :style="dynamicStyles">
    <view v-if="componentProps.title" class="gr-head">
      <view class="gr-bar" />
      <text class="gr-title">
        {{ componentProps.title }}
      </text>
    </view>
    <scroll-view class="gr-scroll" scroll-x :scroll-with-animation="true">
      <view class="gr-track">
        <view
          v-for="item in goodsList"
          :key="item.id"
          class="gr-card"
          @click="openGoods(item.id)"
        >
          <image
            v-if="item.imageUrl"
            class="gr-pic"
            :src="resolveImageSrc(item.imageUrl)"
            mode="aspectFill"
            lazy-load
          />
          <view v-else class="gr-pic gr-pic--empty">
            商品
          </view>
          <view class="gr-name">
            {{ item.name }}
          </view>
          <view class="gr-price-line">
            <text v-if="componentProps.showPrice !== false" class="gr-price">
              <text class="gr-price-symbol">
                ¥
              </text>{{ item.price.toFixed(2) }}
            </text>
            <text
              v-if="componentProps.showOriginalPrice
                && shouldShowOriginalPrice(item.price, item.originalPrice)"
              class="gr-price-original"
            >
              {{ originalPriceText(item) }}
            </text>
          </view>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<style lang="scss" scoped>
.gr-box {
  padding: 20rpx;
}

.gr-head {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 16rpx;
}

.gr-bar {
  width: 8rpx;
  height: 28rpx;
  background: var(--wot-color-theme-primary, #ff2237);
  border-radius: 4rpx;
}

.gr-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #303133;
}

/* 横滑轨道：卡片 200rpx 宽、圆角 16rpx，与管理端预览（100px/8px）同源 */
.gr-scroll {
  width: 100%;
  white-space: nowrap;
}

.gr-track {
  display: flex;
  gap: 16rpx;
}

.gr-card {
  flex: 0 0 200rpx;
  width: 200rpx;
  overflow: hidden;
  background: #fff;
  border: 1rpx solid #eef0f3;
  border-radius: 16rpx;
}

.gr-pic {
  width: 200rpx;
  height: 200rpx;
  display: block;
}

.gr-pic--empty {
  display: grid;
  place-items: center;
  background: #f2f3f5;
  color: #c0c4cc;
  font-size: 24rpx;
}

.gr-name {
  padding: 12rpx 12rpx 0;
  font-size: 26rpx;
  color: #303133;
  line-height: 1.4;
  height: 74rpx;
  display: -webkit-box;
  overflow: hidden;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.gr-price-line {
  display: flex;
  align-items: baseline;
  gap: 8rpx;
  padding: 4rpx 12rpx 12rpx;
}

.gr-price {
  color: var(--wot-color-theme-primary, #ff2237);
  font-size: 30rpx;
  font-weight: 600;
}

.gr-price-symbol {
  font-size: 22rpx;
}

/* 划线原价：灰字小一号，仅作价格锚点 */
.gr-price-original {
  flex: none;
  color: #999;
  font-size: 22rpx;
  font-weight: normal;
  text-decoration: line-through;
}
</style>

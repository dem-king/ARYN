<script setup lang="ts">
import type { DiscountProps } from '@/components/diy/retail-types'

import { computed } from 'vue'

import { followDecorationLink } from '@/components/diy/link-resolver'
import { loadDiscounts } from '@/components/diy/retail-data'
import RetailState from '@/components/diy/retail-state.vue'
import { retailCommonStyle } from '@/components/diy/retail-types'
import { formatRemainingTime, useCountdownTicker } from '@/composables/useCountdown'
import { useDiyStyle } from '@/composables/useDiyStyle'
import { useRetailData } from '@/composables/useRetailData'

const props = withDefaults(defineProps<{ showData?: Partial<DiscountProps> }>(), {
  showData: () => ({}),
})
const showData = computed<DiscountProps>(() => ({
  commonStyle: props.showData.commonStyle || retailCommonStyle,
  count: Number(props.showData.count) || 1,
  dataSource: props.showData.dataSource || { mode: 'automatic' },
  emptyStrategy: props.showData.emptyStrategy || 'placeholder',
  invalidStrategy: props.showData.invalidStrategy || 'hide',
  showCountdown: props.showData.showCountdown !== false,
  title: props.showData.title || '限时折扣',
}))
const dynamicStyles = useDiyStyle(computed(() => showData.value.commonStyle))
const { items, shouldRender, status } = useRetailData(showData, loadDiscounts)
const { now } = useCountdownTicker()

function remaining(endTime: string) {
  return formatRemainingTime(endTime, now.value)
}

/**
 * 折扣文案。打折值为 0.8 表示 8 折（与后端 discountType=1 的口径一致），
 * 故乘以 10 取整展示；减价与固定价直接给出金额语义。
 */
function discountLabel(activity: { discountType: number, discountValue: number }) {
  switch (activity.discountType) {
    case 1:
      return `${Math.round(activity.discountValue * 10)}折`
    case 2:
      return `减￥${activity.discountValue}`
    case 3:
      return '一口价'
    default:
      return '折扣'
  }
}

function openGoods(spuId: string) {
  followDecorationLink({ params: {}, path: '', targetId: spuId, type: 'goods' })
}
</script>

<template>
  <view v-if="shouldRender" class="discount" :style="dynamicStyles">
    <view class="section-title">
      {{ showData.title }}
    </view>
    <RetailState v-if="status !== 'ready'" :status="status" />
    <view v-else class="activity-list">
      <view v-for="activity in items" :key="activity.activityId" class="activity-item">
        <view class="activity-header">
          <text class="activity-name">
            {{ activity.activityName || '限时折扣' }}
          </text>
          <text class="activity-tag">
            {{ discountLabel(activity) }}
          </text>
          <text v-if="showData.showCountdown" class="activity-time">
            {{ activity.status === 2 || !remaining(activity.endTime)
              ? '活动已结束'
              : `剩余 ${remaining(activity.endTime)}` }}
          </text>
        </view>
        <!-- 全场活动没有指定商品清单，折扣在商品详情页生效；给出说明避免看起来是坏数据 -->
        <view v-if="activity.goodsList.length === 0" class="goods-empty">
          全场商品参与，进入商品详情查看折扣价
        </view>
        <view v-else class="goods-list">
          <view
            v-for="goods in activity.goodsList"
            :key="goods.skuId"
            class="goods-card"
            @click="openGoods(goods.spuId)"
          >
            <image
              v-if="goods.goodsImage"
              class="goods-image"
              :src="resolveImageSrc(goods.goodsImage)"
              mode="aspectFill"
              lazy-load
            />
            <view v-else class="goods-image goods-image--empty">
              折扣
            </view>
            <view class="goods-content">
              <view class="goods-name">
                {{ goods.goodsName || '折扣商品' }}
              </view>
              <view class="goods-price-row">
                <text class="goods-price">
                  ￥{{ goods.discountPrice.toFixed(2) }}
                </text>
                <text v-if="goods.originalPrice" class="goods-original">
                  ￥{{ goods.originalPrice.toFixed(2) }}
                </text>
              </view>
            </view>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<!-- 样式与限时活动（拼团）/秒杀楼层同一视觉语言，运营切换楼层时观感一致。 -->
<style scoped lang="scss">
.section-title { margin-bottom: 12rpx; color: #1f2329; font-size: 30rpx; font-weight: 600; }
.activity-list { display: flex; flex-direction: column; gap: 16rpx; }
.activity-header { display: flex; margin-bottom: 12rpx; align-items: baseline; gap: 12rpx; }
.activity-name { max-width: 320rpx; overflow: hidden; color: #1f2329; font-size: 27rpx; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.activity-tag { padding: 2rpx 10rpx; border-radius: 4rpx; background: var(--wot-color-theme-background, #ffe1dc); color: var(--wot-color-theme-primary, #ff2237); font-size: 21rpx; }
.activity-time { color: var(--wot-color-theme-primary, #ff2237); font-size: 22rpx; }
.goods-list { display: flex; flex-direction: column; gap: 14rpx; }
.goods-empty { padding: 24rpx 0; color: #909399; font-size: 24rpx; text-align: center; }
.goods-card { display: flex; overflow: hidden; min-height: 140rpx; gap: 18rpx; border: 1rpx solid #ffe1dc; border-radius: 8rpx; background: #fff; }
.goods-image { display: flex; width: 150rpx; min-height: 140rpx; flex: 0 0 auto; align-items: center; justify-content: center; background: var(--wot-color-theme-background, #fff1ee); color: var(--wot-color-theme-primary, #ff2237); font-size: 22rpx; }
.goods-content { min-width: 0; flex: 1; padding: 16rpx 16rpx 16rpx 0; }
.goods-name { overflow: hidden; color: #303133; font-size: 27rpx; font-weight: 500; text-overflow: ellipsis; white-space: nowrap; }
.goods-price-row { display: flex; margin-top: 14rpx; align-items: baseline; gap: 10rpx; }
.goods-price { color: var(--wot-color-theme-primary, #ff2237); font-size: 30rpx; font-weight: 700; }
.goods-original { color: #a8abb2; font-size: 21rpx; text-decoration: line-through; }
</style>

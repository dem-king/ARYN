<script setup lang="ts">
import type { GoodsRankingProps } from '@/components/diy/retail-types'
import type { ListZonePrice } from '@/utils/zone-price'

import { computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { followDecorationLink } from '@/components/diy/link-resolver'
import { loadGoodsRanking } from '@/components/diy/retail-data'
import RetailState from '@/components/diy/retail-state.vue'
import { retailCommonStyle } from '@/components/diy/retail-types'
import { shouldShowSubtitle } from '@/components/diy/subtitle-display'
import { useDiyStyle } from '@/composables/useDiyStyle'
import { useRetailData } from '@/composables/useRetailData'
import { useZonePrices } from '@/composables/useZonePrices'
import { stockHintText } from '@/utils/stock-display'
import { zonePriceBadgeText } from '@/utils/zone-price'

const props = withDefaults(defineProps<{ showData?: Partial<GoodsRankingProps> }>(), {
  showData: () => ({}),
})
const { t } = useI18n()
const showData = computed<GoodsRankingProps>(() => ({
  commonStyle: props.showData.commonStyle || retailCommonStyle,
  count: Number(props.showData.count) || 5,
  dataSource: props.showData.dataSource || { metric: 'sales', mode: 'ranking' },
  emptyStrategy: props.showData.emptyStrategy || 'placeholder',
  invalidStrategy: props.showData.invalidStrategy || 'hide',
  // 划线原价的运营开关：缺省视为关闭（存量装修数据里没有该字段）
  showOriginalPrice: Boolean(props.showData.showOriginalPrice),
  showRankNumber: props.showData.showRankNumber !== false,
  title: props.showData.title || t('diy.goodsRanking.defaultTitle'),
}))
const dynamicStyles = useDiyStyle(computed(() => showData.value.commonStyle))
const { items, shouldRender, status } = useRetailData(showData, loadGoodsRanking)

/**
 * 列表「专区价」批量补价句柄：会员价/出清价都不在商品列表接口里，
 * 列表先渲染、价格后补，失败静默（见 utils/zone-price 与 composables/useZonePrices）。
 */
const zone = useZonePrices()

watch(
  items,
  (list) => {
    const ids = list.map(item => String((item as any)?.id || '').trim()).filter(Boolean)
    if (!ids.length) return
    void zone.loadForSpuIds(ids)
  },
  { immediate: true },
)

/**
 * 卡片专区价：会员价/捡漏价命中时切换主价与划线，未命中回落普通价与未达标引导，
 * 统一走共享口径 `utils/zone-price#resolveListZonePrice`（与会员专区/低价捡漏一致）。
 */
function cardDisplay(item: any): ListZonePrice {
  return zone.cardPriceOf(item, showData.value.showOriginalPrice)
}

function cardMainPrice(item: any): number {
  return cardDisplay(item).mainPrice
}

function cardStrikePrice(item: any): number | null {
  return cardDisplay(item).strikePrice
}

function cardBadgeText(item: any): string {
  return zonePriceBadgeText(cardDisplay(item).badgeKind)
}

function cardBadgeClass(item: any): string {
  return cardDisplay(item).badgeKind === 'member'
    ? 'ranking-price-badge--member'
    : 'ranking-price-badge--clearance'
}

function cardGuideText(item: any): string {
  return cardDisplay(item).guideText || ''
}

function openGoods(id: string) {
  followDecorationLink({ params: {}, path: '', targetId: id, type: 'goods' })
}
</script>

<template>
  <view v-if="shouldRender" class="goods-ranking" :style="dynamicStyles">
    <view class="section-title">
      {{ showData.title }}
    </view>
    <RetailState v-if="status !== 'ready'" :status="status" />
    <view v-else class="ranking-list">
      <view v-for="(item, index) in items" :key="item.id" class="ranking-item" @click="openGoods(item.id)">
        <view v-if="showData.showRankNumber" class="rank-number" :class="{ 'rank-number--top': index < 3 }">
          {{ index + 1 }}
        </view>
        <image v-if="item.imageUrl" class="ranking-image" :src="item.imageUrl" mode="aspectFill" lazy-load />
        <view v-else class="ranking-image ranking-image--empty">
          {{ t('diy.common.product') }}
        </view>
        <view class="ranking-content">
          <view class="ranking-name">
            {{ item.name }}
          </view>
          <view v-if="shouldShowSubtitle(item.name, item.subTitle)" class="ranking-subtitle">
            {{ item.subTitle }}
          </view>
          <view v-if="stockHintText(item.stock)" class="ranking-stats">
            {{ stockHintText(item.stock) }}
          </view>
          <view class="ranking-price">
            <!-- 角标：会员价 / 捡漏价；主价与划线均由共享专区价口径给出 -->
            <text
              v-if="cardBadgeText(item)"
              class="ranking-price-badge"
              :class="cardBadgeClass(item)"
            >
              {{ cardBadgeText(item) }}
            </text>
            ￥{{ cardMainPrice(item).toFixed(2) }}
            <!-- 划线价：会员价/捡漏价命中划当前售价，普通价仍是「原价 > 售价才划线」 -->
            <text
              v-if="cardStrikePrice(item) !== null"
              class="ranking-price-original"
            >
              ￥{{ Number(cardStrikePrice(item)).toFixed(2) }}
            </text>
          </view>
          <!-- 未达标/未登录的会员价引导：只透出「会员专享 ¥X」，主价不动 -->
          <view v-if="cardGuideText(item)" class="ranking-member-guide">
            {{ cardGuideText(item) }}
          </view>
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
.rank-number--top { color: var(--wot-color-theme-primary, #ff2237); font-weight: 700; }
.ranking-image { display: flex; width: 104rpx; height: 104rpx; flex: 0 0 auto; align-items: center; justify-content: center; border-radius: 8rpx; background: #f3f4f6; color: #a8abb2; font-size: 22rpx; }
.ranking-content { min-width: 0; flex: 1; }
.ranking-name { overflow: hidden; color: #303133; font-size: 26rpx; text-overflow: ellipsis; white-space: nowrap; }
.ranking-subtitle { margin-top: 4rpx; overflow: hidden; color: #909399; font-size: 21rpx; line-height: 1.4; text-overflow: ellipsis; white-space: nowrap; }
.ranking-stats { margin-top: 8rpx; color: #909399; font-size: 21rpx; }
.ranking-price { margin-top: 6rpx; color: var(--wot-color-theme-primary, #ff2237); font-size: 27rpx; font-weight: 600; }
/* 专区价角标：会员价走浅金、捡漏价走主题红，小号标签不抢价格层级 */
.ranking-price-badge { margin-right: 6rpx; padding: 0 6rpx; border-radius: 4rpx; font-size: 19rpx; font-weight: 600; line-height: 1.5; }
.ranking-price-badge--member { border: 1rpx solid #ecd4a5; background: #fffaf0; color: #a06a1f; }
.ranking-price-badge--clearance { background: var(--wot-color-theme-primary, #ff2237); color: #fff; }
/* 划线原价：灰字小一号，仅作价格锚点，不与售价抢视觉层级 */
.ranking-price-original { margin-left: 8rpx; color: #999; font-size: 22rpx; font-weight: normal; text-decoration: line-through; }
/* 会员价引导：未达标/未登录时的一行小字，不改变主价与划线口径 */
.ranking-member-guide { margin-top: 2rpx; color: #a06a1f; font-size: 20rpx; line-height: 1.4; }
</style>

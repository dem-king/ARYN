<script setup lang="ts">
import type { GoodsGroupProps } from '@/components/diy/retail-types'
import type { ListZonePrice } from '@/utils/zone-price'

import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { followDecorationLink } from '@/components/diy/link-resolver'
import {
  loadGoodsGroup,
  loadGoodsGroupPage,
} from '@/components/diy/retail-data'
import { mergePagedItems } from '@/components/diy/retail-paged-list'
import { retailCommonStyle } from '@/components/diy/retail-types'
import { shouldShowSubtitle } from '@/components/diy/subtitle-display'
import PriceText from '@/components/price-text/index.vue'
import QuickCartButton from '@/components/quick-cart-button/index.vue'
import { useDiyPagedLoadMore } from '@/composables/useDiyPagedLoadMore'
import { useDiyStyle } from '@/composables/useDiyStyle'
import { useZonePrices } from '@/composables/useZonePrices'
import { imageAspectPadding } from '@/utils/image-ratio'
import { splitIntoColumns } from '@/utils/masonry'
import { stockHintText } from '@/utils/stock-display'
import { zonePriceBadgeText } from '@/utils/zone-price'

const props = withDefaults(defineProps<{
  showData?: Partial<GoodsGroupProps>
}>(), {
  showData: () => ({}),
})

const { t } = useI18n()
const showData = computed<GoodsGroupProps>(() => ({
  // 兼容后台序列化后 columns 可能为字符串的情况，统一按数值判断
  columns: Number(props.showData.columns) === 3 ? 3 : 2,
  commonStyle: props.showData.commonStyle || retailCommonStyle,
  count: Number(props.showData.count) || 6,
  dataSource: props.showData.dataSource || { mode: 'rule', sort: 'sales' },
  emptyStrategy: props.showData.emptyStrategy || 'placeholder',
  invalidStrategy: props.showData.invalidStrategy || 'hide',
  // 划线原价的运营开关：缺省视为关闭（存量装修数据里没有该字段）
  showOriginalPrice: Boolean(props.showData.showOriginalPrice),
  showSales: props.showData.showSales !== false,
  title: props.showData.title || t('diy.goodsGroup.defaultTitle'),
}))
const dynamicStyles = useDiyStyle(computed(() => showData.value.commonStyle))

// 手选（manual）只渲染运营勾选的固定商品；自动规则模式由 z-paging 分页追加，实现无限滚动
const isManual = computed(() => showData.value.dataSource?.mode === 'manual')
const goodsList = ref<any[]>([])
const pagingRef = ref()
// 双列/三列瀑布流：按索引交替分列，卡片按内容高度错落；翻页追加不重排已有卡片
const goodsColumns = computed(() =>
  splitIntoColumns(goodsList.value, showData.value.columns),
)
// 每页大小：后台 count 作为每页条数，默认 10
const pageSize = computed(() => Math.max(1, Number(showData.value.count) || 10))

/** 图片区按主图真实宽高锁高（后端回填，缺失按 1:1），加载前后卡片高度不变 */
function imageBoxStyle(item: any) {
  return { paddingTop: imageAspectPadding(item?.picWidth, item?.picHeight) }
}

// 自动续页：页面滚动接近底部 / 加载完成后自愈补页，见 useDiyPagedLoadMore
const { runQuery } = useDiyPagedLoadMore(pagingRef, '.goods-group')

/**
 * 列表「专区价」批量补价句柄：会员价/出清价都不在商品列表接口里，
 * 列表先渲染、价格后补（见 utils/zone-price 与 composables/useZonePrices）。
 */
const zone = useZonePrices()

/** 本轮本页商品补专区价：不 await，避免价格接口阻塞商品流 */
function loadZonePrices(items: any[]) {
  const ids = items.map(item => String(item?.id || '').trim()).filter(Boolean)
  if (!ids.length)
    return
  void zone.loadForSpuIds(ids)
}

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
  // 类名直接按枚举派生（会员/捡漏/秒杀/新人各有颜色语义，与主卡同一套令牌）
  return `goods-price-badge--${cardDisplay(item).badgeKind || 'clearance'}`
}

function cardGuideText(item: any): string {
  return cardDisplay(item).guideText || ''
}

async function queryList(pageNo: number, pageSize: number) {
  await runQuery(async () => {
    if (isManual.value) {
      // 手选：一次性取全量，明确告知 z-paging 没有下一页
      const items = await loadGoodsGroup(showData.value)
      loadZonePrices(items)
      await pagingRef.value?.completeByNoMore(items, true)
      return
    }
    const { list, total } = await loadGoodsGroupPage(showData.value, pageNo, pageSize)
    // 按 id 去重并限制单楼层条数：销量并列或翻页期间数据变化都可能返回重复商品
    const { items, reachedLimit } = mergePagedItems(goodsList.value, list)
    const loadedCount = goodsList.value.length + items.length
    const nomore = reachedLimit || list.length === 0 || (total > 0 && loadedCount >= total)
    loadZonePrices(items)
    await pagingRef.value?.completeByNoMore(items, nomore)
  })
}

// 下拉刷新会重新下发页面 JSON，但组件实例按 id 复用；显式 reload 才能重置列表和价格
watch(
  () => [showData.value.dataSource, showData.value.count],
  () => {
    pagingRef.value?.reload?.()?.catch?.(() => {})
  },
  { deep: true },
)

function openGoods(id: string) {
  followDecorationLink({ params: {}, path: '', targetId: id, type: 'goods' })
}
</script>

<template>
  <view class="goods-group" :style="dynamicStyles">
    <view class="section-title">
      {{ showData.title }}
    </view>
    <!--
      use-page-scroll：复用装修页页面滚动，由 useDiyPagedLoadMore 测自身底边位置，
      接近底部前自动加载下一页（无感浏览，不出现「点击加载更多」）。
      refresher-enabled=false：装修页整体已有下拉刷新，避免与 z-paging 下拉冲突。
      loading-more-default-text 置空：默认状态不渲染任何可点击文案。
    -->
    <z-paging
      ref="pagingRef"
      v-model="goodsList"
      use-page-scroll
      :refresher-enabled="false"
      :default-page-size="pageSize"
      loading-more-default-text=""
      @query="queryList"
    >
      <view class="goods-columns">
        <view
          v-for="(column, columnIndex) in goodsColumns"
          :key="columnIndex"
          class="goods-column"
        >
          <view
            v-for="item in column"
            :key="item.id"
            class="goods-card"
            @click="openGoods(item.id)"
          >
            <view class="goods-image-box" :style="imageBoxStyle(item)">
              <image v-if="item.imageUrl" class="goods-image" :src="item.imageUrl" mode="aspectFit" lazy-load />
              <view v-else class="goods-image goods-image--empty">
                {{ t('diy.common.product') }}
              </view>
            </view>
            <view class="goods-name">
              {{ item.name }}
            </view>
            <view v-if="shouldShowSubtitle(item.name, item.subTitle)" class="goods-subtitle">
              {{ item.subTitle }}
            </view>
            <view class="goods-meta">
              <view class="goods-meta-text">
                <view class="goods-price-line">
                  <!-- 角标：会员价 / 捡漏价；主价与划线均由共享专区价口径给出 -->
                  <text
                    v-if="cardBadgeText(item)"
                    class="goods-price-badge"
                    :class="cardBadgeClass(item)"
                  >
                    {{ cardBadgeText(item) }}
                  </text>
                  <price-text class="goods-price" :value="cardMainPrice(item)" symbol="￥" />
                  <!-- 划线价：会员价/捡漏价命中划当前售价，普通价仍是「原价 > 售价才划线」 -->
                  <text
                    v-if="cardStrikePrice(item) !== null"
                    class="goods-price-original"
                  >
                    ￥{{ Number(cardStrikePrice(item)).toFixed(2) }}
                  </text>
                </view>
                <!-- 未达标/未登录的会员价引导：只透出「会员专享 ¥X」，主价不动 -->
                <text v-if="cardGuideText(item)" class="goods-member-guide">
                  {{ cardGuideText(item) }}
                </text>
                <text v-if="stockHintText(item.stock)" class="goods-sales">
                  {{ stockHintText(item.stock) }}
                </text>
              </view>
              <!-- 快捷加购：单规格直接加购，多规格唤起规格弹层；专区价透传给弹层保证「卡片价 = 弹层价」 -->
              <quick-cart-button
                v-if="item.stock > 0"
                :spu-id="item.id"
                size="40rpx"
                :zone-prices="zone.skuZonePricesOf(item.id)"
              />
            </view>
            <view v-if="item.stock <= 0" class="stock-label">
              {{ t('diy.common.outOfStock') }}
            </view>
          </view>
        </view>
      </view>
      <template #empty>
        <view class="goods-empty">
          {{ t('diy.common.noProducts') }}
        </view>
      </template>
    </z-paging>
  </view>
</template>

<style lang="scss" scoped>
@import '@/styles/goods-tag.scss';
.section-title { margin-bottom: 18rpx; color: #1f2329; font-size: 30rpx; font-weight: 600; }
/* 交替瀑布流：两列/三列各自堆叠，卡片按内容高度错落，不做同排等高 */
.goods-columns { display: flex; align-items: flex-start; gap: 16rpx; }
.goods-column { display: flex; flex: 1; flex-direction: column; gap: 16rpx; min-width: 0; }
.goods-card { overflow: hidden; box-sizing: border-box; border: 1rpx solid #eef0f3; border-radius: 8rpx; background: #fff; }
/* 图片区高度由内联 padding-top 按主图真实比例锁定（缺失按 1:1），图片加载前后卡片高度不变 */
.goods-image-box { position: relative; width: 100%; background: #fff; }
.goods-image { position: absolute; top: 0; left: 0; display: flex; width: 100%; height: 100%; align-items: center; justify-content: center; color: #a8abb2; font-size: 24rpx; }
.goods-image--empty { background: #f3f4f6; }
.goods-name { overflow: hidden; margin: 14rpx 14rpx 8rpx; color: #303133; font-size: 26rpx; text-overflow: ellipsis; white-space: nowrap; }
.goods-subtitle { overflow: hidden; margin: 0 14rpx 8rpx; color: #909399; font-size: 22rpx; line-height: 1.4; text-overflow: ellipsis; white-space: nowrap; }
.goods-meta { display: flex; margin: 0 14rpx 14rpx; align-items: center; justify-content: space-between; gap: 8rpx; }
.goods-meta-text { min-width: 0; display: flex; flex-direction: column; }
.goods-price-line { display: flex; align-items: baseline; gap: 6rpx; min-width: 0; }
.goods-price { color: var(--wot-color-theme-primary, #ff2237); font-size: 28rpx; font-weight: 600; }
/* 专区价角标：会员价走浅金、捡漏价走固定红橙，小号标签不抢商品图重心 */
.goods-price-badge { flex: none; padding: 0 6rpx; border-radius: 4rpx; font-size: 19rpx; font-weight: 600; line-height: 1.5; }
.goods-price-badge--member { border: 1rpx solid #ecd4a5; background: #fffaf0; color: #a06a1f; }
.goods-price-badge--clearance { @include goods-tag-clearance; }
.goods-price-badge--seckill { background: linear-gradient(135deg, var(--wot-color-theme-primary, #ff2237) 0%, #ff6b45 100%); color: #fff; }
.goods-price-badge--newbie { @include goods-tag-newbie; }
/* 划线原价/划线售价：灰字小一号，仅作价格锚点，不与售价抢视觉层级 */
.goods-price-original { flex: none; color: #999; font-size: 22rpx; font-weight: normal; text-decoration: line-through; }
/* 会员价引导：未达标/未登录时的一行小字，不改变主价与划线口径 */
.goods-member-guide { margin-top: 2rpx; color: #a06a1f; font-size: 20rpx; line-height: 1.4; }
.goods-sales { color: #909399; font-size: 20rpx; }
.stock-label { margin: -4rpx 14rpx 14rpx; color: #909399; font-size: 20rpx; }
.goods-empty { padding: 40rpx; color: #999; font-size: 26rpx; text-align: center; }
</style>

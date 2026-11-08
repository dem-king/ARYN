<script setup lang="ts">
import type { GoodsWaterfallProps } from '@/components/diy/retail-types'
import type { ListZonePrice } from '@/utils/zone-price'

import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  loadGoodsWaterfall,
  loadGoodsWaterfallPage,
} from '@/components/diy/retail-data'
import { mergePagedItems } from '@/components/diy/retail-paged-list'
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

const props = defineProps({
  showData: {
    type: Object,
    default: () => ({}),
  },
})

const { t } = useI18n()
const componentProps = computed(() => props.showData as unknown as GoodsWaterfallProps)

// 手选模式（manual）只渲染运营勾选的固定商品；自动模式由 z-paging 分页追加，实现无限滚动
const isManual = computed(() => componentProps.value.dataSource?.mode === 'manual')

const goodsList = ref<any[]>([])
const pagingRef = ref()
const dynamicStyles = useDiyStyle(computed(() => componentProps.value.commonStyle))

// 交替瀑布流：按索引奇偶入列；相比 column-count 的列平衡，翻页追加不会让已有卡片跳列
const goodsColumns = computed(() =>
  splitIntoColumns(goodsList.value, Number(componentProps.value.columns) === 3 ? 3 : 2),
)

// 每页大小：后台若配置了 count 则作为每页条数，否则默认 10
const pageSize = computed(() => Math.max(1, Number(componentProps.value.count) || 10))

// 自动续页：页面滚动接近底部 / 加载完成后自愈补页，见 useDiyPagedLoadMore
const { runQuery } = useDiyPagedLoadMore(pagingRef, '.diy-waterfall')

/**
 * 列表「专区价」批量补价句柄：会员价/出清价都不在商品列表接口里，
 * 列表先渲染、价格后补，失败静默（见 utils/zone-price 与 composables/useZonePrices）。
 */
const zone = useZonePrices()

/** 本轮本页商品补专区价：不 await，避免价格接口阻塞瀑布流渲染 */
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
  return zone.cardPriceOf(item, componentProps.value.showOriginalPrice)
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
  return `waterfall-price-badge--${cardDisplay(item).badgeKind || 'clearance'}`
}

function cardGuideText(item: any): string {
  return cardDisplay(item).guideText || ''
}

async function queryList(pageNo: number, pageSize: number) {
  await runQuery(async () => {
    if (isManual.value) {
      // 手选：一次性取全量，回传明确无下一页，z-paging 据此隐藏“点击加载更多”
      const items = await loadGoodsWaterfall(componentProps.value)
      loadZonePrices(items)
      await pagingRef.value?.completeByNoMore(items, true)
      return
    }
    const { list, total } = await loadGoodsWaterfallPage(
      componentProps.value,
      pageNo,
      pageSize,
    )
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
  () => [componentProps.value.dataSource, componentProps.value.count],
  () => {
    pagingRef.value?.reload?.()?.catch?.(() => {})
  },
  { deep: true },
)

function openGoods(id: string) {
  uni.navigateTo({ url: `/sub-pages/product/goods-detail/index?id=${id}` })
}

/**
 * 卡片信息 chip：只展示规格。
 *
 * 库存绝对数不外露，售罄/低库存提示在下方另起一行（见 utils/stock-display）；
 * 运费（免配送费）不在商品卡片上展示，口径统一以商详 / 结算页为准。
 */
function cardTags(item: any): string[] {
  const tags: string[] = []
  if (item.specsInfo)
    tags.push(String(item.specsInfo))
  return tags
}
</script>

<template>
  <view class="diy-waterfall" :style="dynamicStyles">
    <view class="waterfall-inner">
      <view v-if="componentProps.title" class="waterfall-title">
        {{ componentProps.title }}
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
        <view class="waterfall-columns">
          <view
            v-for="(column, columnIndex) in goodsColumns"
            :key="columnIndex"
            class="waterfall-column"
          >
            <view
              v-for="item in column"
              :key="item.id"
              class="waterfall-card"
              @click="openGoods(item.id)"
            >
              <view
                class="waterfall-pic-box"
                :style="{ paddingTop: imageAspectPadding(item.picWidth, item.picHeight) }"
              >
                <image class="waterfall-pic" :src="item.imageUrl" mode="aspectFit" lazy-load />
              </view>
              <view class="waterfall-name">
                {{ item.name }}
              </view>
              <view v-if="shouldShowSubtitle(item.name, item.subTitle)" class="waterfall-subtitle">
                {{ item.subTitle }}
              </view>
              <!-- 信息 chip：仅规格（库存绝对数不外露，低库存另起一行） -->
              <view v-if="cardTags(item).length > 0" class="waterfall-tags">
                <text
                  v-for="tag in cardTags(item)"
                  :key="tag"
                  class="waterfall-tag"
                >
                  {{ tag }}
                </text>
              </view>
              <view class="waterfall-meta">
                <view class="waterfall-meta-text">
                  <view class="waterfall-price-line">
                    <text
                      v-if="componentProps.showPrice !== false"
                      class="waterfall-price"
                    >
                      <!-- 角标：会员价 / 捡漏价；主价与划线均由共享专区价口径给出 -->
                      <text
                        v-if="cardBadgeText(item)"
                        class="waterfall-price-badge"
                        :class="cardBadgeClass(item)"
                      >
                        {{ cardBadgeText(item) }}
                      </text>
                      <price-text :value="cardMainPrice(item)" />
                    </text>
                    <!-- 划线价：会员价/捡漏价命中划当前售价，普通价仍是「原价 > 售价才划线」 -->
                    <text
                      v-if="cardStrikePrice(item) !== null"
                      class="waterfall-price-original"
                    >
                      ¥{{ Number(cardStrikePrice(item)).toFixed(2) }}
                    </text>
                  </view>
                  <!-- 未达标/未登录的会员价引导：只透出「会员专享 ¥X」，主价不动 -->
                  <text v-if="cardGuideText(item)" class="waterfall-member-guide">
                    {{ cardGuideText(item) }}
                  </text>
                  <text v-if="stockHintText(item.stock)" class="waterfall-sales">
                    {{ stockHintText(item.stock) }}
                  </text>
                </view>
                <!-- 快捷加购（单规格直加，多规格弹层）；专区价透传给弹层保证「卡片价 = 弹层价」 -->
                <view class="waterfall-actions">
                  <quick-cart-button
                    v-if="item.stock > 0"
                    :spu-id="item.id"
                    size="40rpx"
                    :zone-prices="zone.skuZonePricesOf(item.id)"
                  />
                </view>
              </view>
            </view>
          </view>
        </view>
        <template #empty>
          <view class="waterfall-empty">
            {{ t('diy.common.noProducts') }}
          </view>
        </template>
      </z-paging>
    </view>
  </view>
</template>

<style lang="scss" scoped>
@import '@/styles/goods-tag.scss';
/* 留白放在内层而不是根节点：根节点的 padding 会被装修 commonStyle 的
   内联样式覆盖成 0（配置缺省即零值），挂根上会让卡片直接顶到屏幕边缘 */
.waterfall-inner {
  padding: 20rpx;
}

.waterfall-title {
  margin-bottom: 16rpx;
  font-size: 32rpx;
  font-weight: 600;
}

/* 交替瀑布流：两列/三列各自堆叠，卡片按内容高度错落 */
.waterfall-columns {
  display: flex;
  align-items: flex-start;
  gap: 16rpx;
}

.waterfall-column {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 16rpx;
  min-width: 0;
}

.waterfall-card {
  display: block;
  overflow: hidden;
  background: #fff;
  border-radius: 12rpx;
}

/* 图片区按主图真实宽高锁高（后端回填，缺失按 1:1），加载前后卡片高度不变 */
.waterfall-pic-box {
  position: relative;
  width: 100%;
  background: #fff;
}

.waterfall-pic {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
}

.waterfall-name {
  padding: 12rpx 12rpx 0;
  font-size: 26rpx;
  display: -webkit-box;
  overflow: hidden;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.waterfall-subtitle {
  padding: 4rpx 12rpx 0;
  overflow: hidden;
  color: #909399;
  font-size: 21rpx;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.waterfall-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6rpx;
  padding: 8rpx 12rpx 0;
}

.waterfall-tag {
  padding: 2rpx 8rpx;
  border-radius: 4rpx;
  background: #f2f3f5;
  color: #8a97a6;
  font-size: 20rpx;
  line-height: 1.5;
}

.waterfall-actions {
  display: flex;
  flex: none;
  align-items: center;
  gap: 12rpx;
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

.waterfall-price-line {
  display: flex;
  align-items: baseline;
  gap: 6rpx;
  min-width: 0;
}

.waterfall-price {
  color: var(--wot-color-theme-primary, #ff2237);
  font-size: 30rpx;
  font-weight: 600;
}

/* 专区价角标：会员价走浅金、捡漏价走固定红橙，小号标签不抢价格层级 */
.waterfall-price-badge {
  margin-right: 4rpx;
  padding: 0 6rpx;
  border-radius: 4rpx;
  font-size: 18rpx;
  font-weight: 600;
  line-height: 1.5;
}

.waterfall-price-badge--member {
  border: 1rpx solid #ecd4a5;
  background: #fffaf0;
  color: #a06a1f;
}

.waterfall-price-badge--clearance {
  @include goods-tag-clearance;
}

.waterfall-price-badge--seckill {
  background: linear-gradient(135deg, var(--wot-color-theme-primary, #ff2237) 0%, #ff6b45 100%);
  color: #fff;
}

.waterfall-price-badge--newbie {
  @include goods-tag-newbie;
}

/* 划线原价：灰字小一号，仅作价格锚点，不与售价抢视觉层级 */
.waterfall-price-original {
  flex: none;
  color: #999;
  font-size: 22rpx;
  font-weight: normal;
  text-decoration: line-through;
}

/* 会员价引导：未达标/未登录时的一行小字，不改变主价与划线口径 */
.waterfall-member-guide {
  color: #a06a1f;
  font-size: 20rpx;
  line-height: 1.4;
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

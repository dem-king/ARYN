<script setup lang="ts">
import type { GoodsWaterfallProps } from '@/components/diy/retail-types'

import { computed, ref } from 'vue'
import { shouldShowOriginalPrice } from '@/components/diy/price-display'
import {
  loadGoodsWaterfall,
  loadGoodsWaterfallPage,
} from '@/components/diy/retail-data'
import QuickCartButton from '@/components/quick-cart-button/index.vue'
import { useDiyPagedLoadMore } from '@/composables/useDiyPagedLoadMore'
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

// 自动续页：页面滚动接近底部 / 加载完成后自愈补页，见 useDiyPagedLoadMore
const { runQuery } = useDiyPagedLoadMore(pagingRef, '.diy-waterfall')

async function queryList(pageNo: number, pageSize: number) {
  await runQuery(async () => {
    if (isManual.value) {
      // 手选：一次性取全量，回传明确无下一页，z-paging 据此隐藏“点击加载更多”
      const items = await loadGoodsWaterfall(componentProps.value)
      await pagingRef.value?.completeByNoMore(items, true)
      return
    }
    const { list, total } = await loadGoodsWaterfallPage(
      componentProps.value,
      pageNo,
      pageSize,
    )
    // 注意：complete 的第二参是 success(布尔)，传总数必须用 completeByTotal，
    // 否则 z-paging 拿不到 total，会退化成“按返回条数<每页条数”猜测是否到底，导致自动续页中途停在“点击加载更多”
    await pagingRef.value?.completeByTotal(list, total)
  })
}

function openGoods(id: string) {
  uni.navigateTo({ url: `/sub-pages/product/goods-detail/index?id=${id}` })
}

/**
 * 卡片信息 chip：规格 / 现货 / 免配送费。
 *
 * 只在这三类信息确实存在时才渲染对应 chip —— 接口没返回就不编造，
 * 避免出现「现货 0」这类误导性文案。字段来源见 retail-normalizers。
 */
function cardTags(item: any): string[] {
  const tags: string[] = []
  if (item.specsInfo)
    tags.push(String(item.specsInfo))
  if (item.stock > 0)
    tags.push(`现货 ${item.stock}`)
  if (item.freeShipping === true)
    tags.push('免配送费')
  return tags
}
</script>

<template>
  <view class="diy-waterfall" :style="dynamicStyles">
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
      <view class="waterfall-columns" :style="{ columnCount: componentProps.columns || 2 }">
        <view
          v-for="item in goodsList"
          :key="item.id"
          class="waterfall-card"
          @click="openGoods(item.id)"
        >
          <image class="waterfall-pic" :src="item.imageUrl" mode="widthFix" lazy-load />
          <view class="waterfall-name">
            {{ item.name }}
          </view>
          <!-- 信息 chip：规格 / 现货 / 免配送费（字段缺失时整行不渲染） -->
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
                <text v-if="componentProps.showPrice !== false" class="waterfall-price">
                  ¥{{ item.price }}
                </text>
                <!-- 划线原价：仅原价严格高于售价时显示（存量商品原价多为 0，会划出￥0） -->
                <text
                  v-if="componentProps.showOriginalPrice
                    && shouldShowOriginalPrice(item.price, item.originalPrice)"
                  class="waterfall-price-original"
                >
                  ¥{{ item.originalPrice }}
                </text>
              </view>
              <text v-if="componentProps.showSales" class="waterfall-sales">
                已售{{ item.sales }}
              </text>
            </view>
            <!-- 快捷加购（单规格直加，多规格弹层） -->
            <view class="waterfall-actions">
              <quick-cart-button v-if="item.stock > 0" :spu-id="item.id" size="40rpx" />
            </view>
          </view>
        </view>
      </view>
      <template #empty>
        <view class="waterfall-empty">
          暂无商品
        </view>
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
  padding: 12rpx 12rpx 0;
  font-size: 26rpx;
  display: -webkit-box;
  overflow: hidden;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
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

/* 划线原价：灰字小一号，仅作价格锚点，不与售价抢视觉层级 */
.waterfall-price-original {
  flex: none;
  color: #999;
  font-size: 22rpx;
  font-weight: normal;
  text-decoration: line-through;
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

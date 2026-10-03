<script setup lang="ts">
import type { GoodsGroupProps } from '@/components/diy/retail-types'

import { computed, ref } from 'vue'
import { followDecorationLink } from '@/components/diy/link-resolver'
import { shouldShowOriginalPrice } from '@/components/diy/price-display'
import {
  loadGoodsGroup,
  loadGoodsGroupPage,
} from '@/components/diy/retail-data'
import { retailCommonStyle } from '@/components/diy/retail-types'
import QuickCartButton from '@/components/quick-cart-button/index.vue'
import { useDiyPagedLoadMore } from '@/composables/useDiyPagedLoadMore'
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
  // 划线原价的运营开关：缺省视为关闭（存量装修数据里没有该字段）
  showOriginalPrice: Boolean(props.showData.showOriginalPrice),
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

// 自动续页：页面滚动接近底部 / 加载完成后自愈补页，见 useDiyPagedLoadMore
const { runQuery } = useDiyPagedLoadMore(pagingRef, '.goods-group')

async function queryList(pageNo: number, pageSize: number) {
  await runQuery(async () => {
    if (isManual.value) {
      // 手选：一次性取全量，明确告知 z-paging 没有下一页
      const items = await loadGoodsGroup(showData.value)
      await pagingRef.value?.completeByNoMore(items, true)
      return
    }
    const { list, total } = await loadGoodsGroupPage(showData.value, pageNo, pageSize)
    // 注意：complete 的第二参是 success(布尔)，传总数必须用 completeByTotal，
    // 否则 z-paging 拿不到 total，会退化成“按返回条数<每页条数”猜测是否到底，导致自动续页中途停在“点击加载更多”
    await pagingRef.value?.completeByTotal(list, total)
  })
}

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
      <view class="goods-grid" :style="gridStyle">
        <view
          v-for="item in goodsList"
          :key="item.id"
          class="goods-card"
          @click="openGoods(item.id)"
        >
          <view class="goods-image-box">
            <image v-if="item.imageUrl" class="goods-image" :src="item.imageUrl" mode="aspectFit" lazy-load />
            <view v-else class="goods-image goods-image--empty">
              商品
            </view>
          </view>
          <view class="goods-name">
            {{ item.name }}
          </view>
          <view class="goods-meta">
            <view class="goods-meta-text">
              <view class="goods-price-line">
                <text class="goods-price">
                  ￥{{ item.price.toFixed(2) }}
                </text>
                <!-- 划线原价：仅原价严格高于售价时显示（存量商品原价多为 0，会划出￥0） -->
                <text
                  v-if="showData.showOriginalPrice && shouldShowOriginalPrice(item.price, item.originalPrice)"
                  class="goods-price-original"
                >
                  ￥{{ item.originalPrice.toFixed(2) }}
                </text>
              </view>
              <text v-if="showData.showSales" class="goods-sales">
                已售 {{ item.sales }}
              </text>
            </view>
            <!-- 快捷加购：单规格直接加购，多规格唤起规格弹层 -->
            <quick-cart-button v-if="item.stock > 0" :spu-id="item.id" size="40rpx" />
          </view>
          <view v-if="item.stock <= 0" class="stock-label">
            暂时缺货
          </view>
        </view>
      </view>
      <template #empty>
        <view class="goods-empty">
          暂无商品
        </view>
      </template>
    </z-paging>
  </view>
</template>

<style lang="scss" scoped>
.section-title { margin-bottom: 18rpx; color: #1f2329; font-size: 30rpx; font-weight: 600; }
.goods-grid { display: grid; gap: 16rpx; }
.goods-card { overflow: hidden; box-sizing: border-box; border: 1rpx solid #eef0f3; border-radius: 8rpx; background: #fff; }
/* 小程序 <image> 组件自带默认宽高，aspect-ratio 写在它上面不生效；
   用 padding-top 百分比（相对卡片宽度计算）把图片区锁成 1:1，
   图片 aspectFit 完整展示，与管理端预览 fit=contain 对齐 */
.goods-image-box { position: relative; width: 100%; padding-top: 100%; background: #fff; }
.goods-image { position: absolute; top: 0; left: 0; display: flex; width: 100%; height: 100%; align-items: center; justify-content: center; color: #a8abb2; font-size: 24rpx; }
.goods-image--empty { background: #f3f4f6; }
.goods-name { overflow: hidden; margin: 14rpx 14rpx 8rpx; color: #303133; font-size: 26rpx; text-overflow: ellipsis; white-space: nowrap; }
.goods-meta { display: flex; margin: 0 14rpx 14rpx; align-items: center; justify-content: space-between; gap: 8rpx; }
.goods-meta-text { min-width: 0; display: flex; flex-direction: column; }
.goods-price-line { display: flex; align-items: baseline; gap: 6rpx; min-width: 0; }
.goods-price { color: var(--wot-color-theme-primary, #ff2237); font-size: 28rpx; font-weight: 600; }
/* 划线原价：灰字小一号，仅作价格锚点，不与售价抢视觉层级 */
.goods-price-original { flex: none; color: #999; font-size: 22rpx; font-weight: normal; text-decoration: line-through; }
.goods-sales { color: #909399; font-size: 20rpx; }
.stock-label { margin: -4rpx 14rpx 14rpx; color: #909399; font-size: 20rpx; }
.goods-empty { padding: 40rpx; color: #999; font-size: 26rpx; text-align: center; }
</style>

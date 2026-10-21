<script setup lang="ts">
import type { GoodsTabConfig, GoodsTabsProps } from '@/components/diy/retail-types'

import { computed, getCurrentInstance, nextTick, reactive, ref, watch } from 'vue'
import { shouldShowOriginalPrice } from '@/components/diy/price-display'
import { loadGoodsTabPage } from '@/components/diy/retail-data'
import { mergePagedItems } from '@/components/diy/retail-paged-list'
import { retailCommonStyle } from '@/components/diy/retail-types'
import QuickCartButton from '@/components/quick-cart-button/index.vue'
import { useDiyPagedLoadMore } from '@/composables/useDiyPagedLoadMore'
import { useDiyStyle } from '@/composables/useDiyStyle'

const props = withDefaults(defineProps<{
  showData?: Partial<GoodsTabsProps>
}>(), {
  showData: () => ({}),
})

function defaultTabs(): GoodsTabConfig[] {
  return [
    {
      id: 'recommend',
      title: '猜你喜欢',
      dataSource: { mode: 'automatic', sort: 'sales', targetIds: [] },
    },
    {
      id: 'bargain',
      title: '低价捡漏',
      dataSource: { mode: 'automatic', sort: 'sales_price', targetIds: [] },
    },
    {
      id: 'member',
      title: '会员专区',
      dataSource: { mode: 'automatic', sort: 'create_time', targetIds: [] },
    },
  ]
}

function normalizeTabs(raw: unknown): GoodsTabConfig[] {
  const list = Array.isArray(raw) ? raw : []
  const tabs = list.map((item, index) => {
    const record = item && typeof item === 'object'
      ? item as Record<string, unknown>
      : {}
    const source = record.dataSource && typeof record.dataSource === 'object'
      ? record.dataSource as Record<string, unknown>
      : {}
    return {
      id: String(record.id || `tab-${index + 1}`),
      title: String(record.title || `Tab ${index + 1}`),
      dataSource: {
        categoryId: String(source.categoryId || ''),
        mode: source.mode === 'manual' ? 'manual' : 'automatic',
        sort: String(source.sort || 'sales'),
        targetIds: Array.isArray(source.targetIds)
          ? source.targetIds.map(value => String(value))
          : [],
      },
    } as GoodsTabConfig
  })
  return tabs.length > 0 ? tabs : defaultTabs()
}

const showData = computed<GoodsTabsProps>(() => ({
  columns: Number(props.showData.columns) === 3 ? 3 : 2,
  commonStyle: props.showData.commonStyle || retailCommonStyle,
  count: Math.max(1, Number(props.showData.count) || 10),
  emptyStrategy: props.showData.emptyStrategy || 'placeholder',
  invalidStrategy: props.showData.invalidStrategy || 'hide',
  showOriginalPrice: Boolean(props.showData.showOriginalPrice),
  showPrice: props.showData.showPrice !== false,
  showSales: props.showData.showSales !== false,
  stickyTabs: props.showData.stickyTabs !== false,
  tabs: normalizeTabs(props.showData.tabs),
}))

const dynamicStyles = useDiyStyle(computed(() => showData.value.commonStyle))
const gridStyle = computed(() => ({
  gridTemplateColumns: `repeat(${showData.value.columns}, minmax(0, 1fr))`,
}))
const pageSize = computed(() => showData.value.count)

const tabs = computed(() => showData.value.tabs)
const activeIndex = ref(0)
const activeTab = computed(() => tabs.value[activeIndex.value] || tabs.value[0])
const goodsList = ref<any[]>([])
const pagingRef = ref()
const instance = getCurrentInstance()

interface FeedState {
  hasMore: boolean
  items: any[]
  loaded: boolean
  nextPage: number
}

/**
 * 每个 Tab 一份 feed 状态。
 *
 * 只换当前 Tab 的数据，不重建组件：切回已加载过的 Tab 时直接复用缓存，
 * 翻页游标 nextPage 也按 Tab 独立推进。
 */
const feedStates = reactive<Record<string, FeedState>>({})
/**
 * 切 Tab 时临时保留旧列表高度。
 *
 * z-paging 的 reload 会先清空列表，页面高度随之骤降；在页面滚动模式下，
 * 浏览器会把超出新内容高度的滚动位置夹回页面顶部，表现为「切 Tab 就回顶」。
 * 高度只在首次查询返回后释放，避免用户在加载瞬间被拉走。
 */
const feedMinHeight = ref(0)
let switchingTab = false
const anchorId = `goods-tabs-anchor-${Math.random().toString(36).slice(2, 8)}`
const stickyOffsetPx = ref(0)

function stateOf(tabId: string) {
  if (!feedStates[tabId]) {
    feedStates[tabId] = {
      hasMore: true,
      items: [],
      loaded: false,
      nextPage: 1,
    }
  }
  return feedStates[tabId]
}

// 页面下拉刷新会重新下发 JSON，组件实例按 id 复用；显式清空各 Tab 缓存并重载
watch(
  () => [props.showData.tabs, props.showData.count],
  () => {
    Object.keys(feedStates).forEach((key) => {
      delete feedStates[key]
    })
    if (activeIndex.value >= tabs.value.length)
      activeIndex.value = 0
    pagingRef.value?.reload?.()?.catch?.(() => {})
  },
  { deep: true },
)

// 自动续页：页面滚动接近底部时只加载当前 Tab 的下一页
const { runQuery } = useDiyPagedLoadMore(pagingRef, '.goods-tabs')

let querySequence = 0

async function queryList(pageNo: number, pageSize: number) {
  const tab = activeTab.value
  if (!tab)
    return
  const state = stateOf(tab.id)
  const sequence = ++querySequence
  const isCurrent = () =>
    sequence === querySequence && tab.id === activeTab.value.id

  try {
    await runQuery(async () => {
      // 切回已加载过的 Tab：直接用缓存恢复，不重复请求第一页
      if (pageNo === 1 && state.loaded) {
        await pagingRef.value?.completeByNoMore([...state.items], !state.hasMore)
        return
      }

      // 恢复缓存后 z-paging 的 pageNo 会回到 1，真实下一面以 Tab 自己的游标为准
      const currentPage = pageNo === 1 ? 1 : state.nextPage
      const result = await loadGoodsTabPage(tab, currentPage, pageSize)
      if (!isCurrent())
        return

      let pageItems = result.list
      let reachedLimit = false
      if (currentPage === 1) {
        state.items = [...pageItems]
      }
      else {
        const merged = mergePagedItems(state.items, pageItems)
        pageItems = merged.items
        reachedLimit = merged.reachedLimit
        state.items = [...state.items, ...pageItems]
      }

      state.loaded = true
      state.nextPage = currentPage + 1
      const nomore = result.manual
        || reachedLimit
        || result.list.length === 0
        || (result.total > 0 && state.items.length >= result.total)
      state.hasMore = !nomore
      await pagingRef.value?.completeByNoMore(pageItems, nomore)
    }, isCurrent)
  }
  finally {
    if (switchingTab && sequence === querySequence) {
      switchingTab = false
      // 等新列表完成一次渲染再释放占位，避免同一帧内高度先塌后涨
      await nextTick()
      feedMinHeight.value = 0
    }
  }
}

function measureFeedHeight(): Promise<number> {
  return new Promise((resolve) => {
    uni.createSelectorQuery()
      .in(instance?.proxy ?? instance)
      .select('.goods-tabs__inner')
      .boundingClientRect((raw) => {
        const rect = (Array.isArray(raw) ? raw[0] : raw) as { height?: number } | null
        resolve(Number(rect?.height || 0))
      })
      .exec()
  })
}

function measureTabPosition(): Promise<{ barTop: number, rootTop: number }> {
  return new Promise((resolve) => {
    const query = uni.createSelectorQuery().in(instance?.proxy ?? instance)
    query.select('.goods-tabs').boundingClientRect()
    query.select('.goods-tabs__bar').boundingClientRect()
    query.exec((raw) => {
      const result = Array.isArray(raw) ? raw : []
      const root = result[0] as { top?: number } | null
      const bar = result[1] as { top?: number } | null
      resolve({
        barTop: Number(bar?.top ?? 0),
        rootTop: Number(root?.top ?? 0),
      })
    })
  })
}

async function switchTab(index: number) {
  if (index === activeIndex.value || !tabs.value[index])
    return

  const { barTop, rootTop } = await measureTabPosition()
  const paddingTop = Number(
    (showData.value.commonStyle as Record<string, unknown> | undefined)
      ?.styleTopPadding || 0,
  )
  // 栏顶高于「根节点 + 顶部内边距」说明已离开自然位置、处于吸顶状态
  const barStuck = showData.value.stickyTabs && barTop < rootTop + paddingTop - 0.5
  if (barStuck) {
    stickyOffsetPx.value = Math.max(0, barTop - paddingTop)
    await nextTick()
    // 锚点悬在根节点上方，滚到锚点即让 Tab 栏停在吸顶位置
    uni.pageScrollTo({ selector: `#${anchorId}`, duration: 200 })
  }

  const height = await measureFeedHeight()
  if (height > 0)
    feedMinHeight.value = height
  switchingTab = true
  activeIndex.value = index
  await nextTick()
  // 只重置当前 Tab 的数据；未吸顶时不主动滚动，保持用户当前页面位置
  pagingRef.value?.reload?.()?.catch?.(() => {})
}

function openGoods(id: string) {
  uni.navigateTo({ url: `/sub-pages/product/goods-detail/index?id=${id}` })
}

/**
 * 卡片信息 chip：规格 / 低库存。
 *
 * 运费（免配送费）不在商品卡片上展示，口径统一以商详 / 结算页为准。
 */
function cardTags(item: any): string[] {
  const tags: string[] = []
  if (item.specsInfo)
    tags.push(String(item.specsInfo))
  const stock = Number(item.stock)
  if (stock > 0 && stock <= 10)
    tags.push(`仅剩 ${stock} 件`)
  return tags
}
</script>

<template>
  <view class="goods-tabs" :style="dynamicStyles">
    <view
      :id="anchorId"
      class="goods-tabs__anchor"
      :style="{ top: `-${stickyOffsetPx}px` }"
    />
    <view
      class="goods-tabs__bar"
      :class="{ 'goods-tabs__bar--sticky': showData.stickyTabs }"
    >
      <view
        v-for="(tab, index) in tabs"
        :key="tab.id"
        class="goods-tabs__item"
        :class="{ 'goods-tabs__item--active': index === activeIndex }"
        @click="switchTab(index)"
      >
        <text class="goods-tabs__label">
          {{ tab.title }}
        </text>
        <view v-if="index === activeIndex" class="goods-tabs__indicator" />
      </view>
    </view>

    <view
      class="goods-tabs__inner"
      :style="feedMinHeight > 0 ? { minHeight: `${feedMinHeight}px` } : undefined"
    >
      <!--
        use-page-scroll：复用装修页页面滚动，由 useDiyPagedLoadMore 测自身底边位置，
        接近底部前自动加载当前 Tab 的下一页（不出现「点击加载更多」）。
        refresher-enabled=false：装修页整体已有下拉刷新，避免与 z-paging 下拉冲突。
      -->
      <z-paging
        ref="pagingRef"
        v-model="goodsList"
        use-page-scroll
        :refresher-enabled="false"
        :default-page-size="pageSize"
        :auto-scroll-to-top-when-reload="false"
        loading-more-default-text=""
        @query="queryList"
      >
        <view class="goods-tabs__grid" :style="gridStyle">
          <view
            v-for="item in goodsList"
            :key="item.id"
            class="goods-tabs__card"
            @click="openGoods(item.id)"
          >
            <view class="goods-tabs__image-box">
              <image
                v-if="item.imageUrl"
                class="goods-tabs__image"
                :src="item.imageUrl"
                mode="aspectFit"
                lazy-load
              />
              <view v-else class="goods-tabs__image goods-tabs__image--empty">
                商品
              </view>
              <view v-if="item.stock <= 0" class="goods-tabs__sold-out">
                暂时缺货
              </view>
            </view>
            <view class="goods-tabs__name">
              {{ item.name }}
            </view>
            <view v-if="cardTags(item).length > 0" class="goods-tabs__tags">
              <text
                v-for="tag in cardTags(item)"
                :key="tag"
                class="goods-tabs__tag"
              >
                {{ tag }}
              </text>
            </view>
            <view class="goods-tabs__meta">
              <view class="goods-tabs__meta-text">
                <view class="goods-tabs__price-line">
                  <text v-if="showData.showPrice" class="goods-tabs__price">
                    ¥{{ item.price.toFixed(2) }}
                  </text>
                  <text
                    v-if="showData.showOriginalPrice
                      && shouldShowOriginalPrice(item.price, item.originalPrice)"
                    class="goods-tabs__price-original"
                  >
                    ¥{{ item.originalPrice.toFixed(2) }}
                  </text>
                </view>
                <text v-if="showData.showSales" class="goods-tabs__sales">
                  已售 {{ item.sales }}
                </text>
              </view>
              <quick-cart-button v-if="item.stock > 0" :spu-id="item.id" size="52rpx" />
            </view>
          </view>
        </view>
        <template #empty>
          <view class="goods-tabs__empty">
            暂无商品
          </view>
        </template>
      </z-paging>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.goods-tabs {
  position: relative;
}

/* 滚动锚点：top 由切 Tab 时的吸顶偏移动态写入，滚到它即可让 Tab 栏停在吸顶位置 */
.goods-tabs__anchor {
  position: absolute;
  left: 0;
  width: 1rpx;
  height: 1rpx;
  opacity: 0;
  pointer-events: none;
}

.goods-tabs__bar {
  display: flex;
  align-items: center;
  height: 88rpx;
  background: #fff;
  border-bottom: 1rpx solid #f2f3f5;
}

/* 吸顶偏移由装修页通过 --diy-sticky-top 注入（避开固定导航栏；内嵌场景为 0） */
.goods-tabs__bar--sticky {
  position: sticky;
  top: var(--diy-sticky-top, 0);
  z-index: 10;
}

.goods-tabs__item {
  position: relative;
  display: flex;
  flex: 1;
  height: 100%;
  align-items: center;
  justify-content: center;
  padding: 0 12rpx;
  color: #4e5969;
  font-size: 30rpx;
}

.goods-tabs__item--active {
  color: #1f2329;
  font-weight: 600;
}

.goods-tabs__indicator {
  position: absolute;
  bottom: 10rpx;
  left: 50%;
  width: 40rpx;
  height: 6rpx;
  margin-left: -20rpx;
  border-radius: 999rpx;
  background: var(--wot-color-theme-primary, #ff2237);
}

.goods-tabs__inner {
  padding: 16rpx 20rpx 20rpx;
}

.goods-tabs__grid {
  display: grid;
  gap: 16rpx;
}

.goods-tabs__card {
  overflow: hidden;
  box-sizing: border-box;
  border: 1rpx solid #eef0f3;
  border-radius: 16rpx;
  background: #fff;
}

/* 小程序 <image> 自带默认宽高，用 padding-top 百分比把图片区锁成 1:1 */
.goods-tabs__image-box {
  position: relative;
  width: 100%;
  padding-top: 100%;
  background: #fff;
}

.goods-tabs__image {
  position: absolute;
  top: 0;
  left: 0;
  display: flex;
  width: 100%;
  height: 100%;
  align-items: center;
  justify-content: center;
  color: #a8abb2;
  font-size: 24rpx;
}

.goods-tabs__image--empty {
  background: #f3f4f6;
}

.goods-tabs__sold-out {
  position: absolute;
  top: 0;
  right: 0;
  left: 0;
  padding: 8rpx;
  background: rgb(0 0 0 / 50%);
  color: #fff;
  font-size: 22rpx;
  text-align: center;
}

.goods-tabs__name {
  display: -webkit-box;
  overflow: hidden;
  min-height: 2.8em;
  margin: 14rpx 14rpx 0;
  color: #303133;
  font-size: 28rpx;
  line-height: 1.4;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.goods-tabs__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6rpx;
  padding: 8rpx 14rpx 0;
}

.goods-tabs__tag {
  padding: 2rpx 8rpx;
  border-radius: 6rpx;
  background: #f2f3f5;
  color: #8a97a6;
  font-size: 20rpx;
  line-height: 1.5;
}

.goods-tabs__meta {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 8rpx;
  padding: 12rpx 14rpx 14rpx;
}

.goods-tabs__meta-text {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.goods-tabs__price-line {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 6rpx;
  min-width: 0;
}

.goods-tabs__price {
  color: var(--wot-color-theme-primary, #ff2237);
  font-size: 32rpx;
  font-weight: 600;
}

.goods-tabs__price-original {
  flex: none;
  color: #999;
  font-size: 22rpx;
  font-weight: normal;
  text-decoration: line-through;
}

.goods-tabs__sales {
  color: #909399;
  font-size: 20rpx;
}

.goods-tabs__empty {
  padding: 60rpx 0;
  color: #999;
  font-size: 26rpx;
  text-align: center;
}
</style>

<script setup lang="ts">
import type { FacetCategory, FacetPriceRange, SearchProductItem, SuggestItem } from '@/api/product/search'
import { searchProducts, searchSuggest } from '@/api/product/search'
import { getTop10HotSearchGoods } from '@/api/product/spu'
import HrSearchNavbar from '@/components/hr-search-navbar/index.vue'

definePage({
  name: 'goods-search',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '商品搜索',
  },
})

const { confirm } = useGlobalMessage()
const router = useRouter()
const { historyList, addSearchHistory, clearSearchHistory } = useSearchHistory()

// === 状态 ===
const keyword = ref('')
const isSearched = ref(false)
const loading = ref(false)
const suggestList = ref<SuggestItem[]>([])
const suggestVisible = ref(false)
let suggestTimer: ReturnType<typeof setTimeout> | null = null

// 搜索结果
const searchResult = ref<SearchProductItem[]>([])
const searchTotal = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const hasMore = ref(true)

// 分面筛选
const facetCategories = ref<FacetCategory[]>([])
const facetPriceRanges = ref<FacetPriceRange[]>([])
const selectedCategory = ref('')
const selectedPriceRange = ref('')
const filterDrawerVisible = ref(false)

// 排序
const sortField = ref('')

// 热搜
const hotSearchList = ref<any[]>([])
const defaultHotWords = ['手机', '笔记本电脑', '耳机', '平板', '智能手表', '充电宝', '键盘', '显示器', '路由器', '音箱']

// === 生命周期 ===
onLoad(async (options) => {
  if (options?.keyword && options?.keyword !== 'undefined') {
    keyword.value = options.keyword
    doSearch(keyword.value)
  }
  getHotSearch()
})

// === 联想词（防抖300ms） ===
function onInputChange(val: string) {
  keyword.value = val
  if (!val.trim()) {
    suggestList.value = []
    suggestVisible.value = false
    return
  }
  if (suggestTimer) clearTimeout(suggestTimer)
  suggestTimer = setTimeout(async () => {
    try {
      const res = await searchSuggest(val.trim())
      suggestList.value = res || []
      suggestVisible.value = suggestList.value.length > 0
    }
    catch {
      suggestList.value = []
      suggestVisible.value = false
    }
  }, 300)
}

function selectSuggest(item: SuggestItem) {
  suggestVisible.value = false
  keyword.value = item.text
  doSearch(item.text)
}

// === 搜索 ===
async function doSearch(name: any) {
  const val = typeof name === 'string' ? name : name?.value
  if (!val?.trim()) return
  keyword.value = val
  addSearchHistory(val.trim())
  suggestVisible.value = false
  isSearched.value = true
  currentPage.value = 1
  searchResult.value = []
  hasMore.value = true
  await fetchSearchResult()
}

async function fetchSearchResult() {
  loading.value = true
  try {
    const params: Record<string, any> = { q: keyword.value.trim(), page: currentPage.value, limit: pageSize.value }
    if (selectedCategory.value) params.category = selectedCategory.value
    if (selectedPriceRange.value) params.price_range = selectedPriceRange.value
    if (sortField.value) params.sort = sortField.value
    const res = await searchProducts(params)
    if (currentPage.value === 1) {
      searchResult.value = res.records || []
    } else {
      searchResult.value.push(...(res.records || []))
    }
    searchTotal.value = res.total || 0
    hasMore.value = searchResult.value.length < searchTotal.value
    if (res.facets) {
      facetCategories.value = res.facets.categories || []
      facetPriceRanges.value = res.facets.priceRanges || []
    }
  }
  catch { /* 静默处理 */ }
  finally { loading.value = false }
}

function loadMore() {
  if (!hasMore.value || loading.value) return
  currentPage.value++
  fetchSearchResult()
}

// === 分面筛选 ===
function selectCategoryFilter(catId: string) {
  selectedCategory.value = selectedCategory.value === catId ? '' : catId
}
function selectPriceRangeFilter(range: string) {
  selectedPriceRange.value = selectedPriceRange.value === range ? '' : range
}
function applyFilter() {
  filterDrawerVisible.value = false
  currentPage.value = 1
  searchResult.value = []
  hasMore.value = true
  fetchSearchResult()
}
function resetFilter() {
  selectedCategory.value = ''
  selectedPriceRange.value = ''
}

// === 排序 ===
function changeSort(field: string) {
  sortField.value = sortField.value === field ? '' : field
  currentPage.value = 1
  searchResult.value = []
  hasMore.value = true
  fetchSearchResult()
}

// === 热搜 ===
async function getHotSearch() {
  try {
    const res = await getTop10HotSearchGoods()
    hotSearchList.value = res || []
  }
  catch { hotSearchList.value = [] }
}

// === 搜索历史 ===
function delAll() {
  confirm({
    title: '提示',
    msg: '该操作将清空搜索历史是否继续？',
    closeOnClickModal: false,
    success: (res: any) => { if (res.action === 'confirm') clearSearchHistory() },
  })
}

function toGoods(id: string) {
  router.push({ name: 'goods-detail', params: { id } })
}

const hotWords = computed(() => hotSearchList.value.length > 0 ? hotSearchList.value.map((h: any) => h.name) : defaultHotWords)
</script>

<template>
  <hr-search-navbar v-model="keyword" placeholder="搜索商品" :placeholder-left="true" :focus="true" @search="doSearch" />
  <view class="page-wrapper">
    <!-- 联想词下拉 -->
    <view v-if="suggestVisible" class="suggest-panel">
      <view v-for="(item, index) in suggestList" :key="index" class="suggest-item" @click="selectSuggest(item)">
        <wd-icon name="search" size="28rpx" color="#999" />
        <text class="flex-1 ml-12rpx text-26rpx text-[#333]">{{ item.text }}</text>
        <text class="text-22rpx text-[#999] bg-[#f5f5f5] px-12rpx py-2rpx rounded-8rpx">
          {{ item.type === 'category' ? '分类' : item.type === 'product' ? '商品' : '关键词' }}
        </text>
      </view>
    </view>

    <!-- 未搜索状态 -->
    <template v-if="!isSearched">
      <!-- 搜索历史 -->
      <view class="p-2">
        <view class="flex items-center justify-between">
          <view class="text-sm">搜索历史</view>
          <wd-icon v-if="historyList && historyList.length > 0" name="delete-thin" size="28rpx" @click="delAll" />
        </view>
        <template v-if="historyList && historyList.length > 0">
          <view class="flex flex-wrap pt-2">
            <view v-for="(item, index) in historyList" :key="index" class="p-1">
              <wd-tag round bg-color="#f2f2f2" color="#3a3a3a" @click="doSearch({ value: item })">{{ item }}</wd-tag>
            </view>
          </view>
        </template>
        <template v-else>
          <view class="flex items-center justify-center pt-2 text-xs">暂无搜索历史</view>
        </template>
      </view>
      <!-- 热搜榜 -->
      <view class="p-2">
        <view class="mb-2 flex items-center"><text class="text-sm font-bold">热搜榜</text></view>
        <view class="flex flex-wrap gap-10rpx">
          <view v-for="(item, index) in hotWords" :key="index" class="p-1">
            <wd-tag round :bg-color="index < 3 ? '#fff0f0' : '#f2f2f2'" :color="index < 3 ? '#ff2237' : '#3a3a3a'" @click="doSearch({ value: item })">
              <text v-if="index < 3" class="mr-4rpx font-bold">{{ index + 1 }}</text>{{ item }}
            </wd-tag>
          </view>
        </view>
      </view>
    </template>

    <!-- 搜索结果 -->
    <template v-else>
      <!-- 排序栏 -->
      <view class="sort-bar">
        <view class="sort-item" :class="{ active: sortField === '' }" @click="changeSort('')">综合</view>
        <view class="sort-item" :class="{ active: sortField === 'sales_volume:desc' }" @click="changeSort('sales_volume:desc')">销量</view>
        <view class="sort-item" :class="{ active: sortField.includes('sales_price') }" @click="changeSort(sortField === 'sales_price:asc' ? 'sales_price:desc' : 'sales_price:asc')">
          价格 <wd-icon :name="sortField === 'sales_price:asc' ? 'arrow-up' : 'arrow-down'" size="20rpx" />
        </view>
        <view class="filter-btn" @click="filterDrawerVisible = !filterDrawerVisible"><wd-icon name="filter" size="28rpx" /> 筛选</view>
      </view>
      <!-- 已选筛选 -->
      <view v-if="selectedCategory || selectedPriceRange" class="flex flex-wrap gap-8rpx bg-[#fafafa] px-24rpx py-12rpx">
        <wd-tag v-if="selectedCategory" closable type="primary" size="small" @close="selectedCategory = ''">
          {{ facetCategories.find(c => c.id === selectedCategory)?.name || selectedCategory }}
        </wd-tag>
        <wd-tag v-if="selectedPriceRange" closable type="primary" size="small" @close="selectedPriceRange = ''">{{ selectedPriceRange }}</wd-tag>
      </view>
      <!-- 商品列表 -->
      <scroll-view scroll-y class="result-list" @scrolltolower="loadMore">
        <view v-if="searchResult.length === 0 && !loading" class="flex justify-center py-100rpx text-28rpx text-[#999]">未找到相关商品</view>
        <view class="flex flex-wrap gap-16rpx p-16rpx">
          <view v-for="item in searchResult" :key="item.id" class="goods-card" @click="toGoods(item.id)">
            <image :src="item.spuUrls?.[0]" class="w-full h-320rpx" mode="aspectFill" lazy-load />
            <view class="p-16rpx">
              <text class="goods-name">{{ item.name }}</text>
              <view class="flex items-baseline justify-between mt-12rpx">
                <text class="text-32rpx font-bold text-[#ff2237]">¥{{ item.salesPrice }}</text>
                <text class="text-22rpx text-[#999]">已售{{ item.salesVolume }}件</text>
              </view>
            </view>
          </view>
        </view>
        <view v-if="loading" class="flex items-center justify-center gap-8rpx py-24rpx text-24rpx text-[#999]"><wd-loading />加载中...</view>
        <view v-else-if="!hasMore && searchResult.length > 0" class="flex justify-center py-24rpx text-24rpx text-[#999]">没有更多了</view>
      </scroll-view>
    </template>

    <!-- 分面筛选侧栏 -->
    <wd-popup v-model="filterDrawerVisible" position="right" width="70%">
      <view class="flex h-full flex-col p-24rpx">
        <view class="border-b border-[#f0f0f0] pb-24rpx"><text class="text-32rpx font-bold">筛选</text></view>
        <view class="border-b border-[#f5f5f5] py-24rpx">
          <text class="mb-16rpx block text-28rpx font-bold text-[#333]">分类</text>
          <view class="flex flex-wrap gap-16rpx">
            <view v-for="cat in facetCategories" :key="cat.id" class="filter-option" :class="{ active: selectedCategory === cat.id }" @click="selectCategoryFilter(cat.id)">
              <text>{{ cat.name }}</text><text class="text-22rpx text-[#999]">{{ cat.count }}</text>
            </view>
          </view>
        </view>
        <view class="border-b border-[#f5f5f5] py-24rpx">
          <text class="mb-16rpx block text-28rpx font-bold text-[#333]">价格区间</text>
          <view class="flex flex-wrap gap-16rpx">
            <view v-for="(pr, idx) in facetPriceRanges" :key="idx" class="filter-option" :class="{ active: selectedPriceRange === pr.label }" @click="selectPriceRangeFilter(pr.label)">
              <text>{{ pr.label }}</text><text class="text-22rpx text-[#999]">{{ pr.count }}</text>
            </view>
          </view>
        </view>
        <view class="mt-auto flex gap-24rpx py-24rpx">
          <wd-button size="small" plain @click="resetFilter">重置</wd-button>
          <wd-button size="small" type="primary" @click="applyFilter">确定</wd-button>
        </view>
      </view>
    </wd-popup>
  </view>
</template>

<style lang="scss" scoped>
.page-wrapper { min-height: calc(100vh - var(--window-top)); background: #fff; }
.suggest-panel { position: fixed; top: calc(var(--window-top) + 44px); left: 0; right: 0; z-index: 999; background: #fff; border-bottom: 1px solid #eee; max-height: 400rpx; overflow-y: auto; }
.suggest-item { display: flex; align-items: center; padding: 16rpx 24rpx; border-bottom: 1px solid #f5f5f5; }
.sort-bar { display: flex; align-items: center; height: 80rpx; background: #fff; border-bottom: 1px solid #f0f0f0; padding: 0 16rpx; }
.sort-item { flex: 1; display: flex; align-items: center; justify-content: center; font-size: 26rpx; color: #666; &.active { color: var(--wot-color-theme-primary); font-weight: bold; } }
.filter-btn { flex: 0 0 auto; width: 120rpx; display: flex; align-items: center; justify-content: center; gap: 4rpx; font-size: 26rpx; color: #666; }
.result-list { height: calc(100vh - var(--window-top) - 44px - 80rpx); }
.goods-card { width: calc(50% - 8rpx); background: #fff; border-radius: 16rpx; overflow: hidden; box-shadow: 0 2rpx 12rpx rgba(0,0,0,0.06); }
.goods-name { font-size: 26rpx; color: #333; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; line-height: 1.4; }
.filter-option { display: flex; align-items: center; gap: 8rpx; padding: 12rpx 24rpx; background: #f5f5f5; border-radius: 12rpx; font-size: 26rpx; color: #333; &.active { background: rgba(255,34,55,0.1); color: #ff2237; } }
</style>

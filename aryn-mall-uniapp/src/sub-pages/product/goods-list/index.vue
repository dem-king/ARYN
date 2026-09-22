<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app'
/**
 * 商品列表页。
 *
 * 商品流（查询/排序/分页/卡片/快捷加购）统一由 `goods-list-panel` 提供，
 * 本页只负责导航栏、品牌筛选与入参解析。
 *
 * 入参兼容：
 *   · `categoryFirstId`  → 一级类目
 *   · `categorySecondId` → 二级类目
 *   · `categoryId`       → **二级类目的历史别名**，装修链接（link-resolver）
 *                          与优惠券/购物车跳转仍在用，不能移除
 */
import { onMounted, reactive, ref } from 'vue'
import { getList as getBrandList } from '@/api/product/brand'
import HrSearchNavbar from '@/components/hr-search-navbar/index.vue'

definePage({
  name: 'goods-list',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '商品列表',
  },
})

interface State {
  queryParams: {
    name: string
    brandId: string
  }
}

const router = useRouter()
const pagingRef = ref()
const layout = ref(true)
const brandList = ref<any[]>([])
const isCanBack = ref(true)
const keyword = ref('')
const categoryFirstId = ref('')
const categorySecondId = ref('')
const state = reactive<State>({
  queryParams: {
    name: '',
    brandId: '',
  },
})

onLoad((options) => {
  state.queryParams.name = options?.keyword ?? ''
  keyword.value = options?.keyword ?? ''
  categoryFirstId.value = options?.categoryFirstId ?? ''
  categorySecondId.value = options?.categorySecondId ?? options?.categoryId ?? ''
  getBrandList().then((res) => {
    brandList.value = res ?? []
  })
})

function handleSearch() {
  const pages = getCurrentPages()
  const isPrevSearch
    = pages.length > 1
      && pages[pages.length - 2]?.route === 'sub-pages/product/goods-search/index'
  if (isPrevSearch) {
    router.back()
  }
  else {
    router.push({ name: 'goods-search', params: { keyword: state.queryParams.name } })
  }
}

function toggleLayout() {
  layout.value = !layout.value
}

onMounted(() => {
  const pages = getCurrentPages()
  isCanBack.value = !(
    pages.length <= 1 || pages[pages.length - 1].route === 'pages/login/index'
  )
})
</script>

<template>
  <view class="goods-list-page">
    <hr-search-navbar
      v-model="state.queryParams.name"
      placeholder="搜索"
      :placeholder-left="true"
      :focus="false"
      :disabled="true"
      :search-btn="false"
      @search="handleSearch"
      @focus="handleSearch"
    />

    <view class="filter-bar">
      <scroll-view scroll-x class="brand-scroll" :show-scrollbar="false">
        <view class="brand-list">
          <view
            class="brand-item"
            :class="state.queryParams.brandId === '' ? 'brand-item--active' : ''"
            @click="state.queryParams.brandId = ''"
          >
            全部品牌
          </view>
          <view
            v-for="brand in brandList"
            :key="brand.id"
            class="brand-item"
            :class="state.queryParams.brandId === brand.id ? 'brand-item--active' : ''"
            @click="state.queryParams.brandId = brand.id"
          >
            {{ brand.name }}
          </view>
        </view>
      </scroll-view>
      <view class="layout-toggle" @click="toggleLayout">
        <wd-icon :name="layout ? 'app' : 'server'" size="40rpx" color="#666" />
      </view>
    </view>

    <view class="goods-panel-wrap">
      <goods-list-panel
        ref="pagingRef"
        :category-first-id="categoryFirstId"
        :category-second-id="categorySecondId"
        :keyword="keyword"
        :brand-id="state.queryParams.brandId"
        :grid="layout"
        show-sort
      />
    </view>
  </view>
</template>

<style lang="scss" scoped>
.goods-list-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - var(--window-bottom, 0px));
  overflow: hidden;
  background: #f6f6f6;
}

.filter-bar {
  display: flex;
  align-items: center;
  flex: none;
  padding: 12rpx 16rpx;
  background: #fff;
}

.brand-scroll {
  flex: 1;
  min-width: 0;
  white-space: nowrap;
}

.brand-list {
  display: inline-flex;
  align-items: center;
}

.brand-item {
  margin-right: 12rpx;
  padding: 10rpx 24rpx;
  font-size: 26rpx;
  color: #666;
  border-radius: 8rpx;

  &--active {
    background: var(--theme-color-primary, var(--wot-color-theme-primary));
    color: #fff;
  }
}

.layout-toggle {
  flex: none;
  width: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.goods-panel-wrap {
  flex: 1;
  min-height: 0;
}
</style>

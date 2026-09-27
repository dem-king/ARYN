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
 *   · `categoryId`       → 装修链接（link-resolver）/优惠券/购物车跳转的历史入参。
 *                          它可能是一级也可能是二级类目 id，进入本页后会先查分类树
 *                          判断层级，再映射到上面两个字段，不能盲目当二级用。
 */
import { onMounted, reactive, ref } from 'vue'
import { getList as getBrandList } from '@/api/product/brand'
import { getTree } from '@/api/product/category'
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
/** 历史 categoryId 层级解析完成前不渲染商品面板，避免先按「全部分类」空查一次 */
const categoryReady = ref(false)
const state = reactive<State>({
  queryParams: {
    name: '',
    brandId: '',
  },
})

onLoad(async (options) => {
  state.queryParams.name = options?.keyword ?? ''
  keyword.value = options?.keyword ?? ''
  categoryFirstId.value = options?.categoryFirstId ?? ''
  categorySecondId.value = options?.categorySecondId ?? ''

  // 历史装修链接（金刚区等）只传 categoryId，旧实现一律把它当二级类目。
  // 但金刚区实际配置的多是「水果」这类一级类目 id，用一级 id 去匹配
  // goods_spu.category_second_id 永远为空，导致点进去没数据。
  // 这里查一次分类树判断层级：一级 → categoryFirstId；二级 → categorySecondId（并回填 firstId）。
  if (!categoryFirstId.value && !categorySecondId.value && options?.categoryId)
    await resolveCategoryId(options.categoryId)

  categoryReady.value = true
  getBrandList().then((res) => {
    brandList.value = res ?? []
  })
})

/**
 * 把历史 categoryId 解析成正确的一级 / 二级类目入参。
 * 树接口返回 [{ id, name, children: [...] }]：
 *   · 命中顶层节点 → 它是一级类目
 *   · 命中某一级的 children → 它是二级类目，顺带把父级 id 填到 categoryFirstId，
 *     后端按 AND 组合结果不变，但查询更精确
 * 解析失败（脏数据 / 类目已删）时回退到历史行为：当作二级类目，保持不回归。
 */
async function resolveCategoryId(rawId: string) {
  try {
    const tree = await getTree()
    const list = tree ?? []
    for (const first of list) {
      if (String(first.id) === String(rawId)) {
        categoryFirstId.value = rawId
        categorySecondId.value = ''
        return
      }
      const second = (first.children ?? []).find((c: any) => String(c.id) === String(rawId))
      if (second) {
        categoryFirstId.value = first.id
        categorySecondId.value = second.id
        return
      }
    }
  }
  catch {
    // 树请求失败不阻塞页面，回退历史二级行为
  }
  categorySecondId.value = rawId
}

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
        v-if="categoryReady"
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

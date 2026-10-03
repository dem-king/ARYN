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
 *
 * 品牌条按**分面导航**语义渲染：只列「在当前分类/关键词下确有在售商品」的品牌。
 * 历史缺陷：这里拉的是全租户品牌列表（42 个）且与当前分类无关，而品牌在本租户
 * 是稀疏属性（272 件在售商品仅 62 件有品牌），水果等 7 个一级分类下全部商品无品牌 ——
 * 表现为「每个品牌点进去都是空列表」。因此改用 `/app/goodsbrand/filter-list`。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { getTree } from '@/api/product/category'
import HrSearchNavbar from '@/components/hr-search-navbar/index.vue'
import { getFilterList } from '@/sub-pages/api/product/brand'

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
const categoryLocateStore = useCategoryLocateStore()
const pagingRef = ref()
const layout = ref(true)
/** 当前条件下有货的品牌（分面结果），已由服务端排除零商品品牌 */
const brandOptions = ref<any[]>([])
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

/**
 * 无任何品牌可选时隐藏品牌条（该分类下品牌维度不存在，例如水果、海鲜水产）。
 *
 * 保留「全部品牌」之外整条不渲染，避免用一排点不出商品的选项占用首屏；
 * 布局切换按钮不属于品牌条，仍在下方常驻。
 */
const showBrandFilter = computed(() => brandOptions.value.length > 0)

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
  // 分类需先解析出层级，品牌分面才带得上正确条件（见 resolveCategoryId）
  loadBrandOptions()
})

/**
 * 加载当前条件下的品牌筛选项。
 *
 * 条件与商品查询同源同义，因此返回的每个品牌点进去都必然有商品。
 * 请求失败时保持空列表 —— 品牌条隐藏，商品流不受影响（品牌不是必需维度）。
 */
async function loadBrandOptions() {
  try {
    const params: { categoryFirstId?: string, categorySecondId?: string, name?: string } = {}
    if (categoryFirstId.value)
      params.categoryFirstId = categoryFirstId.value
    if (categorySecondId.value)
      params.categorySecondId = categorySecondId.value
    if (keyword.value)
      params.name = keyword.value
    brandOptions.value = (await getFilterList(params)) ?? []
  }
  catch (e) {
    brandOptions.value = []
    console.warn('[goods-list] 品牌筛选项加载失败，已隐藏品牌条', e)
  }
}

/**
 * 清空品牌筛选（空态里的出口）。
 *
 * 不重新拉品牌条：分面结果不含 brandId，重拉不会改变选项集合，
 * 反而多一次请求。
 */
function clearBrandFilter() {
  state.queryParams.brandId = ''
}

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
    // 必须用全量树（getTree 而非 getActiveTree）：这里做的是「id → 层级」解析，
    // 停用分类的历史链接也要能解出层级。过滤后停用一级会落入兜底分支被
    // 当成二级，categorySecondId 传一级 id 查出来恒空。
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

/**
 * 跳「分类」tab 页查看全部分类，并定位到当前分类。
 *
 * 本页由金刚区/装修链接带入单一分类，想换类目只能回首页再点；
 * 分类页是 tabBar 页，navigateTo 打不开，只能走 tab 跳转。
 *
 * 必须用 reLaunch 而非 switchTab：本页是 navigateTo 压在某个 tab（通常是首页）
 * 之上的堆叠页，微信的 switchTab 会先销毁本页露出栈底那个 tab、再切到目标 tab，
 * 两段式过渡表现为「先闪一下首页再进分类」。reLaunch 一次性关闭全部页面并
 * 直接打开分类页，单次过渡无中间帧。代价是页面栈清空（首页 tab 下次切回时
 * 重载），对本入口可接受。switchTab 带不了 query，目标分类经 categoryLocateStore
 * 中转，由分类页 onShow 消费后选中对应一级/二级。
 */
function goCategoryPage() {
  if (categoryFirstId.value || categorySecondId.value) {
    categoryLocateStore.setPendingLocate({
      categoryFirstId: categoryFirstId.value,
      categorySecondId: categorySecondId.value,
    })
  }
  uni.reLaunch({ url: '/pages/product/category/index' })
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

    <!--
      品牌条：只列当前条件下有货的品牌；一个都没有时整条隐藏（见 showBrandFilter）。
      布局切换按钮常驻，不能跟着品牌条一起消失，否则该分类下无法切换栅格/列表。
    -->
    <view class="filter-bar">
      <scroll-view v-if="showBrandFilter" scroll-x class="brand-scroll" :show-scrollbar="false">
        <view class="brand-list">
          <view
            class="brand-item"
            :class="state.queryParams.brandId === '' ? 'brand-item--active' : ''"
            @click="state.queryParams.brandId = ''"
          >
            全部品牌
          </view>
          <view
            v-for="brand in brandOptions"
            :key="brand.id"
            class="brand-item"
            :class="state.queryParams.brandId === brand.id ? 'brand-item--active' : ''"
            @click="state.queryParams.brandId = brand.id"
          >
            {{ brand.name }}
            <!-- 商品数：点之前就能判断该品牌有没有货（服务端已保证 ≥1） -->
            <text v-if="brand.goodsCount" class="brand-item__count">
              {{ brand.goodsCount }}
            </text>
          </view>
        </view>
      </scroll-view>
      <!-- 无品牌可选时把空间让给切换按钮，避免右侧留一条空白 -->
      <view v-else class="brand-scroll brand-scroll--empty" />
      <!-- 分类入口：查看全部类目（品牌条隐藏时也常驻，见 goCategoryPage） -->
      <view class="category-entry" @click="goCategoryPage">
        全部分类
        <wd-icon name="arrow-right" size="24rpx" color="#666" />
      </view>
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
        :clear-filter="clearBrandFilter"
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

  /* 品牌条隐藏时的占位：仍占据可伸缩空间，把切换按钮推到右侧 */
  &--empty {
    display: block;
  }
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

    /* 选中态底色由主题色填充，计数需换成低对比的同色系前景 */
    .brand-item__count {
      color: rgb(255 255 255 / 75%);
    }
  }

  &__count {
    margin-left: 6rpx;
    font-size: 22rpx;
    color: #b0b3b8;
  }
}

.category-entry {
  flex: none;
  display: flex;
  align-items: center;
  margin: 0 12rpx 0 16rpx;
  padding: 10rpx 20rpx;
  font-size: 26rpx;
  color: #666;
  white-space: nowrap;
  background: #f6f6f6;
  border-radius: 8rpx;
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

<script setup lang="ts">
/**
 * 可复用的商品流面板。
 *
 * 分类页右侧、商品列表页共用同一份「查询 + 排序 + 分页 + 卡片 + 快捷加购」实现，
 * 避免两处各写一套分页/排序/错误处理导致口径漂移。
 *
 * 类目过滤语义：
 *   · `categoryFirstId`  → 一级类目（后端 `selectApiPage` 支持该字段）
 *   · `categorySecondId` → 二级类目
 *   两者可同时传，后端按 AND 组合。
 *
 * 面板只负责「展示与请求」，不持有左侧 rail / 顶部图标条的状态；
 * 父组件通过 props 变化或 `refresh()` 驱动重新查询。
 */
import { computed, ref, watch } from 'vue'

import { getPage } from '@/api/product/spu'
import GoodsDetailSheet from '@/components/goods-detail-sheet/index.vue'
import QuickCartButton from '@/components/quick-cart-button/index.vue'

interface Props {
  /** 一级类目 ID，为空表示不限 */
  categoryFirstId?: string
  /** 二级类目 ID，为空表示不限 */
  categorySecondId?: string
  /** 关键词，用于搜索结果页复用 */
  keyword?: string
  /** 品牌 ID，为空表示不限 */
  brandId?: string
  /** 是否显示排序条 */
  showSort?: boolean
  /** 栅格 / 列表两种卡片布局 */
  grid?: boolean
  /**
   * 点击商品时从底部弹出详情面板（参考小象超市），而非跳转整页详情。
   * 默认 false 保持整页跳转；分类页等需要快捷加购的场景开启。
   */
  detailSheet?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  categoryFirstId: '',
  categorySecondId: '',
  keyword: '',
  brandId: '',
  showSort: true,
  grid: true,
  detailSheet: false,
})

const router = useRouter()
const pagingRef = ref()
const goodsList = ref<any[]>([])
const priceSort = ref(0)
const salesSort = ref(0)
const sortMode = ref<'default' | 'newGoods'>('default')
const pageOrder = ref<{ asc?: string, desc?: string }>({})
/** 底部详情弹层 */
const detailVisible = ref(false)
const currentSpuId = ref('')

async function queryList(pageNo: number, pageSize: number) {
  try {
    const response = await getPage({
      current: pageNo,
      size: pageSize,
      categoryFirstId: props.categoryFirstId || undefined,
      categorySecondId: props.categorySecondId || undefined,
      name: props.keyword || undefined,
      brandId: props.brandId || undefined,
      ...pageOrder.value,
    })
    pagingRef.value?.complete(response?.records ?? [])
  }
  catch {
    // 失败时结束本次分页，避免 z-paging 一直停留在加载中
    pagingRef.value?.complete(false)
  }
}

/** 切换筛选条件后重置到第一页 */
function refresh() {
  pagingRef.value?.reload()
}

defineExpose({ refresh })

watch(
  () => [props.categoryFirstId, props.categorySecondId, props.keyword, props.brandId],
  () => {
    pageOrder.value = {}
    priceSort.value = 0
    salesSort.value = 0
    sortMode.value = 'default'
    refresh()
  },
)

function sortHandler(val: 'default' | 'sales' | 'price' | 'newGoods') {
  pageOrder.value = {}
  switch (val) {
    case 'sales':
      pageOrder.value = salesSort.value > 0 ? { desc: 'sales_volume' } : { asc: 'sales_volume' }
      priceSort.value = 0
      sortMode.value = 'default'
      break
    case 'price':
      pageOrder.value = priceSort.value > 0 ? { desc: 'sales_price' } : { asc: 'sales_price' }
      salesSort.value = 0
      sortMode.value = 'default'
      break
    case 'newGoods':
      pageOrder.value = { desc: 'create_time' }
      priceSort.value = 0
      salesSort.value = 0
      sortMode.value = 'newGoods'
      break
    default:
      priceSort.value = 0
      salesSort.value = 0
      sortMode.value = 'default'
      break
  }
  refresh()
}

function toDetail(id: string) {
  if (props.detailSheet) {
    currentSpuId.value = id
    detailVisible.value = true
    return
  }
  router.push({ name: 'goods-detail', params: { id } })
}

const cardClass = computed(() => (props.grid ? 'goods-card goods-card--grid' : 'goods-card goods-card--row'))
</script>

<template>
  <view class="goods-list-panel">
    <z-paging
      ref="pagingRef"
      v-model="goodsList"
      :fixed="false"
      :safe-area-inset-bottom="false"
      @query="queryList"
    >
      <template v-if="showSort" #top>
        <view class="sort-bar">
          <view
            class="sort-item"
            :class="sortMode === 'default' ? 'sort-item--active' : ''"
            @click="sortHandler('default')"
          >
            综合推荐
          </view>
          <view class="sort-item">
            <wd-sort-button v-model="salesSort" title="销量" @change="sortHandler('sales')" />
          </view>
          <view class="sort-item">
            <wd-sort-button v-model="priceSort" title="价格" @change="sortHandler('price')" />
          </view>
          <view
            class="sort-item"
            :class="sortMode === 'newGoods' ? 'sort-item--active' : ''"
            @click="sortHandler('newGoods')"
          >
            新品
          </view>
        </view>
      </template>

      <view class="goods-list" :class="grid ? 'goods-list--grid' : 'goods-list--row'">
        <view
          v-for="item in goodsList"
          :key="item.id"
          :class="cardClass"
          @click="toDetail(item.id)"
        >
          <image
            class="goods-pic"
            :class="grid ? 'goods-pic--grid' : 'goods-pic--row'"
            :src="item.spuUrls?.[0]"
            mode="aspectFill"
          />
          <view class="goods-meta" :class="grid ? '' : 'goods-meta--row'">
            <view class="goods-name">
              {{ item.name }}
            </view>
            <view class="goods-bottom">
              <view class="goods-price">
                <text class="goods-price-symbol">
                  ¥
                </text>
                <text class="goods-price-value">
                  {{ item.salesPrice }}
                </text>
              </view>
              <quick-cart-button :spu-id="item.id" size="44rpx" />
            </view>
          </view>
        </view>
      </view>
    </z-paging>

    <goods-detail-sheet v-model="detailVisible" :spu-id="currentSpuId" />
  </view>
</template>

<style lang="scss" scoped>
.goods-list-panel {
  height: 100%;
}

.sort-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 88rpx;
  padding: 0 8rpx;
  background: #fff;
  font-size: 28rpx;
  color: #666;

  .sort-item {
    display: flex;
    flex: 1;
    align-items: center;
    justify-content: center;

    &--active {
      color: var(--theme-color-primary, var(--wot-color-theme-primary));
      font-weight: 600;
    }
  }
}

.goods-list {
  padding: 16rpx;

  &--row {
    padding: 0;
  }
}

.goods-list--grid {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.goods-card {
  background: #fff;
  overflow: hidden;
}

.goods-card--grid {
  width: calc(50% - 8rpx);
  border-radius: 16rpx;
}

.goods-card--row {
  display: flex;
  padding: 16rpx;
  border-bottom: 1rpx solid #f2f2f2;
}

.goods-pic--grid {
  display: block;
  width: 100%;
  height: 320rpx;
}

.goods-pic--row {
  width: 240rpx;
  height: 240rpx;
  flex: none;
  border-radius: 12rpx;
}

.goods-meta {
  padding: 16rpx;
}

.goods-meta--row {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.goods-name {
  font-size: 28rpx;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.goods-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 12rpx;
}

.goods-price {
  color: #ff2237;
  font-weight: 600;

  .goods-price-symbol {
    font-size: 22rpx;
  }

  .goods-price-value {
    font-size: 32rpx;
  }
}
</style>

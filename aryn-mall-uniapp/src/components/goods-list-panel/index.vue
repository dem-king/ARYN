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
import GoodsPickCheckbox from '@/components/goods-pick-checkbox/index.vue'
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
    // eslint-disable-next-line no-console
    console.log('[goods-list-panel] queryList', { pageNo, keys: Object.keys(response ?? {}), records: response?.records?.length, sample: response?.records?.[0] })
    pagingRef.value?.complete(response?.records ?? [])
  }
  catch (e) {
    // eslint-disable-next-line no-console
    console.warn('[goods-list-panel] queryList failed', e)
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

/**
 * 卡片信息 chip：规格 / 现货 / 免配送费。
 *
 * 列表接口只返回 SPU 字段，规格与库存不一定有；有才显示，不编造。
 * `freightType === '0'` 是商品级包邮，文案用「免配送费」而非「包邮」，
 * 避免与运费模板/满额免运费规则混淆。
 */
function cardTags(item: any): string[] {
  const tags: string[] = []
  if (item.specsInfo)
    tags.push(String(item.specsInfo))
  if (Number(item.stock) > 0)
    tags.push(`现货 ${item.stock}`)
  if (item.freightType === '0')
    tags.push('免配送费')
  return tags
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
      <!--
        顶部内容区（装修 banner + 排序条）排在**滚动区内**，随商品流一起滚动。

        排序条原先挂在 z-paging 的 `#top` 插槽上，而该插槽渲染在滚动容器之外
        （z-paging.vue 注释即「顶部固定的 slot」），装修 banner 只能排在它下方，
        与参考图「banner 在上、排序条在下」的次序相反。因此两者同置滚动区内：
        banner 随商品流移出，排序条用 sticky 维持常驻可见。

        容器不做 `v-if="$slots.banner"` 判断：uni-app 小程序端的 slot 检测不可靠，
        传了内容也可能判定为空而整块不渲染。空插槽的 view 无样式、高度为 0，
        无条件渲染没有副作用。
      -->
      <view class="list-banner">
        <slot name="banner" />
      </view>

      <view v-if="showSort" class="sort-bar">
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

      <view class="goods-list" :class="grid ? 'goods-list--grid' : 'goods-list--row'">
        <view
          v-for="(item, index) in goodsList"
          :key="item.id"
          :class="cardClass"
          :style="{ animationDelay: `${Math.min(index, 8) * 36}ms` }"
          hover-class="goods-card--pressed"
          :hover-stay-time="120"
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
            <!-- 信息 chip：规格 / 现货 / 免配送费（字段缺失时不渲染） -->
            <view v-if="cardTags(item).length > 0" class="goods-tags">
              <text v-for="tag in cardTags(item)" :key="tag" class="goods-tag">
                {{ tag }}
              </text>
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
              <view class="goods-actions">
                <goods-pick-checkbox :spu-id="item.id" size="36rpx" />
                <quick-cart-button :spu-id="item.id" size="44rpx" />
              </view>
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

/**
 * 装修 banner 容器（banner 插槽内容）。
 *
 * 只做「随商品流滚动」的定位，不加内边距：具体留白由各装修组件自带的
 * commonStyle 决定，这里再加一层会与编辑器里的预览值叠加、两端对不上。
 */
.list-banner {
  position: relative;
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
  /* 排序条排在滚动区内（见模板注释），靠 sticky 常驻顶部 */
  position: sticky;
  top: 0;
  z-index: 10;

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

/**
 * 两列走 grid 轨道均分，不要写成 flex-wrap + 卡宽 `calc(50% - gap/2)`。
 *
 * 后者算出来正好等于容器宽度的 100%、零余量；小程序把 rpx 换算成整数 px 时
 * 多出的零头会让 flex-wrap 判定「放不下」，表现成每行只剩一张卡、右侧半屏空白。
 * grid 先扣 gap 再分轨道宽度，不存在这个临界问题。
 */
.goods-list--grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16rpx;
}

.goods-card {
  background: #fff;
  overflow: hidden;
  /* 卡片逐项入场（按 index 错峰），backwards 保证结束后 :active 按压仍生效 */
  animation: goods-card-in 300ms ease-out backwards;
}

.goods-card--grid {
  border-radius: 16rpx;
  box-shadow: 0 4rpx 14rpx rgba(22, 34, 51, 0.05);
  transition: transform 160ms ease, box-shadow 160ms ease;

  &:active {
    transform: scale(0.98);
    box-shadow: 0 2rpx 8rpx rgba(22, 34, 51, 0.06);
  }
}

@keyframes goods-card-in {
  from {
    opacity: 0;
    transform: translateY(16rpx);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 按压反馈：H5 用 :active，小程序用 hover-class，两者效果一致 */
.goods-card--pressed {
  transform: scale(0.98);
}

.goods-card--row.goods-card--pressed {
  transform: none;
  background: #fafbfc;
}

.goods-card--row {
  display: flex;
  padding: 16rpx;
  border-bottom: 1rpx solid #f2f2f2;
  transition: background-color 160ms ease;

  &:active {
    background: #fafbfc;
  }
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

.goods-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6rpx;
  padding-top: 8rpx;
}

.goods-tag {
  padding: 2rpx 8rpx;
  border-radius: 4rpx;
  background: #f2f3f5;
  color: #8a97a6;
  font-size: 20rpx;
  line-height: 1.5;
}

.goods-actions {
  display: flex;
  flex: none;
  align-items: center;
  gap: 12rpx;
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

@media (prefers-reduced-motion: reduce) {
  .goods-card {
    animation: none;
    transition: none;
  }
}
</style>

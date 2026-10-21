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
import type { SharedCart } from '@/api/order/sharedCart'
import { computed, ref, watch } from 'vue'

import { getPage } from '@/api/product/spu'
import { shouldShowOriginalPrice } from '@/components/diy/price-display'
import { shouldShowSubtitle } from '@/components/diy/subtitle-display'
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
   * 是否在排序条右侧显示「双列 / 单列」切换按钮。
   *
   * 默认关闭：商品列表页已有独立的布局开关，重复展示会让用户面对两个入口；
   * 分类页没有那一行头部，由本按钮就地切换。
   */
  showLayoutToggle?: boolean
  /**
   * 点击商品时在**本面板区域内**从底部弹出详情弹层（参考小象超市：弹层与遮罩
   * 只盖住商品流，宿主页面的分类栏等保持可点），而非跳转整页详情。
   * 默认 false 保持整页跳转；分类页等需要快捷加购的场景开启。
   */
  detailSheet?: boolean
  /**
   * 空结果的「清除筛选」出口回调。
   *
   * 传了才在空态展示该按钮：面板不持有筛选状态（品牌选中项在父组件），
   * 因此只能由父组件清空并触发展开重查。分类页不传该项、也就没有这个入口。
   */
  clearFilter?: () => void
  /**
   * 共享购物车分类选货模式：卡片与详情弹层的加购都锁到该车，
   * 不再询问加入共享车还是个人车。为空时保持统一目的地弹层的行为。
   */
  sharedCart?: SharedCart | null
  /** 已成功加入当前共享车的 SPU：卡片左上角标「已加入」，减少重复加购 */
  addedSpuIds?: string[]
}

const props = withDefaults(defineProps<Props>(), {
  categoryFirstId: '',
  categorySecondId: '',
  keyword: '',
  brandId: '',
  showSort: true,
  grid: true,
  showLayoutToggle: false,
  detailSheet: false,
  clearFilter: undefined,
  sharedCart: null,
  addedSpuIds: () => [],
})

const emit = defineEmits<{
  (e: 'added', spuId: string): void
  (e: 'update:grid', value: boolean): void
}>()

function handleAdded(spuId: string) {
  emit('added', spuId)
}

/** 双列 / 单列切换：布局只影响卡片样式，不触发重新查询 */
function toggleLayout() {
  emit('update:grid', !props.grid)
}

const router = useRouter()
const pagingRef = ref()
const goodsList = ref<any[]>([])
const priceSort = ref(0)
const salesSort = ref(0)
const sortMode = ref<'default' | 'newGoods'>('default')
const pageOrder = ref<{ asc?: string, desc?: string }>({})
/** 本次查询是否失败：空态必须区分「没数据」与「请求失败」（见 #empty 插槽） */
const loadFailed = ref(false)
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
    loadFailed.value = false
    pagingRef.value?.complete(response?.records ?? [])
  }
  catch (e) {
    console.warn('[goods-list-panel] queryList failed', e)
    loadFailed.value = true
    // 失败时结束本次分页，避免 z-paging 一直停留在加载中
    pagingRef.value?.complete(false)
  }
}

/** 切换筛选条件后重置到第一页；旧商品的详情弹层一并收起，详情不属于新结果集 */
function refresh() {
  detailVisible.value = false
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
 * 卡片信息 chip：规格 / 低库存。
 *
 * 不再展示「现货」：能出现在商品流的都是上架在售商品，这枚 chip 没有信息量；
 * 商品介绍由后台「商品子标题」（subTitle）单独成行呈现，对齐参考产品的卡片结构。
 * 不再展示「免配送费」：运费口径统一以商详 / 结算页为准，卡片只留会直接影响
 * 下单决策的信息（低库存提醒），避免「卡片看着包邮、结算却收运费」的误导。
 * 列表接口只返回 SPU 字段，规格不一定有；有才显示，不编造。
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

/** 划线原价：存量商品常缺省为 0，只有严格高于售价时才展示（与商详/首页楼层同口径） */
function showOriginalPrice(item: any): boolean {
  return shouldShowOriginalPrice(item.salesPrice, item.originalPrice)
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
  <view class="goods-list-panel" :class="detailSheet ? 'goods-list-panel--sheet' : ''">
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
          <!-- 价格首次点击按电商习惯走低→高；desc-first 让首次值为 -1，落到 sortHandler 的 asc 分支 -->
          <wd-sort-button v-model="priceSort" title="价格" desc-first @change="sortHandler('price')" />
        </view>
        <view
          class="sort-item"
          :class="sortMode === 'newGoods' ? 'sort-item--active' : ''"
          @click="sortHandler('newGoods')"
        >
          新品
        </view>
        <!-- 布局切换：图标沿用商品列表页的 app / server 口径，仅分类页开启 -->
        <view
          v-if="showLayoutToggle"
          class="sort-layout-toggle"
          hover-class="sort-layout-toggle--pressed"
          :hover-stay-time="120"
          @click="toggleLayout"
        >
          <wd-icon :name="grid ? 'app' : 'server'" size="36rpx" color="#666" />
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
          <!--
            图片区锁 1:1 方框 + aspectFit 完整展示（与首页商品分组同一口径）。

            商品图源比例混杂（实测 800×533 横图 / 533×800 竖图 / 1:1 方图）：
            定高 + aspectFill 会裁掉边缘，看起来「图不完整」；
            高度随原图比例浮动（widthFix）又会让栅格同一行的矮卡下方空出页面底色，
            看起来像「商品之间多了一块留白」。方框 contain 两者兼得。
          -->
          <view class="goods-pic-box" :class="grid ? 'goods-pic-box--grid' : 'goods-pic-box--row'">
            <image class="goods-pic" :src="item.spuUrls?.[0]" mode="aspectFit" lazy-load />
            <!-- 共享选货页：已加购的商品留一个角标，长列表里避免同款重复加 -->
            <view v-if="addedSpuIds.includes(item.id)" class="goods-added-flag">
              已加入
            </view>
          </view>
          <view class="goods-meta" :class="grid ? '' : 'goods-meta--row'">
            <view class="goods-name">
              {{ item.name }}
            </view>
            <!-- 商品介绍：后台「商品子标题」字段，与首页楼层/商详同一数据源 -->
            <view v-if="shouldShowSubtitle(item.name, item.subTitle)" class="goods-desc">
              {{ item.subTitle }}
            </view>
            <!-- 信息 chip：规格 / 低库存（字段缺失时不渲染） -->
            <view v-if="cardTags(item).length > 0" class="goods-tags">
              <text v-for="tag in cardTags(item)" :key="tag" class="goods-tag">
                {{ tag }}
              </text>
            </view>
            <view class="goods-bottom">
              <view class="goods-price">
                <view class="goods-price-main">
                  <text class="goods-price-symbol">
                    ¥
                  </text>
                  <text class="goods-price-value">
                    {{ item.salesPrice }}
                  </text>
                </view>
                <text v-if="showOriginalPrice(item)" class="goods-price-original">
                  ¥{{ item.originalPrice }}
                </text>
              </view>
              <view class="goods-actions">
                <quick-cart-button
                  :spu-id="item.id"
                  :shared-cart="sharedCart"
                  size="56rpx"
                  @added="handleAdded"
                />
              </view>
            </view>
          </view>
        </view>
      </view>

      <!--
        空态：必须区分「请求失败」与「无数据」—— z-paging 默认文案是「没有数据哦~」，
        把加载失败也显示成没数据会让用户以为这个分类确实没商品（船供列表踩过同一个坑）。
        品牌筛选是「先选条件再收敛」的分面语义，选完可能落到空结果集，
        因此父组件传了 clearFilter 时给出清除筛选的出口，避免用户停在无路的死状态。
      -->
      <template #empty>
        <view class="list-empty">
          <wd-status-tip
            :image="loadFailed ? 'network' : 'content'"
            image-size="176rpx"
            :tip="loadFailed ? '加载失败，请检查网络后重试' : '当前筛选条件下暂无商品'"
          />
          <view
            v-if="loadFailed || (props.brandId && props.clearFilter)"
            class="list-empty__action"
            @tap="loadFailed ? pagingRef?.reload() : props.clearFilter?.()"
          >
            {{ loadFailed ? '重新加载' : '清除筛选' }}
          </view>
        </view>
      </template>
    </z-paging>

    <goods-detail-sheet
      v-model="detailVisible"
      :spu-id="currentSpuId"
      :shared-cart="sharedCart"
      @added="handleAdded"
    />
  </view>
</template>

<style lang="scss" scoped>
.goods-list-panel {
  height: 100%;
}

/**
 * detailSheet 模式下在本容器上常驻一个 transform，**故意**把 wd-popup（fixed 定位）
 * 的包含块从视口改成商品流右栏：详情弹层与遮罩只盖住右栏，左侧分类栏/顶部图标条
 * 保持可点，点分类由 refresh() 收起弹层并切换（对标小象超市，非全屏弹层）。
 * 必须常驻声明、不能靠页面入场动画 fill 残留的 transform 顶替——动画一改，
 * 弹层就会悄悄变回全屏。
 */
.goods-list-panel--sheet {
  transform: translateZ(0);
}

/* 空态：与船供列表同一套排版（status-tip + 主题色文字动作） */
.list-empty {
  padding: 60rpx 0;

  &__action {
    margin-top: 16rpx;
    font-size: 26rpx;
    color: var(--theme-color-primary, var(--wot-color-theme-primary));
    text-align: center;
  }
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
  border-bottom: 1rpx solid #f2f3f5;
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

  /* 固定宽度、不参与 flex 均分：四项排序文案不能被压到换行 */
  .sort-layout-toggle {
    display: flex;
    flex: none;
    align-items: center;
    justify-content: center;
    width: 56rpx;
    height: 56rpx;
    margin-left: 2rpx;
    border-radius: 12rpx;
    transition: background-color 160ms ease, transform 160ms ease;
  }

  .sort-layout-toggle--pressed {
    background: #f2f3f5;
    transform: scale(0.94);
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
  display: flex;
  flex-direction: column;
  border-radius: 16rpx;
  box-shadow: 0 4rpx 14rpx rgba(22, 34, 51, 0.05);
  transition: transform 160ms ease, box-shadow 160ms ease;

  &:active {
    transform: scale(0.98);
    box-shadow: 0 2rpx 8rpx rgba(22, 34, 51, 0.06);
  }

  /* 图片锁方，标题预留两行、底部信息贴底，保证同一行两张卡的价格/加购对齐 */
  .goods-pic-box {
    flex: none;
  }

  .goods-meta {
    display: flex;
    flex: 1;
    flex-direction: column;
  }

  .goods-name {
    min-height: 2.8em;
  }

  .goods-bottom {
    margin-top: auto;
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

/**
 * 图片区：1:1 方框，图 aspectFit 完整落在框内（与首页商品分组同一口径）。
 *
 * 框比例用 `padding-top: 100%` 而非 `aspect-ratio`：小程序 `<image>` 基座自带
 * 默认宽高，比例写在它上面不生效（首页 goods-group 踩过同一个坑）。
 * 底色留白，与商品图背景融合，容器化后的留白边不显脏。
 */
.goods-pic-box {
  position: relative;
  width: 100%;
  background: #fff;
}

.goods-pic-box--grid {
  padding-top: 100%;
}

.goods-pic-box--row {
  flex: none;
  width: 240rpx;
  height: 240rpx;
  border-radius: 12rpx;
  overflow: hidden;
}

.goods-pic {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
}

/* 已加入角标：共享选货页专用（其他宿主不传 addedSpuIds，不渲染） */
.goods-added-flag {
  position: absolute;
  top: 10rpx;
  left: 10rpx;
  padding: 4rpx 12rpx;
  border-radius: 999rpx;
  background: rgb(15 110 86 / 92%);
  box-shadow: 0 2rpx 8rpx rgb(15 110 86 / 24%);
  font-size: 20rpx;
  line-height: 1.5;
  color: #fff;
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
  line-height: 1.4;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

/* 商品介绍单行省略：卡片高度有限，完整卖点留给商详 */
.goods-desc {
  padding-top: 6rpx;
  overflow: hidden;
  font-size: 22rpx;
  line-height: 1.4;
  color: #909399;
  text-overflow: ellipsis;
  white-space: nowrap;
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
  padding: 4rpx 10rpx;
  border-radius: 6rpx;
  background: #f5f6f8;
  color: #5f6b7a;
  font-size: 24rpx;
  line-height: 1.4;
}

.goods-actions {
  display: flex;
  flex: none;
  align-items: center;
  gap: 12rpx;
}

/**
 * 价格区：售价与划线原价同排。
 *
 * 卡片（尤其分类页右栏两列）内宽有限，原价必须允许换行：售价位数多时
 * 整行放不下，宁可让原价掉到下一行，也不能把加购按钮挤出卡片。
 */
.goods-price {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  column-gap: 10rpx;
  min-width: 0;
  color: var(--wot-color-theme-primary, #ff2237);
  font-weight: 600;

  .goods-price-main {
    display: flex;
    align-items: baseline;
  }

  .goods-price-symbol {
    font-size: 22rpx;
  }

  .goods-price-value {
    font-size: 32rpx;
  }

  .goods-price-original {
    color: #999;
    font-size: 22rpx;
    font-weight: 400;
    text-decoration: line-through;
  }
}

@media (prefers-reduced-motion: reduce) {
  .goods-card {
    animation: none;
    transition: none;
  }
}
</style>

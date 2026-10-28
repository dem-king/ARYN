<script setup lang="ts">
import type { SharedCart } from '@/api/order/sharedCart'
import { onLoad, onShow } from '@dcloudio/uni-app'

/**
 * 共享购物车「分类选货」页。
 *
 * 为什么不是直接打开分类 Tab：
 * - 分类页是 tabBar 页，switchTab/reLaunch 会清空当前选货栈，用户再也回不到
 *   刚创建的共享车与它的选货页；
 * - 分类页的加购入口面向普通浏览，默认会问「加共享车还是个人车」，在
 *   「给这张车选货」的上下文里多问一次，很容易把货投进个人购物车。
 *
 * 因此这里复用分类页的浏览结构（一级图标条 + 二级侧栏 + 商品流），
 * 但把目的地锁定到当前共享车，并保留底部「查看共享购物车」出口。
 */
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { getSharedCart, getSharedCartItems } from '@/api/order/sharedCart'
import { getActiveTree } from '@/api/product/category'
import CategoryAllSheet from '@/components/category-all-sheet/index.vue'
import CategoryIconStrip from '@/components/category-icon-strip/index.vue'
import GoodsListPanel from '@/components/goods-list-panel/index.vue'
import HrNavbar from '@/components/hr-navbar/index.vue'
import { resolveCategoryBadge } from '@/utils/category-badge'

definePage({
  name: 'shared-cart-category',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '分类选货',
  },
})

const cartId = ref('')
const cart = ref<SharedCart | null>(null)
const cartLoaded = ref(false)
const { t } = useI18n()
/** 清单已有明细数：进入时拉一次，加购后自增，底部条用它回答「清单总共多少」 */
const cartItemCount = ref(0)
/** 本页已加入的 SPU：卡片角标 + 底部「已选 N 种」，避免长列表里同款重复加 */
const addedSpuIds = ref<string[]>([])

const categories = ref<any[]>([])
const treeLoaded = ref(false)
const activeFirst = ref(0)
const activeSecond = ref(0)
const sheetVisible = ref(false)
/** 「全部分类」浮层顶边：与分类页同口径，从导航栏下沿开始 */
const sheetTopOffset = ref(0)
/** 切换类目时重放商品区淡入，与卡片逐项入场衔接 */
const goodsSwitching = ref(true)

const contextVesselName = computed(() => cart.value?.vesselName || t('sharedCart.category.sharedCart'))
const contextLocation = computed(() =>
  [cart.value?.portName, cart.value?.berth].filter(Boolean).join(' '),
)

const subCategories = computed<any[]>(() =>
  (categories.value[activeFirst.value]?.children ?? []).map((item: any) => ({
    ...item,
    badge: resolveCategoryBadge(item.badgeType),
  })),
)

/** 树未就绪时不把空数组当成真实子类，左栏交给 skeleton 占位 */
const visibleSubCategories = computed<any[]>(() => (treeLoaded.value ? subCategories.value : []))

const currentFirstId = computed(() => categories.value[activeFirst.value]?.id ?? '')
const currentSecondId = computed(() => subCategories.value[activeSecond.value]?.id ?? '')

const activeRailId = computed(() => {
  const item = visibleSubCategories.value[activeSecond.value]
  return item ? `picker-rail-${item.id ?? activeSecond.value}` : ''
})

const addedSkuCount = computed(() => addedSpuIds.value.length)

watch([activeFirst, activeSecond], () => {
  goodsSwitching.value = false
  nextTick(() => {
    goodsSwitching.value = true
  })
})

onLoad((options) => {
  cartId.value = options?.id ?? ''
  void fetchCart()
  void fetchCategories()
})

/** 从上层页返回时刷新清单计数（加购后本地已自增，这里以服务端为准） */
onShow(() => {
  if (cartId.value && cartLoaded.value)
    void fetchCart()
})

onMounted(() => {
  // 与 hr-search-navbar 的 placeholderHeight 同一口径：状态栏 + 44px 导航栏
  sheetTopOffset.value = (uni.getWindowInfo().statusBarHeight ?? 0) + 44
})

async function fetchCart() {
  if (!cartId.value) {
    cartLoaded.value = true
    return
  }
  try {
    const [detail, items] = await Promise.all([
      getSharedCart(cartId.value),
      getSharedCartItems(cartId.value),
    ])
    cart.value = detail ?? null
    cartItemCount.value = items?.length ?? 0
  }
  catch {
    // 详情失败不阻塞分类浏览：商品面板会展示重试入口，避免在未知目的地时加购
    cart.value = null
  }
  finally {
    cartLoaded.value = true
  }
}

async function fetchCategories() {
  try {
    categories.value = (await getActiveTree()) ?? []
  }
  catch {
    categories.value = []
  }
  finally {
    treeLoaded.value = true
  }
}

function handleAdded(spuId: string) {
  cartItemCount.value += 1
  if (!addedSpuIds.value.includes(spuId))
    addedSpuIds.value = [...addedSpuIds.value, spuId]
}

function selectFirst(index: number) {
  if (index === activeFirst.value)
    return
  activeFirst.value = index
  activeSecond.value = 0
}

function selectSecond(index: number) {
  if (index === activeSecond.value)
    return
  activeSecond.value = index
}

function handleSheetSelect(index: number) {
  selectFirst(index)
  sheetVisible.value = false
}

function openSheet() {
  sheetVisible.value = true
}

/**
 * 查看共享购物车。
 *
 * 压入新的详情页而不是回退：选货过程中看清单是「中途核对」，看完要能原路
 * 返回继续按分类挑。回退到栈里已有的详情页会把本页一起弹掉，用户只能从头
 * 重新进入；与选货页 goSharedCartDetail 保持同一口径。
 */
function goSharedCartDetail() {
  if (!cartId.value)
    return
  uni.navigateTo({ url: `/sub-pages/order/shared-cart/detail?id=${cartId.value}` })
}
</script>

<template>
  <view class="picker-page">
    <hr-navbar :title="t('sharedCart.category.navTitle')" />

    <!-- 共享车上下文：随时确认在给哪条船选货，避免选完才发现投错车 -->
    <view class="picker-context">
      <view class="picker-context__icon">
        <wd-icon name="cart" size="18px" color="#185FA5" />
      </view>
      <view class="picker-context__body">
        <view class="picker-context__vessel">
          {{ contextVesselName }}
        </view>
        <view class="picker-context__meta">
          {{ contextLocation || t('sharedCart.category.locationPending') }}
        </view>
      </view>
      <view class="picker-context__tag">
        {{ t('sharedCart.category.sharedCartTag') }}
      </view>
    </view>

    <category-icon-strip
      :categories="categories"
      :active-index="activeFirst"
      :loading="!treeLoaded"
      @select="selectFirst"
      @expand="openSheet"
    />

    <view class="picker-content">
      <scroll-view
        class="picker-rail"
        scroll-y
        :show-scrollbar="false"
        :scroll-into-view="activeRailId"
        scroll-with-animation
      >
        <view v-if="!treeLoaded" class="picker-rail-skeleton">
          <view
            v-for="n in 6"
            :key="n"
            class="picker-rail-skeleton__item skeleton-shimmer"
          />
        </view>
        <view
          v-for="(item, index) in visibleSubCategories"
          :id="`picker-rail-${item.id ?? index}`"
          :key="item.id ?? index"
          class="picker-rail-item"
          :class="index === activeSecond ? 'picker-rail-item--active' : ''"
          hover-class="picker-rail-item--pressed"
          :hover-stay-time="120"
          @click="selectSecond(index)"
        >
          <view class="picker-rail-item__indicator" />
          <view
            v-if="item.badge"
            class="picker-rail-item__badge"
            :style="{ backgroundColor: item.badge.bgColor, color: item.badge.color }"
          >
            {{ item.badge.text }}
          </view>
          <text class="picker-rail-item__name">
            {{ item.name }}
          </text>
        </view>
        <view v-if="treeLoaded && subCategories.length === 0" class="picker-rail-empty">
          <wd-icon name="layers" size="38rpx" color="#b5bac3" />
          <text>{{ t('sharedCart.category.noSubCategories') }}</text>
        </view>
      </scroll-view>

      <view class="picker-goods" :class="treeLoaded && cart && goodsSwitching ? 'picker-goods--enter' : ''">
        <!-- 类目树/购物车就绪前先用骨架占位，避免先按「全部分类」白查一次 -->
        <view v-if="!treeLoaded || !cartLoaded" class="picker-goods-skeleton">
          <view class="picker-goods-skeleton__sort">
            <view
              v-for="n in 4"
              :key="n"
              class="picker-goods-skeleton__sort-item skeleton-shimmer"
            />
          </view>
          <view class="picker-goods-skeleton__grid">
            <view v-for="n in 4" :key="n" class="picker-goods-skeleton__card">
              <view class="picker-goods-skeleton__pic skeleton-shimmer" />
              <view class="picker-goods-skeleton__line skeleton-shimmer" />
              <view class="picker-goods-skeleton__line picker-goods-skeleton__line--short skeleton-shimmer" />
            </view>
          </view>
        </view>
        <!-- 共享车加载失败：此时加购没有明确目的地，先给出重试而不是放行 -->
        <view v-else-if="!cart" class="picker-goods-empty">
          <wd-status-tip
            image="network"
            image-size="176rpx"
            :tip="t('sharedCart.category.loadFailed')"
          />
          <view class="picker-goods-empty__action" @tap="fetchCart">
            {{ t('common.retry') }}
          </view>
        </view>
        <goods-list-panel
          v-else
          :category-first-id="currentFirstId"
          :category-second-id="currentSecondId"
          :shared-cart="cart"
          :added-spu-ids="addedSpuIds"
          show-sort
          detail-sheet
          @added="handleAdded"
        />
      </view>
    </view>

    <!-- 底部常驻清单条：与选货页同一口径，选完直接交回清单 -->
    <view class="picker-bar">
      <view class="picker-bar__info">
        <view class="picker-bar__count">
          {{ t('sharedCart.category.selectedPrefix') }} <text class="picker-bar__count-num">
            {{ addedSkuCount }}
          </text> {{ t('sharedCart.category.selectedSuffix') }}
        </view>
        <view class="picker-bar__meta">
          {{ t('sharedCart.category.listSummary', { count: cartItemCount }) }}
        </view>
      </view>
      <view class="picker-bar__btn" @tap="goSharedCartDetail">
        {{ t('sharedCart.category.viewSharedCart') }}
      </view>
    </view>

    <category-all-sheet
      v-if="sheetVisible"
      :categories="categories"
      :active-index="activeFirst"
      :top-offset="sheetTopOffset"
      @select="handleSheetSelect"
      @close="sheetVisible = false"
    />
  </view>
</template>

<style lang="scss" scoped>
/**
 * 整页固定高度：负责导航栏/上下文条/图标条/底部清单条，只有中间内容区滚动。
 * 与分类页同一 flex 口径，避免小程序里嵌套 100% 高度解析失败。
 */
.picker-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - var(--window-bottom, 0px));
  overflow: hidden;
  background: #f4f5f7;
}

/* 共享车上下文：沿用选货页的蓝卡语言，与普通分类页的白底区分 */
.picker-context {
  display: flex;
  flex: none;
  align-items: center;
  gap: 16rpx;
  margin: 16rpx 20rpx 12rpx;
  padding: 18rpx 24rpx;
  border: 1rpx solid #cfe2f8;
  border-radius: 16rpx;
  background: linear-gradient(135deg, #e8f2fd, #dceafa);

  &__icon {
    display: flex;
    flex: none;
    align-items: center;
    justify-content: center;
    width: 64rpx;
    height: 64rpx;
    border-radius: 32rpx;
    background: rgb(255 255 255 / 78%);
  }

  &__body {
    flex: 1;
    min-width: 0;
  }

  &__vessel {
    overflow: hidden;
    font-size: 30rpx;
    font-weight: 600;
    line-height: 1.35;
    color: #10456f;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__meta {
    margin-top: 4rpx;
    overflow: hidden;
    font-size: 23rpx;
    color: #2b6ca3;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__tag {
    flex: none;
    padding: 6rpx 16rpx;
    border-radius: 20rpx;
    background: rgb(255 255 255 / 82%);
    font-size: 22rpx;
    font-weight: 600;
    color: #185fa5;
  }
}

.picker-content {
  display: flex;
  flex: 1;
  min-height: 0;
  margin-top: 12rpx;
  background: #fff;
}

/* 左栏与分类页保持同一宽度与选中态，切页时视觉不跳 */
.picker-rail {
  width: 176rpx;
  height: 100%;
  flex: none;
  padding: 16rpx 8rpx;
  box-sizing: border-box;
  background: linear-gradient(180deg, #f5f6f8 0%, #f1f2f4 100%);
  border-right: 1rpx solid #eef0f2;
}

.picker-rail-item {
  position: relative;
  display: flex;
  min-height: 88rpx;
  align-items: center;
  padding: 16rpx 12rpx;
  font-size: 26rpx;
  line-height: 1.4;
  color: #5f6673;
  text-align: left;
  word-break: break-all;
  border-radius: 14rpx;
  transition: color 180ms ease, background-color 180ms ease, transform 180ms ease;
  animation: picker-rail-in 260ms ease-out backwards;

  &__indicator {
    width: 5rpx;
    height: 0;
    flex: none;
    margin-right: 10rpx;
    border-radius: 8rpx;
    background: var(--theme-color-primary, var(--wot-color-theme-primary));
    opacity: 0;
    transition: height 180ms ease, opacity 180ms ease;
  }

  &__name {
    flex: 1;
    min-width: 0;
  }

  &__badge {
    flex: none;
    height: 28rpx;
    margin-right: 6rpx;
    padding: 0 5rpx;
    border-radius: 4rpx;
    font-size: 19rpx;
    font-weight: 600;
    line-height: 28rpx;
    text-align: center;
  }

  &--active {
    color: var(--theme-color-primary, var(--wot-color-theme-primary));
    font-weight: 600;
    background: #fff;
    box-shadow: 0 6rpx 18rpx rgba(31, 44, 65, 0.055);
    transform: translateX(3rpx);

    .picker-rail-item__indicator {
      height: 32rpx;
      opacity: 1;
    }
  }
}

@keyframes picker-rail-in {
  from {
    opacity: 0;
    transform: translateX(14rpx);
  }

  to {
    opacity: 1;
    transform: translateX(0);
  }
}

/* 小程序端 :active 不生效，用 hover-class 提供同等按压反馈 */
.picker-rail-item--pressed {
  transform: scale(0.97);
}

.picker-rail-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
  padding: 44rpx 10rpx;
  font-size: 22rpx;
  color: #6b7280;
  text-align: center;
}

.picker-rail-skeleton__item {
  height: 72rpx;
  margin-bottom: 16rpx;
  border-radius: 12rpx;
}

/* 商品流容器：与分类页一致，只做弹性块，不重复声明高度 */
.picker-goods {
  flex: 1;
  min-width: 0;
  min-height: 0;
  background: #fff;

  &--enter {
    animation: picker-goods-enter 240ms ease-out both;
  }

  .goods-list-panel {
    height: 100%;
  }
}

@keyframes picker-goods-enter {
  from {
    opacity: 0.82;
    transform: translateY(8rpx);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.picker-goods-skeleton {
  height: 100%;
  padding: 16rpx;
  box-sizing: border-box;
  overflow: hidden;
}

.picker-goods-skeleton__sort {
  display: flex;
  height: 76rpx;
  align-items: center;
  justify-content: space-between;
  padding: 0 8rpx;
}

.picker-goods-skeleton__sort-item {
  width: 80rpx;
  height: 24rpx;
  border-radius: 12rpx;
}

.picker-goods-skeleton__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16rpx;
}

.picker-goods-skeleton__card {
  overflow: hidden;
  border-radius: 16rpx;
  background: #fff;
  box-shadow: 0 4rpx 14rpx rgba(22, 34, 51, 0.05);
}

.picker-goods-skeleton__pic {
  padding-top: 100%;
}

.picker-goods-skeleton__line {
  height: 24rpx;
  margin: 16rpx 16rpx 0;
  border-radius: 8rpx;

  &--short {
    width: 55%;
    margin-bottom: 16rpx;
  }
}

.picker-goods-empty {
  display: flex;
  height: 100%;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding-bottom: 80rpx;

  &__action {
    margin-top: 16rpx;
    font-size: 26rpx;
    color: var(--theme-color-primary, var(--wot-color-theme-primary));
  }
}

/**
 * 底部清单条：本页不是 z-paging 根，直接排在 flex 尾部。
 * 安全区并进自身内边距，与选货页 checkout-bar 同一口径。
 */
.picker-bar {
  display: flex;
  flex: none;
  align-items: center;
  padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
  background: #fff;
  box-shadow: 0 -2rpx 12rpx rgb(27 39 57 / 6%);

  &__info {
    flex: 1;
    min-width: 0;
  }

  &__count {
    font-size: 27rpx;
    color: #1b2739;
  }

  &__count-num {
    margin: 0 4rpx;
    font-size: 32rpx;
    font-weight: 600;
    color: var(--wot-color-theme-primary, #ff2237);
  }

  &__meta {
    margin-top: 2rpx;
    font-size: 22rpx;
    color: #9aa2ae;
  }

  &__btn {
    flex: none;
    height: 76rpx;
    padding: 0 32rpx;
    border-radius: 38rpx;
    background: linear-gradient(135deg, var(--wot-color-theme-secondary, #ff8a00), var(--wot-color-theme-primary, #ff4d2e));
    box-shadow: 0 6rpx 16rpx rgb(255 77 46 / 30%);
    font-size: 27rpx;
    font-weight: 500;
    line-height: 76rpx;
    color: #fff;
  }
}

.skeleton-shimmer {
  background: linear-gradient(90deg, #f2f3f5 25%, #e9ebef 37%, #f2f3f5 63%);
  background-size: 400% 100%;
  animation: picker-skeleton-shimmer 1.4s ease infinite;
}

@keyframes picker-skeleton-shimmer {
  0% {
    background-position: 100% 50%;
  }

  100% {
    background-position: 0 50%;
  }
}

@media (prefers-reduced-motion: reduce) {
  .picker-rail-item,
  .picker-goods--enter,
  .skeleton-shimmer {
    animation: none;
    transition: none;
  }
}
</style>

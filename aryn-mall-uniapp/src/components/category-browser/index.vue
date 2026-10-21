<script setup lang="ts">
/**
 * 移动端分类页（参考小象超市：一级图标条 + 二级侧栏 + 商品流）。
 *
 * 同一套 UI 被两个页面复用：
 *   · `pages/product/category/index`（tabBar 页，无返回箭头）；
 *   · `sub-pages/product/category-browse/index`（商详「去逛逛」压栈打开，左上角返回商详）。
 * 页面生命周期钩子不能写在组件里，因此首屏装载放 onMounted，
 * 每次页面显示刷新走 usePageShowLoad（onMounted + onShow，兼顾小程序端迟到注册）。
 *
 * 结构：
 *   自定义搜索导航栏（搜索 + 购物车角标）
 *   ├─ 一级分类圆形图标横滑条（未配图走首字色块兜底）｜右端「展开」
 *   └─ 左：二级分类 rail（筛选）  右：商品流（categoryFirstId/categorySecondId）
 *         点击「展开」→ 全屏「全部分类」浮层（懒挂载）
 *
 * 与旧版的差别：旧版右侧是一级 banner + 二级宫格，点击二级跳商品列表；
 * 现在左右是**筛选关系**，商品直接展示在右栏。旧版为「滚动锚点定位」写的
 * 一整套量测逻辑（量节点高度、重复点击时偏移 scroll-top、滚动反查激活项）
 * 在筛选模型下没有对应物，已整体删除。
 */
import type { CategoryLocateTarget } from '@/store/categoryLocateStore'
import { onMounted, ref } from 'vue'
import { getActiveTree } from '@/api/product/category'
import CategoryAllSheet from '@/components/category-all-sheet/index.vue'
import CategoryIconStrip from '@/components/category-icon-strip/index.vue'
import DiyPage from '@/components/diy/index.vue'
import GoodsListPanel from '@/components/goods-list-panel/index.vue'
import HrSearchNavbar from '@/components/hr-search-navbar/index.vue'
import { usePageDecoration } from '@/composables/usePageDecoration'
import { usePageShowLoad } from '@/composables/usePageShowLoad'
import { resolveCategoryBadge } from '@/utils/category-badge'
import { locateCategoryInTree } from '@/utils/category-tree'
import { Local } from '@/utils/storage'

interface Props {
  /** 压栈打开时显示左上角返回箭头；tab 页保持无箭头 */
  showBack?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  showBack: false,
})

const router = useRouter()
const authStore = useAuthStore()
const shoppingCartStore = useShoppingCartStore()
const categoryLocateStore = useCategoryLocateStore()

// 分类页装修（pageType=3）：在图标条与商品列表之间嵌入活动 banner / 优惠券入口
const { pageContentData, loading: decorationLoading, fetch: fetchDecoration } = usePageDecoration('3')

const categories = ref<any[]>([])
/** 类目树请求结束（含失败）后再生效商品流面板，避免先按「全部分类」白查一次 */
const treeLoaded = ref(false)
/** 搜索导航栏实例：用于读取它的实际占位高度，让「全部分类」面板从导航栏下沿开始 */
const searchNavbarRef = ref<any>(null)
const activeFirst = ref(0)
const activeSecond = ref(0)
const sheetVisible = ref(false)
/** 商品列表页「全部分类」带来的定位目标；树未就绪时先挂着，加载后应用 */
const pendingLocate = ref<CategoryLocateTarget | null>(null)

/**
 * 商品流布局：双列（默认）/ 单列（图左信息右，对标小象超市）。
 * 记住上次选择——分类页是高频入口，每次都回到双列会让偏好单列的用户反复切换。
 */
const gridLayout = ref(Local.get('category-goods-grid') !== false)
watch(gridLayout, value => Local.set('category-goods-grid', value))

/** 当前一级分类下的二级列表（带解析后的角标，供左栏直接渲染） */
const subCategories = computed<any[]>(() =>
  (categories.value[activeFirst.value]?.children ?? []).map((item: any) => ({
    ...item,
    badge: resolveCategoryBadge(item.badgeType),
  })),
)

const currentFirstId = computed(() => categories.value[activeFirst.value]?.id ?? '')
const currentSecondId = computed(() => subCategories.value[activeSecond.value]?.id ?? '')

/** 树未就绪时不把空数组当成真实子类，左栏交给 skeleton 占位 */
const visibleSubCategories = computed<any[]>(() => (treeLoaded.value ? subCategories.value : []))

/** 左栏选中项 id：切换分类或「全部分类」定位后自动滚入视野 */
const activeRailId = computed(() => {
  const item = visibleSubCategories.value[activeSecond.value]
  return item ? `category-rail-${item.id ?? activeSecond.value}` : ''
})

/**
 * 商品区切换过渡：一级/二级类目变化时重放容器淡入动画，
 * 与商品卡片的逐项入场衔接成「整区响应 + 明细渐入」的两段式反馈。
 * 类名先移除再在下一帧添加，确保 CSS animation 能重新触发。
 */
const goodsSwitching = ref(true)

watch([activeFirst, activeSecond], () => {
  goodsSwitching.value = false
  nextTick(() => {
    goodsSwitching.value = true
  })
})

onMounted(() => {
  getCategory()
  fetchDecoration()
})

async function refreshOnShow() {
  // 「全部分类」/商详「去逛逛」带来的定位目标：tab 页 switchTab、压栈页 navigateTo
  // 都不便直接带分类 query，统一经 store 中转；页面每次显示都消费一次。
  // 先消费定位再发购物车请求：定位是首屏体验，不能被网络请求拖慢。
  const locateTarget = categoryLocateStore.consumePendingLocate()
  if (locateTarget) {
    pendingLocate.value = locateTarget
    applyPendingLocate()
  }

  // 加购后回到分类页要看到最新角标。
  // 分类页在路由白名单内（访客可浏览），未登录时不能发这个请求：
  // 购物车数量接口无 token 会返回 401，统一错误处理会把访客直接踢到登录页。
  if (authStore.isLoggedIn)
    await shoppingCartStore.fetchCartCount().catch(() => {})
}

// 首屏兜底 + 每次页面显示刷新；组件内不能直接依赖页面 onLoad/onShow
usePageShowLoad(refreshOnShow)

/**
 * 应用待定位目标。
 *
 * 首次进分类页时 onShow 早于树请求返回，此时 categories 为空，目标先挂在
 * pendingLocate 上，待 getCategory 完成后由 watch 补应用。目标在启用树中
 * 找不到（已删 / 停用被过滤）则放弃定位，保持默认选中，不拦跳转。
 */
function applyPendingLocate() {
  const target = pendingLocate.value
  if (!target || !categories.value.length)
    return
  pendingLocate.value = null
  const { firstIndex, secondIndex } = locateCategoryInTree(categories.value, target)
  if (firstIndex < 0)
    return
  activeFirst.value = firstIndex
  activeSecond.value = secondIndex >= 0 ? secondIndex : 0
}

watch([categories, pendingLocate], applyPendingLocate)

async function getCategory() {
  try {
    // 导航展示位只呈现启用中的分类（一级/二级均过滤），与首页金刚区对齐
    const response = await getActiveTree()
    categories.value = response ?? []
  }
  catch {
    categories.value = []
  }
  finally {
    treeLoaded.value = true
  }
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

function toSearch() {
  router.push({ name: 'goods-search' })
}

function toCart() {
  router.pushTab({ name: 'shopping-cart' })
}

const cartCount = computed(() => (authStore.isLoggedIn ? shoppingCartStore.getCartCount : 0))

/**
 * 「全部分类」面板的顶边偏移 = 导航栏占位高度。
 *
 * 直接读组件暴露的真实高度（状态栏 + 导航栏），不在页面里按平台手算：
 * 参考图中搜索框保持纯白未被遮挡，面板恰好从它下沿开始。
 */
const sheetTopOffset = computed(() => searchNavbarRef.value?.placeholderHeight ?? 0)
</script>

<template>
  <view class="category-page">
    <hr-search-navbar
      ref="searchNavbarRef"
      :left-arrow="props.showBack"
      :disabled="true"
      placeholder="搜索商品"
      :search-btn="true"
      @search="toSearch"
      @focus="toSearch"
    >
      <template #right>
        <view class="category-cart" @click="toCart">
          <wd-badge :model-value="cartCount">
            <wd-icon name="cart" size="48rpx" color="#333" />
          </wd-badge>
        </view>
      </template>
    </hr-search-navbar>

    <category-icon-strip
      :categories="categories"
      :active-index="activeFirst"
      :loading="!treeLoaded"
      @select="selectFirst"
      @expand="openSheet"
    />

    <view class="category-content">
      <scroll-view
        class="category-rail"
        scroll-y
        :show-scrollbar="false"
        :scroll-into-view="activeRailId"
        scroll-with-animation
      >
        <view v-if="!treeLoaded" class="category-rail-skeleton">
          <view
            v-for="n in 6"
            :key="n"
            class="category-rail-skeleton__item skeleton-shimmer"
          />
        </view>
        <view
          v-for="(item, index) in visibleSubCategories"
          :id="`category-rail-${item.id ?? index}`"
          :key="item.id ?? index"
          class="category-rail-item"
          :class="index === activeSecond ? 'category-rail-item--active' : ''"
          :style="{ animationDelay: `${Math.min(index, 9) * 30}ms` }"
          hover-class="category-rail-item--pressed"
          :hover-stay-time="120"
          @click="selectSecond(index)"
        >
          <view class="category-rail-item__indicator" />
          <!-- 角标（「荐」/「热」）：后端 badgeType 为空或 0 时不渲染任何节点 -->
          <view
            v-if="item.badge"
            class="category-rail-item__badge"
            :style="{ backgroundColor: item.badge.bgColor, color: item.badge.color }"
          >
            {{ item.badge.text }}
          </view>
          <text class="category-rail-item__name">
            {{ item.name }}
          </text>
        </view>
        <view v-if="treeLoaded && subCategories.length === 0" class="category-rail-empty">
          <wd-icon name="apps" size="38rpx" color="#b5bac3" />
          <text>暂无子类目</text>
        </view>
      </scroll-view>

      <view class="category-goods" :class="treeLoaded && goodsSwitching ? 'category-goods--enter' : ''">
        <!-- 树请求期间先用骨架占住右栏，避免首屏白块后突然跳变 -->
        <view v-if="!treeLoaded" class="category-goods-skeleton">
          <view class="category-goods-skeleton__banner skeleton-shimmer" />
          <view class="category-goods-skeleton__sort">
            <view
              v-for="n in 4"
              :key="n"
              class="category-goods-skeleton__sort-item skeleton-shimmer"
            />
          </view>
          <view
            class="category-goods-skeleton__grid"
            :class="gridLayout ? '' : 'category-goods-skeleton__grid--single'"
          >
            <view v-for="n in 4" :key="n" class="category-goods-skeleton__card">
              <view class="category-goods-skeleton__pic skeleton-shimmer" />
              <view class="category-goods-skeleton__line skeleton-shimmer" />
              <view class="category-goods-skeleton__line category-goods-skeleton__line--short skeleton-shimmer" />
            </view>
          </view>
        </view>
        <goods-list-panel
          v-else
          v-model:grid="gridLayout"
          :category-first-id="currentFirstId"
          :category-second-id="currentSecondId"
          show-sort
          show-layout-toggle
          detail-sheet
        >
          <!--
            分类页装修区：放进右栏商品流内（而非横在上方的通栏），
            与参考图一致 —— banner 与左栏 rail 并排、随商品流一起滚动。
          -->
          <template #banner>
            <view v-if="pageContentData">
              <DiyPage :page-content-data="pageContentData" embedded />
            </view>
            <view v-else-if="decorationLoading" class="category-decoration-skeleton skeleton-shimmer" />
          </template>
        </goods-list-panel>
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
 * 页面整体固定高度 = 可视区 - tabBar（H5 的 --window-bottom 含底部安全区，小程序为 0）。
 *
 * 不再按平台手算导航栏高度：hr-search-navbar 自带等高占位，
 * 页面用 flex 纵向布局，内容区 flex:1 自适应即可。
 */
.category-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - var(--window-bottom, 0px));
  overflow: hidden;
  background: #f4f5f7;
}

.category-cart {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 88rpx;
  height: 88rpx;
}

.category-decoration-skeleton {
  flex: none;
  height: 200rpx;
  margin: 16rpx;
  border-radius: 12rpx;
  background: #f5f5f5;
}

.category-goods-skeleton {
  height: 100%;
  padding: 16rpx;
  box-sizing: border-box;
  overflow: hidden;
}

.category-goods-skeleton__banner {
  height: 200rpx;
  border-radius: 12rpx;
}

.category-goods-skeleton__sort {
  display: flex;
  height: 88rpx;
  align-items: center;
  justify-content: space-between;
  padding: 0 8rpx;
}

.category-goods-skeleton__sort-item {
  width: 80rpx;
  height: 24rpx;
  border-radius: 12rpx;
}

.category-goods-skeleton__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16rpx;
}

.category-goods-skeleton__grid--single {
  grid-template-columns: minmax(0, 1fr);
}

.category-goods-skeleton__card {
  overflow: hidden;
  border-radius: 16rpx;
  background: #fff;
  box-shadow: 0 4rpx 14rpx rgba(22, 34, 51, 0.05);
}

.category-goods-skeleton__pic {
  padding-top: 100%;
}

.category-goods-skeleton__line {
  height: 24rpx;
  margin: 16rpx 16rpx 0;
  border-radius: 8rpx;
}

.category-goods-skeleton__line--short {
  width: 55%;
  margin-bottom: 16rpx;
}

.category-content {
  display: flex;
  flex: 1;
  min-height: 0;
  background: #fff;
}

.category-rail {
  width: 176rpx;
  height: 100%;
  flex: none;
  padding: 20rpx 8rpx;
  box-sizing: border-box;
  background: linear-gradient(180deg, #f5f6f8 0%, #f1f2f4 100%);
  /* 与右侧白色商品区形成清晰分隔，提升层次 */
  border-right: 1rpx solid #eef0f2;
}

.category-rail-item {
  position: relative;
  display: flex;
  min-height: 88rpx;
  align-items: center;
  padding: 16rpx 12rpx;
  color: #5f6673;
  font-size: 26rpx;
  line-height: 1.4;
  text-align: left;
  word-break: break-all;
  transition: color 180ms ease, background-color 180ms ease, transform 180ms ease;
  /* 逐项入场：右滑淡入，动画结束后回落自身样式，保证 :active 按压仍生效 */
  animation: rail-item-in 260ms ease-out backwards;

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

  /**
   * 类目角标（「荐」绿 / 「热」红）：名称前的小方块。
   *
   * 只占一个字的宽度、不参与换行：左栏仅 176rpx 宽，角标带内边距会把类目名
   * 挤成两行，与参考图里角标紧贴文字的效果也不符。
   */
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
    border-radius: 14rpx;
    box-shadow: 0 6rpx 18rpx rgba(31, 44, 65, 0.055);
    transform: translateX(3rpx);

    .category-rail-item__indicator {
      height: 32rpx;
      opacity: 1;
    }
  }
}

@keyframes rail-item-in {
  from {
    opacity: 0;
    transform: translateX(14rpx);
  }

  to {
    opacity: 1;
    transform: translateX(0);
  }
}

.category-rail-item:active {
  transform: scale(0.97);
}

/* 小程序端 :active 不生效，用 hover-class 提供同等按压反馈 */
.category-rail-item--pressed {
  transform: scale(0.97);
}

.category-rail-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
  padding: 44rpx 10rpx;
  color: #6b7280;
  font-size: 22rpx;
  text-align: center;
  animation: rail-item-in 260ms ease-out backwards;
}

.category-rail-skeleton {
  padding: 0;
}

.category-rail-skeleton__item {
  height: 72rpx;
  margin-bottom: 16rpx;
  border-radius: 12rpx;
}

/**
 * 商品流容器：与商品列表页 .goods-panel-wrap 保持一致的弹性块容器，
 * 不再在中间多套一层 flex-direction:column——否则横向 flex(.category-content)
 * 里再嵌一层纵向 flex，内部 height:100% 在小程序 flex 嵌套中解析失败，
 * z-paging 内容区高度塌成 0，表现为排序条在、商品流空白。
 */
.category-goods {
  flex: 1;
  min-width: 0;
  min-height: 0;
  background: #fff;

  // 类目切换时由页面层重放该动画（见 goodsSwitching），与卡片逐项入场衔接。
  // 详情弹层「只盖右栏」的包含块由 goods-list-panel 自身显式声明（--sheet 修饰符），
  // 不依赖本动画 fill 残留的 transform——那种巧合机制改动画就会让弹层变全屏。
  &--enter {
    animation: goods-panel-enter 240ms ease-out both;
  }

  // 子组件根节点会带上父组件的 scoped 属性，可直接命中
  .goods-list-panel {
    height: 100%;
  }
}

@keyframes goods-panel-enter {
  from {
    opacity: 0.82;
    transform: translateY(8rpx);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.skeleton-shimmer {
  background: linear-gradient(90deg, #f2f3f5 25%, #e9ebef 37%, #f2f3f5 63%);
  background-size: 400% 100%;
  animation: category-skeleton-shimmer 1.4s ease infinite;
}

@keyframes category-skeleton-shimmer {
  0% {
    background-position: 100% 50%;
  }

  100% {
    background-position: 0 50%;
  }
}

@media (prefers-reduced-motion: reduce) {
  .category-rail-item,
  .category-rail-item__indicator,
  .category-rail-empty,
  .category-goods--enter,
  .skeleton-shimmer {
    animation: none;
    transition: none;
  }
}
</style>

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
 *   └─ 左：二级分类 rail（筛选）  右：单列商品流（categoryFirstId/categorySecondId）
 *         点击「展开」→ 全屏「全部分类」浮层（懒挂载）
 *
 * 与旧版的差别：旧版右侧是一级 banner + 二级宫格，点击二级跳商品列表；
 * 现在左右是**筛选关系**，商品直接展示在右栏。旧版为「滚动锚点定位」写的
 * 一整套量测逻辑（量节点高度、重复点击时偏移 scroll-top、滚动反查激活项）
 * 在筛选模型下没有对应物，已整体删除。
 */
import type { CategoryLocateTarget } from '@/store/categoryLocateStore'
import { getCurrentInstance, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { getActiveTree } from '@/api/product/category'
import CategoryAllSheet from '@/components/category-all-sheet/index.vue'
import CategoryIconStrip from '@/components/category-icon-strip/index.vue'
import DiyPage from '@/components/diy/index.vue'
import GoodsListPanel from '@/components/goods-list-panel/index.vue'
import HrSearchNavbar from '@/components/hr-search-navbar/index.vue'
import { useFrostedNavbar } from '@/composables/useFrostedNavbar'
import { usePageDecoration } from '@/composables/usePageDecoration'
import { usePageShowLoad } from '@/composables/usePageShowLoad'
import { resolveCategoryBadge } from '@/utils/category-badge'
import { locateCategoryInTree } from '@/utils/category-tree'

interface Props {
  /** 压栈打开时显示左上角返回箭头；tab 页保持无箭头 */
  showBack?: boolean
  /**
   * 是否挂在 tab 页下（底部有自绘玻璃底栏）。
   *
   * 同一个组件同时服务 tab 页与商详「去逛逛」压栈页：小程序端 tab 页要把内容区
   * 底部让出一条底栏高度，压栈页没有底栏、不能让，因此必须由宿主显式声明。
   */
  withTabBar?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  showBack: false,
  withTabBar: false,
})

const { t, locale } = useI18n()
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

/**
 * 顶栏磨砂玻璃：分类页是「页面不滚、右栏独立滚」的结构，滚动状态必须从商品流
 * （z-paging）的 @scroll 转进来，搜索栏与一级图标条才会跟着切材质。
 * scrolled 同时下传给图标条，撤掉它自己的白底，让宿主覆盖层的毛玻璃透出来。
 */
const { handlePageScroll, scrolled } = useFrostedNavbar()

/** 状态栏 + 44px 导航条：与 hr-search-navbar 的 placeholderHeight 同口径，首帧即可定位 */
const { statusBarHeight = 0 } = uni.getWindowInfo()
const categoryNavHeight = statusBarHeight + 44
/** 图标条实测高度：首帧按 176rpx 估值占位，量到后回填，避免首屏跳动 */
const iconStripHeight = ref(uni.upx2px(176))
const categoryHeaderHeight = computed(() => categoryNavHeight + iconStripHeight.value)

const instance = getCurrentInstance()
/** 量图标条真实高度（文案换行会改变高度，不能写死） */
function measureIconStripHeight() {
  if (!instance)
    return
  uni.createSelectorQuery()
    .in(instance.proxy as any)
    .select('.icon-strip')
    .boundingClientRect((rect) => {
      const height = (rect as any)?.height
      if (typeof height === 'number' && height > 0)
        iconStripHeight.value = height
    })
    .exec()
}
const activeFirst = ref(0)
const activeSecond = ref(0)
const sheetVisible = ref(false)
/** 商品列表页「全部分类」带来的定位目标；树未就绪时先挂着，加载后应用 */
const pendingLocate = ref<CategoryLocateTarget | null>(null)

/**
 * 商品流固定单列（图左信息右，对齐小象超市分类页）。
 *
 * 右栏可用宽度有限，单列卡片能完整承载商品名、卖点、身份价与营销标签；
 * 商品列表页仍保留自己的双列 / 单列切换，与本页互不影响。
 */

/**
 * 当前一级分类下的二级列表（带解析后的角标，供左栏直接渲染）。
 *
 * 先取服务端下发的 `item.badge` 再覆盖本地 `badge` 字段：角标配置化后以服务端为准，
 * 本地旧映射只做兜底，不能在展开覆盖时把服务端配置冲掉。
 */
const subCategories = computed<any[]>(() =>
  (categories.value[activeFirst.value]?.children ?? []).map((item: any) => {
    const serverBadge = item.badge
    return {
      ...item,
      badge: resolveCategoryBadge(item.badgeType, serverBadge),
    }
  }),
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
  nextTick(measureIconStripHeight)
})

/** 图标条高度随类目文案（语言切换 / 名称换行）变化，重渲染后补量一次 */
watch([categories, locale], () => nextTick(measureIconStripHeight))

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
 * 「全部分类」面板的顶边偏移 = 导航栏实际渲染高度。
 *
 * 直接读组件暴露的 visualHeight（状态栏 + 胶囊偏移 + 导航栏），不在页面里按平台手算：
 * 占位高度不含微信胶囊的纵向偏移，按它贴边会在导航栏下沿漏出一根深色细线。
 */
const sheetTopOffset = computed(() => searchNavbarRef.value?.visualHeight ?? 0)
</script>

<template>
  <view
    class="category-page"
    :class="{ 'category-page--with-tabbar': props.withTabBar }"
    :style="{
      '--category-nav-height': `${categoryNavHeight}px`,
      '--category-strip-height': `${iconStripHeight}px`,
      '--category-header-height': `${categoryHeaderHeight}px`,
      '--goods-sort-sticky-top': `${categoryHeaderHeight}px`,
    }"
  >
    <hr-search-navbar
      ref="searchNavbarRef"
      :left-arrow="props.showBack"
      :disabled="true"
      :bordered="!sheetVisible"
      :force-frosted="sheetVisible"
      :placeholder="t('catalog.searchProducts')"
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

    <!--
      头部覆盖层：一级图标条钉在搜索栏下沿，内容从其下方穿过。
      滚动后本层一步切成半透明白 + 背景模糊（与首页顶栏同一材质），
      图标条自身撤掉白底（:frosted），毛玻璃才透得出来。
    -->
    <view class="category-header" :class="{ 'category-header--frosted': scrolled }">
      <category-icon-strip
        :categories="categories"
        :active-index="activeFirst"
        :loading="!treeLoaded"
        :frosted="scrolled"
        @select="selectFirst"
        @expand="openSheet"
      />
    </view>

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
          <!--
            角标：优先渲染服务端下发配置（运营可配 1–2 字、配色、图片、尺寸与圆角）。
            有图时渲染图片角标（宽高 = 高度、圆角可配），图片优先于文字；否则渲染文字角标。
            旧数据回退「荐」/「热」的默认 28rpx；无角标时不渲染任何节点。
          -->
          <image
            v-if="item.badge && item.badge.iconUrl"
            class="category-rail-item__badge-image"
            :src="item.badge.iconUrl"
            mode="aspectFit"
            :style="{
              width: `${item.badge.height}rpx`,
              height: `${item.badge.height}rpx`,
              borderRadius: `${item.badge.borderRadius}rpx`,
            }"
          />
          <view
            v-else-if="item.badge"
            class="category-rail-item__badge"
            :style="{
              backgroundColor: item.badge.bgColor,
              color: item.badge.color,
              height: `${item.badge.height}rpx`,
              lineHeight: `${item.badge.height}rpx`,
              borderRadius: `${item.badge.borderRadius}rpx`,
              fontSize: `${item.badge.fontSize}rpx`,
              padding: `0 ${item.badge.paddingX}rpx`,
            }"
          >
            {{ item.badge.text }}
          </view>
          <text class="category-rail-item__name">
            {{ item.name }}
          </text>
        </view>
        <view v-if="treeLoaded && subCategories.length === 0" class="category-rail-empty">
          <wd-icon name="layers" size="38rpx" color="#b5bac3" />
          <text>{{ t('catalog.noSubcategories') }}</text>
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
          <view class="category-goods-skeleton__grid">
            <view v-for="n in 4" :key="n" class="category-goods-skeleton__card">
              <view class="category-goods-skeleton__pic skeleton-shimmer" />
              <view class="category-goods-skeleton__info">
                <view class="category-goods-skeleton__line skeleton-shimmer" />
                <view class="category-goods-skeleton__line category-goods-skeleton__line--short skeleton-shimmer" />
              </view>
            </view>
          </view>
        </view>
        <goods-list-panel
          v-else
          :grid="false"
          :category-first-id="currentFirstId"
          :category-second-id="currentSecondId"
          show-sort
          detail-sheet
          @scroll="handlePageScroll"
        >
          <!--
            分类页装修区：放进右栏商品流内（而非横在上方的通栏），
            与参考图一致 —— banner 与左栏 rail 并排、随商品流一起滚动。
          -->
          <template #banner>
            <!--
              头部覆盖层等高占位：商品流初始位置仍在图标条下沿，
              只有滚动后内容才从磨砂头部下方穿过（与首页楼层同一观感）。
            -->
            <view :style="{ height: `${categoryHeaderHeight}px` }" />
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
      frosted
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
  position: relative;
  display: flex;
  flex-direction: column;
  height: calc(100vh - var(--window-bottom, 0px));
  overflow: hidden;
  background: var(--app-bg);
}

/* tab 页挂悬浮玻璃底栏：与其他 tab 页同口径，内容延伸到屏底从栏下穿过，
   只在滚动末尾经 --page-glass-bar-clearance 让位（右栏商品流/详情弹层复用
   goods-list-panel 既有变量，左栏 rail 补底部内边距），末行商品与最后一个
   子类能滚到玻璃上沿之上；压栈页不声明 withTabBar，变量取 0 行为不变 */
.category-page--with-tabbar {
  --page-glass-bar-clearance: var(--app-tabbar-height, 0px);

  .category-rail {
    padding-bottom: calc(20rpx + var(--page-glass-bar-clearance, 0px));
  }
}

/**
 * 头部覆盖层：一级图标条钉在搜索栏下沿（搜索栏自身 fixed，z-index 1000）。
 * 压在「全部分类」浮层（999）之下，浮层打开时从搜索栏下沿整块盖住图标条。
 */
.category-header {
  position: fixed;
  top: var(--category-nav-height, 0px);
  left: 0;
  right: 0;
  z-index: 900;
  background: var(--app-card);
}

/* 滚动后一步切磨砂：半透明白 + 背景模糊，与首页顶栏同一材质数值 */
.category-header--frosted {
  background: rgba(255, 255, 255, 0.86);
  /* 大写 WEBKIT 避开 autoprefixer 的过时前缀清理（iOS 17- 的 WKWebView 只认带前缀的写法） */
  -WEBKIT-backdrop-filter: blur(18px) saturate(160%);
  backdrop-filter: blur(18px) saturate(160%);
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
  background: var(--app-card-2);
}

.category-goods-skeleton {
  height: 100%;
  /* 骨架与真实商品流同起点：让出头部覆盖层高度，首屏不被磨砂头部遮住 */
  padding: calc(var(--category-header-height, 0px) + 16rpx) 16rpx 16rpx;
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

/**
 * 单列骨架与真实单列商品卡同构：图左 1:1 方框、信息右；
 * 负外边距抵消容器内边距，让分隔线像真实列表一样通栏。
 */
.category-goods-skeleton__grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  margin: 0 -16rpx;
}

.category-goods-skeleton__card {
  display: flex;
  align-items: flex-start;
  padding: 16rpx;
  border-bottom: 1rpx solid var(--app-border);
}

.category-goods-skeleton__pic {
  flex: none;
  width: 200rpx;
  height: 200rpx;
  border-radius: 12rpx;
}

.category-goods-skeleton__info {
  flex: 1;
  min-width: 0;
  padding-left: 16rpx;
}

.category-goods-skeleton__line {
  height: 24rpx;
  margin-bottom: 16rpx;
  border-radius: 8rpx;
}

.category-goods-skeleton__line--short {
  width: 55%;
}

.category-content {
  display: flex;
  flex: 1;
  min-height: 0;
  background: var(--app-card);
  /**
   * 让出图标条覆盖层：左栏从图标条下沿开始，不在固定层下面滚动
   * （scroll-into-view 会把激活项顶到滚动区上沿，藏在头部下就看不见了）。
   */
  box-sizing: border-box;
  padding-top: var(--category-strip-height, 0px);
}

.category-rail {
  width: 192rpx;
  height: 100%;
  flex: none;
  padding: 20rpx 8rpx;
  box-sizing: border-box;
  background: var(--app-card-2);
  /* 与右侧白色商品区形成清晰分隔，提升层次 */
  border-right: 1rpx solid var(--app-border);
}

.category-rail-item {
  position: relative;
  display: flex;
  min-height: 88rpx;
  align-items: center;
  padding: 16rpx 12rpx;
  color: var(--app-text-2);
  font-size: 26rpx;
  line-height: 1.4;
  text-align: left;
  transition: color 180ms ease, background-color 180ms ease, transform 180ms ease;
  /* 逐项入场：右滑淡入，动画结束后回落自身样式，保证 :active 按压仍生效 */
  animation: rail-item-in 260ms ease-out backwards;

  /**
   * 选中指示条：绝对定位悬在左侧内边距里，不占流内宽度。
   * 若让它参与 flex 布局（宽 5rpx + 间距），所有类目名都要为它常驻让位，
   * 带角标的 4 字名就会放不下而折行/截断。
   */
  &__indicator {
    position: absolute;
    top: 50%;
    left: 3rpx;
    width: 5rpx;
    height: 0;
    border-radius: 8rpx;
    background: var(--theme-color-primary, var(--wot-color-theme-primary));
    opacity: 0;
    transform: translateY(-50%);
    transition: height 180ms ease, opacity 180ms ease;
  }

  /**
   * 名称恒为单行（对标盒马/叮咚买菜的左栏口径），超长省略号截断，绝不折行。
   * 192rpx 栏宽下：角标 + 5 字名 = 130 + 33 = 163rpx ≤ 168rpx 内宽，完整放下；
   * 6 字名不带角标也放得下，更长的名称才落到省略号。
   */
  &__name {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  /**
   * 类目角标（「荐」绿 / 「热」红）：名称前的小方块。
   *
   * 尺寸、字号、圆角与配色全部由服务端配置经 `item.badge` 内联下发
   * （默认 28rpx 高、19rpx 字号、4rpx 圆角），这里只保留排版约束：
   * 不参与换行、不被压缩；与名称的留白已收紧，间距不可再放大，
   * 否则「角标 + 5 字名」会重新挤到省略号（见 __name 注释的宽度账）。
   */
  &__badge {
    flex: none;
    margin-right: 4rpx;
    font-weight: 600;
    text-align: center;
  }

  /** 图片角标：按正方形渲染，宽高与圆角同样由 `item.badge` 内联下发 */
  &__badge-image {
    flex: none;
    margin-right: 4rpx;
    display: block;
  }

  &--active {
    color: var(--theme-color-primary, var(--wot-color-theme-primary));
    font-weight: 600;
    background: var(--app-card);
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
  color: var(--app-text-2);
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
  background: var(--app-card);
  /**
   * 右栏商品流上探到页面顶部：滚动时商品从搜索栏 + 图标条（磨砂玻璃）下方穿过。
   * 负外边距 + 等高补偿只作用在本列，左栏位置不受影响。
   */
  margin-top: calc(-1 * var(--category-header-height, 0px));
  height: calc(100% + var(--category-header-height, 0px));

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
  background: linear-gradient(90deg, var(--app-skeleton) 25%, var(--app-skeleton-band) 37%, var(--app-skeleton) 63%);
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

/**
 * 折叠屏 / 平板展开态（仅微信小程序，断点 600px 取微信《大屏适配指南》口径）。
 *
 * 问题：左栏 rail 原为 192rpx，展开到 ~700px 时按 750rpx 基准换算 ≈179px，占掉 1/4 屏；
 * 类目行高、角标、骨架块随 rpx 一起放大 ~1.8 倍，右栏被挤成「放大版手机」。本段把 rail
 * 钉成固定 96px（不随窗口变宽放大），字号/内边距/骨架换成平板密度 px。右栏 3 列由
 * goods-list-panel 的宽屏段给出（同一份网格口径；本文件不穿透子组件内部网格，
 * 否则两处各改一套，迟早漂移）。
 *
 * 折叠态（<600px）根本不会命中本段；H5 / App 产物被条件编译整段剔除 → 手机端零变化。
 * 段内禁用 rpx/upx：rpx 以「窗口宽 = 750rpx」为基准，展开态会等比放大 ≈1.8 倍，
 * 正是本次要修的根因。
 *
 * 宽度账（A7 验收口径，700px 窗口）：rail 96px → 右栏 ≈604px；
 * 右栏 3 列时卡宽 =（604 − goods-list 左右 padding 12×2 − 列间距 12×2）/ 3 ≈ 185px ≥ 180px。
 * 断点临界 600px 时卡宽 ≈ 152px，属首档起步区间（方案只用一个断点，不再加第二档）。
 */
/* #ifdef MP-WEIXIN */
@media (min-width: 600px) {
  /* 左栏不再随 rpx 放大：固定 96px 把多出来的窗口宽度全部让给商品区 */
  .category-rail {
    width: 96px;
    padding: 10px 6px;
  }

  /* 行高 44px 对齐调研 P0 的最小热区建议；字号回到平板正文 14px */
  .category-rail-item {
    min-height: 44px;
    padding: 8px 6px;
    font-size: var(--wide-body-font, 14px);
    border-radius: 8px;

    /* 选中指示条改 px：原 5rpx 在展开态约 4.7px，与 96px 的窄栏不成比例 */
    &__indicator {
      left: 3px;
      width: 3px;
      border-radius: 3px;
    }

    /*
     * 宽屏按平板密度固定角标尺寸（与改造前写死的 18px/11px/3px 一致）：
     * 尺寸/字号来自服务端内联 rpx，展开态会随窗口等比放大约 1.8 倍（28rpx ≈ 26px），
     * 比类目名（14px）还大；本段用 !important 压回固定 px。折叠态（<600px）不命中本段，
     * 仍完全按运营配置渲染，不受影响。
     */
    &__badge {
      height: 18px !important;
      padding: 0 3px !important;
      font-size: 11px !important;
      line-height: 18px !important;
      border-radius: 3px !important;
      margin-right: 3px;
    }

    &__badge-image {
      width: 18px !important;
      height: 18px !important;
      border-radius: 3px !important;
      margin-right: 3px;
    }

    &--active {
      border-radius: 8px;
      box-shadow: 0 2px 8px rgba(31, 44, 65, 0.08);
      transform: translateX(2px);

      .category-rail-item__indicator {
        height: 18px;
      }
    }
  }

  /* 骨架项与真实 44px 行高对齐，树加载完成切换时左栏不会跳一下 */
  .category-rail-skeleton__item {
    height: 38px;
    margin-bottom: 6px;
    border-radius: 8px;
  }

  /* 左栏空态：字号/内边距改 px（38rpx 的图标是模板内联属性，不在本段处理） */
  .category-rail-empty {
    gap: 8px;
    padding: 22px 6px;
    font-size: 12px;
  }

  /* 购物车热区：展开态至少 44×44px 才点得准（调研 P0 建议） */
  .category-cart {
    width: 44px;
    height: 44px;
  }

  /* 模板里 wd-icon 的 size="48rpx" 是内联字号：展开态会放大到 ≈44.8px 撑满整个导航条，
     内联样式只能用 !important 压回手机端同视觉的 26px（48rpx 在 390px 窗口 ≈25px） */
  .category-cart :deep(.wd-icon) {
    font-size: 26px !important;
  }

  /* 右栏骨架：与真实单列商品流同一密度（图左信息右、同样的行卡留白） */
  .category-goods-skeleton {
    padding: calc(var(--category-header-height, 0px) + 12px) 12px 12px;
  }

  .category-goods-skeleton__banner {
    height: 120px;
    border-radius: 8px;
  }

  .category-goods-skeleton__sort {
    height: 44px;
    padding: 0 6px;
  }

  .category-goods-skeleton__sort-item {
    width: 56px;
    height: 16px;
    border-radius: 8px;
  }

  /* 分类页固定单列：宽屏也不得重排成多列（展开态 rpx 会放大，统一改 px） */
  .category-goods-skeleton__grid {
    grid-template-columns: minmax(0, 1fr);
    margin: 0 -12px;
  }

  .category-goods-skeleton__card {
    padding: 12px;
    border-bottom: 1px solid var(--app-border);
  }

  .category-goods-skeleton__pic {
    width: 96px;
    height: 96px;
    border-radius: 8px;
  }

  .category-goods-skeleton__info {
    padding-left: 12px;
  }

  .category-goods-skeleton__line {
    height: 16px;
    margin-bottom: 12px;
    border-radius: 4px;
  }

  /* 右栏装修位骨架（分类页 banner 位）：200rpx 展开态 ≈187px 过高，收到平板密度 */
  .category-decoration-skeleton {
    height: 120px;
    margin: 12px;
    border-radius: 8px;
  }
}
/* #endif */
</style>

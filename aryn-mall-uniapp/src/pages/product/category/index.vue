<script setup lang="ts">
import { onLoad, onShow } from '@dcloudio/uni-app'
/**
 * 移动端分类页（参考小象超市：一级图标条 + 二级侧栏 + 商品流）。
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
import { ref } from 'vue'
import { getTree } from '@/api/product/category'
import CategoryAllSheet from '@/components/category-all-sheet/index.vue'
import CategoryIconStrip from '@/components/category-icon-strip/index.vue'
import DiyPage from '@/components/diy/index.vue'
import GoodsListPanel from '@/components/goods-list-panel/index.vue'
import HrSearchNavbar from '@/components/hr-search-navbar/index.vue'
import { usePageDecoration } from '@/composables/usePageDecoration'
import { resolveCategoryBadge } from '@/utils/category-badge'

definePage({
  name: 'category',
  layout: 'tabbar',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '分类',
  },
})

const router = useRouter()
const authStore = useAuthStore()
const shoppingCartStore = useShoppingCartStore()

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

/** 当前一级分类下的二级列表（带解析后的角标，供左栏直接渲染） */
const subCategories = computed<any[]>(() =>
  (categories.value[activeFirst.value]?.children ?? []).map((item: any) => ({
    ...item,
    badge: resolveCategoryBadge(item.badgeType),
  })),
)

const currentFirstId = computed(() => categories.value[activeFirst.value]?.id ?? '')
const currentSecondId = computed(() => subCategories.value[activeSecond.value]?.id ?? '')

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

onLoad(() => {
  getCategory()
  fetchDecoration()
})

onShow(() => {
  // 加购后回到分类页要看到最新角标。
  // 分类页在路由白名单内（访客可浏览），未登录时不能发这个请求：
  // 购物车数量接口无 token 会返回 401，统一错误处理会把访客直接踢到登录页。
  if (authStore.isLoggedIn)
    shoppingCartStore.fetchCartCount().catch(() => {})
})

async function getCategory() {
  try {
    const response = await getTree()
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
      :left-arrow="false"
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
      @select="selectFirst"
      @expand="openSheet"
    />

    <view class="category-content">
      <scroll-view class="category-rail" scroll-y :show-scrollbar="false">
        <view class="category-rail-heading">
          <view class="category-rail-heading__bar" />
          <text class="category-rail-heading__title">
            {{ categories[activeFirst]?.name || '全部分类' }}
          </text>
          <text class="category-rail-heading__caption">
            {{ subCategories.length ? `${subCategories.length} 个分类` : '当前分类' }}
          </text>
        </view>
        <view
          v-for="(item, index) in subCategories"
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
          <text class="category-rail-item__name">{{ item.name }}</text>
        </view>
        <view v-if="subCategories.length === 0" class="category-rail-empty">
          <wd-icon name="apps" size="38rpx" color="#b5bac3" />
          <text>暂无子类目</text>
        </view>
      </scroll-view>

      <view class="category-goods" :class="goodsSwitching ? 'category-goods--enter' : ''">
        <goods-list-panel
          v-if="treeLoaded"
          :category-first-id="currentFirstId"
          :category-second-id="currentSecondId"
          show-sort
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
            <view v-else-if="decorationLoading" class="category-decoration-skeleton" />
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
}

.category-decoration-skeleton {
  flex: none;
  height: 200rpx;
  margin: 16rpx;
  border-radius: 12rpx;
  background: #f5f5f5;
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
  padding: 0 8rpx 20rpx;
  box-sizing: border-box;
  background: linear-gradient(180deg, #f5f6f8 0%, #f1f2f4 100%);
  /* 与右侧白色商品区形成清晰分隔，提升层次 */
  border-right: 1rpx solid #eef0f2;
}

.category-rail-heading {
  position: relative;
  display: flex;
  flex-direction: column;
  padding: 28rpx 14rpx 20rpx 28rpx;
  border-bottom: 1rpx solid rgba(27, 39, 57, 0.06);

  &__bar {
    position: absolute;
    left: 14rpx;
    top: 30rpx;
    bottom: 20rpx;
    width: 6rpx;
    border-radius: 6rpx;
    background: linear-gradient(
      180deg,
      var(--theme-color-primary, var(--wot-color-theme-primary)),
      var(--theme-color-secondary, var(--wot-color-theme-secondary))
    );
  }
}

.category-rail-heading__title {
  overflow: hidden;
  color: #30343b;
  font-size: 25rpx;
  font-weight: 700;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.category-rail-heading__caption {
  margin-top: 6rpx;
  color: #9a9faa;
  font-size: 19rpx;
  letter-spacing: 1rpx;
}

.category-rail-item {
  position: relative;
  display: flex;
  min-height: 88rpx;
  align-items: center;
  padding: 16rpx 12rpx;
  color: #686d76;
  font-size: 24rpx;
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
    font-weight: 650;
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
  color: #9a9faa;
  font-size: 22rpx;
  text-align: center;
  animation: rail-item-in 260ms ease-out backwards;
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

  // 类目切换时由页面层重放该动画（见 goodsSwitching），与卡片逐项入场衔接
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

@media (prefers-reduced-motion: reduce) {
  .category-rail-item,
  .category-rail-item__indicator,
  .category-rail-empty,
  .category-goods--enter {
    animation: none;
    transition: none;
  }
}
</style>

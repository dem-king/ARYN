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
import GoodsListPanel from '@/components/goods-list-panel/index.vue'
import HrSearchNavbar from '@/components/hr-search-navbar/index.vue'

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

const categories = ref<any[]>([])
/** 类目树请求结束（含失败）后再生效商品流面板，避免先按「全部分类」白查一次 */
const treeLoaded = ref(false)
/** 搜索导航栏实例：用于读取它的实际占位高度，让「全部分类」面板从导航栏下沿开始 */
const searchNavbarRef = ref<any>(null)
const activeFirst = ref(0)
const activeSecond = ref(0)
const sheetVisible = ref(false)

/** 当前一级分类下的二级列表 */
const subCategories = computed<any[]>(() => categories.value[activeFirst.value]?.children ?? [])

const currentFirstId = computed(() => categories.value[activeFirst.value]?.id ?? '')
const currentSecondId = computed(() => subCategories.value[activeSecond.value]?.id ?? '')

onLoad(() => {
  getCategory()
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
        <view
          v-for="(item, index) in subCategories"
          :key="item.id ?? index"
          class="category-rail-item"
          :class="index === activeSecond ? 'category-rail-item--active' : ''"
          @click="selectSecond(index)"
        >
          {{ item.name }}
        </view>
        <view v-if="subCategories.length === 0" class="category-rail-empty">
          暂无子类目
        </view>
      </scroll-view>

      <view class="category-goods">
        <goods-list-panel
          v-if="treeLoaded"
          :category-first-id="currentFirstId"
          :category-second-id="currentSecondId"
          show-sort
          detail-sheet
        />
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
  background: #f6f6f6;
}

.category-cart {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 88rpx;
}

.category-content {
  display: flex;
  flex: 1;
  min-height: 0;
  background: #fff;
}

.category-rail {
  width: 168rpx;
  height: 100%;
  flex: none;
  background: #f6f6f6;
}

.category-rail-item {
  padding: 28rpx 12rpx;
  font-size: 26rpx;
  color: #333;
  text-align: center;
  word-break: break-all;

  &--active {
    background: #fff;
    color: var(--theme-color-primary, var(--wot-color-theme-primary));
    font-weight: 600;
  }
}

.category-rail-empty {
  padding: 40rpx 12rpx;
  font-size: 24rpx;
  color: #999;
  text-align: center;
}

/**
 * 商品流容器用 flex 而非 height:100%：
 * 小程序里父级高度由 flex 拉伸决定时，子级百分比高度不总可靠。
 */
.category-goods {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;

  // 子组件根节点会带上父组件的 scoped 属性，可直接命中
  .goods-list-panel {
    flex: 1;
    min-height: 0;
  }
}
</style>

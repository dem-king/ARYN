<script setup lang="ts">
import type { AppSeckillGoodsVO } from '@/api/promotion'
import type { PurchaseDecision } from '@/utils/goods-purchase'
import { onLoad, onPageScroll, onShareAppMessage } from '@dcloudio/uni-app'
// @ts-expect-error: mp-html type declaration issue
import mpHtml from 'mp-html/dist/uni-app/components/mp-html/mp-html'
import {
  computed,
  getCurrentInstance,
  nextTick,
  onMounted,
  reactive,
  ref,
} from 'vue'
import { addShoppingCart } from '@/api/order/shoppingCart'
import {
  addObj as addCollect,
  deleteObj as deleteCollect,
} from '@/api/product/collect'
import { getById as getSpuById } from '@/api/product/spu'
import { getGoodsSeckillInfo } from '@/api/promotion'
import { getPage as getCouponPage } from '@/api/promotion/couponInfo'
import { getDefault } from '@/api/user/address'
import DiyPage from '@/components/diy/index.vue'
import {
  buildDistributionSharePath,
  captureDistributionShareParams,
  flushPendingDistributionShareBinding,
} from '@/composables/useDistributionShare'
import { usePageDecoration } from '@/composables/usePageDecoration'
import { useQuickCart } from '@/composables/useQuickCart'
import { resolvePurchaseDecision, resolveSpecRow } from '@/utils/goods-purchase'
import { initGoodsSpecs } from '@/utils/goods-specs'
import { customerServiceRoute } from '@/utils/message'
import GoodsComment from './components/GoodsComment.vue'
import GoodsFooter from './components/GoodsFooter.vue'
import GoodsInfo from './components/GoodsInfo.vue'
import GoodsNavbar from './components/GoodsNavbar.vue'
import MorePopup from './components/MorePopup.vue'
import SharePopup from './components/SharePopup.vue'
import ShipProfileCard from './components/ShipProfileCard.vue'

definePage({
  name: 'goods-detail',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '商品详情',
  },
})
interface State {
  couponState: boolean // 领券弹窗
  shareShow: boolean // 分享弹窗
  moreShow: boolean
  isCanBack: boolean
  goodsSpu: any // 商品信息
  address: any // 默认收货地址
  tabActive: number
  selectArr: string
  scrollStatus: boolean
  couponList: any[]
  rateType: string
  /** 当前 SKU 的秒杀信息；null 表示无进行中的秒杀 */
  seckillInfo: AppSeckillGoodsVO | null
}

// 定义变量
const skuKey = ref(false)
const skuMode = ref(1)
const loading = ref(true)
const skuPopup = ref()

// 商详装修（pageType=2）：加载已发布装修 Schema，失败降级不影响主流程
const { pageContentData: decorationContent, loading: decorationLoading, fetch: fetchDecoration } = usePageDecoration('2')

// 组件ref
const goodsInfoRef = ref()
const goodsCommentRef = ref()

const tabList = ref([
  {
    name: '商品',
    top: 0,
    height: 0,
  },
  {
    name: '评价',
    top: 0,
    height: 0,
  },
  {
    name: '详情',
    top: 0,
    height: 0,
  },
  {
    name: '推荐',
    top: 0,
    height: 0,
  },
])
const state = reactive<State>({
  couponState: false, // 领券弹窗
  shareShow: false, // 分享弹窗
  moreShow: false,
  isCanBack: false,
  goodsSpu: {}, // 商品信息
  address: {}, // 默认收货地址
  tabActive: 0,
  selectArr: '',
  scrollStatus: false,
  couponList: [],
  rateType: '',
  seckillInfo: null,
})
const router = useRouter()
const authStore = useAuthStore()
const { confirm } = useGlobalMessage()
const globalLoading = useGlobalLoading()
const userStore = useUserStore()
const shoppingCartStore = useShoppingCartStore()
/** 单规格直加购与列表页快捷加购共用同一份规则（MOQ/步长/登录守卫） */
const { quickAdd } = useQuickCart()
const spuId = ref()
const proxy = getCurrentInstance()?.proxy
const { statusBarHeight }: any = uni.getSystemInfoSync()

/**
 * 购买决策（在注入「默认」占位规格前计算）：
 * 单规格商品免选规格——加入购物车直接按 MOQ/步长的合法数量入车；
 * 多规格仍需用户在弹层里选规格组合。
 */
const purchaseDecision = ref<PurchaseDecision>({ needChoose: true, specText: '' })
const specRow = computed(() => resolveSpecRow(purchaseDecision.value, state.selectArr))
// 导航背景颜色
const background = ref('rgba(255, 255, 255, 0)')
// 导航标题字体颜色
const rootOpacity = ref(0)
const rootStyle = computed(() => {
  const style = {
    'padding-top': `${statusBarHeight}px`,
  }
  return style
})

function resolveSpuIdFromOptions(options?: Record<string, any>) {
  if (!options)
    return ''
  if (options.scene) {
    const scene = decodeURIComponent(String(options.scene))
    const segments = scene.split('&')
    const firstSegment = segments[0] || ''
    if (firstSegment.includes('=')) {
      const [_, value] = firstSegment.split('=')
      return value || ''
    }
    return firstSegment
  }
  return options.id ? String(options.id) : ''
}

onLoad(async (options) => {
  captureDistributionShareParams({
    ...(options || {}),
    sourcePath: '/sub-pages/product/goods-detail/index',
  })
  await flushPendingDistributionShareBinding()
  const id = resolveSpuIdFromOptions(options as Record<string, any>)
  spuId.value = id
  if (!id)
    return
  // 商品详情
  getSpu(id)
  // 获取已登录用户默认收货地址
  getDefaultAddress()
  // 获取商详装修 Schema（pageType='2'），失败不影响主流程
  fetchDecoration()
})

/**
 * 加载当前 SKU 的秒杀信息。
 *
 * 秒杀价由服务端在下单时实时重算，商详页只做展示与提示；活动结束或库存抢完时
 * 接口返回 null，页面回落到普通价格，不会出现「显示秒杀价、下单按原价」的错位。
 */
async function loadSeckillInfo() {
  const skuIds = (state.goodsSpu?.goodsSkus || [])
    .map((sku: any) => sku.id)
    .filter(Boolean)
  if (!skuIds.length)
    return
  try {
    const list = await Promise.all(skuIds.map((skuId: string) => getGoodsSeckillInfo(skuId)))
    // 同一 SPU 下可能多个 SKU 同时在秒杀，取第一个命中；无命中则视为无秒杀
    const hit = list.find(item => item && item.seckillPrice != null)
    state.seckillInfo = hit || null
  }
  catch {
    // 秒杀信息属增强展示，接口失败不影响正常购买
    state.seckillInfo = null
  }
}

onPageScroll((res) => {
  // 获取当前滚动位置
  const scrollTop = res.scrollTop
  // 定义最大滚动高度和最大透明度
  const maxScrollTop = 200 // 根据你的页面调整这个值
  const maxOpacity = 1 // 最大透明度
  state.scrollStatus = scrollTop > 100
  // 计算透明度
  let opacity = (scrollTop / maxScrollTop) * maxOpacity
  // 确保透明度在0到maxOpacity之间
  opacity = Math.min(maxOpacity, Math.max(0, opacity))
  background.value = `rgba(255, 255, 255, ${opacity})`
  rootOpacity.value = opacity
  // 处理tabs切换
  const index = tabList.value.findIndex(
    item => scrollTop < item.top + item.height - statusBarHeight - 60,
  )
  state.tabActive = index >= 0 ? index : tabList.value.length - 1
})
onMounted(async () => {
  const pages = getCurrentPages()
  /**
   * 判断是否能返回
   */
  if (
    pages.length <= 1
    || pages[pages.length - 1].route === 'pages/login/index'
  ) {
    state.isCanBack = false
  }
  else {
    state.isCanBack = true
  }
})
// 富文本渲染完成
function readyHandel() {
  getBoundingClientRect('#scroll-description')
    .then((data: any) => {
      tabList.value[2].top = data.top
      tabList.value[2].height = data.height
    })
    .catch((error: any) => {
      console.error('获取商品详情位置失败:', error)
    })
  getBoundingClientRect('#scroll-like')
    .then((data: any) => {
      tabList.value[3].top = data.top
      tabList.value[3].height = data.height
    })
    .catch((error: any) => {
      console.error('获取推荐位置失败:', error)
    })
}
function goBack() {
  if (state.isCanBack) {
    uni.navigateBack({
      delta: 1,
    })
  }
  else {
    toHome()
  }
}

function handleBuyAction(value: number) {
  // 单规格商品（约 98% 在架商品）不需要选规格：与列表页快捷加购同口径直接入车，
  // 数量取满足船供 MOQ/步长的最小合法值；多规格仍弹层选规格。
  const isDirectAdd = value === 2 && !purchaseDecision.value.needChoose
  if (!isDirectAdd) {
    skuMode.value = value
    skuKey.value = true
    return
  }
  const spuIdValue = String(spuId.value || '')
  if (!spuIdValue)
    return
  quickAdd(spuIdValue).then((result) => {
    if (result.added)
      return
    // 前后端判定不一致（需选规格）或查询异常时退回弹层，避免点击无响应；
    // 缺货等场景 useQuickCart 已 toast 具体原因
    if (result.needChoose || result.info === null) {
      skuMode.value = 2
      skuKey.value = true
    }
  })
}
function onCurrentPage(item: any, index: number) {
  uni.pageScrollTo({
    scrollTop: item.top - statusBarHeight - 50,
  })
  state.tabActive = index
}
onShareAppMessage(() => {
  const path = buildDistributionSharePath(
    `/sub-pages/product/goods-detail/index?id=${state.goodsSpu.id}`,
  )
  return {
    title: state.goodsSpu.name,
    path,
    imageUrl: state.goodsSpu?.spuUrls[0],
  }
})
// 商品分享
function goodsShare() {
  const path = buildDistributionSharePath(
    `/sub-pages/product/goods-detail/index?id=${state.goodsSpu.id}`,
  )
  // #ifdef H5
  uni.setClipboardData({
    data: window.location.origin + path,
    success() {},
  })
  // #endif
}

// 处理分享按钮点击
function handleShare() {
  state.shareShow = true
}

// 处理更多按钮点击
function handleMore() {
  state.moreShow = true
}
// sku组件 开始-----------------------------------------------------------
function onOpenSkuPopup() {
  console.log('监听 - 打开sku组件')
}
function onCloseSkuPopup(data: any) {
  // 弹层正常关闭回传已选规格数组；「close」字符串只出现在远端取数失败路径，
  // 用 Array.isArray 挡掉，避免对字符串调 join 抛错
  if (Array.isArray(data)) {
    state.selectArr = data.join(' ').trim()
  }
  console.log('监听 - 关闭sku组件')
}
// 打开优惠券弹窗
function showCoupon() {
  state.couponState = true
}
// 通过id查询商品详情
async function getSpu(id: string) {
  globalLoading.loading('加载中...')
  loading.value = true
  try {
    const response = await getSpuById(id)
    loading.value = false
    state.goodsSpu = response
    if (response) {
      queryCoupon()
      loadSeckillInfo()
      // 在注入「默认」占位规格前取原始规格口径：单规格商品的规格行要展示真实规格值
      purchaseDecision.value = resolvePurchaseDecision(response)
      // 规格结构初始化与 goods-detail-sheet / quick-cart-button 共用同一份工具
      initGoodsSpecs(state.goodsSpu)
      nextTick(() => {
        uni.setNavigationBarTitle({
          title: state.goodsSpu.name,
        })
        // 在数据加载完成且DOM更新后获取元素位置
        updateTabListPositions()
      })
    }
    else {
      confirm({
        title: '提示',
        msg: '商品已下架',
        closeOnClickModal: false,
        success: () => {
          // 下架商品只需离开本页；两个分支原本都是 navigateBack，
          // 但本页可能是页面栈底（分享/扫码直达），此时 navigateBack 必然失败，
          // 需与 goBack() 同口径退回首页。
          goBack()
        },
      })
    }
  }
  catch (error) {
    console.error('获取商品详情失败:', error)
  }
  finally {
    globalLoading.close()
  }
}
function getBoundingClientRect(id: string) {
  return new Promise((resolve, reject) => {
    const query = uni.createSelectorQuery().in(proxy)
    query
      .select(id)
      .boundingClientRect((data) => {
        if (data) {
          resolve(data)
        }
        else {
          reject(new Error('Element not found or query failed'))
        }
      })
      .exec()
  })
}

// 更新tabList位置的函数
async function updateTabListPositions() {
  // 添加延迟确保组件完全渲染
  await new Promise(resolve => setTimeout(resolve, 500))

  // 通过组件ref获取元素位置
  if (goodsInfoRef.value) {
    goodsInfoRef.value
      .getScrollPicRect()
      .then((data: any) => {
        tabList.value[0].top = data.top
        tabList.value[0].height = data.height
      })
      .catch((error: any) => {
        console.error('获取商品信息位置失败:', error)
      })
  }

  if (goodsCommentRef.value) {
    goodsCommentRef.value
      .getScrollCommentRect()
      .then((data: any) => {
        tabList.value[1].top = data.top
        tabList.value[1].height = data.height
      })
      .catch((error: any) => {
        console.error('获取评论位置失败:', error)
      })
  }
}
// 添加购物车（弹层回传路径；单规格直加路径在 handleBuyAction → useQuickCart）
function addCart(data: any) {
  addShoppingCart(data).then(() => {
    uni.showToast({
      title: '已加入购物车',
      icon: 'none',
      duration: 3000,
    })
    skuKey.value = false
    shoppingCartStore.fetchCartCount()
  })
}
// 收藏
async function handleCollect() {
  globalLoading.loading('加载中...')
  if (state.goodsSpu.collectId) {
    // 取消收藏
    try {
      await deleteCollect(state.goodsSpu.collectId)
      state.moreShow = false
      state.goodsSpu.collectId = ''
      userStore.updateUserCollectCount(userStore.getCollectCount - 1)
    }
    finally {
      globalLoading.close()
    }
  }
  else {
    try {
      const response = await addCollect({
        spuId: state.goodsSpu.id,
        salesPrice: state.goodsSpu.salesPrice,
      })
      state.moreShow = false
      state.goodsSpu.collectId = response.id
      userStore.updateUserCollectCount(userStore.getCollectCount + 1)
    }
    finally {
      globalLoading.close()
    }
  }
}

// 查询用户默认收货地址
async function getDefaultAddress() {
  if (authStore.isLoggedIn) {
    state.address = await getDefault()
  }
}

// 查询优惠券
function queryCoupon() {
  getCouponPage({
    spuId: state.goodsSpu.id,
    current: 1,
    size: 50,
    desc: 'create_time',
  }).then((response) => {
    state.couponList = response.records
  })
}

// 跳转收货地址
function toAddress() {
  router.push({
    name: 'address-list',
  })
}
// 跳转首页
function toHome() {
  router.pushTab({ name: 'home' })
}
// 跳转购物车
function toCart() {
  router.pushTab({
    name: 'shopping-cart',
  })
}

function toCustomerService() {
  const payload = {
    image: state.goodsSpu.spuUrls?.[0] || '',
    productId: String(state.goodsSpu.id),
    summary: state.goodsSpu.introduction || state.goodsSpu.name,
    title: state.goodsSpu.name,
  }
  uni.navigateTo({
    url: customerServiceRoute({ messageType: 'PRODUCT_CARD', payload }),
  })
}

function handleSwiper(obj: any) {
  uni.previewImage({
    current: obj.index,
    urls: state.goodsSpu.spuUrls,
  })
}

// 领取优惠券
async function handleReceive(coupon: any) {
  const item = state.couponList.find(item => item.id === coupon.id)
  if (item) {
    item.userReceiveCount = item.userReceiveCount + 1
  }
}
</script>

<template>
  <!-- 顶部导航组件 -->
  <GoodsNavbar
    :scroll-status="state.scrollStatus"
    :is-can-back="state.isCanBack"
    :tab-active="state.tabActive"
    :tab-list="tabList"
    :background="background"
    :root-opacity="rootOpacity"
    :root-style="rootStyle"
    @go-back="goBack"
    @current-page="onCurrentPage"
    @share="handleShare"
    @more="handleMore"
  />
  <view v-if="!loading">
    <!-- 商品信息组件 -->
    <GoodsInfo
      ref="goodsInfoRef"
      :goods-spu="state.goodsSpu"
      :coupon-list="state.couponList"
      :spec-row="specRow"
      :address="state.address"
      :seckill-info="state.seckillInfo"
      @swiper="handleSwiper"
      @show-coupon="showCoupon"
      @open-sku-popup="handleBuyAction"
      @to-address="toAddress"
      @collect="handleCollect"
      @share="handleShare"
    />
    <ShipProfileCard :spu-id="spuId" />
    <!-- 商详装修区域（商品信息下方、评价上方） -->
    <view v-if="decorationContent">
      <DiyPage
        :page-content-data="decorationContent"
        :goods-id="String(spuId || '')"
      />
    </view>
    <view v-else-if="decorationLoading" class="decoration-skeleton" />
    <!-- 评论组件 -->
    <GoodsComment ref="goodsCommentRef" :spu-id="state.goodsSpu.id" />
    <!-- 商品介绍 -->
    <view
      id="scroll-description"
      class="m-2 rounded-xl bg-white p-2 dark:bg-[var(--wot-dark-background2)]"
    >
      <wd-divider custom-class="px-26! text-[#333333]!">
        商品详情
      </wd-divider>
      <view>
        <mp-html :content="state.goodsSpu.description" @ready="readyHandel" />
      </view>
    </view>
    <!-- 猜你喜欢 -->
    <view
      id="scroll-like"
      class="my-2 flex items-center justify-center text-sm"
    >
      你可能还会喜欢
    </view>
    <WaterfallGoods />
    <wd-gap :height="40" />
    <GoodsFooter
      :shopping-cart-count="shoppingCartStore.getCartCount"
      @to-home="toHome"
      @to-cart="toCart"
      @customer-service="toCustomerService"
      @open-sku-popup="handleBuyAction"
    />
  </view>

  <vk-data-goods-sku-popup
    ref="skuPopup"
    v-model="skuKey"
    border-radius="20"
    :localdata="state.goodsSpu"
    sku-arr-name="specsArr"
    sku-list-name="goodsSkus"
    spec-list-name="specList"
    :mode="skuMode"
    @open="onOpenSkuPopup"
    @close="onCloseSkuPopup"
    @add-cart="addCart"
  />
  <!-- 领券弹窗 -->
  <wd-action-sheet
    v-model="state.couponState"
    title="优惠券"
    custom-class="coupon-action"
    @close="state.couponState = false"
  >
    <scroll-view scroll-y class="mb-4 h-400px">
      <view
        v-for="coupon in state.couponList"
        :key="coupon.id"
        class="px-4 py-1"
      >
        <CouponCard
          :coupon="coupon"
          :status="coupon.userReceiveCount > 0 ? 'received' : 'available'"
          @receive="handleReceive"
        />
      </view>
    </scroll-view>
  </wd-action-sheet>

  <!-- 分享弹窗 -->
  <SharePopup v-model="state.shareShow" @share="goodsShare" />

  <!-- 更多弹窗 -->
  <MorePopup
    v-model="state.moreShow"
    :collect-id="state.goodsSpu?.collectId"
    @to-home="toHome"
    @collect="handleCollect"
  />
</template>

<style lang="scss">
.wd-popup-title {
  color: var(--wot-action-sheet-color, rgba(0, 0, 0, 0.85));
  position: relative;
  height: var(--wot-action-sheet-title-height, 40px);
  line-height: var(--wot-action-sheet-title-height, 40px);
  text-align: center;
  font-size: var(--wot-action-sheet-title-fs, var(--wot-fs-title, 16px));
  font-weight: var(--wot-action-sheet-weight, 500);
}

.coupon-action {
  max-height: 500px;
}

:deep() {
  .wd-swiper__track {
    border-radius: 0px !important;
  }
  .wd-swiper-nav--bottom {
    bottom: 30rpx !important;
  }
}

.decoration-skeleton {
  height: 300rpx;
  margin: 16rpx;
  border-radius: 12rpx;
  background: #f5f5f5;
}
</style>

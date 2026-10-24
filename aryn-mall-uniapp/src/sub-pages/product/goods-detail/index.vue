<script setup lang="ts">
import type { AppDiscountVO, AppNewbiePriceVO, AppSeckillGoodsVO } from '@/api/promotion'
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
import { addObj as addCollect, deleteObj as deleteCollect } from '@/api/product/collect'
import { getById as getSpuById } from '@/api/product/spu'
import { getGoodsDiscountPrices, getGoodsSeckillInfo, getNewbieSkuPrices } from '@/api/promotion'
import { getPage as getCouponPage } from '@/api/promotion/couponInfo'
import { getDefault } from '@/api/user/address'
import DiyPage from '@/components/diy/index.vue'
import { useCartDestination } from '@/composables/useCartDestination'
import {
  buildDistributionSharePath,
  captureDistributionShareParams,
  flushPendingDistributionShareBinding,
} from '@/composables/useDistributionShare'
import { usePageDecoration } from '@/composables/usePageDecoration'
import { useQuickCart } from '@/composables/useQuickCart'
import { emptyDiscountDisplay, resolveDiscountPriceDisplay } from '@/utils/discount-price'
import { resolvePurchaseDecision, resolveSpecRow } from '@/utils/goods-purchase'
import { initGoodsSpecs } from '@/utils/goods-specs'
import { customerServiceRoute } from '@/utils/message'
import { emptyNewbieDisplay, newbieLimitHint, resolveNewbiePriceDisplay } from '@/utils/newbie-price'
import GoodsCategoryEntry from './components/GoodsCategoryEntry.vue'
import GoodsComment from './components/GoodsComment.vue'
import GoodsFooter from './components/GoodsFooter.vue'
import GoodsInfo from './components/GoodsInfo.vue'
import GoodsNavbar from './components/GoodsNavbar.vue'
import MorePopup from './components/MorePopup.vue'
import SharePopup from './components/SharePopup.vue'

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
  /** 本 SPU 各 SKU 的新人价（含当前用户资格三态）；null 表示无活动/接口失败 */
  newbiePriceVO: AppNewbiePriceVO | null
  /** 本 SPU 各 SKU 命中的最优限时折扣；null 表示无活动/接口失败 */
  discountPrices: AppDiscountVO[] | null
  /**
   * 多规格商品「用户当前看着的 SKU」。
   *
   * 不能只靠 selectArr 反查：弹层选完规格点加购时是**程序化关闭**（页面把
   * skuKey 置 false），不会触发 `@close` 回传规格数组，只靠 selectArr 会让价格区
   * 停在旧 SKU 上。这里用「弹层加购回传的 skuId」+「关闭时按规格反查」两路共同维护。
   */
  selectSkuId: string
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
  newbiePriceVO: null,
  discountPrices: null,
  selectSkuId: '',
})
const router = useRouter()
const authStore = useAuthStore()
const { confirm } = useGlobalMessage()
const globalLoading = useGlobalLoading()
const userStore = useUserStore()
const shoppingCartStore = useShoppingCartStore()
/** 单规格直加购与列表页快捷加购共用同一份规则（MOQ/步长/登录守卫） */
const { quickAdd } = useQuickCart()
const { submitCartAdd } = useCartDestination()
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
 * 加载本 SPU 各 SKU 的新人价（含当前用户资格）。
 *
 * 与秒杀同口径：失败静默、只影响价格标注，不影响加购/下单主流程。
 *
 * 注意这里**必须**带上登录态（接口故意不传 skipToken）——未登录时后端返回
 * `eligible=null`（按可享展示，做营销吸引），已登录且确定不符合时返回
 * `eligible=false`，页面据此回落到原价并说明原因，避免「商详标新人价、
 * 结算按原价」的价差观感（用户投诉的正是这里）。
 */
async function loadNewbiePrices() {
  const skuIds = (state.goodsSpu?.goodsSkus || [])
    .map((sku: any) => sku.id)
    .filter(Boolean)
  if (!skuIds.length)
    return
  try {
    state.newbiePriceVO = await getNewbieSkuPrices(skuIds)
  }
  catch {
    // 新人价属增强展示，接口失败按原价展示
    state.newbiePriceVO = null
  }
}

/**
 * 加载本 SPU 各 SKU 命中的最优限时折扣（免登批量）。
 *
 * 与秒杀/新人价同口径：失败静默、只影响价格标注，不影响加购/下单主流程。
 * 折扣价与会场展示同源（后端按同一份进行中活动计算），缺失时页面回落售价。
 */
async function loadDiscountPrices() {
  const skuIds = (state.goodsSpu?.goodsSkus || [])
    .map((sku: any) => sku.id)
    .filter(Boolean)
  if (!skuIds.length) {
    state.discountPrices = null
    return
  }
  try {
    state.discountPrices = await getGoodsDiscountPrices(skuIds)
  }
  catch {
    // 折扣价属增强展示，接口失败按原价展示
    state.discountPrices = null
  }
}

/**
 * 当前聚焦的 SKU。
 *
 * 单规格（98% 在架商品）直接取唯一 SKU——否则用户进商详永远看不到新人价，
 * 这正是「楼层点进来还是原价」的直接原因。
 *
 * 多规格优先用「最近一次选中的 SKU」：
 *   · 弹层加购时回传的 skuId 最准（用户确实在点哪个规格）；
 *   · 关闭弹层时回传的规格数组反查（未选完为空 → 价格区维持原价，
 *     不做「选着 A 规格、按 B 规格标新人价」的错误标注）。
 * 两者都没有时保持原价。
 */
const currentSkuId = computed(() => {
  const skus = state.goodsSpu?.goodsSkus || []
  if (!skus.length)
    return ''
  if (!purchaseDecision.value.needChoose)
    return String(skus[0]?.id || '')
  // 弹层加购回传的 SKU 优先：程序化关闭不会回传规格数组，这是多规格唯一可靠信号
  if (state.selectSkuId && skus.some((sku: any) => String(sku?.id) === state.selectSkuId))
    return state.selectSkuId
  // 关闭弹层回传的规格数组：join(' ') 后未选完的位会留下连续空格，
  // 统一按空白切分只比较实际选中的规格值
  const selectedParts = String(state.selectArr || '')
    .split(/\s+/)
    .filter(Boolean)
  if (!selectedParts.length)
    return ''
  const hit = skus.find((sku: any) => {
    const values = (sku?.specsArr || [])
      .map((spec: any) => spec?.specsValueName)
      .filter((name: string) => typeof name === 'string' && name.length > 0)
    return values.length === selectedParts.length
      && values.every((value: string, index: number) => value === selectedParts[index])
  })
  return String(hit?.id || '')
})

/**
 * 当前 SKU 的新人价展示。
 *
 * 优先级与后端 `OrderPriceComputeService` 一致：**秒杀 > 新人价**。
 * 秒杀进行中时整块新人价展示（含限购/资格提示）都关掉——两个活动同时挂着价，
 * 页面标一个、结算按另一个，价差观感只会更严重。
 */
const newbieDisplay = computed(() => {
  if (state.seckillInfo)
    return emptyNewbieDisplay()
  return resolveNewbiePriceDisplay(state.newbiePriceVO, currentSkuId.value)
})

/**
 * 当前 SKU 的限时折扣展示。
 *
 * 优先级与后端 `OrderPriceComputeService` 一致：**秒杀 > 新人价 > 限时折扣 > 原价**。
 * 上层活动命中时整块折扣展示关掉——同一时刻只解释一个成交价，
 * 否则页面标一个、结算按另一个，价差观感只会更严重。
 */
const discountDisplay = computed(() => {
  if (state.seckillInfo)
    return emptyDiscountDisplay()
  if (newbieDisplay.value.showNewbiePrice)
    return emptyDiscountDisplay()
  return resolveDiscountPriceDisplay(state.discountPrices, currentSkuId.value)
})

/**
 * 单规格直加时交给加购确认弹层的成交价。
 *
 * 与价格区同一优先级（秒杀 > 新人价 > 售价），否则弹层会出现「页面 ¥0.01、
 * 小计 ¥12.90」的价差；活动价必须绑定当前 SKU——快捷加购由后端按库存挑 SKU，
 * 标错规格的价比不标更糟。活动限购同时下发，让弹层提前封顶数量。
 */
const quickAddPromotion = computed(() => {
  const skuId = currentSkuId.value
  if (!skuId)
    return null
  const seckill = state.seckillInfo
  if (seckill && String(seckill.skuId || '') === skuId) {
    const limit = Math.floor(Number(seckill.limitPerUser))
    const maxQuantity = Number.isFinite(limit) && limit > 0 ? limit : null
    return {
      skuId,
      unitPrice: seckill.seckillPrice,
      maxQuantity,
      hint: maxQuantity ? `秒杀价每人限购 ${maxQuantity} 件` : '',
    }
  }
  const display = newbieDisplay.value
  if (display.showNewbiePrice) {
    return {
      skuId,
      unitPrice: display.newbiePrice,
      maxQuantity: display.limitPerUser,
      hint: newbieLimitHint(display),
    }
  }
  const discount = discountDisplay.value
  if (discount.showDiscountPrice) {
    return {
      skuId,
      unitPrice: discount.discountPrice,
      // 限时折扣不限购（库存由下单链路按活动占用校验），只提示力度
      maxQuantity: null,
      hint: `限时折扣 ${discount.badgeText}`,
    }
  }
  return null
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
// 商品详情/推荐锚点：富文本渲染完成、评价异步加载后都要重新量，否则点击顶部 tab 会偏
function measureDetailSections() {
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
// 富文本渲染完成
function readyHandel() {
  measureDetailSections()
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
  quickAdd(spuIdValue, { promotion: quickAddPromotion.value }).then((result) => {
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
      // 与秒杀并行、互不等待：三者都是增强展示，任一失败都不影响商详主流程
      loadNewbiePrices()
      loadDiscountPrices()
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

  measureDetailSections()
}
// 添加购物车（弹层回传路径；单规格直加路径在 handleBuyAction → useQuickCart）。
// 去向由统一入口决定：有进行中的共享购物车时先询问，提示与角标也由它处理
async function addCart(data: any) {
  try {
    const dest = await submitCartAdd({
      skuId: data.skuId,
      quantity: data.quantity,
      spuId: data.spuId || state.goodsSpu.id,
    })
    if (dest === 'abort')
      return
    skuKey.value = false
  }
  catch {
    // 请求异常已由统一拦截器提示，弹层保持打开让用户重试
  }
}

/**
 * 弹层加购的旁观者：只记录用户实际选中的 SKU，不改变加购行为。
 *
 * 单独一层而不是写进 addCart：弹层程序化关闭不会回传规格数组，
 * 价格区需要知道「用户刚选的是哪个规格」才能把新人价标对；
 * 但加购本身涉及共享车去向等多方逻辑，不适合在这里再挂展示职责。
 */
function onSkuAddCart(data: any) {
  const skuId = String(data?.skuId || '').trim()
  if (skuId)
    state.selectSkuId = skuId
  return addCart(data)
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
      :newbie-display="newbieDisplay"
      :discount-display="discountDisplay"
      @swiper="handleSwiper"
      @show-coupon="showCoupon"
      @open-sku-popup="handleBuyAction"
      @to-address="toAddress"
      @collect="handleCollect"
      @share="handleShare"
    />
    <!-- 商详装修区域（商品信息下方、评价上方） -->
    <view v-if="decorationContent">
      <DiyPage
        :page-content-data="decorationContent"
        :goods-id="String(spuId || '')"
      />
    </view>
    <view v-else-if="decorationLoading" class="decoration-skeleton" />
    <!-- 评论组件 -->
    <GoodsComment ref="goodsCommentRef" :spu-id="state.goodsSpu.id" @loaded="updateTabListPositions" />
    <!-- 评价下方的分类回流：去逛逛会落到当前商品所属分类 -->
    <GoodsCategoryEntry
      :spu-id="String(state.goodsSpu.id || '')"
      :category-first-id="state.goodsSpu.categoryFirstId || ''"
      :category-second-id="state.goodsSpu.categorySecondId || ''"
      :category-name="state.goodsSpu.categoryName || ''"
    />
    <!-- 商品介绍 -->
    <view
      id="scroll-description"
      class="detail-section bg-white dark:bg-[var(--wot-dark-background2)]"
    >
      <view class="detail-section__head">
        <view class="detail-section__bar" />
        <text class="detail-section__title">
          商品详情
        </text>
      </view>
      <view class="detail-section__body">
        <mp-html :content="state.goodsSpu.description" @ready="readyHandel" />
      </view>
    </view>
    <!-- 猜你喜欢 -->
    <view id="scroll-like" class="detail-rec-head">
      <view class="detail-section__bar" />
      <text class="detail-section__title">
        你可能还会喜欢
      </text>
    </view>
    <WaterfallGoods />
    <!-- 与固定底栏等高的留白：避免最后一行商品被操作栏压住 -->
    <view class="detail-bottom-space" />
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
    @add-cart="onSkuAddCart"
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
/**
 * 商详区块标题：主题色竖条 + 标题。
 * 与底部推荐区共用同一套视觉，避免「商品详情」是一条分割线、
 * 「你可能还会喜欢」是一行居中灰字的两种语言。
 */
.detail-section {
  margin: 16rpx 24rpx;
  padding: 24rpx;
  overflow: hidden;
  border-radius: 24rpx;
}

.detail-section__head {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.detail-section__bar {
  width: 6rpx;
  height: 28rpx;
  flex: none;
  border-radius: 999rpx;
  background: var(--theme-color-primary, var(--wot-color-theme-primary, #ff2237));
}

.detail-section__title {
  color: #1f2329;
  font-size: 28rpx;
  font-weight: 600;
}

.detail-section__body {
  margin-top: 16rpx;
}

.detail-rec-head {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin: 24rpx 24rpx 8rpx;
}

.wot-theme-dark .detail-section__title {
  color: #f2f3f5;
}

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
    bottom: 48rpx !important;
  }
}

.detail-bottom-space {
  height: calc(140rpx + env(safe-area-inset-bottom));
}

.decoration-skeleton {
  height: 300rpx;
  margin: 16rpx;
  border-radius: 12rpx;
  background: #f5f5f5;
}
</style>

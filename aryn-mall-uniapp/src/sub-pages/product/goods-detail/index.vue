<script setup lang="ts">
import type { AppDiscountVO, AppMemberPriceVO, AppNewbiePriceVO, AppSeckillGoodsVO } from '@/api/promotion'
import type { GoodsAttrTagPayload } from '@/api/product/attr-tags'
import type { GoodsPromotionHints as PromotionHints } from '@/api/promotion/goods-hints'
import type { LadderPriceItem } from '@/api/promotion/ladder-price'
import type { AppClearancePriceVO } from '@/utils/clearance-price'
import type { GoodsProfile } from '@/utils/goods-profile'
import type { PurchaseDecision } from '@/utils/goods-purchase'
import type { NewbiePriceDisplay } from '@/utils/newbie-price'
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
  watch,
} from 'vue'
import { addObj as addCollect, deleteObj as deleteCollect } from '@/api/product/collect'
import { getClearanceSkuPrices, getById as getSpuById } from '@/api/product/spu'
import { getGoodsDiscountPrices, getGoodsSeckillInfo, getMemberSkuPrices, getNewbieSkuPrices } from '@/api/promotion'
import { getGoodsPromotionHints } from '@/api/promotion/goods-hints'
import { getLadderPrices } from '@/api/promotion/ladder-price'
import { getPolicyPrices } from '@/api/promotion/policy-price'
import { getCalendarSpecials } from '@/api/promotion/trade-calendar'
import { getDefault } from '@/api/user/address'
import DiyPage from '@/components/diy/index.vue'
import { useCartDestination } from '@/composables/useCartDestination'
// 页面滚动广播：底部「猜你喜欢」推荐流靠它自动续页，同时覆盖商详装修楼层
import { useDecorationInfiniteScroll } from '@/composables/useDecorationInfiniteScroll'
import {
  buildDistributionSharePath,
  captureDistributionShareParams,
  flushPendingDistributionShareBinding,
} from '@/composables/useDistributionShare'
import { usePageDecoration } from '@/composables/usePageDecoration'
import { useQuickCart } from '@/composables/useQuickCart'
import { useRequireLogin } from '@/composables/useRequireLogin'
import { useI18n } from '@/sub-pages/locales'
import {
  emptyClearancePriceDisplay,
  resolveClearancePriceDisplay,
} from '@/utils/clearance-price'
import { emptyDiscountDisplay, resolveDiscountPriceDisplay } from '@/utils/discount-price'
import { resolveGoodsParams, skuSpecValues } from '@/utils/goods-params'
import { hasGoodsProfile, normalizeDetailSections, normalizeGoodsProfile } from '@/utils/goods-profile'
import { resolveDeliveryRow, resolvePurchaseDecision, resolveSpecRow } from '@/utils/goods-purchase'
import { resolvePurchaseTags } from '@/utils/goods-purchase-tags'
import { initGoodsSpecs } from '@/utils/goods-specs'
import { ladderTiersOf, nextPurchasableLadderTier, resolveLadderUnitPrice } from '@/utils/ladder-price'
import { emptyMemberPriceDisplay, resolveMemberPriceDisplay } from '@/utils/member-price'
import { customerServiceRoute } from '@/utils/message'
import { canGoBackFromCurrentPage } from '@/utils/navigation-lock'
import { emptyNewbieDisplay, resolveNewbiePriceDisplay } from '@/utils/newbie-price'
import { resolveListZonePrice } from '@/utils/zone-price'
import GoodsCategoryEntry from './components/GoodsCategoryEntry.vue'
import GoodsComment from './components/GoodsComment.vue'
import GoodsDetailSections from './components/GoodsDetailSections.vue'
import GoodsFooter from './components/GoodsFooter.vue'
import GoodsInfo from './components/GoodsInfo.vue'
import GoodsNavbar from './components/GoodsNavbar.vue'
import GoodsParamCard from './components/GoodsParamCard.vue'
import GoodsProfileSheet from './components/GoodsProfileSheet.vue'
import GoodsPromotionHints from './components/GoodsPromotionHints.vue'
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
  shareShow: boolean // 分享弹窗
  moreShow: boolean
  isCanBack: boolean
  goodsSpu: any // 商品信息
  address: any // 默认收货地址
  tabActive: number
  selectArr: string
  scrollStatus: boolean
  rateType: string
  /** 当前 SKU 的秒杀信息；null 表示无进行中的秒杀 */
  seckillInfo: AppSeckillGoodsVO | null
  /** 本 SPU 各 SKU 的新人价（含当前用户资格三态）；null 表示无活动/接口失败 */
  newbiePriceVO: AppNewbiePriceVO | null
  /**
   * 本 SPU 各 SKU 的会员价（含当前用户等级资格三态 + 会员折扣率权益
   * `userDiscountRate`）；null 表示无活动/接口失败。
   * 达标展示价由 `resolveMemberPriceDisplay` 取优（会员价 vs 售价×折扣率，不叠加）。
   */
  memberPriceVO: AppMemberPriceVO | null
  /** 本 SPU 各 SKU 的有效出清价；null 表示未打标/到期/接口失败 */
  clearancePriceVO: AppClearancePriceVO | null
  /** 本 SPU 各 SKU 命中的最优限时折扣；null 表示无活动/接口失败 */
  discountPrices: AppDiscountVO[] | null
  /**
   * 当前会话可享的协议价（按 SKU，含划线基准与起订量）；null=无上下文/未命中。
   * `minQty` 参与成交校验：件数不足时不切主价，只出「协议价 N 件起」提示。
   */
  policyPrices: Array<{ minQty?: number, originalPrice?: number, policyPrice: number, skuId: string }> | null
  /** 本 SPU 各 SKU 命中的档期特供价（按 SKU，可多活动并存）；null=无进行中特供/接口失败 */
  calendarSpecialPrices: Array<{ originalPrice?: null | number, perUserLimit?: null | number, price: number, remainingQuota?: null | number, skuId: string }> | null
  /** 本 SPU 各 SKU 命中的阶梯价档位（按当前件数取档）；null=无阶梯活动/接口失败 */
  ladderPrices: LadderPriceItem[] | null
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
// 2 = 只显示购物车按钮：B2B 采购统一走「加入购物车 → 购物车确认」，商详不提供立即下单
const skuMode = ref(2)
const loading = ref(true)
const skuPopup = ref()

// 商详装修（pageType=2）：加载已发布装修 Schema，失败降级不影响主流程
const { pageContentData: decorationContent, loading: decorationLoading, fetch: fetchDecoration } = usePageDecoration('2')

// 组件ref
const goodsInfoRef = ref()
const goodsCommentRef = ref()

// 只存词条 key：名称在 GoodsNavbar 里按当前语言渲染，切换语言无需重算锚点
const tabList = ref([
  {
    nameKey: 'product.tab.goods',
    top: 0,
    height: 0,
  },
  {
    nameKey: 'product.tab.reviews',
    top: 0,
    height: 0,
  },
  {
    nameKey: 'product.tab.detail',
    top: 0,
    height: 0,
  },
  {
    nameKey: 'product.tab.recommend',
    top: 0,
    height: 0,
  },
])

/** tab 下标 → 页面锚点；点击时现量一次，避免异步评价/装修没回填 tabList 时点击无响应 */
const TAB_ANCHOR_SELECTORS = ['', '#scroll-comment', '#scroll-description', '#scroll-like']
const state = reactive<State>({
  shareShow: false, // 分享弹窗
  moreShow: false,
  isCanBack: false,
  goodsSpu: {}, // 商品信息
  address: {}, // 默认收货地址
  tabActive: 0,
  selectArr: '',
  scrollStatus: false,
  rateType: '',
  seckillInfo: null,
  newbiePriceVO: null,
  memberPriceVO: null,
  clearancePriceVO: null,
  discountPrices: null,
  policyPrices: null as null | Array<{ minQty?: number, originalPrice?: number, policyPrice: number, skuId: string }>,
  calendarSpecialPrices: null as null | Array<{ originalPrice?: null | number, perUserLimit?: null | number, price: number, remainingQuota?: null | number, skuId: string }>,
  ladderPrices: null as null | LadderPriceItem[],
  selectSkuId: '',
})
const router = useRouter()
const { t } = useI18n()
const authStore = useAuthStore()
const { confirm } = useGlobalMessage()
const globalLoading = useGlobalLoading()
const userStore = useUserStore()
const shoppingCartStore = useShoppingCartStore()
/** 采购上下文：协议价/档期特供/阶梯价都按采购场景+船舶圈定（与列表补价层同一取法） */
const shipContextStore = useShipContextStore()
/**
 * 用户在 SKU 弹层里改过的件数；null=未交互，按当前 SKU 起订量预填（与单规格直加购同口径）。
 * 协议价 minQty 与阶梯档位都按这个件数判定，避免「页面标协议价、结算按售价」。
 */
const pickedQuantity = ref<number | null>(null)
/** 商品参数卡头部的“商品档案”弹层：只放配料/营养/标准，基础参数仍在参数卡内展开 */
const profileVisible = ref(false)
/** 单规格直加购与列表页快捷加购共用同一份规则（MOQ/步长/登录守卫） */
const { quickAdd } = useQuickCart()
const { submitCartAdd } = useCartDestination()
/** 需要登录的点击入口（收藏/客服/弹层加购确认）：未登录先记录回跳并打开登录页 */
const { ensureLoggedIn } = useRequireLogin()
const spuId = ref()
const proxy = getCurrentInstance()?.proxy
const { statusBarHeight } = uni.getWindowInfo()

// 推荐流自动续页：页面滚动/触底时广播检查（导航栏渐变另有 onPageScroll，两者互不冲突，
// 多个页面生命周期钩子会按注册顺序依次执行）
useDecorationInfiniteScroll()

/**
 * 购买决策（在注入「默认」占位规格前计算）：
 * 单规格商品免选规格——加入购物车直接按 MOQ/步长的合法数量入车；
 * 多规格仍需用户在弹层里选规格组合。
 */
const purchaseDecision = ref<PurchaseDecision>({ needChoose: true, specText: '' })
const specRow = computed(() => {
  const row = resolveSpecRow(purchaseDecision.value, state.selectArr)
  // 多规格未选时共享层给的是中文占位文案，展示层按当前语言替换；已选值/单规格值保持接口原文
  if (!purchaseDecision.value.needChoose || state.selectArr.trim())
    return row
  return { ...row, text: t('product.chooseSpecs') }
})
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
 * 加载当前会话可享的协议价（免登可访问：服务端对游客/非成员自动清空主体上下文）。
 *
 * 与五路价同口径：失败静默、只影响价格标注，不影响加购/下单主流程。
 * **必须带船舶上下文**：合同价按船舶/主体圈定，漏传 vesselId 会拿到另一条船的政策价。
 * 岸上企业主体暂无 C 端 store，只传船（后端按登录态自行解析主体）。
 * `minQty` 原样保留：件数不足时不切主价，改出「协议价 N 件起」提示。
 */
async function loadPolicyPrices() {
  const skuIds = (state.goodsSpu?.goodsSkus || [])
    .map((sku: any) => sku.id)
    .filter(Boolean)
  if (!skuIds.length) {
    state.policyPrices = null
    return
  }
  try {
    const items = await getPolicyPrices({ skuIds, vesselId: shipContextStore.vesselId || undefined })
    state.policyPrices = (items || []).map(item => ({
      minQty: item.minQty,
      originalPrice: item.originalPrice,
      policyPrice: item.policyPrice,
      skuId: item.skuId,
    }))
  }
  catch {
    state.policyPrices = null
  }
}

/**
 * 加载本 SPU 命中的档期特供价（免登可访问，按采购场景/船舶圈定）。
 *
 * 特供按活动配置整 SKU 覆盖：同一 SKU 命中多个活动取最低（服务端已按
 * 活动窗+档期窗过滤并剔除限量耗尽活动，这里只做展示匹配）。
 */
async function loadCalendarSpecialPrices() {
  const skuIds = (state.goodsSpu?.goodsSkus || [])
    .map((sku: any) => sku.id)
    .filter(Boolean)
  if (!skuIds.length) {
    state.calendarSpecialPrices = null
    return
  }
  try {
    const specials = await getCalendarSpecials({
      purchaseScene: shipContextStore.purchaseScene,
      vesselId: shipContextStore.vesselId || undefined,
    })
    const items: Array<{ originalPrice?: null | number, perUserLimit?: null | number, price: number, remainingQuota?: null | number, skuId: string }> = []
    for (const special of specials || []) {
      for (const item of special.items || []) {
        if (skuIds.includes(item.skuId) && Number(item.price) > 0) {
          items.push({
            originalPrice: item.originalPrice,
            perUserLimit: item.perUserLimit,
            price: Number(item.price),
            remainingQuota: special.remainingQuota,
            skuId: item.skuId,
          })
        }
      }
    }
    state.calendarSpecialPrices = items
  }
  catch {
    state.calendarSpecialPrices = null
  }
}

/**
 * 加载本 SPU 各 SKU 的阶梯价档位（免登可访问，按采购场景/船舶圈定）。
 *
 * 阶梯价与件数相关：这里只取档位，按当前件数取档在 `currentLadderPrice` 里算，
 * 与结算进度提示共用 `utils/ladder-price`，避免两处各算一遍。
 */
async function loadLadderPrices() {
  const skuIds = (state.goodsSpu?.goodsSkus || [])
    .map((sku: any) => sku.id)
    .filter(Boolean)
  if (!skuIds.length) {
    state.ladderPrices = null
    return
  }
  try {
    state.ladderPrices = await getLadderPrices({
      skuIds,
      purchaseScene: shipContextStore.purchaseScene,
      vesselId: shipContextStore.vesselId || undefined,
    })
  }
  catch {
    state.ladderPrices = null
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
 * 加载本 SPU 各 SKU 的会员价（含当前用户等级资格）。
 *
 * 与秒杀/新人价同口径：失败静默、只影响价格标注，不影响加购/下单主流程。
 *
 * 注意这里**必须**带上登录态（接口故意不传 skipToken）——未登录时后端返回
 * `eligible=null`（保持原价展示，并用活动配置价给「会员专享 ¥x」引导），
 * 已登录且等级不达标时返回 `eligible=false`，达标才切主价，避免
 * 「商详标会员价、结算按原价」的价差观感。
 */
async function loadMemberPrices() {
  const skuIds = (state.goodsSpu?.goodsSkus || [])
    .map((sku: any) => sku.id)
    .filter(Boolean)
  if (!skuIds.length)
    return
  try {
    state.memberPriceVO = await getMemberSkuPrices(skuIds)
  }
  catch {
    // 会员价属增强展示，接口失败按原价展示
    state.memberPriceVO = null
  }
}

/**
 * 加载本 SPU 各 SKU 的有效出清价（免登批量）。
 *
 * 与会员价并行、互不等待：失败只影响价格标注，商详主流程照常。
 */
async function loadClearancePrices() {
  const skuIds = (state.goodsSpu?.goodsSkus || [])
    .map((sku: any) => sku.id)
    .filter(Boolean)
  if (!skuIds.length) {
    state.clearancePriceVO = null
    return
  }
  try {
    state.clearancePriceVO = await getClearanceSkuPrices(skuIds)
  }
  catch {
    state.clearancePriceVO = null
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
  return skus.some((sku: any) => String(sku?.id) === state.selectSkuId) ? state.selectSkuId : ''
})

const parameterRows = computed(() => resolveGoodsParams(state.goodsSpu, {
  needChoose: purchaseDecision.value.needChoose,
  singleSpecText: purchaseDecision.value.specText,
  skuId: currentSkuId.value,
}))
/** 商品档案（配料/营养/标准）：与参数卡分离，只有新增深层内容才显示入口 */
const goodsProfile = computed<GoodsProfile | null>(() => normalizeGoodsProfile(state.goodsSpu?.goodsProfile))
const showGoodsProfile = computed(() => hasGoodsProfile(goodsProfile.value))
/** 结构化详情段落：产地/储存/场景，空数组不影响原商品描述 */
const detailSections = computed(() => normalizeDetailSections(state.goodsSpu?.detailSections))
/**
 * 首屏采购标签：储存/规格/箱规/产地（起订量真实存在时才补位）。
 * 用 `attrTags` 的展示壳承载，不新增首屏组件；无足够采购事实时回退运营配置的属性标签。
 */
const purchaseTags = computed<GoodsAttrTagPayload[]>(() =>
  resolvePurchaseTags(state.goodsSpu, {
    needChoose: purchaseDecision.value.needChoose,
    singleSpecText: purchaseDecision.value.specText,
    skuId: currentSkuId.value,
  }).map(tag => ({
    key: tag.key,
    label: t(`product.purchaseTags.${tag.key}`),
    value: tag.value,
    icon: tag.icon,
    tone: tag.tone,
    linkType: 'none',
  })))
/** 基础参数与配料/营养统一收进商品档案弹层，主页面只保留一个紧凑入口。 */
const showGoodsArchive = computed(() => parameterRows.value.length > 0 || showGoodsProfile.value)
const promotionHints = ref<PromotionHints | null>(null)
watch(
  () => [state.goodsSpu?.id, currentSkuId.value, shipContextStore.purchaseScene, shipContextStore.vesselCallId, authStore.isLoggedIn] as const,
  async ([id, skuId, purchaseScene, vesselCallId, loggedIn], _, onCleanup) => {
    promotionHints.value = null
    if (!id)
      return
    let current = true
    onCleanup(() => {
      current = false
    })
    try {
      const hints = await getGoodsPromotionHints(String(id), {
        skuId: skuId || undefined,
        purchaseScene,
        vesselCallId: loggedIn && purchaseScene === '2' ? vesselCallId || undefined : undefined,
      })
      if (current && (hints?.gifts?.length || hints?.volumes?.length))
        promotionHints.value = hints
    }
    catch {
      // 活动提示属于附加信息，失败时保留商品与购买入口。
    }
  },
)
const showShipArrangement = computed(() => shipContextStore.isShipSupply && shipContextStore.hasVesselContext)
const shipArrangement = computed(() => resolveDeliveryRow({
  hasVesselContext: showShipArrangement.value,
  vesselName: shipContextStore.vesselName,
  portName: shipContextStore.portName,
  berth: shipContextStore.berth,
  deliveryWindowStart: shipContextStore.deliveryWindowStart,
  deliveryWindowEnd: shipContextStore.deliveryWindowEnd,
}))

const currentSku = computed(() => (state.goodsSpu?.goodsSkus || [])
  .find((item: any) => String(item?.id || '') === currentSkuId.value))
const minimumOrderQty = computed(() => Math.max(1, Math.floor(Number(currentSku.value?.minOrderQty ?? currentSku.value?.moq ?? state.goodsSpu?.moq)) || 1))
const orderStepQty = computed(() => Math.max(1, Math.floor(Number(currentSku.value?.stepQty)) || 1))
/** 当前件数：弹层改过就用弹层值，否则取 SKU 起订量。 */
const currentQuantity = computed(() => {
  if (pickedQuantity.value !== null)
    return pickedQuantity.value
  return minimumOrderQty.value
})

/** 当前 SKU 售价：活动价的统一比较基准（多规格按选中 SKU，未选时回落 SPU 售价） */
const currentSalesPrice = computed(() => {
  const sku = (state.goodsSpu?.goodsSkus || []).find((item: any) => String(item?.id || '') === currentSkuId.value)
  const price = Number(sku?.salesPrice ?? state.goodsSpu?.salesPrice)
  return Number.isFinite(price) && price > 0 ? price : 0
})

/** 当前 SKU 的协议价命中项（含起订量）；null=未命中/无上下文 */
const policyHit = computed(() =>
  (state.policyPrices || []).find(item => item.skuId === currentSkuId.value) || null)

/** 协议起订量（缺省 1）：件数不足时协议价不参与成交，只出「N 件起」提示 */
const policyMinQty = computed(() => {
  const min = Math.floor(Number(policyHit.value?.minQty))
  return Number.isFinite(min) && min > 0 ? min : 1
})

/** 当前 SKU 的特供命中项（多活动取最低）；null=无进行中特供 */
const specialHit = computed(() => {
  const hits = (state.calendarSpecialPrices || []).filter(item => item.skuId === currentSkuId.value)
  if (!hits.length)
    return null
  return hits.reduce((best, item) => (item.price < best.price ? item : best), hits[0])
})

/** 当前 SKU 的阶梯档位（多活动取档位更优的一份，minQty 升序） */
const ladderTiers = computed(() => ladderTiersOf(state.ladderPrices, currentSkuId.value))

/** 当前件数命中的阶梯单价；未达首档为 null（卡片不算阶梯价，商详才算） */
const currentLadderPrice = computed(() => resolveLadderUnitPrice(ladderTiers.value, currentQuantity.value))

/**
 * 当前 SKU 的会员专享价展示。
 *
 * 优先级与后端 `OrderPriceComputeService` 一致：
 * **拼团 > 秒杀 > 会员价 > 新人价 > 限时折扣 > 原价**。
 * 秒杀进行中时整块会员价展示关掉；未达标/未登录时主价保持原价，
 * 只给「会员专享 ¥x」引导，不做购买拦截。
 */
const rawMemberDisplay = computed(() => {
  if (state.seckillInfo)
    return emptyMemberPriceDisplay()
  // 达标价在共享层取优：min(会员价, 当前售价 × vo.userDiscountRate)，取优不叠加；
  // 会员折扣率权益随 VO 透传，页面不再各算一遍（与结算同口径）
  return resolveMemberPriceDisplay(state.memberPriceVO, currentSkuId.value)
})

/** 当前 SKU 的出清价原始展示（优先级收敛统一在 zonePrice） */
const rawClearanceDisplay = computed(() => resolveClearancePriceDisplay(state.clearancePriceVO, currentSkuId.value))

/** 当前 SKU 的新人价原始展示（资格三态由共享层判定） */
const rawNewbieDisplay = computed(() => resolveNewbiePriceDisplay(state.newbiePriceVO, currentSkuId.value))

/**
 * 商详成交价的唯一共享判定（与商品卡/结算同一份 `utils/zone-price` 口径）：
 * 协议价（件数 ≥ minQty）> 秒杀 > min(阶梯价@件数, 特供, 会员, 出清)
 * > 新人价（仅严格更低）> 售价。
 *
 * 下面各展示位只保留「胜出那一层」再下发给 GoodsInfo——组件内不重复判定优先级，
 * 页面标什么价、加购弹层与结算就按什么价。
 */
const zonePrice = computed(() => resolveListZonePrice({
  clearanceDisplay: rawClearanceDisplay.value,
  ladderPrice: currentLadderPrice.value,
  memberDisplay: { ...rawMemberDisplay.value, strikePrice: rawMemberDisplay.value.originalPrice },
  newbiePrice: rawNewbieDisplay.value.showNewbiePrice ? rawNewbieDisplay.value.newbiePrice : null,
  policyPrice: policyHit.value && currentQuantity.value >= policyMinQty.value ? policyHit.value.policyPrice : null,
  salesPrice: currentSalesPrice.value,
  seckillPrice: state.seckillInfo?.seckillPrice ?? null,
  specialPrice: specialHit.value?.price ?? null,
}))

/** 特供余量（胜出活动级限量剩余；null=不限量）：商详「档期供应·余量 N」展示 */
const specialRemainingQuota = computed(() =>
  zonePrice.value.badgeKind === 'special' ? (specialHit.value?.remainingQuota ?? null) : null)

const memberDisplay = computed(() => {
  const kind = zonePrice.value.badgeKind
  // 家族里胜出才切主价；都未命中时保留 raw（承载「会员专享 ¥x」引导文案）
  return kind === 'member' || kind === '' ? rawMemberDisplay.value : emptyMemberPriceDisplay()
})

/** 有效出清价：身份价家族取低后胜出才展示（同价归属特供 > 会员 > 出清） */
const effectiveClearanceDisplay = computed(() =>
  zonePrice.value.badgeKind === 'clearance' ? rawClearanceDisplay.value : emptyClearancePriceDisplay())

/**
 * 协议价展示（当前 SKU 匹配）：合同价锁定成交基价，展示优先级最高。
 * **按件数 gating**：数量 < minQty 时不切主价（返回 null），由价格区出「协议价 N 件起」提示。
 */
const policyDisplay = computed(() => {
  if (zonePrice.value.badgeKind !== 'policy')
    return null
  return { originalPrice: policyHit.value?.originalPrice ?? null, price: zonePrice.value.mainPrice }
})

/**
 * 档期特供价展示（当前 SKU 匹配，多活动取最低）：与会员价/出清价取低，
 * 胜出才切主价；null=未命中或被更低的身份价/阶梯价接管。
 */
const calendarSpecialDisplay = computed(() => {
  if (zonePrice.value.badgeKind !== 'special')
    return null
  return { originalPrice: specialHit.value?.originalPrice ?? null, price: zonePrice.value.mainPrice }
})

/** 阶梯价展示：按当前件数取档且严格低于身份价家族才接管主价（同价保留身份角标） */
const ladderDisplayPrice = computed(() =>
  zonePrice.value.badgeKind === 'ladder' ? zonePrice.value.mainPrice : null)

/**
 * 新人价资格说明的本地化。
 *
 * 共享层 `newbieIneligibleText` 只产出中文，这里按同一个原因码口径换成当前语言；
 * 原因码无法识别时沿用共享层结果（该场景共享层本身返回空串，整行隐藏）。
 */
function localizeNewbieIneligibleText(display: NewbiePriceDisplay): string {
  if (!display.ineligibleText)
    return ''
  const vo = state.newbiePriceVO
  switch (String(vo?.ineligibleReason || '').trim()) {
    case 'ALREADY_PAID':
      return t('product.newbieAlreadyPaid')
    case 'REGISTER_TIMEOUT': {
      const days = Math.floor(Number(vo?.registerWithinDays))
      return days > 0
        ? t('product.newbieRegisterLimit', { days })
        : t('product.newbieNewUserOnly')
    }
    default:
      return display.ineligibleText
  }
}

/**
 * 当前 SKU 的新人价展示。
 *
 * 优先级与后端 `OrderPriceComputeService` 一致：**秒杀 > 会员价 > 新人价**。
 * 秒杀进行中或会员价已命中时整块新人价展示（含限购/资格提示）都关掉——
 * 同一时刻只解释一个成交价，否则页面标一个、结算按另一个，价差观感只会更严重。
 */
const newbieDisplay = computed(() => {
  if (state.seckillInfo)
    return emptyNewbieDisplay()
  if (memberDisplay.value.showMemberPrice)
    return emptyNewbieDisplay()
  if (effectiveClearanceDisplay.value.showClearancePrice)
    return emptyNewbieDisplay()
  // 协议价/特供/阶梯价胜出时同样整块关掉：新人价只在严格更低时才接管（共享层已判定）
  const kind = zonePrice.value.badgeKind
  if (kind === 'policy' || kind === 'special' || kind === 'ladder')
    return emptyNewbieDisplay()
  const display = rawNewbieDisplay.value
  return { ...display, ineligibleText: localizeNewbieIneligibleText(display) }
})

/**
 * 当前 SKU 的限时折扣展示。
 *
 * 优先级与后端 `OrderPriceComputeService` 一致：
 * **秒杀 > 会员价 > 新人价 > 限时折扣 > 原价**。
 * 上层活动命中时整块折扣展示关掉——同一时刻只解释一个成交价，
 * 否则页面标一个、结算按另一个，价差观感只会更严重。
 */
const discountDisplay = computed(() => {
  if (state.seckillInfo)
    return emptyDiscountDisplay()
  if (newbieDisplay.value.showNewbiePrice)
    return emptyDiscountDisplay()
  if (memberDisplay.value.showMemberPrice)
    return emptyDiscountDisplay()
  if (effectiveClearanceDisplay.value.showClearancePrice)
    return emptyDiscountDisplay()
  // 协议价/特供/阶梯价命中时也不解释折扣价（同一时刻只解释一个成交价）
  const kind = zonePrice.value.badgeKind
  if (kind === 'policy' || kind === 'special' || kind === 'ladder')
    return emptyDiscountDisplay()
  return resolveDiscountPriceDisplay(state.discountPrices, currentSkuId.value)
})

const ladderProgress = computed(() => {
  const quantity = currentQuantity.value
  const tiers = ladderTiers.value
  const price = zonePrice.value
  if (!tiers.length || !currentSku.value || ['policy', 'special', 'seckill', 'newbie'].includes(price.badgeKind ?? ''))
    return ''
  const stock = Number(currentSku.value.stock)
  const next = nextPurchasableLadderTier(tiers, quantity, minimumOrderQty.value, orderStepQty.value, stock)
  if (next && !discountDisplay.value.showDiscountPrice && next.unitPrice < price.mainPrice
    && (!policyHit.value || quantity + next.needQty < policyMinQty.value)) {
    const unit = currentSku.value.salesUnit || t('product.ladderFallbackUnit')
    return t('product.ladderNextTier', { count: next.needQty, unit, price: next.unitPrice.toFixed(2) })
  }
  const highest = tiers[tiers.length - 1]
  if (highest && quantity >= Math.max(highest.minQty, minimumOrderQty.value) && price.badgeKind === 'ladder' && quantity <= stock
    && (quantity - minimumOrderQty.value) % orderStepQty.value === 0) {
    return t('product.ladderHighestTier')
  }
  return ''
})

/**
 * 单规格直加时交给加购确认弹层的成交价。
 *
 * 与价格区同一优先级（秒杀 > 会员价 > 新人价 > 限时折扣 > 售价），否则弹层会出现
 * 「页面 ¥0.01、小计 ¥12.90」的价差；活动价必须绑定当前 SKU——快捷加购由后端
 * 按库存挑 SKU，标错规格的价比不标更糟。活动限购同时下发，让弹层提前封顶数量。
 */
const quickAddPromotion = computed(() => {
  const memberHint = t('product.memberPrice')
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
      // 秒杀是硬性限购（后端预扣校验拒绝超量单），确认弹层仍要封顶
      capQuantity: true,
      hint: maxQuantity ? t('product.seckillLimit', { count: maxQuantity }) : '',
    }
  }
  // 协议价（件数 ≥ minQty 才命中）：合同价锁定成交基价，弹层小计与价格区同源
  const policy = policyDisplay.value
  if (policy)
    return { skuId, unitPrice: policy.price, maxQuantity: null, hint: t('product.policyPrice') }
  // 档期特供：身份价家族里胜出才接管，限购随活动配置下发（超量件由后端拆行结算）
  const special = calendarSpecialDisplay.value
  if (special) {
    const limit = Math.floor(Number(specialHit.value?.perUserLimit))
    return {
      skuId,
      unitPrice: special.price,
      maxQuantity: Number.isFinite(limit) && limit > 0 ? limit : null,
      hint: t('product.calendarSpecialPrice'),
    }
  }
  // 阶梯价：按当前件数取档且严格低于身份价才接管
  const ladderPrice = ladderDisplayPrice.value
  if (ladderPrice !== null)
    return { skuId, unitPrice: ladderPrice, maxQuantity: null, hint: t('product.ladderPrice') }
  const member = memberDisplay.value
  if (member.showMemberPrice)
    return { skuId, unitPrice: member.memberPrice, maxQuantity: null, hint: memberHint }
  const clearance = effectiveClearanceDisplay.value
  if (clearance.showClearancePrice && clearance.clearancePrice !== null)
    return { skuId, unitPrice: clearance.clearancePrice, maxQuantity: null, hint: t('product.clearancePrice') }
  const display = newbieDisplay.value
  if (display.showNewbiePrice)
    return { skuId, unitPrice: display.newbiePrice, maxQuantity: display.limitPerUser, hint: t('product.newbieLimit', { count: display.limitPerUser }) }
  const discount = discountDisplay.value
  if (discount.showDiscountPrice) {
    return {
      skuId,
      unitPrice: discount.discountPrice,
      // 限时折扣不限购（库存由下单链路按活动占用校验），只提示力度
      maxQuantity: null,
      hint: t('product.limitedDiscountWithBadge', { badge: discount.badgeText }),
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

let pageScrollTop = 0
onPageScroll((res) => {
  // 获取当前滚动位置
  const scrollTop = res.scrollTop
  pageScrollTop = scrollTop
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
  // 返回能力按点击时刻现算：onMounted 的快照在 redirectTo/replaceAll 改写页面栈后
  // 会失真（首屏调 navigateBack 必然失败、开发者工具报 Page route 错误）。
  state.isCanBack = canGoBackFromCurrentPage()
  if (state.isCanBack) {
    router.back()
  }
  else {
    toHome()
  }
}

function openSkuPopup(mode: number) {
  skuMode.value = mode
  skuKey.value = true
}

function handleBuyAction(value: number) {
  // 单规格商品（约 98% 在架商品）不需要选规格：与列表页快捷加购同口径直接入车，
  // 数量取满足船供 MOQ/步长的最小合法值；多规格仍弹层选规格。
  const isDirectAdd = value === 2 && !purchaseDecision.value.needChoose
  if (!isDirectAdd) {
    openSkuPopup(value)
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
      openSkuPopup(2)
    }
  })
}
async function measureTabAnchor(index: number, selector: string) {
  // H5 优先用原生 DOM 量：评价卡在 GoodsComment 子组件里，页面级 selectorQuery 查不到；
  // 小程序没有 document，再回退到 uni 的 selectorQuery / 组件暴露方法。
  if (typeof document !== 'undefined') {
    const el = document.querySelector(selector)
    if (el) {
      const rect = el.getBoundingClientRect()
      return {
        top: rect.top + window.scrollY,
        height: rect.height,
      }
    }
  }
  if (index === 1 && goodsCommentRef.value?.getScrollCommentRect) {
    const data = await goodsCommentRef.value.getScrollCommentRect()
    return {
      top: data.top + pageScrollTop,
      height: data.height,
    }
  }
  return getBoundingClientRect(selector)
}

/** H5 的 uni.pageScrollTo 在该页面不生效，原生滚动兜底；非 H5 继续走 uni。 */
function scrollPageTo(top: number) {
  if (typeof window !== 'undefined' && typeof window.scrollTo === 'function') {
    window.scrollTo({ top, behavior: 'smooth' })
    return
  }
  uni.pageScrollTo({
    scrollTop: top,
    duration: 200,
  })
}

async function onCurrentPage(item: any, index: number) {
  // 点击时现量锚点：评价异步加载、装修渲染可能晚于首次量测，tabList 里的 top 仍是 0；
  // 不能因为缓存没值就让点击直接返回，否则顶部「评价/详情」看起来完全没响应。
  if (index > 0) {
    const selector = TAB_ANCHOR_SELECTORS[index]
    if (selector) {
      try {
        const data = await measureTabAnchor(index, selector)
        item.top = data.top
        item.height = data.height
      }
      catch {
        // 元素还没渲染时才退回缓存锚点；缓存也没有就不滚动，避免跳到错误位置。
      }
    }
    if (!item?.top)
      return
  }
  scrollPageTo(Math.max(0, item.top - statusBarHeight - 50))
  state.tabActive = index
}

// 口碑气泡点击：跳评价区（复用评价 tab 的锚点滚动；点击时现量，不再依赖异步缓存）
function gotoCommentFromBubble() {
  const reviewsTab = tabList.value[1]
  onCurrentPage(reviewsTab, 1)
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
    const selectedValues = data.map(value => String(value ?? '').trim())
    const skus = state.goodsSpu?.goodsSkus || []
    const selectedSku = skus.find((sku: any) => {
      const values = skuSpecValues(sku)
      return sku.status === '0' && values.length > 0 && values.length === selectedValues.length
        && values.every((value, index) => value && value === selectedValues[index])
    })
    state.selectSkuId = String(selectedSku?.id || '')
  }
  console.log('监听 - 关闭sku组件')
}
/** 弹层件数变化：协议价 minQty 与阶梯档位都按用户实际选的件数复算（与结算同源） */
function onSkuNumChange(value: any) {
  const quantity = Math.floor(Number(value))
  pickedQuantity.value = Number.isFinite(quantity) && quantity > 0 ? quantity : null
}
function onPopupSkuChange(skuId: unknown, quantity: unknown) {
  state.selectSkuId = String(skuId ?? '')
  onSkuNumChange(skuId ? quantity : null)
}
// 通过id查询商品详情
async function getSpu(id: string) {
  globalLoading.loading(t('common.loading'))
  loading.value = true
  try {
    const response = await getSpuById(id)
    loading.value = false
    state.goodsSpu = response
    if (response) {
      loadSeckillInfo()
      // 与秒杀并行、互不等待：四者都是增强展示，任一失败都不影响商详主流程
      loadNewbiePrices()
      loadMemberPrices()
      loadClearancePrices()
      loadDiscountPrices()
      // 协议价/档期特供/阶梯价（免登可访问，按采购场景+船舶圈定）：同为增强展示，失败静默
      loadPolicyPrices()
      loadCalendarSpecialPrices()
      loadLadderPrices()
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
        title: t('product.notice'),
        msg: t('product.offShelf'),
        closeOnClickModal: false,
        confirmButtonText: t('common.confirm'),
        cancelButtonText: t('common.cancel'),
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
  return new Promise<{ top: number, height: number }>((resolve, reject) => {
    const query = uni.createSelectorQuery().in(proxy)
    query.select(id).boundingClientRect()
    query.selectViewport().scrollOffset(() => {})
    query.exec(([rect, viewport]) => {
      if (typeof rect?.top === 'number' && typeof rect?.height === 'number') {
        resolve({ top: rect.top + (viewport?.scrollTop ?? pageScrollTop), height: rect.height })
      }
      else {
        reject(new Error('Element not found or query failed'))
      }
    })
  })
}

// 更新tabList位置的函数
async function updateTabListPositions() {
  await nextTick()
  if (loading.value || !state.goodsSpu?.id)
    return

  // 通过组件ref获取元素位置
  if (goodsInfoRef.value) {
    goodsInfoRef.value
      .getScrollPicRect()
      .then((data: any) => {
        tabList.value[0].top = data.top + pageScrollTop
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
        tabList.value[1].top = data.top + pageScrollTop
        tabList.value[1].height = data.height
      })
      .catch((error: any) => {
        console.error('获取评论位置失败:', error)
      })
  }

  measureDetailSections()
}
watch(
  [parameterRows, shipArrangement, decorationContent, decorationLoading, zonePrice, ladderTiers, ladderProgress, promotionHints],
  updateTabListPositions,
  { flush: 'post' },
)
// 添加购物车（弹层回传路径；单规格直加路径在 handleBuyAction → useQuickCart）。
// 去向由统一入口决定：有进行中的共享购物车时先询问，提示与角标也由它处理
async function addCart(data: any) {
  // 游客不进入加购链路：先登录，登录后回到商详再操作
  if (!ensureLoggedIn())
    return
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
  // 收藏/取消收藏都调需登录接口，游客先登录再回来操作
  if (!ensureLoggedIn())
    return
  globalLoading.loading(t('common.loading'))
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
  // 在线客服需要登录态（会话按用户建立）：游客先登录再回商详
  if (!ensureLoggedIn())
    return
  const payload = {
    image: state.goodsSpu.spuUrls?.[0] || '',
    productId: String(state.goodsSpu.id),
    // 卖点是富文本，会话商品卡只展示可见文本，避免标签外露
    summary: plainText(state.goodsSpu.introduction) || state.goodsSpu.name,
    title: state.goodsSpu.name,
  }
  uni.navigateTo({
    url: customerServiceRoute({ messageType: 'PRODUCT_CARD', payload }),
  })
}

/** 富文本转单行可见文本（仅用于会话卡片摘要，不走渲染） */
function plainText(html: string) {
  return String(html || '')
    .replace(/<[^>]*>/g, '')
    .replace(/&nbsp;/g, ' ')
    .replace(/&amp;/g, '&')
    .replace(/\s+/g, ' ')
    .trim()
}

function handleSwiper(obj: any) {
  uni.previewImage({
    current: obj.index,
    urls: state.goodsSpu.spuUrls,
  })
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
  <view v-if="!loading" class="goods-detail-content">
    <!-- 商品信息组件 -->
    <GoodsInfo
      ref="goodsInfoRef"
      :goods-spu="state.goodsSpu"
      :spec-row="specRow"
      :show-ship-arrangement-separately="showShipArrangement"
      :address="state.address"
      :seckill-info="state.seckillInfo"
      :member-display="memberDisplay"
      :clearance-display="effectiveClearanceDisplay"
      :newbie-display="newbieDisplay"
      :discount-display="discountDisplay"
      :policy-display="policyDisplay"
      :calendar-special-display="calendarSpecialDisplay"
      :ladder-display-price="ladderDisplayPrice"
      :ladder-tiers="ladderTiers"
      :ladder-progress="ladderProgress"
      :policy-min-qty-hint="policyHit && currentQuantity < policyMinQty ? policyMinQty : null"
      :special-remaining-quota="specialRemainingQuota"
      :social-bubble="state.goodsSpu?.socialBubble ?? null"
      :purchase-tags="purchaseTags"
      @swiper="handleSwiper"
      @open-sku-popup="handleBuyAction"
      @to-address="toAddress"
      @collect="handleCollect"
      @share="handleShare"
      @goto-comment="gotoCommentFromBubble"
    />
    <GoodsProfileSheet v-model="profileVisible" :rows="parameterRows" :profile="goodsProfile" />
    <!-- 靠港安排紧跟商品信息：船供场景先把「送到哪、什么时候到」说清，再谈优惠 -->
    <view v-if="showShipArrangement" class="detail-section bg-white dark:bg-[var(--wot-dark-background2)]">
      <view class="detail-section__head">
        <view class="detail-section__bar" />
        <text class="detail-section__title">
          {{ t('product.currentPortSchedule') }}
        </text>
      </view>
      <view class="detail-section__body min-w-0 break-words text-sm text-[var(--app-text)]">
        <text>{{ shipArrangement.text }}</text>
        <view v-if="shipArrangement.detail" class="mt-1 text-xs text-[var(--app-text-2)]">
          {{ shipArrangement.detail }}
        </view>
      </view>
    </view>
    <view v-if="promotionHints || showGoodsArchive" class="detail-quick-panel">
      <view v-if="promotionHints" class="detail-quick-panel__item">
        <GoodsPromotionHints
          :hints="promotionHints"
          :quantity="currentQuantity"
          :unit-price="currentSalesPrice"
          :unit="String(currentSku?.salesUnit || '')"
        />
      </view>
      <view v-if="showGoodsArchive" class="detail-quick-panel__item">
        <GoodsParamCard
          :rows="parameterRows"
          :show-profile="showGoodsProfile"
          @open-profile="profileVisible = true"
        />
      </view>
    </view>
    <!-- 商详装修区域（商品信息下方、评价上方）：内嵌内容，不渲染装修导航、不撑满 100vh -->
    <view v-if="decorationContent">
      <DiyPage
        :page-content-data="decorationContent"
        :goods-id="String(spuId || '')"
        embedded
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
      v-if="detailSections.length || state.goodsSpu.description"
      id="scroll-description"
      class="detail-section bg-white dark:bg-[var(--wot-dark-background2)]"
    >
      <view class="detail-section__head">
        <view class="detail-section__bar" />
        <text class="detail-section__title">
          {{ t('product.detailSection') }}
        </text>
      </view>
      <view class="detail-section__body">
        <GoodsDetailSections v-if="detailSections.length" :sections="detailSections" @resize="updateTabListPositions" />
        <view v-if="detailSections.length && state.goodsSpu.description" class="detail-stories-divider" />
        <mp-html v-if="state.goodsSpu.description" :content="state.goodsSpu.description" @ready="readyHandel" />
      </view>
    </view>
    <!-- 猜你喜欢 -->
    <view id="scroll-like" class="detail-rec-head">
      <view class="detail-section__bar" />
      <text class="detail-section__title">
        {{ t('product.mayAlsoLike') }}
      </text>
    </view>
    <WaterfallGoods v-if="spuId" :spu-id="String(spuId)" />
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
    use-sku-measurement
    :hide-stock="true"
    @open="onOpenSkuPopup"
    @close="onCloseSkuPopup"
    @add-cart="onSkuAddCart"
    @num-change="onSkuNumChange"
    @sku-change="onPopupSkuChange"
  />

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
.goods-detail-content {
  min-height: 100vh;
  background: linear-gradient(180deg, #fff7ef 0, #f8f5f0 440rpx, var(--app-bg) 900rpx);
}

.wot-theme-dark .goods-detail-content {
  background: var(--app-bg);
}

/* 采购优惠与商品档案合并为一张轻量面板，减少连续白卡造成的页面割裂。 */
.detail-quick-panel {
  margin: 16rpx 24rpx 24rpx;
  overflow: hidden;
  border: 1rpx solid rgb(91 67 47 / 6%);
  border-radius: 24rpx;
  background: var(--app-card);
  box-shadow: 0 8rpx 24rpx rgb(91 67 47 / 5%);
}

.detail-quick-panel__item + .detail-quick-panel__item {
  border-top: 1rpx solid var(--app-border);
}

.wot-theme-dark .detail-quick-panel {
  border-color: var(--app-border);
  box-shadow: none;
}

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
  color: var(--app-text);
  font-size: 28rpx;
  font-weight: 600;
}

.detail-stories-divider {
  margin: 20rpx 0;
  border-top: 1rpx solid var(--app-border);
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

:deep() {
  .wd-swiper__track {
    border-radius: 0px !important;
  }
  .wd-swiper-nav--bottom {
    bottom: 48rpx !important;
  }
}

/* 与悬浮玻璃底栏等高的让位：52px 胶囊 + 16px 贴底 + 12px 呼吸位 */
.detail-bottom-space {
  height: calc(var(--app-tabbar-height, 68px) + 12px);
}

.decoration-skeleton {
  height: 300rpx;
  margin: 16rpx;
  border-radius: 12rpx;
  background: var(--app-card-2);
}

/**
 * ===== 折叠屏 / 平板展开态：首屏以下保持全宽单栏（仅微信小程序，窗口宽 ≥600px） =====
 *
 * 商详首屏的双栏 grid 由 GoodsInfo 组件内部承担（左图集 / 右价格信息），本页只负责
 * 首屏以下的区块：已确认不重排、不切栏（保持滚动锚点 #scroll-description / #scroll-like
 * 的测量口径不变），仅把随 rpx 放大的区块间距、标题条、骨架高度改成平板密度 px，
 * 避免展开后「区块之间空一大截、标题比正文大两级」。
 *
 * 只 MP-WEIXIN 生效（H5 / App 产物不含本段），折叠态（<600px）不命中媒体查询零变化；
 * 段内禁止 rpx/upx，只用 px 与 App.vue 下发的 --wide-* token（一律带兜底值）。
 */
/* #ifdef MP-WEIXIN */
@media (min-width: 600px) {
  .detail-section {
    margin: 16px 24px;
    padding: 16px;
    border-radius: var(--wide-radius, 12px);
  }

  .detail-section__head {
    gap: 6px;
  }

  .detail-section__bar {
    width: 3px;
    height: 14px;
  }

  /* 区块标题比正文（14px）高一档取 15px；--wide-card-font（13px）是列表卡片标题的
     口径，混用会让「商品详情」「猜你喜欢」比正文还小 */
  .detail-section__title {
    font-size: 15px;
  }

  .detail-section__body {
    margin-top: 8px;
  }

  .detail-rec-head {
    margin: 24px 24px 8px;
    gap: 6px;
  }

  .decoration-skeleton {
    height: 150px;
    margin: 8px;
    border-radius: 8px;
  }

  /* .detail-bottom-space 已是 px（底栏等高让位），宽屏无需再调 */
}
/* #endif */
</style>

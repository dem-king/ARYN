<script setup lang="ts">
import type { NewbiePriceDisplay } from '@/utils/newbie-price'
import { onLoad, onPageScroll, onUnload } from '@dcloudio/uni-app'
import { storeToRefs } from 'pinia'
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { orderCreate, orderSettlement } from '@/api/order/orderInfo'
import { getNewbieSkuPrices } from '@/api/promotion'
import { getPage as getCouponList } from '@/api/promotion/couponUser'
import { getById as getAddressById, getDefault } from '@/api/user/address'
import { getMyVessels, getVesselCalls } from '@/api/vessel'
import ShipContextPicker from '@/components/ship-context-picker/index.vue'
import { useFrostedNavbar } from '@/composables/useFrostedNavbar'
import { useDirectImportOrderStore } from '@/store/directImportOrderStore'
import { useShipContextStore } from '@/store/shipContextStore'
import { applyDeliveryContext, resolveDeliveryContext } from '@/utils/checkout-delivery'
import { pickBestCoupon } from '@/utils/coupon-usability'
import {
  hasNewbiePrice,
  newbieIneligibleNotice,
  resolveNewbiePriceDisplay,
} from '@/utils/newbie-price'
import { mergeDuplicateSkuItems } from '@/utils/order-sku-merge'
import AddressSelector from './components/AddressSelector.vue'
import CouponSelector from './components/CouponSelector.vue'
import PaymentFooter from './components/PaymentFooter.vue'
import ShopOrderItem from './components/ShopOrderItem.vue'

const { handlePageScroll } = useFrostedNavbar()

definePage({
  name: 'order-confirm',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '确认订单',
  },
})
interface State {
  orderInfo: any
  couponUserList: any[]
  orderParams: any
  totalPrice: number
  freightPrice: number
  couponPrice: number
  paymentPrice: number
  createWay: string
  isAddressShow: boolean
}
interface CouponState {
  couponUserList: any[]
  orderPrice: number
  couponPopup: boolean
  couponUserId: string
  orderItemList: any[]
  /** 券门槛可计算金额（允许叠券明细折后合计，结算接口下发）：券弹层门槛判定用 */
  couponEligibleBase: number | null
}
interface Address {
  id: string
  detailAddress: string
  recipientName: string
  telephone: string
  provinceName: string
  cityName: string
  areaName: string
}
const goodsStore = useGoodsStore()
const shoppingCartStore = useShoppingCartStore()
const shipContextStore = useShipContextStore()
const directImportOrderStore = useDirectImportOrderStore()
const router = useRouter()
const { t } = useI18n()
const { createGoodsList } = storeToRefs(goodsStore)
const globalLoading = useGlobalLoading()
const loading = ref(true)
const submitting = ref(false)
const pointsEnabled = ref(false)
const userStore = useUserStore()
let settlementSeq = 0

/**
 * 导入直下单交接态（「导入清单下单」向导 → 报告页 → 本页）。
 *
 * 只有 URL 上的 handoff 与 store 匹配时才生效：普通购物车/立即购买同样走
 * createWay=2，不能只凭一个全局 active 布尔值判断，否则用户退出导入后再下
 * 普通订单会被残留的船供/岸上场景接管。
 *
 * 船供=way4/scene2（船舶与靠港随交接持久化，刷新后仍可恢复）；
 * 岸上=way3/scene3。普通订单的交接收不到 handoff 参数即清理残留。
 */
interface ImportHandoff {
  handoffId: string
  scene: string
  userAddressId: string
  remark: string
  vesselId: string
  vesselCallId: string
  vesselName: string
  portName: string
  berth: string
}
const importHandoff = ref<ImportHandoff | null>(null)
const importContextActive = computed(() => !!importHandoff.value)
const importContextInvalid = ref(false)
const importShipPickerVisible = ref(false)
/** 船供卡展示：优先用交接快照，刷新后全局 shipContextStore 可能是空的 */
const shipCardContext = computed(() => {
  const handoff = importHandoff.value
  if (handoff && handoff.scene !== '3') {
    return {
      vesselName: handoff.vesselName || shipContextStore.vesselName,
      portName: handoff.portName || shipContextStore.portName,
      berth: handoff.berth || shipContextStore.berth,
    }
  }
  return {
    vesselName: shipContextStore.vesselName,
    portName: shipContextStore.portName,
    berth: shipContextStore.berth,
  }
})
/**
 * 自动选券状态（页面级，一次）：
 * - couponAutoApplied：是否已尝试过「自动选最优券」（只尝试一次，避免每次结算都改写用户选择）；
 * - couponUserTouched：用户是否在券弹层里手动确认过（选券 / 换券 / 不使用），此后不再自动选。
 */
let couponAutoApplied = false
let couponUserTouched = false
const selectedAddress = ref<Address | null>(null)

const couponState = reactive<CouponState>({
  couponUserList: [],
  orderPrice: 0,
  couponPopup: false,
  couponUserId: '',
  orderItemList: [],
  couponEligibleBase: null,

})
const state = reactive<State>({
  orderInfo: {},
  couponUserList: [],
  orderParams: {}, // 结算和下单参数
  totalPrice: 0, // 订单总金额
  freightPrice: 0, // 运费总金额
  couponPrice: 0, // 优惠券减免总金额
  paymentPrice: 0, // 支付总金额
  createWay: '2', // 订单创建方式：1.购物车下单；2.普通购买下单
  isAddressShow: false, // 是否显示收货地址
})
onLoad(async (options) => {
  state.createWay = options?.createWay || '2'
  state.orderParams.requestId = createOrderRequestId()
  // 拼团单：活动 SKU 由拼团详情页写入 goodsStore，此处只记录拼团记录 ID，
  // 服务端按记录校验资格并以拼团价成交（前端不传价格，防篡改）
  if (options?.groupBuyRecordId) {
    state.orderParams.groupBuyRecordId = options.groupBuyRecordId
  }
  const handoffParam = typeof options?.handoff === 'string' ? options.handoff : ''
  if (handoffParam) {
    if (!directImportOrderStore.matches(handoffParam)) {
      // 交接过期/被新流程覆盖：不能猜场景继续下单，回向导重新选择配送目标
      uni.showToast({ title: t('checkout.importHandoff.expired'), icon: 'none' })
      setTimeout(() => {
        uni.redirectTo({ url: '/sub-pages/order/shared-cart/import-entry' })
      }, 800)
      return
    }
    importHandoff.value = {
      handoffId: directImportOrderStore.handoffId,
      scene: directImportOrderStore.scene,
      userAddressId: directImportOrderStore.userAddressId,
      remark: directImportOrderStore.remark,
      vesselId: directImportOrderStore.vesselId,
      vesselCallId: directImportOrderStore.vesselCallId,
      vesselName: directImportOrderStore.vesselName,
      portName: directImportOrderStore.portName,
      berth: directImportOrderStore.berth,
    }
  }
  else if (directImportOrderStore.active) {
    // 普通下单入口：清掉旧的导入交接，避免残留影响本次 way/scene
    directImportOrderStore.clear()
  }
  getDefaultAddress()
  void validateImportShipContext()
})

/**
 * 刷新/冷启动后重新校验船供交接：船舶仍属于当前用户、靠港仍挂在该船下。
 * 失败（船被解绑、靠港删除、换船）时不清空交接、也不静默改用全局上下文，
 * 而是置位阻断并给出「重新选择靠港」入口；网络异常不阻断，服务端结算/提交仍校验。
 */
async function validateImportShipContext() {
  const handoff = importHandoff.value
  if (!handoff || handoff.scene === '3')
    return
  if (!handoff.vesselId || !handoff.vesselCallId) {
    importContextInvalid.value = true
    return
  }
  try {
    const vessels = await getMyVessels()
    const vessel = (vessels || []).find((item: any) => item.id === handoff.vesselId)
    if (!vessel) {
      importContextInvalid.value = true
      return
    }
    const calls = await getVesselCalls(handoff.vesselId)
    const call = (calls || []).find((item: any) => item.id === handoff.vesselCallId)
    if (!call) {
      importContextInvalid.value = true
      return
    }
    handoff.vesselName = vessel.vesselName || handoff.vesselName
    handoff.portName = call.portName || handoff.portName
    handoff.berth = call.berth || handoff.berth
    directImportOrderStore.setVesselContext({
      vesselId: handoff.vesselId,
      vesselCallId: handoff.vesselCallId,
      vesselName: handoff.vesselName,
      portName: handoff.portName,
      berth: handoff.berth,
    })
    importContextInvalid.value = false
  }
  catch {
    // 网络抖动不误判为「靠港失效」；服务端仍会做最终校验
  }
}

/**
 * 用户在结算页重新选择靠港后更新交接并重结算：
 * 预览与提交必须使用同一组 vesselId/vesselCallId，不能只改全局 store。
 */
watch(
  () => [shipContextStore.vesselId, shipContextStore.vesselCallId] as const,
  ([vesselId, vesselCallId]) => {
    const handoff = importHandoff.value
    if (!handoff || handoff.scene === '3' || !vesselId || !vesselCallId)
      return
    const changed = handoff.vesselId !== vesselId || handoff.vesselCallId !== vesselCallId
    handoff.vesselId = vesselId
    handoff.vesselCallId = vesselCallId
    handoff.vesselName = shipContextStore.vesselName
    handoff.portName = shipContextStore.portName
    handoff.berth = shipContextStore.berth
    directImportOrderStore.setVesselContext({
      vesselId,
      vesselCallId,
      vesselName: shipContextStore.vesselName,
      portName: shipContextStore.portName,
      berth: shipContextStore.berth,
    })
    importContextInvalid.value = false
    if (changed)
      toSettlement()
  },
)

/** 岸上导入换地址后同步交接，刷新结算页仍恢复用户最后选择的地址 */
watch(selectedAddress, (address) => {
  const handoff = importHandoff.value
  if (!handoff || handoff.scene !== '3' || !address?.id)
    return
  handoff.userAddressId = address.id
  directImportOrderStore.setUserAddressId(address.id)
})

/** 结算/提交共用的配送参数解析：切换积分/券重新结算也走同一份场景 */
function applyCheckoutDeliveryContext() {
  const handoff = importHandoff.value
  const resolved = resolveDeliveryContext({
    deliveryWay: state.orderParams.deliveryWay,
    importScene: handoff?.scene,
    importVesselId: handoff?.vesselId,
    importVesselCallId: handoff?.vesselCallId,
    globalContext: shipContextStore.deliveryContextParams,
  })
  applyDeliveryContext(state.orderParams, resolved)
  return resolved
}

/** 是否拼团单：拼团价由服务端重算，支付方式必须在线（成团判定依赖支付回调） */
const isGroupBuyOrder = computed(() => !!state.orderParams.groupBuyRecordId)

/**
 * 底部「已优惠」副行：活动/会员/券/积分是四个互不重叠的优惠口径桶
 * （各行明细已分别展示），这里只做求和汇总，0 时不展示。
 */
const totalSavings = computed(() => {
  const info: any = state.orderInfo || {}
  const sum = Number(info.activityDiscountPrice || 0)
    + Number(info.memberDiscountPrice || 0)
    + Number(info.couponPrice || 0)
    + Number(info.pointsDeductPrice || 0)
  return Number.isFinite(sum) && sum > 0 ? Math.round(sum * 100) / 100 : 0
})

/**
 * 新人价资格提示。
 *
 * 只做提示、**不参与算价**：应付金额一律以 orderSettlement 返回的权威值为准
 * （后端已按「拼团 > 秒杀 > 新人价 > 折扣」重算并校验资格），前端再算一遍
 * 只会引入第二套口径，改错时反而与真实扣款不一致。
 *
 * 提示存在的意义是解释价差：用户从新人价楼层点进来，结算若按原价收，
 * 没有说明就是「页面标 0.01、结算收 5.90」的观感来源（验收用例 11）。
 *
 * 这里只存结构化状态，文案在 computed 里按当前语言生成：
 * 切换语言时提示条能实时刷新，不在异步回调里拼好中文串存 ref。
 */
type NewbieNoticeState
  = | { kind: 'none' }
    | { kind: 'ineligible', reason: string }
    | { kind: 'applied' }

const newbieNoticeState = ref<NewbieNoticeState>({ kind: 'none' })

const newbieNotice = computed(() => {
  const notice = newbieNoticeState.value
  switch (notice.kind) {
    case 'ineligible':
      return t('checkout.newbie.ineligibleSuffix', { reason: notice.reason })
    case 'applied':
      return t('checkout.newbie.applied')
    default:
      return ''
  }
})

/**
 * 本单是否真的按新人价成交。
 *
 * 用「服务端返回的明细成交单价 == 新人价」来判定，而不是看 promotionDetails：
 * `applyShipWholeDiscount` 在整单优惠为 0 时会提前 return（不写 promotionDetails），
 * 因此新人价订单大多数情况下 _没有_ detail 可比，用 detail 判定会永远为假。
 * 明细单价则是后端改价后的权威结果，比对它不存在口径漂移。
 *
 * @param priceBySku  本单 skuId → display（与明细按 skuId 对齐，不依赖数组顺序）
 */
function isNewbieApplied(priceBySku: Record<string, NewbiePriceDisplay>) {
  const items: any[] = state.orderInfo?.orderItemList || []
  if (!items.length)
    return false
  return items.some((item: any) => {
    const display = priceBySku[String(item?.skuId ?? '')]
    if (!display?.showNewbiePrice || display.newbiePrice === null)
      return false
    // 金额比对留 1 分钱余量：后端是 BigDecimal，前端是二进制浮点
    return Math.abs(Number(item?.salesPrice) - display.newbiePrice) < 0.01
  })
}

/**
 * 查询本单 SKU 的新人价与当前用户资格，生成提示文案。
 *
 * 失败静默：提示属增强信息，接口异常时页面照常按服务端金额展示与提交。
 */
async function loadNewbieHint() {
  const skuIds = (createGoodsList.value || [])
    .map((item: any) => String(item?.skuId || '').trim())
    .filter(Boolean)
  if (!skuIds.length) {
    newbieNoticeState.value = { kind: 'none' }
    return
  }
  try {
    const vo = await getNewbieSkuPrices(skuIds)
    const priceBySku: Record<string, NewbiePriceDisplay> = {}
    const displays = skuIds.map((skuId) => {
      const display = resolveNewbiePriceDisplay(vo, skuId)
      priceBySku[skuId] = display
      return display
    })
    // 「已登录但确定不符合」优先于正向提示：这是用户最需要解释的一种情况
    const ineligible = newbieIneligibleNotice(displays)
    if (ineligible) {
      newbieNoticeState.value = { kind: 'ineligible', reason: ineligible }
      return
    }
    // 正向提示只在「资格可享」且「服务端确实按新人价成交」时出现：
    // 只满足其一都说明两边判定不一致，此时不提示比提示错更好
    newbieNoticeState.value = hasNewbiePrice(displays) && isNewbieApplied(priceBySku)
      ? { kind: 'applied' }
      : { kind: 'none' }
  }
  catch {
    newbieNoticeState.value = { kind: 'none' }
  }
}

function createOrderRequestId() {
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}-${Math.random().toString(36).slice(2)}`
}
// 初始化订单
function initData() {
  // 空清单守卫：购物车/导入来源都可能带出空列表，放下去只会渲染 ¥0.00 空态
  if (!createGoodsList.value || createGoodsList.value.length === 0) {
    loading.value = false
    useGlobalToast().warning(t('checkout.toast.emptyGoods'))
    setTimeout(() => {
      if (getCurrentPages().length > 1)
        uni.navigateBack()
      else
        uni.switchTab({ url: '/pages/home/index' })
    }, 800)
    return
  }
  loading.value = true
  // 购物车按靠港分组，同一 SKU 可能合法地出现多行（已结束靠港的滞留行 +
  // 当前无靠港行）；结算/下单接口只接受一个 SKU 一行，这里按 SKU 合并数量后再提交。
  // 注意：防串船校验仍用原始 createGoodsList 逐行比对，不能用合并后的列表
  const mergedGoodsList = mergeDuplicateSkuItems(createGoodsList.value || [])
  const orderItemList: Array<any> = []
  mergedGoodsList.forEach((item: any) => {
    orderItemList.push({
      spuId: item.spuId,
      picUrl: item.picUrl,
      spuName: item.spuName,
      quantity: item.quantity,
      specsInfo: item.specsInfo,
      skuId: item.skuId,
    })
  })
  if (importContextActive.value && importHandoff.value) {
    // 导入直下单：配送方式与场景在向导里锁定，不走全局船舶上下文推断。
    // 船供=way4/scene2（船舶与靠港取自交接，刷新后仍能恢复）；岸上=way3/scene3。
    // 用同一个解析函数生成参数，避免这里和 toSettlement/toPay 各写一套。
    const handoff = importHandoff.value
    applyDeliveryContext(state.orderParams, resolveDeliveryContext({
      deliveryWay: handoff.scene === '3' ? '3' : '4',
      importScene: handoff.scene,
      importVesselId: handoff.vesselId,
      importVesselCallId: handoff.vesselCallId,
    }))
    // 备注唯一来源：用户编辑过（含清空）就用 store 里的值，不能再被向导值覆盖
    state.orderParams.remark = handoff.remark
    state.orderInfo.remark = handoff.remark
  }
  else if (shipContextStore.hasVesselContext) {
    // 入口统一：在船成员只走内部配送（way=4），结算页不再提供地址簿类配送方式；
    // 上下文来自船舶工作台，服务端结算时再次校验
    state.orderParams.deliveryWay = '4'
    Object.assign(state.orderParams, shipContextStore.deliveryContextParams)
  }
  else {
    // 未绑定上下文（岸上场景）：默认商城配送（deliveryWay=3），用户可在结算页弹层里改选
    state.orderParams.deliveryWay = '3'
  }
  // 商城配送（3）/内部配送（4）默认货到付款，收货后线下结算；用户仍可在支付方式行切回在线支付。
  // 拼团单例外：必须在线支付（成团判定依赖支付回调），不默认、也不允许切货到付款
  if (!isGroupBuyOrder.value
    && (state.orderParams.deliveryWay === '3' || state.orderParams.deliveryWay === '4')) {
    state.orderParams.paymentType = '3'
  }
  state.orderParams.skuReqList = orderItemList
  toSettlement()
}
/**
 * 查询默认地址。
 *
 * 失败兜底：getDefault 接口异常时不能再让 initData 悬空（页面会永远停在
 * loading 白屏），任何分支都必须走到 initData；无默认地址归一化为 null，
 * 让地址卡渲染「请选择收货地址」空态而不是空白字段的有址态。
 */
async function getDefaultAddress() {
  try {
    // 导入直下单（岸上）：预取向导里选定的收货地址，取不到再回落默认地址
    const handoff = importHandoff.value
    if (handoff && handoff.scene === '3' && handoff.userAddressId) {
      try {
        const picked = await getAddressById(handoff.userAddressId)
        selectedAddress.value = picked?.id ? picked : null
      }
      catch {
        selectedAddress.value = null
      }
      if (selectedAddress.value) {
        initData()
        return
      }
    }
    const res = await getDefault()
    selectedAddress.value = res?.id ? res : null
  }
  catch {
    selectedAddress.value = null
  }
  initData()
}
// 配送方式切换
function deliveryWayChange(item: any) {
  // 导入直下单在向导里锁定配送目标，结算页不允许切走（场景与 way 必须成对）
  if (importContextActive.value)
    return
  const previousWay = state.orderParams.deliveryWay
  state.orderParams.deliveryWay = item.deliveryWay
  // 货到付款仅商城配送（3）/内部配送（4）可用：切走时重置为在线支付，
  // 从其它方式切入时默认货到付款；3/4 之间互切保留用户已选的支付方式。
  // 拼团单恒为在线支付（成团判定依赖支付回调）
  if (isGroupBuyOrder.value) {
    state.orderParams.paymentType = ''
  }
  else {
    const codAvailable = item.deliveryWay === '3' || item.deliveryWay === '4'
    if (!codAvailable) {
      state.orderParams.paymentType = ''
    }
    else if (previousWay !== '3' && previousWay !== '4') {
      state.orderParams.paymentType = '3'
    }
  }
  toSettlement()
}

// 支付方式切换（货到付款不影响结算价格，无需重新结算）
function paymentWayChange(paymentWay: string) {
  state.orderParams.paymentType = paymentWay
}
/**
 * 备注输入监听：展示、重新结算与提交共用 orderParams.remark。
 * 导入交接同步回 store（含空串），刷新后不会被向导备注填回。
 */
function remarkChange(item: any) {
  const remark = item?.remark ?? ''
  state.orderParams.remark = remark
  if (state.orderInfo && typeof state.orderInfo === 'object')
    state.orderInfo.remark = remark
  if (importContextActive.value)
    directImportOrderStore.setRemark(remark)
  if (importHandoff.value)
    importHandoff.value.remark = remark
}

function showCoupon(item: any) {
  // 弹层预选以「提交参数」为唯一口径：结算响应里的 couponUserId 可能滞后一帧
  couponState.couponUserId = item.couponUserId || state.orderParams.couponUserId || ''
  couponState.couponPopup = true
  couponState.orderPrice = item.totalPrice
  couponState.orderItemList = item.orderItemList
  const eligible = Number(item.couponEligibleAmount)
  couponState.couponEligibleBase = Number.isFinite(eligible) && eligible > 0 ? eligible : null
}
// 选中优惠券回调（含「不使用优惠券」）
function couponConfirm(couponUserId: string) {
  // 用户手动确认过：此后不再自动选券，尊重用户选择
  couponUserTouched = true
  const next = couponUserId || ''
  state.orderParams.couponUserId = next
  couponState.couponUserId = next
  couponState.couponPopup = false
  toSettlement()
}
function closeCouponPopup() {
  couponState.couponPopup = false
}

function pointsToggle(enabled: boolean) {
  if (enabled && Number(state.orderInfo?.pointsDeductMax) <= 0) {
    useGlobalToast().warning(t('checkout.points.noneDeductible'))
    return
  }
  pointsEnabled.value = enabled
  toSettlement()
}

/**
 * 自动选最优券：确认页默认帮用户选好本单抵扣最大的一张，兑现「领券后下单自动抵扣」。
 *
 * 触发时机：首轮结算成功且券列表拉到之后 —— 可用性判定同时依赖结算下发的
 * `couponEligibleAmount`（券门槛可计算金额）与券列表，缺一不可。
 * 只执行一次；用户手动操作过后不再覆盖（尊重用户选择）。
 * 选中后必须重新结算，页面金额一律以服务端返回为权威值。
 */
function autoSelectBestCoupon() {
  if (couponAutoApplied || couponUserTouched)
    return
  if (state.orderParams.couponUserId) {
    // 已带券（例如从购物车/上一页带入）：不覆盖
    couponAutoApplied = true
    return
  }
  const info: any = state.orderInfo || {}
  // 整单不可叠券：保持未选券，券行已展示置灰原因
  if (info.couponStackReason) {
    couponAutoApplied = true
    return
  }
  const best = pickBestCoupon(
    state.couponUserList,
    info.orderItemList || [],
    info.couponEligibleAmount,
  )
  couponAutoApplied = true
  if (!best)
    return
  state.orderParams.couponUserId = best.id
  couponState.couponUserId = best.id
  void reSettleWithAutoCoupon()
}

/** 自动选券后的二次结算；服务端判不可用（极少数竞态）时回退为不使用券，保证页面可提交 */
async function reSettleWithAutoCoupon() {
  try {
    await toSettlement()
  }
  catch {
    state.orderParams.couponUserId = ''
    couponState.couponUserId = ''
    await toSettlement().catch(() => {})
  }
}

// 去结算
async function toSettlement() {
  const seq = ++settlementSeq
  if (pointsEnabled.value) {
    // 开关语义为「使用全部可用积分」：由服务端按最新优惠与余额重新计算额度，
    // 避免优惠变化后前端仍按旧额度抵扣导致确认金额与实际扣款不一致
    state.orderParams.useMaxPoints = true
    delete state.orderParams.usePoints
  }
  else {
    delete state.orderParams.useMaxPoints
    delete state.orderParams.usePoints
  }
  globalLoading.loading(t('common.loading'))
  state.orderParams.createWay = state.createWay
  state.orderParams.userAddressId = selectedAddress.value ? selectedAddress.value.id : ''
  // 预览与提交共用同一份场景解析（导入船供 scene2 / 岸上 scene3 / 普通到船全局上下文）
  applyCheckoutDeliveryContext()

  // 结算订单
  try {
    const response = await orderSettlement(state.orderParams)
    // 快速切换积分/优惠券会并发多次结算，只应用最后一次响应
    if (seq !== settlementSeq)
      return
    state.orderInfo = response
    if (pointsEnabled.value && Number(response?.pointsUsed) <= 0) {
      pointsEnabled.value = false
      delete state.orderParams.useMaxPoints
    }
    // 叠加规则：所选券对全部明细不可叠时服务端不下发券额（couponStackReason 说明原因），
    // 自动取消选择避免提交时被 60067 硬拒；金额以本次结算（未用券）为准
    if (response?.couponStackReason && state.orderParams.couponUserId) {
      state.orderParams.couponUserId = ''
      useGlobalToast().warning(response.couponStackReason)
    }
    // 资格提示依赖登录态与活动，不 await：结算金额已是权威值，
    // 提示晚到一帧不影响用户看到正确金额
    void loadNewbieHint()
    // 查询优惠券：列表到位后才能做「自动选最优券」的可用性判定（门槛/范围/叠加）
    void getCouponList({ status: '0' })
      .then((res) => {
        state.couponUserList = res.records
        autoSelectBestCoupon()
      })
      .catch(() => {
        // 券列表拉取失败不影响结算金额；保持未选券，用户点券行时会再次拉取
      })
  }
  catch {
    // 结算失败（如地址不在配送范围）：拦截器已 toast 原因，保留上次数据展示，
    // 不能把页面清空成 ¥0.00 死态
  }
  finally {
    if (seq === settlementSeq) {
      loading.value = false
      globalLoading.close()
    }
  }
}
// 去支付
async function toPay() {
  if (submitting.value)
    return
  state.orderParams.createWay = state.createWay
  state.orderParams.userAddressId = selectedAddress.value ? selectedAddress.value.id : ''
  const delivery = applyCheckoutDeliveryContext()
  if (!state.orderParams.deliveryWay) {
    return useGlobalToast().warning(t('checkout.toast.selectDelivery'))
  }

  // 导入船供：靠港在刷新后失效时不允许提交，必须重新选择（不能静默换用全局上下文）
  if (importContextActive.value && importHandoff.value?.scene === '2' && importContextInvalid.value) {
    importShipPickerVisible.value = true
    return useGlobalToast().warning(t('checkout.importHandoff.invalidDesc'))
  }

  if (state.orderParams.deliveryWay === '1' && !selectedAddress.value?.id) {
    return useGlobalToast().warning(t('checkout.toast.selectAddress'))
  }

  // 拼团单兜底：支付方式只允许在线支付（服务端同样校验，防前端状态残留）
  if (isGroupBuyOrder.value) {
    state.orderParams.paymentType = ''
  }

  // 商城配送（deliveryWay=3）同样需要收货地址
  if (state.orderParams.deliveryWay === '3' && !selectedAddress.value?.id) {
    return useGlobalToast().warning(t('checkout.toast.selectAddress'))
  }

  // 防串船：购物车下单时所选行的靠港归属必须与当前上下文一致（无归属的旧行放行）
  if (state.createWay === '1' && shipContextStore.vesselCallId) {
    const mismatched = (createGoodsList.value || []).filter(
      (item: any) => item.vesselCallId && item.vesselCallId !== shipContextStore.vesselCallId,
    )
    if (mismatched.length > 0) {
      return useGlobalToast().warning(
        t('checkout.toast.otherVesselItems', { count: mismatched.length }),
      )
    }
  }

  // 内部配送（deliveryWay=4）必须携带船舶与靠港计划上下文（导入船供从交接取，
  // 刷新后 shipContextStore 为空也能提交；普通到船从全局上下文取）
  if (state.orderParams.deliveryWay === '4'
    && (!delivery.vesselId || !delivery.vesselCallId)) {
    if (importContextActive.value)
      importShipPickerVisible.value = true
    return useGlobalToast().warning(t('checkout.toast.selectVesselContext'))
  }

  submitting.value = true
  globalLoading.loading(t('common.loading'))

  try {
    // 携带确认页金额：服务端重算不一致时会拒绝成单，避免优惠额度变化后静默涨价
    const expectedPaymentPrice = Number(state.orderInfo?.paymentPrice)
    if (Number.isFinite(expectedPaymentPrice)) {
      state.orderParams.expectedPaymentPrice = expectedPaymentPrice
    }
    else {
      delete state.orderParams.expectedPaymentPrice
    }
    // 创建订单
    const response = await orderCreate(state.orderParams)
    // 导入直下单交接完成：清掉上下文，避免残留影响下一次普通下单
    if (importContextActive.value) {
      directImportOrderStore.clear()
      importHandoff.value = null
    }
    // 下单后刷新积分余额；刷新属增强信息，失败不打断下单成功流程
    userStore.refreshPointsInfo().catch(() => {})
    if (state.orderParams.paymentType === '3') {
      // 货到付款：无需在线支付，直接进订单详情
      useGlobalToast().success(t('checkout.toast.codSuccess'))
      router.replace({
        name: 'order-detail',
        params: {
          id: response.id,
        },
      })
    }
    else {
      router.replace({
        name: 'order-pay',
        params: {
          orderNo: response.orderNo,
        },
      })
    }
    if (state.createWay === '1') {
      // 刷新购物车数量
      shoppingCartStore.fetchCartCount()
    }
  }
  finally {
    submitting.value = false
    globalLoading.close()
  }
}
// 跳转到收货地址页面
function toAddress() {
  router.push({
    name: 'address-list',
    params: {
      placeChooseFlag: 'true',
      placeChooseId: selectedAddress.value && selectedAddress.value.id ? selectedAddress.value.id : '',
    },
  })
}
function handleSelectedAddressUpdate(newAddress: Address) {
  selectedAddress.value = newAddress
  // 选址后必须重新结算：一是地址决定运费，二是无地址首结失败后页面停在
  // 空数据态（¥0.00），只有重结算才能把商品与金额补回来
  toSettlement()
}

uni.$on('update:selectedAddress', handleSelectedAddressUpdate)

onUnload(() => {
  uni.$off('update:selectedAddress', handleSelectedAddressUpdate)
})

// 顶栏磨砂玻璃：字面量注册滚动（小程序按页面源码生成滚动位标记，包进 composable 会静默失效）
onPageScroll(handlePageScroll)
</script>

<template>
  <hr-navbar :title="t('checkout.title')" />
  <view v-if="!loading" class="confirm-page">
    <!-- 导入船供：刷新后靠港已失效时阻断提交，并给出重新选择入口 -->
    <view
      v-if="importContextActive && importHandoff?.scene === '2' && importContextInvalid"
      class="import-warning"
    >
      <view class="import-warning__body">
        <text class="import-warning__title">
          {{ t('checkout.importHandoff.invalidTitle') }}
        </text>
        <text class="import-warning__desc">
          {{ t('checkout.importHandoff.invalidDesc') }}
        </text>
      </view>
      <view class="import-warning__action" @tap="importShipPickerVisible = true">
        {{ t('checkout.importHandoff.reselect') }}
      </view>
    </view>

    <!-- 内部配送上下文（delivery_way=4） -->
    <view
      v-if="state.orderParams.deliveryWay === '4'"
      class="ship-card"
    >
      <view class="ship-card__icon">
        <wd-icon
          name="location"
          size="18px"
          color="var(--wot-color-theme-primary, #ff2e4d)"
        />
      </view>
      <view class="ship-card__body">
        <view class="ship-card__title">
          {{ t('checkout.delivery.deliverTo', { name: shipCardContext.vesselName }) }}
        </view>
        <view class="ship-card__meta">
          {{ shipCardContext.portName }} {{ shipCardContext.berth }}
          <text v-if="shipContextStore.deliveryWindowStart">
            （{{ shipContextStore.deliveryWindowStart }} ~
            {{ shipContextStore.deliveryWindowEnd }}）
          </text>
        </view>
        <view class="ship-card__note">
          {{ t('checkout.delivery.toVesselNote') }}
        </view>
      </view>
    </view>

    <!-- 收货地址选择（普通快递/商城配送） -->
    <AddressSelector
      v-if="state.orderParams.deliveryWay !== '4'"
      :selected-address="selectedAddress"
      @to-address="toAddress"
    />

    <!--
      新人价资格提示：价格本身由服务端结算重算，这里只解释「为什么是这个价」。
      放在营销优惠明细之前——它是对整单价格的说明，不是某条优惠的明细。
    -->
    <view v-if="newbieNotice" class="newbie-notice">
      <text class="newbie-notice-text">
        {{ newbieNotice }}
      </text>
    </view>

    <!--
      营销优惠 + 买赠赠品合并为一张卡：两者都是「这单为什么便宜/多了什么」
      的解释性信息，拆两张白卡只会增加滚动长度。
    -->
    <view
      v-if="
        (state.orderInfo.promotionDetails && state.orderInfo.promotionDetails.length)
          || state.orderInfo.promoPrice > 0
          || (state.orderInfo.giftItems && state.orderInfo.giftItems.length)
      "
      class="promo-card"
    >
      <template v-if="(state.orderInfo.promotionDetails && state.orderInfo.promotionDetails.length) || state.orderInfo.promoPrice > 0">
        <view class="promo-card__title">
          {{ t('checkout.promotion.title') }}
        </view>
        <view
          v-for="detail in state.orderInfo.promotionDetails || []"
          :key="detail.activityId"
          class="promo-card__row"
        >
          <text class="promo-card__dot" />
          <text class="promo-card__text">
            {{ t('checkout.promotion.activitySaved', { name: detail.activityName }) }}
            <template v-if="detail.activityType === '4'">
              {{ t('checkout.promotion.tieredPrice') }}
            </template>
            <template v-else>
              ¥{{ detail.discountAmount }}
            </template>
          </text>
        </view>
        <view v-if="state.orderInfo.promoPrice > 0" class="promo-card__row">
          <text class="promo-card__dot" />
          <text class="promo-card__text">
            {{ t('checkout.promotion.vesselTotal', { amount: state.orderInfo.promoPrice }) }}
          </text>
        </view>
      </template>
      <!-- 买赠赠品预览（结算已按真实库存过滤；库存不足的赠品不展示即不赠） -->
      <template v-if="state.orderInfo.giftItems && state.orderInfo.giftItems.length">
        <view class="promo-card__title">
          {{ t('checkout.promotion.gifts') }}
        </view>
        <view
          v-for="gift in state.orderInfo.giftItems"
          :key="gift.skuId"
          class="promo-card__row"
        >
          <text class="promo-card__dot promo-card__dot--gift" />
          <text class="promo-card__text">
            {{ t('checkout.promotion.giftItem', { name: gift.spuName || gift.skuId, qty: gift.quantity }) }}
          </text>
        </view>
      </template>
    </view>

    <!-- 订单列表 -->
    <ShopOrderItem
      :order="state.orderInfo"
      :coupon-user-list="state.couponUserList"
      :delivery-way="state.orderParams.deliveryWay"
      :payment-way="state.orderParams.paymentType"
      :group-buy-order="isGroupBuyOrder"
      :points-enabled="pointsEnabled"
      :address-pending="!selectedAddress?.id"
      :delivery-way-locked="importContextActive"
      @delivery-way-change="deliveryWayChange" @payment-way-change="paymentWayChange" @remark-change="remarkChange" @show-coupon="showCoupon" @points-toggle="pointsToggle"
    />

    <!-- 底部结算栏占位：与固定栏高度+安全区匹配，防止内容被盖 -->
    <view class="footer-safe" />
    <!-- 支付底部：货到付款时按钮为「提交订单」，下单后直接进详情不再拉起收银台 -->
    <PaymentFooter
      :payment-price="state.orderInfo.paymentPrice"
      :payment-way="state.orderParams.paymentType"
      :total-discount="totalSavings"
      @to-pay="toPay"
    />
    <!-- 优惠券选择 -->
    <CouponSelector
      :coupon-popup="couponState.couponPopup" :coupon-user-list="state.couponUserList"
      :coupon-user-id="state.orderParams.couponUserId"
      :order-price="couponState.orderPrice"
      :order-item-list="couponState.orderItemList"
      :coupon-eligible-base="couponState.couponEligibleBase"
      @close-coupon-popup="closeCouponPopup" @coupon-confirm="couponConfirm"
    />
    <ShipContextPicker v-model="importShipPickerVisible" />
  </view>
</template>

<style scoped lang="scss">
/*
 * 结算页底色：浅灰衬托白卡，卡片节奏统一为 20rpx 圆角 + 20rpx 侧边距。
 */
.confirm-page {
  min-height: 100vh;
  background: var(--app-bg);
}

/*
 * 导入船供交接失效提示：靠港被删除/换船后阻断提交，右侧给出就地重选入口。
 */
.import-warning {
  display: flex;
  align-items: center;
  gap: 20rpx;
  margin: 20rpx 20rpx 0;
  padding: 24rpx;
  border-left: 6rpx solid #f59e0b;
  border-radius: 16rpx;
  background: #fff7e8;
}

.import-warning__body {
  flex: 1;
  min-width: 0;
}

.import-warning__title {
  display: block;
  font-size: 28rpx;
  font-weight: 600;
  color: #8a4b3a;
  line-height: 40rpx;
}

.import-warning__desc {
  display: block;
  margin-top: 4rpx;
  font-size: 24rpx;
  line-height: 36rpx;
  color: #8a4b3a;
}

.import-warning__action {
  flex: none;
  padding: 12rpx 20rpx;
  border-radius: 999rpx;
  font-size: 24rpx;
  color: #fff;
  background: var(--wot-color-theme-primary, #ff2e4d);
}

/*
 * 内部配送上下文卡（delivery_way=4）：图标 + 船名 + 靠港信息 + 送达说明，
 * 与收货地址卡同一结构，视觉上都是「送到哪里」。
 */
.ship-card {
  display: flex;
  align-items: flex-start;
  margin: 20rpx 20rpx 0;
  padding: 28rpx 24rpx;
  border-radius: 20rpx;
  background: var(--app-card);
}

.ship-card__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 68rpx;
  height: 68rpx;
  border-radius: 50%;
  background: var(--wot-color-theme-primary-soft, rgba(255, 34, 55, 0.09));
}

.ship-card__body {
  flex: 1;
  min-width: 0;
  margin-left: 20rpx;
}

.ship-card__title {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--app-text);
  line-height: 42rpx;
}

.ship-card__meta {
  margin-top: 6rpx;
  font-size: 26rpx;
  color: var(--app-text-2);
  line-height: 38rpx;
}

.ship-card__note {
  display: inline-flex;
  margin-top: 12rpx;
  padding: 6rpx 16rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
  line-height: 30rpx;
  color: #07c160;
  background: rgba(7, 193, 96, 0.1);
}

/*
 * 营销优惠/赠品卡：小圆点引导的橙色明细行，克制不抢金额区的视觉权重。
 */
.promo-card {
  margin: 20rpx 20rpx 0;
  padding: 24rpx;
  border-radius: 20rpx;
  background: var(--app-card);
}

.promo-card__title {
  margin-bottom: 4rpx;
  font-size: 28rpx;
  font-weight: 600;
  color: var(--app-text);
  line-height: 40rpx;
}

.promo-card__row {
  display: flex;
  align-items: flex-start;
  margin-top: 12rpx;
}

.promo-card__dot {
  flex-shrink: 0;
  width: 10rpx;
  height: 10rpx;
  margin-top: 14rpx;
  margin-right: 14rpx;
  border-radius: 50%;
  background: #ff8f1f;

  &--gift {
    background: var(--wot-color-theme-primary, #ff2e4d);
  }
}

.promo-card__text {
  flex: 1;
  min-width: 0;
  font-size: 24rpx;
  color: #ff8f1f;
  line-height: 34rpx;
}

/*
 * 新人价提示条。
 *
 * 用浅暖底 + 左侧色条区分「说明」与「可操作区域」：
 * 它是解释性信息，不该读起来像第二张营销优惠卡片。
 */
.newbie-notice {
  margin: 20rpx;
  padding: 18rpx 24rpx;
  border-left: 6rpx solid var(--wot-color-theme-primary, #ff2237);
  border-radius: 12rpx;
  background: #fff7e8;

  .newbie-notice-text {
    color: #8a4b3a;
    font-size: 24rpx;
    line-height: 36rpx;
  }
}

/* 底部固定结算栏（约 120rpx）+ 安全区的内容占位 */
.footer-safe {
  height: calc(150rpx + constant(safe-area-inset-bottom));
  height: calc(150rpx + env(safe-area-inset-bottom));
}
</style>

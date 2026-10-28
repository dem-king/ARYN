<script setup lang="ts">
import type { DiscountPriceDisplay } from '@/utils/discount-price'
import type { MemberPriceDisplay } from '@/utils/member-price'
import type { NewbiePriceDisplay } from '@/utils/newbie-price'
import { onShow } from '@dcloudio/uni-app'
import { computed, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { delShoppingCart, editShoppingCart, getPage, transferCartRowsToCall } from '@/api/order/shoppingCart'
import { saveBatch } from '@/api/product/collect'
import { getById } from '@/api/product/spu'
import { getGoodsDiscountPrices, getMemberSkuPrices, getNewbieSkuPrices } from '@/api/promotion'
// 引入组件
import { shouldShowOriginalPrice } from '@/components/diy/price-display'
import { shouldShowSubtitle } from '@/components/diy/subtitle-display'
import ShipContextPicker from '@/components/ship-context-picker/index.vue'
import WaterfallGoods from '@/components/waterfall-goods/index.vue'
// 推荐商品流（「为你推荐」）由本页滚动广播驱动自动续页，见 WaterfallGoods
import { useDecorationInfiniteScroll } from '@/composables/useDecorationInfiniteScroll'
import { useShipContextLoad } from '@/composables/useShipContextLoad'
import { useShipContextStore } from '@/store/shipContextStore'
import { useTenantCapabilityStore } from '@/store/tenantCapabilityStore'
import {
  discountUnitPrice,
  emptyDiscountDisplay,
  resolveDiscountPriceDisplay,
} from '@/utils/discount-price'
import { initGoodsSpecs } from '@/utils/goods-specs'
import {
  emptyMemberPriceDisplay,
  memberPriceUnitPrice,
  resolveMemberPriceDisplay,
  resolvePromotionPriority,
} from '@/utils/member-price'
import {
  clampNewbieQuantity,
  effectiveUnitPrice,
  emptyNewbieDisplay,
  hasNewbiePrice,
  resolveNewbiePriceDisplay,
} from '@/utils/newbie-price'
import { useTabBarLocale } from '@/composables/useTabBarLocale'

definePage({
  name: 'shopping-cart',
  layout: 'tabbar',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '购物车',
  },
})

const { t } = useI18n()
useTabBarLocale()
// 页面滚动接近底部时广播检查，「为你推荐」收到后自动加载下一页，不再需要点「点击加载更多」
useDecorationInfiniteScroll()

interface ShoppingCartState {
  customStyle: {
    margin: string
    padding: string
  }
  checkedList: any[] // 更具体化类型
  cartList: any[] // 更具体化类型
  shopCheckedAll: boolean
  totalAmount: string
  isEdit: boolean
  goodsSpu: any
  selectArr: any[]
}

// 定义变量
const loading = ref(false)
/** 船舶与靠港选择器：购物车按靠港分组，跨靠港的行需要用户切换船舶才能一起结算 */
const shipPickerVisible = ref(false)
const message = useMessage()
const skuPopup = ref()
const skuKey = ref<boolean>(false)
const skuMode = ref<number>(1)
const selectedSku = ref()
const selectedCartId = ref()
const router = useRouter()
const authStore = useAuthStore()
const goodsStore = useGoodsStore()
const shoppingCartStore = useShoppingCartStore()
// 防串船：按加购靠港分组，当前船舶组排前
const shipContextStore = useShipContextStore()
// 船舶上下文不持久化，冷启动为空；本页自行装载，不依赖用户是否访问过首页
const { load: loadShipContext } = useShipContextLoad()
// 船舶靠港状态行：不用先加购商品也能看到配送计划状态（2026-10-04）。
// 纯零售租户没有船舶链路、未装载到船舶的用户无可展示内容，都不渲染
const capabilityStore = useTenantCapabilityStore()
const showShipStatusBar = computed(() =>
  authStore.isLoggedIn && capabilityStore.shipSupplyEnabled && !!shipContextStore.vesselId,
)
/**
 * 岸上合并采购入口：未绑船的船供租户用户（港口附近工厂等岸上客户）此前没有任何
 * 共享购物车入口——共享车不再只属于船，但绑船入口只对有船舶上下文的用户渲染。
 */
const showShoreEntryBar = computed(() =>
  authStore.isLoggedIn && capabilityStore.shipSupplyEnabled && !shipContextStore.vesselId,
)
const hasActiveCall = computed(() => !!shipContextStore.vesselCallId)
const shipStatusBarText = computed(() => {
  if (!hasActiveCall.value)
    return t('cart.noActiveCall')
  const context = shipContextStore
  // 时间窗由运营排产时补填，刚申报的靠港没有；有则展示 MM-DD HH:mm 区间
  const windowText = context.deliveryWindowStart && context.deliveryWindowEnd
    ? ` · ${context.deliveryWindowStart.slice(5, 16)}~${context.deliveryWindowEnd.slice(5, 16)}`
    : ''
  const berthText = context.berth ? ` ${context.berth}` : ''
  return t('cart.shipStatusAtPort', {
    port: context.portName || t('cart.portPending'),
    berth: berthText,
    window: windowText,
  })
})
/** 船舶卡状态点：无靠港计划用橙色预警，与右侧「去申报」同色 */
const shipStatusTone = computed(() => (hasActiveCall.value ? 'is-active' : 'is-warning'))
/** 分组头与其上方船舶卡重复报同一艘船名，这里收敛成只留区分靠港的部分 */
const currentCallSuffix = computed(() =>
  shipContextStore.portName
    ? ` · ${shipContextStore.portName}${shipContextStore.berth ? ` ${shipContextStore.berth}` : ''}`
    : '',
)
const cartGroups = computed(() => {
  const groups: Record<string, any[]> = {}
  for (const row of state.cartList) {
    const key = row.vesselCallId || ''
    if (!groups[key])
      groups[key] = []
    groups[key].push(row)
  }
  const currentKey = shipContextStore.vesselCallId || ''
  return Object.keys(groups)
    .sort((a, b) => (a === currentKey && !!a ? -1 : b === currentKey && !!b ? 1 : 0))
    .map(key => ({
      key,
      // 只有「非空的当前靠港」才算当前船舶。currentKey 为空时它与无归属行
      // 都是 ''，若直接比 key === currentKey 就会把「未指定配送计划」这一组
      // 标成「当前船舶：未命名」——用户明明有船却显示未命名，正是这个误判。
      label: groupLabel(key, groups[key]),
      isCurrent: !!key && key === currentKey,
      action: groupAction(key, groups[key]),
      rows: groups[key],
    }))
})

/** 无靠港组的标签要区分两种行：靠港已结束的船归属行 vs 从未归属过船舶的行 */
function groupLabel(key: string, rows: any[]) {
  if (key) {
    // 当前靠港组由上方船舶状态卡承载船名与靠港，这里只补区分别组所需的信息
    if (key === (shipContextStore.vesselCallId || ''))
      return t('cart.currentGroupLabel', { suffix: currentCallSuffix.value })
    return nonCurrentCallLabel(rows)
  }
  // 靠港结束（ETD 过点/已完成）后快照只带船不带靠港，行 vesselId 有值、
  // vesselCallId 为空——这不是「未指定」，回落到常规配送方式是设计内行为
  const vesselRow = rows.find(row => row.vesselId)
  if (!vesselRow)
    return t('cart.noDeliveryPlan')
  // 行上只有船 ID 没有船名；行归属恰好是当前上下文那艘时才报得出船名
  return vesselRow.vesselId === shipContextStore.vesselId
    ? t('cart.currentVesselNoCall', { name: shipContextStore.vesselName || t('cart.unnamedVessel') })
    : t('cart.vesselNoCall')
}

/**
 * 非当前靠港组的标签：说人话，绝不甩裸靠港 ID（用户看不出 413825 是什么）。
 * 行上按靠港批量回填了展示快照（港口/船名/是否已结束），远程缺失时回落通用文案。
 */
function nonCurrentCallLabel(rows: any[]) {
  const place = rows.find(row => row.vesselCallPortName)?.vesselCallPortName || ''
  const vesselName = rows.find(row => row.vesselCallVesselName)?.vesselCallVesselName || ''
  const where = place || vesselName
  if (rows.some(row => row.vesselCallOrderable === false)) {
    const base = where
      ? t('cart.previousCallWithPlace', { place: where })
      : t('cart.previousCall')
    // 当前无靠港时没有「转入」目标（见 groupAction），就地讲清结算走向，别让用户猜
    return shipContextStore.vesselCallId
      ? base
      : t('cart.previousCallCheckoutRegular', { base })
  }
  // 快照缺失/仍是进行中的另一条靠港都走这里；后者由 groupAction 给切换提示
  return where
    ? t('cart.otherCallWithPlace', { place: where })
    : t('cart.otherCall')
}

/**
 * 非当前靠港组的行动提示，只在用户真能做到时才给：
 * - 行归属「仍在进行中」的另一条靠港 → 切换船舶有目标可切（红字入口）；
 * - 行归属已结束的靠港且当前有靠港 → 给「转入本次靠港」，删掉重加太折磨；
 * - 当前无靠港（没有可切换/可转入的目标）→ 不出行动，标签里已说明结算走向。
 */
function groupAction(key: string, rows: any[]) {
  if (!key || key === (shipContextStore.vesselCallId || ''))
    return ''
  if (rows.some(row => row.vesselCallOrderable === false))
    return shipContextStore.vesselCallId ? 'transfer' : ''
  if (rows.some(row => row.vesselCallOrderable === true))
    return 'switch'
  // 快照缺失（远程失败）：保守维持旧的切换提示，仅当切换有目标时
  return shipContextStore.vesselCallId ? 'switch' : ''
}
const globalLoading = useGlobalLoading()
const { success: showSuccess } = useGlobalToast()
const { confirm } = useGlobalMessage()
const state = reactive<ShoppingCartState>({
  customStyle: {
    margin: '10rpx',
    padding: '20rpx',
  }, // 去结算按钮样式
  checkedList: [], // 已勾选数组
  cartList: [], // 购物车数据
  shopCheckedAll: false, // 全选
  totalAmount: '0', // 合计金额
  isEdit: false, // 编辑按钮
  goodsSpu: {},
  selectArr: [],
})

/**
 * 新人价展示：`skuId → display`。
 *
 * 用 `ref<Record<...>>` 而不是 Map：整份对象直接替换即可触发依赖它的
 * 行价/合计/限购计算，不必处理 Map 的响应式代理与就地 set。
 * 未命中/接口失败时保持空对象，所有读取都回落到原价（降级无需额外分支）。
 */
const newbieDisplays = ref<Record<string, NewbiePriceDisplay>>({})

/** 取某行的新人价展示；无活动/未命中时返回共享的空展示（不新建对象，避免每行一个） */
function newbieDisplayOf(row: any): NewbiePriceDisplay {
  return newbieDisplays.value[String(row?.skuId ?? '')] || emptyNewbieDisplay()
}

/**
 * 限时折扣展示：`skuId → display`。
 *
 * 与新人价同构：整份对象替换即可触发依赖它的行价/合计/划线价重算；
 * 未命中/接口失败时保持空对象，所有读取都回落原价（降级无需额外分支）。
 */
const discountDisplays = ref<Record<string, DiscountPriceDisplay>>({})

/** 取某行的限时折扣展示；无活动/未命中时返回共享的空展示（不新建对象，避免每行一个） */
function discountDisplayOf(row: any): DiscountPriceDisplay {
  return discountDisplays.value[String(row?.skuId ?? '')] || emptyDiscountDisplay()
}

/**
 * 会员专享价展示：`skuId → display`。
 *
 * 与新人价/折扣价同构：整份对象替换即可触发依赖它的行价/合计/划线价重算；
 * 未命中/接口失败时保持空对象，所有读取都回落原价（降级无需额外分支）。
 * 达标展示价在共享层取优：min(会员价, 当前售价 × vo.userDiscountRate)
 * （会员折扣率权益随 VO 透传；取优不叠加，与结算同口径）。
 */
const memberDisplays = ref<Record<string, MemberPriceDisplay>>({})

/** 取某行的会员价展示；无活动/未命中时返回共享的空展示（不新建对象，避免每行一个） */
function memberDisplayOf(row: any): MemberPriceDisplay {
  return memberDisplays.value[String(row?.skuId ?? '')] || emptyMemberPriceDisplay()
}

/** 取某行的会员价引导文案（未达标/未登录时「会员专享 ¥x」）；无则不渲染 */
function memberGuideOf(row: any): string {
  const display = memberDisplayOf(row)
  if (!display.guideText || display.memberPrice === null)
    return ''
  return t('cart.memberGuide', { price: display.memberPrice })
}

/**
 * 行内划线价：优先划 SKU 原价（与售价同一数据源），存量商品没维护原价时，
 * 会员价/新人价场景退回划当前售价 —— 原本的对比提示不能凭空消失。
 * 普通场景无原价则不划线，与商详/列表的 shouldShowOriginalPrice 口径一致。
 *
 * 优先级与成交价一致：会员价 > 新人价 > 限时折扣 > 原价。
 */
function strikePriceOf(row: any): number | string | undefined {
  const sku = row?.goodsSku
  if (!sku)
    return undefined
  const member = memberDisplayOf(row)
  if (member.showMemberPrice) {
    // 会员价的基准价是 SKU 当前售价（接口下发的 originalPrice）；
    // 商品维护了更大的原价时优先划原价，否则至少划当前售价，保持「有优惠」的可比性
    return shouldShowOriginalPrice(member.memberPrice, sku.originalPrice)
      ? sku.originalPrice
      : sku.salesPrice
  }
  const newbie = newbieDisplayOf(row)
  if (newbie.showNewbiePrice) {
    return shouldShowOriginalPrice(newbie.newbiePrice, sku.originalPrice)
      ? sku.originalPrice
      : sku.salesPrice
  }
  // 限时折扣的划线价是活动基准价（接口下发的 originalPrice，等同 SKU 售价）：
  // 主价已切折后价，划线价必须取同一份判定结果，否则「折后价 vs 划线价」对不上
  const discount = discountDisplayOf(row)
  if (discount.showDiscountPrice && discount.originalPrice !== null)
    return discount.originalPrice
  return shouldShowOriginalPrice(sku.salesPrice, sku.originalPrice) ? sku.originalPrice : undefined
}

/**
 * 行的实际成交单价：会员价 > 新人价 > 限时折扣 > 售价
 * （与后端 OrderPriceComputeService 的「拼团 > 秒杀 > 会员价 > 新人价 > 限时折扣 > 原价」同向；
 * 秒杀价购物车未接入，属既有边界，不为标价新增秒杀请求）。
 *
 * 三层展示先经 `resolvePromotionPriority` 统一收敛（命中高优先级时清空低优先级），
 * 行价/合计再读同一份结果，避免这里各写一遍 if/else 与商详判定漂移。
 */
function cartUnitPrice(row: any): number {
  const fallback = row?.goodsSku?.salesPrice
  const priority = resolvePromotionPriority({
    discount: discountDisplayOf(row),
    member: memberDisplayOf(row),
    newbie: newbieDisplayOf(row),
    seckill: false,
  })
  const member = priority.member
  if (member.showMemberPrice)
    return memberPriceUnitPrice(fallback, member)
  const newbie = priority.newbie
  if (newbie.showNewbiePrice)
    return effectiveUnitPrice(fallback, newbie)
  return discountUnitPrice(fallback, priority.discount)
}

/** 页面是否存在可享新人价的行（决定顶部说明条是否渲染） */
const cartHasNewbiePrice = computed(() =>
  hasNewbiePrice(Object.values(newbieDisplays.value)),
)

/** 新人价说明条文案：任取一个可享行即可，限购数由活动统一（后端固定 1 件） */
const cartNewbieHint = computed(() => {
  const hit = Object.values(newbieDisplays.value).find(display => display.showNewbiePrice)
  return hit ? t('cart.newbieLimitHint', { count: hit.limitPerUser }) : ''
})

/**
 * 商品件数不叫「种数」：一行可能买 3 件，按 cartList.length 统计会让顶部
 * 「共 1 件商品」与底部「去结算(3)」对不上。
 * 勾选件数同样按数量累加，保证顶部汇总、底部按钮、编辑模式删除条三处同口径。
 */
const cartPieceCount = computed(() =>
  state.cartList.reduce((sum: number, row: any) => sum + (Number(row.quantity) || 0), 0),
)
const checkedPieceCount = computed(() =>
  state.checkedList.reduce((sum: number, row: any) => sum + (Number(row.quantity) || 0), 0),
)

/**
 * 推荐上下文：车内商品去重后既作为分类来源，也作为排除项，
 * 避免「为你推荐」反复出现已经在车里的商品。
 */
const cartRecommendSpuIds = computed(() => {
  const ids = state.cartList
    .map((row: any) => String(row?.spuId ?? '').trim())
    .filter(Boolean)
  return [...new Set(ids)]
})
const cartRecommendSourceIds = computed(() => cartRecommendSpuIds.value.slice(0, 20))
const cartRecommendExcludeIds = computed(() => cartRecommendSpuIds.value.slice(0, 50))

/**
 * 出现多行的 SKU 集合（`String(skuId)` 归一，空 skuId 忽略）。
 *
 * 同一 SKU 可能来自不同靠港分组：已结束靠港的滞留行 + 当前无靠港行，这是
 * 购物车按 (SKU, 靠港) 分组后的合法状态，不能直接合并展示 —— 每行的靠港归属
 * 要保留给分组头与防串船校验；结算/下单时才按 SKU 合并数量。这里只标记重复行，
 * 在商品卡里补一句说明，避免用户以为是重复加购。
 */
const duplicateSkuIds = computed(() => {
  const seen = new Set<string>()
  const duplicated = new Set<string>()
  for (const row of state.cartList) {
    const skuId = String(row?.skuId ?? '').trim()
    if (!skuId)
      continue
    if (seen.has(skuId))
      duplicated.add(skuId)
    else
      seen.add(skuId)
  }
  return duplicated
})

// 编辑按钮
function handleEdit() {
  state.isEdit = !state.isEdit
  // 退出编辑模式时剔除已选中但无货/下架的行（filter 重建，
  // 边遍历边 splice 会因下标前移漏掉相邻项）
  if (!state.isEdit) {
    state.checkedList = state.checkedList.filter(
      (val: any) => val.goodsSku && val.goodsSku.stock > 0,
    )
  }
  if (state.isEdit && state.shopCheckedAll) {
    // 如果全选了，检查是否存在无货商品
    state.cartList.forEach((val: any) => {
      if (!val.goodsSku || val.goodsSku.stock <= 0) {
        // 存在无货商品，取消全选
        state.shopCheckedAll = false
        // 取消单选全选
        val.checked = false
      }
    })
  }
  if (!state.isEdit && !state.shopCheckedAll) {
    let allChecked = true
    state.cartList.forEach((val: any) => {
      if (!val.checked && val.goodsSku && val.goodsSku.stock > 0) {
        allChecked = false
      }
    })
    if (allChecked) {
      state.shopCheckedAll = true
    }
  }
  computePrice()
}
// 全选
function checkedAll(e: any) {
  if (e.value) {
    state.cartList.forEach((val: any) => {
      const exists = state.checkedList.some(obj => obj.id === val.id)
      if (state.isEdit || (val.goodsSku && val.goodsSku.stock > 0)) {
        if (!exists) {
          val.checked = e.value
          state.checkedList.push(val)
        }
      }
    })
  }
  else {
    state.checkedList = []
    state.cartList.forEach((val) => {
      val.checked = e.value
    })
  }

  computePrice()
}
// 单选
function cartCheck(e: any, index: number) {
  const shop = state.cartList[index]
  shop.checked = e.value
  if (e.value) {
    state.checkedList.push(shop)
  }
  else {
    state.checkedList.forEach((val, index) => {
      if (shop.id === val.id) {
        state.checkedList.splice(index, 1)
      }
    })
  }
  computePrice()
}
function selectedAll() {
  if (!state.cartList || state.cartList.length <= 0) {
    state.shopCheckedAll = false
    return
  }
  // 全选按钮是否选中
  state.shopCheckedAll = state.cartList.every((f: any) => f.checked)
}
// 数量改变
function quantityChange(e: any, id: string) {
  const params = { id, quantity: e.value }
  editCart(params)
  computePrice()
}
// 圆形勾选：单选切换（视觉重构后替代 wd-checkbox 的 change 事件）
function toggleGoodsCheck(row: any, _index: number) {
  row.checked = !row.checked
  if (row.checked) {
    if (!state.checkedList.some(v => v.id === row.id)) {
      state.checkedList.push(row)
    }
  }
  else {
    const i = state.checkedList.findIndex(v => v.id === row.id)
    if (i > -1)
      state.checkedList.splice(i, 1)
  }
  computePrice()
}
// 圆形勾选：全选切换
function toggleAllCheck() {
  state.shopCheckedAll = !state.shopCheckedAll
  if (state.shopCheckedAll) {
    state.cartList.forEach((val: any) => {
      const exists = state.checkedList.some(obj => obj.id === val.id)
      if ((state.isEdit || (val.goodsSku && val.goodsSku.stock > 0)) && !exists) {
        val.checked = true
        state.checkedList.push(val)
      }
    })
  }
  else {
    state.checkedList = []
    state.cartList.forEach((val: any) => {
      val.checked = false
    })
  }
  computePrice()
}
/**
 * 步进器上限：可享新人价的行取 `min(库存, 限购数)`，其余行沿用库存。
 *
 * 提前在步进器上拦住「+」比让用户点完再被后端拒绝友好得多；库存仍要参与取小，
 * 否则限购 1 件、库存 0 时上限不会是 0（虽然这类行本身不渲染步进器）。
 */
function quantityMax(row: any): number {
  const stock = Number(row?.goodsSku?.stock) || 0
  const display = newbieDisplayOf(row)
  if (!display.showNewbiePrice)
    return stock
  return Math.min(stock, display.limitPerUser)
}

// 计算合计金额
function computePrice() {
  selectedAll()
  let amount = 0
  state.checkedList.forEach((val: any) => {
    if (val.checked && val.goodsSku) {
      // 走页面内与行价同源的 cartUnitPrice（新人价 > 限时折扣 > 售价）。
      // 不能继续写 salesPrice —— 否则行价显示折后价、合计按原价，两个数字对不上
      amount += cartUnitPrice(val) * val.quantity
    }
  })
  state.totalAmount = amount.toFixed(2)
}

/**
 * 批量查询购物车行的新人价。
 *
 * 必须整页一次查询（一次带上所有 skuId）：逐行请求会把购物车首屏放大成 N 个请求，
 * 且各请求的资格判定可能落在活动切换的边界上，出现同一屏里一部分行是新人价、
 * 一部分不是。
 *
 * 失败静默：新人价只是价格标注，接口异常绝不能影响购物车列表与结算主流程。
 */
async function loadNewbiePrices() {
  const skuIds = state.cartList
    .map((row: any) => String(row?.skuId || '').trim())
    .filter(Boolean)
  if (!skuIds.length) {
    newbieDisplays.value = {}
    return
  }
  try {
    const vo = await getNewbieSkuPrices(skuIds)
    const next: Record<string, NewbiePriceDisplay> = {}
    // 按行的 skuId 逐个解析（同一 SKU 可能有多行，如共享购物车按成员拆行），
    // 保证行价、限购、合计三处读的是同一份判定结果
    for (const skuId of skuIds) {
      next[skuId] = resolveNewbiePriceDisplay(vo, skuId)
    }
    newbieDisplays.value = next
  }
  catch {
    // 接口失败按原价展示（空表即全部回落 salesPrice）
    newbieDisplays.value = {}
  }
  clampOverLimitQuantities()
}

/**
 * 历史超限数量收敛（只改展示层，不写回后端）。
 *
 * 活动上线前用户可能已加了 2 件同款，此时步进器上限会小于当前值，
 * 若不处理，输入框会显示 2 而「+」是灰的、合计却按 2 件算——用户点结算
 * 才被后端以「限购 1 件」拒绝，是最差的一种体验。
 *
 * 取舍：只把本地 `row.quantity` 收敛到限购数并以此计算合计，**不调用
 * editShoppingCart 写回**。理由是写回属于用户购物数据的静默变更：
 * 用户可能只是暂时超量、之后还想按原数量改买其他活动商品，
 * 而活动结束后这份限制也不复存在。真正的拦截仍在提交订单链路（后端硬校验），
 * 这里只保证「页面显示的数量 / 合计 / 提交数量」三者一致。
 */
function clampOverLimitQuantities() {
  for (const row of state.cartList) {
    if (!row?.goodsSku)
      continue
    const clamped = clampNewbieQuantity(row.quantity, newbieDisplayOf(row))
    if (clamped !== Number(row.quantity)) {
      row.quantity = clamped
    }
  }
  computePrice()
}

/**
 * 批量查询购物车行的限时折扣价。
 *
 * 与新人价同一时机、整页一次批量：逐行请求会把购物车首屏放大成 N 个请求，
 * 且各请求可能落在活动起止边界两侧，出现同一屏里一部分行折后价、
 * 一部分行原价的错乱。skuId 先去重（同一 SKU 可能有多行，如共享购物车按成员拆行），
 * 避免把查询串白白拉长。
 *
 * 失败静默：折扣价只是价格标注，接口异常绝不能影响购物车列表与结算主流程。
 */
async function loadDiscountPrices() {
  const skuIds = Array.from(new Set(
    state.cartList
      .map((row: any) => String(row?.skuId || '').trim())
      .filter(Boolean),
  ))
  if (!skuIds.length) {
    discountDisplays.value = {}
    computePrice()
    return
  }
  try {
    const vo = await getGoodsDiscountPrices(skuIds)
    const next: Record<string, DiscountPriceDisplay> = {}
    // 按行的 skuId 逐个解析：行价、划线价、合计三处读的是同一份判定结果
    for (const skuId of skuIds) {
      next[skuId] = resolveDiscountPriceDisplay(vo, skuId)
    }
    discountDisplays.value = next
  }
  catch {
    // 接口失败按原价展示（空表即全部回落 salesPrice）
    discountDisplays.value = {}
  }
  // 折扣价晚于首屏返回：回来时必须重算合计，否则底栏仍显示按原价算出的金额
  computePrice()
}

/**
 * 批量查询购物车行的会员专享价（含当前用户等级资格）。
 *
 * 与新人价/折扣价同一时机、整页一次批量：逐行请求会把购物车首屏放大成 N 个请求，
 * 且各请求可能落在活动起止边界两侧。skuId 先去重（同一 SKU 可能有多行，如共享
 * 购物车按成员拆行），避免把查询串白白拉长。
 *
 * 失败静默：会员价只是价格标注，接口异常绝不能影响购物车列表与结算主流程。
 * 未登录时接口等价免登（返回 eligible=null + 活动价），行价保持原价并透出引导文案。
 */
async function loadMemberPrices() {
  const skuIds = Array.from(new Set(
    state.cartList
      .map((row: any) => String(row?.skuId || '').trim())
      .filter(Boolean),
  ))
  if (!skuIds.length) {
    memberDisplays.value = {}
    computePrice()
    return
  }
  try {
    const vo = await getMemberSkuPrices(skuIds)
    const next: Record<string, MemberPriceDisplay> = {}
    // 按行的 skuId 逐个解析：行价、划线价、合计三处读的是同一份判定结果；
    // 会员折扣率权益（vo.userDiscountRate）由共享取优逻辑消费，取优不叠加
    for (const skuId of skuIds) {
      next[skuId] = resolveMemberPriceDisplay(vo, skuId)
    }
    memberDisplays.value = next
  }
  catch {
    // 接口失败按原价展示（空表即全部回落 salesPrice）
    memberDisplays.value = {}
  }
  // 会员价晚于首屏返回：回来时必须重算合计，否则底栏仍显示按原价算出的金额
  computePrice()
}

async function getCartPage() {
  globalLoading.loading(t('cart.pleaseWait'))
  try {
    const response = await getPage({
      current: 1,
      size: 100,
    })
    // 空购物车时后端返回 data:null，统一归一化为空数组，
    // 避免各处 length / forEach / 分组计算面对 null 各写一遍判空
    state.cartList = response || []
    // 空车没有可管理对象，工具行会随列表一起隐藏；编辑态必须同步退出，
    // 否则用户删光商品后找不到退出编辑的入口（底栏停在编辑态变死锁）
    if (state.cartList.length === 0) {
      state.isEdit = false
    }
    loading.value = false
    state.checkedList = []
    computePrice()
    shoppingCartStore.fetchCartCount()
    // 不 await：新人价/会员价/限时折扣价都是增强展示，让它们晚一帧回来即可，
    // 购物车列表/勾选/结算不该为了价格标注多等一个请求。
    // 三者同一时机触发（列表加载/刷新、增删行都会走 getCartPage），
    // 各自整页一次批量查询，互不阻塞
    void loadNewbiePrices()
    void loadMemberPrices()
    void loadDiscountPrices()
  }
  finally {
    loading.value = false
    globalLoading.close()
  }
}
// 删除选中的商品
function delCart() {
  if (state.checkedList.length > 0) {
    confirm({
      title: t('cart.deleteTitle'),
      msg: t('cart.deleteSelectedMessage'),
      closeOnClickModal: false,
      confirmButtonText: t('common.confirm'),
      cancelButtonText: t('common.cancel'),
      success: (res) => {
        if (res.action === 'confirm') {
          const ids = state.checkedList.map(v => v.id)
          delShoppingCart(ids).then(() => {
            getCartPage()
          })
        }
      },
    })
  }
}
// 删除单个商品（管理模式下点垃圾桶图标）
function delOne(row: any) {
  confirm({
    title: t('cart.deleteTitle'),
    msg: t('cart.deleteOneMessage', { name: row.spuName }),
    closeOnClickModal: false,
    confirmButtonText: t('common.confirm'),
    cancelButtonText: t('common.cancel'),
    success: (res) => {
      if (res.action === 'confirm') {
        delShoppingCart([row.id]).then(() => {
          const i = state.checkedList.findIndex(v => v.id === row.id)
          if (i > -1)
            state.checkedList.splice(i, 1)
          getCartPage()
        })
      }
    },
  })
}
// 清空购物车
function clearCart() {
  if (!state.cartList.length)
    return
  confirm({
    title: t('cart.clearCartTitle'),
    msg: t('cart.clearCartMessage', { count: state.cartList.length }),
    closeOnClickModal: false,
    confirmButtonText: t('cart.clear'),
    cancelButtonText: t('common.cancel'),
    success: (res) => {
      if (res.action === 'confirm') {
        const ids = state.cartList.map((v: any) => v.id)
        delShoppingCart(ids).then(() => {
          state.checkedList = []
          state.shopCheckedAll = false
          getCartPage()
        })
      }
    },
  })
}
async function openSkuPopup(row: any) {
  globalLoading.loading(t('cart.pleaseWait'))
  selectedCartId.value = row.id
  try {
    state.goodsSpu = await getById(row.spuId)
    skuMode.value = 2
    skuKey.value = true
    initGoodsSpecs(state.goodsSpu)
    if (row.goodsSku) {
      selectedSku.value = row.goodsSku
      selectedSku.value.quantity = row.quantity
    }
  }
  finally {
    globalLoading.close()
  }
}
// sku组件 开始-----------------------------------------------------------
function onOpenSkuPopup() {
  console.log('监听 - 打开sku组件')
}
function onCloseSkuPopup(data: any) {
  state.selectArr = data
}
// 添加购物车
function editCart(row: any) {
  if (!row.id) {
    row.id = selectedCartId.value
  }
  editShoppingCart(row).then(() => {
    getCartPage()
    skuKey.value = false
  })
}
function toPageUrl() {
  router.push({
    name: 'goods-list',
  })
}

// 跳转商品详情
function toGoodsDetail(id: string) {
  router.push({
    name: 'goods-detail',
    params: {
      id,
    },
  })
}
/** 进入共享购物车列表（同船多成员合并采购） */
function toSharedCart() {
  uni.navigateTo({ url: '/sub-pages/order/shared-cart/list' })
}
/** 空态次操作：常购清单（船供场景的复购入口，与首页工作台同一个） */
function toFrequent() {
  uni.navigateTo({ url: '/sub-pages/product/frequent/index' })
}
/** 打开船舶与靠港选择器；切换后分组随 shipContextStore 变化自动重算 */
function openShipPicker() {
  shipPickerVisible.value = true
}
/**
 * 把「此前靠港计划」滞留的整组商品转入当前靠港。
 *
 * 服务端把行归属快照刷成当前靠港（同 SKU 撞车自动合并数量）；成功后整页刷新，
 * 该组随归属变化并入「本单商品」，防串船拦截自然解除。失败提示由请求层统一弹出。
 */
function transferGroup(group: { key: string, rows: any[] }) {
  const targetCallId = shipContextStore.vesselCallId
  if (!targetCallId)
    return
  confirm({
    title: t('cart.transferTitle'),
    msg: t('cart.transferMessage', { count: group.rows.length }),
    closeOnClickModal: false,
    confirmButtonText: t('cart.transferConfirm'),
    cancelButtonText: t('cart.transferLater'),
    success: (res) => {
      if (res.action !== 'confirm')
        return
      transferCartRowsToCall(targetCallId, group.rows.map(row => row.id))
        .then(() => {
          showSuccess({ msg: t('cart.transferred') })
          getCartPage()
        })
    },
  })
}// 去结算 跳转结算页
function toSettlement() {
  goodsStore.setGoodsList(state.checkedList)
  router.push({
    name: 'order-confirm',
    params: {
      createWay: '1',
    },
  })
}
// 收藏
async function toCollect() {
  message
    .confirm({
      msg: t('cart.moveToFavoritesMessage'),
      title: t('cart.moveToFavorites'),
    })
    .then(() => {
      saveBatch(state.checkedList).then(() => {
        getCartPage()
        showSuccess({ msg: t('cart.movedToFavorites') })
      })
    })
    .catch(() => { })
}

onShow(async () => {
  if (authStore.isLoggedIn) {
    // 两者并行：cartGroups 是 computed，任一侧就绪都会重算，
    // 不必让列表等上下文拉回来才渲染
    void loadShipContext()
    getCartPage()
  }
})
</script>

<template>
  <hr-navbar :title="t('cart.title')" :left-arrow="false" />
  <!--
    这里曾常驻一条「收货地址」栏，但它是纯展示：state.address 只用于渲染这行文案，
    toSettlement 并不会把它带给结算页，结算页自己会重新取默认地址并允许改选；
    地址的常驻入口在「我的 → 收货地址」。留在购物车只会误导（尤其是船内配送
    delivery_way=4 时地址根本不参与下单），故整行移除，同时省去每次 onShow
    都发一次 getDefault 请求。
  -->
  <!--
    船舶上下文控制区。
    原先是两张等权重的白卡纵向堆叠：上面一张「共享购物车」通栏、下面一张船舶状态卡，
    而「当前船舶：X」在紧接着的分组头里又出现一次。首屏只有一件商品时，
    用户先看到两块几乎一样重的大卡片和两次重复的船名，密度全耗在容器上。
    现在合并为一个区：船舶卡（主控，点按切换/申报）+ 共享购物车降为区内的胶囊入口，
    分组头随即去重（只报靠港，不重复船名）。
    有船舶上下文时渲染船舶卡；未绑船的船供租户用户（岸上客户）改为渲染岸上合并
    采购入口卡；纯零售租户两者都不渲染。
  -->
  <view v-if="showShipStatusBar" class="ship-panel">
    <view class="ship-card" @click="openShipPicker">
      <!-- 状态点：有进行中靠港=主题色，无靠港=橙色（与右侧「去申报」同色），
           颜色之外还有文案区分，不依赖颜色单独传达状态 -->
      <view class="ship-status-dot" :class="shipStatusTone" />
      <view class="min-w-0 flex-1">
        <view class="ship-name">
          {{ shipContextStore.vesselName || t('cart.unnamedVessel') }}
        </view>
        <view class="ship-meta" :class="{ 'is-warning': !hasActiveCall }">
          {{ shipStatusBarText }}
        </view>
      </view>
      <text
        v-if="!hasActiveCall"
        class="ship-action is-warning"
      >
        {{ t('cart.reportCall') }}
      </text>
      <text v-else class="ship-action">
        {{ t('cart.switchVessel') }}
      </text>
    </view>
    <!-- 共享购物车：同船多成员分别加购，采购确认人统一提交整船订单 -->
    <view class="ship-shared-entry" @click="toSharedCart">
      {{ t('cart.mergeWithCrew') }}
      <text class="ship-shared-arrow">
        &gt;
      </text>
    </view>
  </view>
  <!-- 岸上合并采购入口：未绑船用户的共享购物车入口（发起时选岸上收货地址） -->
  <view v-else-if="showShoreEntryBar" class="ship-panel">
    <view class="ship-card" @click="toSharedCart">
      <view class="ship-status-dot is-active" />
      <view class="min-w-0 flex-1">
        <view class="ship-name">
          {{ t('cart.shoreGroupPurchase') }}
        </view>
        <view class="ship-meta">
          {{ t('cart.shoreEntryHint') }}
        </view>
      </view>
      <text class="ship-action">
        {{ t('cart.startGroupPurchase') }}
      </text>
    </view>
  </view>
  <view class="cart-container">
    <view v-if="state.cartList && state.cartList.length > 0" class="cart-item">
      <!-- 管理入口挂在列表头而非导航栏：小程序端导航栏右侧要避让微信胶囊，
           文字会被挤到标题和胶囊之间；列表头右侧空间充裕且语义更贴切 -->
      <view class="cart-toolbar">
        <!-- 件数走数量累加：一行买 3 件时「共 1 件商品」会与底部结算件数对不上 -->
        <view class="toolbar-info">
          <text class="toolbar-count">
            {{ t('cart.itemCount', { count: cartPieceCount }) }}
          </text>
          <text v-if="state.shopCheckedAll" class="toolbar-badge">
            {{ t('cart.allSelected') }}
          </text>
          <text v-else-if="state.checkedList.length > 0" class="toolbar-badge">
            {{ t('cart.selectedCount', { count: checkedPieceCount }) }}
          </text>
        </view>
        <view class="manage-pill" :class="{ 'is-active': state.isEdit }" @click="handleEdit">
          <wd-icon
            :name="state.isEdit ? 'check' : 'edit-outline'" size="24rpx"
            :color="state.isEdit ? '#07c160' : '#646566'"
          />
          <text class="manage-pill-text">
            {{ state.isEdit ? t('cart.done') : t('cart.manage') }}
          </text>
        </view>
      </view>
      <!--
        新人价说明条：只有真的存在可享行才出现（否则新人限购与用户无关）。
        放在列表头而非底部结算条：底栏高度紧张，且用户是在逐行核对价格时
        才会产生「为什么这么便宜/为什么限 1 件」的疑问。
      -->
      <view v-if="cartHasNewbiePrice" class="newbie-tip">
        <text class="newbie-tip-badge">
          {{ t('cart.newbieBadge') }}
        </text>
        <text class="newbie-tip-text">
          {{ cartNewbieHint }}
        </text>
      </view>
      <!--
        防串船分组头：商品归属哪条船由上方船舶卡回答（分组头不再重复船名）。
        行动入口只在用户真能做到时出现——「结算前请切换船舶」仅当存在可切换的
        进行中靠港；已结束靠港的滞留行给「转入本次靠港」；当前无靠港时不出
        行动，只说明结算走向。裸靠港 ID 任何情况下都不展示（用户读不懂尾号）。
      -->
      <view
        v-for="group in cartGroups"
        :key="group.key || 'none'"
        class="group-label"
      >
        <text class="group-text">
          {{ group.label }}
        </text>
        <text
          v-if="group.action === 'switch'"
          class="group-switch"
          @click.stop="openShipPicker"
        >
          {{ t('cart.groupSwitchHint') }}
        </text>
        <text
          v-else-if="group.action === 'transfer'"
          class="group-transfer"
          @click.stop="transferGroup(group)"
        >
          {{ t('cart.transferAction') }}
        </text>
      </view>
      <view
        v-for="(goods, goodsIndex) in state.cartList" :key="goodsIndex" class="goods-card"
        @click="toGoodsDetail(goods.spuId)"
      >
        <!-- 管理模式下右上角删除图标 -->
        <view v-if="state.isEdit" class="card-del" @tap.stop="delOne(goods)">
          <wd-icon name="delete" size="32rpx" color="#969799" />
        </view>
        <!-- 圆形勾选：编辑模式全可选；非编辑模式仅在售商品可勾 -->
        <view
          v-if="state.isEdit || (goods.goodsSku && goods.goodsSku.stock > 0)"
          class="check-circle" :class="{ 'is-checked': goods.checked }"
          @tap.stop="toggleGoodsCheck(goods, goodsIndex)"
        >
          <wd-icon v-if="goods.checked" name="check" size="22rpx" color="#fff" />
        </view>
        <view v-else class="check-circle is-disabled">
          <text class="no-stock-text">
            {{ goods.goodsSku ? t('cart.outOfStockShort') : t('cart.delisted') }}
          </text>
        </view>
        <!-- left -->
        <image class="goods-img" :src="goods.picUrl" mode="aspectFill" />
        <!-- right -->
        <view class="goods-info">
          <wd-text
            :lines="2" :text="goods.spuName" size="26rpx"
            :color="goods.goodsSku && goods.goodsSku.stock > 0 ? '#303133' : '#c0c4cc'"
          />
          <!-- 商品卖点：后端按 spuId 从 SPU 子标题补齐，缺失时不占位 -->
          <wd-text
            v-if="shouldShowSubtitle(goods.spuName, goods.subTitle)" :lines="1" :text="goods.subTitle" size="22rpx"
            color="#909399"
          />
          <view v-if="goods.specsInfo" class="spec-tag" @tap.stop="openSkuPopup(goods)">
            <wd-text
              :text="goods.goodsSku ? goods.specsInfo : t('cart.specMissing')" size="21rpx"
              color="#909399"
            />
            <wd-icon name="arrow-down" size="20rpx" color="#909399" />
          </view>
          <view
            v-if="goods.goodsSku && goods.goodsSku.stock > 0 && goods.goodsSku.stock <= 10"
            class="stock-tip"
          >
            {{ t('cart.stockLeft', { count: goods.goodsSku.stock }) }}
          </view>
          <!-- 同款多行（不同靠港分组）时说明结算口径，避免被当成重复加购 -->
          <view v-if="duplicateSkuIds.has(String(goods.skuId))" class="merge-tip">
            {{ t('cart.duplicateMergeHint') }}
          </view>
          <view v-if="goods.goodsSku" class="goods-bottom">
            <view class="goods-price">
              <!-- 主价走 cartUnitPrice（会员价 > 新人价 > 限时折扣 > 售价），与合计同源；
                   划线价优先取原价，无原价时会员价/新人价场景退回划售价，折扣场景划活动基准价 -->
              <view class="goods-price-main">
                <wd-text
                  :text="cartUnitPrice(goods)"
                  size="30rpx" color="var(--wot-color-theme-primary, #ff2e4d)" mode="price"
                  prefix="￥"
                />
                <!-- 会员价角标：会员价是身份权益，优先于新人价/折扣角标 -->
                <text v-if="memberDisplayOf(goods).showMemberPrice" class="member-badge">
                  {{ t('cart.memberBadge') }}
                </text>
                <text v-else-if="newbieDisplayOf(goods).showNewbiePrice" class="newbie-badge">
                  {{ t('cart.newbieBadge') }}
                </text>
                <!-- 限时折扣角标：新人价优先，无新人价且命中折扣时才出现（文案如「8折」） -->
                <text v-else-if="discountDisplayOf(goods).showDiscountPrice" class="discount-badge">
                  {{ discountDisplayOf(goods).badgeText }}
                </text>
              </view>
              <wd-text
                v-if="strikePriceOf(goods) !== undefined"
                :text="strikePriceOf(goods)" size="22rpx" color="#a8abb2" mode="price"
                prefix="￥" decoration="line-through"
              />
              <!-- 会员价引导：未达标/未登录时「会员专享 ¥x」，只透出不下单拦截 -->
              <view v-if="memberGuideOf(goods)" class="member-guide">
                {{ memberGuideOf(goods) }}
              </view>
            </view>
            <wd-input-number
              v-if="goods.goodsSku && goods.goodsSku.stock > 0" v-model="goods.quantity" :min="1" :index="goods.id"
              step-strictly :max="quantityMax(goods)" :step="1"
              @change="quantityChange($event, goods.id)" @tap.stop=""
            />
          </view>
        </view>
      </view>
    </view>
    <view v-else class="cart-empty">
      <image class="cart-empty-img" src="/static/promotion/empty/empty-cart.png" mode="aspectFit" />
      <text class="cart-empty-title">
        {{ t('cart.emptyTitle') }}
      </text>
      <!-- 空态给两条出路：主操作回商品列表，次操作去常购清单。
           原先只有「去逛逛」，常购用户每次都要从分类页重新翻一遍 -->
      <view class="cart-empty-actions">
        <view class="empty-btn empty-btn-primary" @tap.stop="toPageUrl">
          {{ t('cart.goShopping') }}
        </view>
        <view class="empty-btn" @tap.stop="toFrequent">
          {{ t('cart.frequentList') }}
        </view>
      </view>
      <view class="cart-empty-tip">
        {{ t('cart.emptyTip') }}
      </view>
    </view>
  </view>
  <!-- 底部全选结算 -->
  <view class="cart-footer">
    <view class="cart-footer-warp">
      <view class="footer-check" @click="toggleAllCheck">
        <view class="check-circle check-circle-lg" :class="{ 'is-checked': state.shopCheckedAll }">
          <wd-icon v-if="state.shopCheckedAll" name="check" size="24rpx" color="#fff" />
        </view>
        <text class="footer-all-text">
          {{ t('cart.selectAll') }}
        </text>
      </view>
      <view v-if="!state.isEdit" class="footer-right">
        <view class="footer-price">
          <text class="footer-price-label">
            {{ t('cart.total') }}
          </text>
          <wd-text size="32rpx" mode="price" :text="state.totalAmount" prefix="￥" color="var(--wot-color-theme-primary, #ff2e4d)" />
        </view>
        <!-- 按钮禁用态去掉饱和渐变与投影：一屏里最重的色块落在不可点的按钮上，
             会让人以为那是可点的主操作。禁用时改浅灰，可点时才用品牌渐变 -->
        <view
          class="settle-btn" :class="{ 'is-disabled': state.checkedList.length <= 0 }"
          @click="state.checkedList.length > 0 && toSettlement()"
        >
          {{ t('cart.checkoutWithCount', { count: checkedPieceCount }) }}
        </view>
      </view>
      <view v-else class="footer-right">
        <view class="clear-entry" @click="clearCart">
          {{ t('cart.clear') }}
        </view>
        <view
          class="action-btn action-btn-plain" :class="{ 'is-disabled': state.checkedList.length <= 0 }"
          @click="state.checkedList.length > 0 && toCollect()"
        >
          {{ t('cart.moveToFavorites') }}
        </view>
        <view
          class="action-btn action-btn-danger" :class="{ 'is-disabled': state.checkedList.length <= 0 }"
          @click="state.checkedList.length > 0 && delCart()"
        >
          {{ t('cart.deleteWithCount', { count: checkedPieceCount }) }}
        </view>
      </view>
    </view>
  </view>
  <!-- 为你推荐 -->
  <view class="like-title py-2">
    <wd-icon name="heart" color="red" size="18px" />
    <text class="px-1 text-xs">
      {{ t('cart.recommend') }}
    </text>
    <wd-icon name="heart" color="red" size="18px" />
  </view>
  <WaterfallGoods
    :source-spu-ids="cartRecommendSourceIds"
    :exclude-spu-ids="cartRecommendExcludeIds"
  />
  <!-- 底部结算条占位：结算条固定悬浮，避免遮挡推荐商品最后一行 -->
  <view class="cart-footer-holder" />
  <vk-data-goods-sku-popup
    ref="skuPopup" v-model="skuKey" border-radius="20" :localdata="state.goodsSpu"
    :default-select="selectedSku" sku-arr-name="specsArr" sku-list-name="goodsSkus"
    spec-list-name="specList" :mode="skuMode" @open="onOpenSkuPopup" @close="onCloseSkuPopup"
    @add-cart="editCart"
  />
  <!-- 船舶与靠港选择器：跨靠港分组需要切换船舶才能合并结算；
       申报成功（chooseCall）后 change 回来刷新列表，让顺延过的行按新靠港重新分组 -->
  <ShipContextPicker v-model="shipPickerVisible" @change="getCartPage" />
</template>

<style scoped lang="scss">
/* 船舶控制区：船舶卡（主控）+ 共享购物车胶囊（次入口） */
.ship-panel {
  margin: 20rpx 20rpx 0;
}

.ship-card {
  display: flex;
  align-items: center;
  padding: 24rpx;
  border-radius: 20rpx;
  background: #fff;

  .ship-status-dot {
    width: 12rpx;
    height: 12rpx;
    border-radius: 50%;
    flex-shrink: 0;
    margin-right: 16rpx;

    &.is-active {
      background: var(--wot-color-theme-primary, #ff2e4d);
    }

    &.is-warning {
      background: #ff8f1f;
    }
  }

  .ship-name {
    font-size: 28rpx;
    font-weight: 700;
    color: #1d2129;
    line-height: 40rpx;
  }

  .ship-meta {
    margin-top: 6rpx;
    font-size: 24rpx;
    color: #86909c;
    line-height: 34rpx;

    &.is-warning {
      color: #ff8f1f;
    }
  }

  .ship-action {
    flex-shrink: 0;
    margin-left: 16rpx;
    font-size: 26rpx;
    color: #86909c;

    &.is-warning {
      color: var(--wot-color-theme-primary, #ff2e4d);
    }
  }
}

/* 共享购物车由通栏大卡降为区内胶囊：它平时只是偶尔用的协作入口，
   与「切换船舶」这对高频操作不该有同等视觉权重 */
.ship-shared-entry {
  display: inline-flex;
  align-items: center;
  margin-top: 12rpx;
  padding: 10rpx 20rpx;
  border-radius: 999rpx;
  background: #fff;
  font-size: 24rpx;
  color: #4e5969;

  .ship-shared-arrow {
    margin-left: 8rpx;
    color: #c9cdd4;
  }
}

/* 列表工具行：件数 + 选中态 + 管理入口 */
.cart-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 20rpx 20rpx 0;

  .toolbar-info {
    display: flex;
    align-items: center;
    min-width: 0;
  }

  .toolbar-count {
    font-size: 24rpx;
    color: #86909c;
  }

  .toolbar-badge {
    margin-left: 12rpx;
    padding: 2rpx 12rpx;
    border-radius: 16rpx;
    font-size: 22rpx;
    color: var(--wot-color-theme-primary, #ff2e4d);
    background: rgba(255, 46, 77, 0.08);
  }

  .manage-pill {
    display: flex;
    align-items: center;
    height: 56rpx;
    padding: 0 22rpx;
    border-radius: 28rpx;
    background: #fff;
    transition: all 0.15s;

    .manage-pill-text {
      margin-left: 6rpx;
      font-size: 24rpx;
      color: #303133;
    }

    &.is-active {
      background: rgba(7, 193, 96, 0.1);

      .manage-pill-text {
        color: #07c160;
      }
    }
  }
}

.cart-container {
  background: #f5f6f8;
  min-height: 100vh;
  padding-bottom: 20rpx;
}

/*
 * 新人价说明条：与 stock-tip 同一视觉层级（细窄、浅色底、小字号），
 * 不做重卡片——它只解释规则，不该与商品卡或船舶卡争夺注意力。
 * 颜色沿用价格色的浅底，保证与行价的「新人价」角标同源可联想。
 */
.newbie-tip {
  display: flex;
  align-items: center;
  margin: 20rpx 20rpx 0;
  padding: 12rpx 20rpx;
  border-radius: 12rpx;
  background: rgba(255, 46, 77, 0.06);

  .newbie-tip-badge {
    flex-shrink: 0;
    margin-right: 12rpx;
    padding: 2rpx 12rpx;
    border-radius: 999rpx;
    background: var(--wot-color-theme-primary, #ff2e4d);
    color: #fff;
    font-size: 20rpx;
    font-weight: 600;
  }

  .newbie-tip-text {
    min-width: 0;
    color: #8a4b3a;
    font-size: 22rpx;
    line-height: 32rpx;
  }
}

/* 分组说明行：细窄、无底色块，靠左侧色条表达「这一组归属同一靠港」 */
.group-label {
  display: flex;
  align-items: center;
  font-size: 24rpx;
  color: #86909c;
  margin: 20rpx 20rpx 0;
  padding-left: 16rpx;
  line-height: 36rpx;
  border-left: 4rpx solid #dcdfe6;

  .group-text {
    min-width: 0;
  }

  .group-switch {
    margin-left: 12rpx;
    color: var(--wot-color-theme-primary, #ff2e4d);
    flex-shrink: 0;
  }

  .group-transfer {
    margin-left: 12rpx;
    color: var(--wot-color-theme-primary, #ff2e4d);
    flex-shrink: 0;
  }
}

/* 商品卡片 */
.goods-card {
  display: flex;
  align-items: center;
  background: #fff;
  border-radius: 24rpx;
  margin: 20rpx;
  padding: 24rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.03);
  position: relative;

  .card-del {
    position: absolute;
    top: 16rpx;
    right: 16rpx;
    padding: 8rpx;
  }

  .goods-img {
    width: 168rpx;
    height: 168rpx;
    border-radius: 16rpx;
    flex-shrink: 0;
    background: #f7f8fa;
  }

  .goods-info {
    flex: 1;
    min-width: 0;
    margin-left: 20rpx;
    display: flex;
    flex-direction: column;

    .spec-tag {
      display: inline-flex;
      align-items: center;
      align-self: flex-start;
      background: #f4f5f7;
      border-radius: 8rpx;
      padding: 4rpx 12rpx;
      margin-top: 10rpx;
    }

    .stock-tip {
      margin-top: 8rpx;
      font-size: 22rpx;
      color: #ff976a;
    }

    /* 同款多行提示：比库存提示更弱一档，只解释结算口径，不制造「重复加购」焦虑 */
    .merge-tip {
      margin-top: 4rpx;
      font-size: 20rpx;
      color: #e6a23c;
    }

    .goods-bottom {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-top: 16rpx;
    }

    /* 价格块：主价 + 角标一行，划线原价另起一行，避免与步进器抢横向空间 */
    .goods-price {
      display: flex;
      min-width: 0;
      flex-direction: column;
      align-items: flex-start;

      .goods-price-main {
        display: flex;
        align-items: center;
        min-width: 0;
      }
    }

    /* 新人价角标：与商详页同款圆角胶囊，行内尺寸更小以适应卡片密度 */
    .newbie-badge {
      flex-shrink: 0;
      margin-left: 8rpx;
      padding: 2rpx 10rpx;
      border-radius: 999rpx;
      background: rgba(255, 46, 77, 0.1);
      color: var(--wot-color-theme-primary, #ff2e4d);
      font-size: 18rpx;
      font-weight: 600;
      line-height: 1.6;
    }

    /* 会员价角标：金色系表达会员身份权益，与新人价/折扣角标同为小字重胶囊 */
    .member-badge {
      flex-shrink: 0;
      margin-left: 8rpx;
      padding: 2rpx 10rpx;
      border-radius: 999rpx;
      background: rgba(184, 134, 47, 0.12);
      color: #b8862f;
      font-size: 18rpx;
      font-weight: 600;
      line-height: 1.6;
    }

    /* 会员价引导：未达标/未登录时一行小字，不占主价与划线的位置 */
    .member-guide {
      margin-top: 4rpx;
      color: #b0803a;
      font-size: 18rpx;
      line-height: 1.5;
    }

    /* 限时折扣角标：复用新人价角标的圆角胶囊尺寸（文案如「8折」「减¥5」），
       底色取活动强调的橙色系，与新人价角标可区分且同为小字重 */
    .discount-badge {
      flex-shrink: 0;
      margin-left: 8rpx;
      padding: 2rpx 10rpx;
      border-radius: 999rpx;
      background: rgba(255, 138, 0, 0.12);
      color: var(--wot-color-theme-secondary, #ff8a00);
      font-size: 18rpx;
      font-weight: 600;
      line-height: 1.6;
    }
  }
}

/* 圆形勾选 */
.check-circle {
  width: 36rpx;
  height: 36rpx;
  border-radius: 50%;
  border: 2rpx solid #dcdee0;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-right: 16rpx;
  transition: all 0.15s;

  &.is-checked {
    background: #07c160;
    border-color: #07c160;
  }

  &.is-disabled {
    background: #f2f3f5;
    border-color: #ebedf0;

    .no-stock-text {
      font-size: 18rpx;
      color: #969799;
    }
  }
}

.check-circle-lg {
  width: 40rpx;
  height: 40rpx;
  margin-right: 12rpx;
}

/* 底部结算条 */
.cart-footer {
  position: fixed;
  width: 100%;
  left: 0;
  z-index: 10;
  box-sizing: border-box;
  background: #fff;
  padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
  box-shadow: 0 -2rpx 12rpx rgba(0, 0, 0, 0.04);
  // 结算条固定在 TabBar 之上：H5 的 --window-bottom 为 TabBar 高度（含安全区），
  // 小程序为原生 TabBar，该变量为 0，视口本身已排除 TabBar。
  bottom: var(--window-bottom, 0px);

  .cart-footer-warp {
    display: flex;
    align-items: center;
  }

  .footer-check {
    display: flex;
    align-items: center;
  }

  .footer-all-text {
    font-size: 28rpx;
    color: #303133;
  }

  .footer-right {
    flex: 1;
    display: flex;
    align-items: center;
    justify-content: flex-end;
  }

  .footer-price {
    display: flex;
    align-items: baseline;
    margin-right: 24rpx;

    .footer-price-label {
      font-size: 26rpx;
      color: #303133;
    }
  }

  .settle-btn {
    min-width: 240rpx;
    height: 80rpx;
    line-height: 80rpx;
    text-align: center;
    padding: 0 44rpx;
    border-radius: 40rpx;
    background: linear-gradient(135deg, var(--wot-color-theme-secondary, #ff8a00), var(--wot-color-theme-primary, #ff4d2e));
    color: #fff;
    font-size: 30rpx;
    font-weight: 500;
    box-shadow: 0 6rpx 16rpx rgba(255, 77, 46, 0.3);

    &.is-disabled {
      /* 禁用态彻底去饱和：渐变与品牌色投影都会让不可点的按钮看着像主操作 */
      background: #e5e6eb;
      color: #a8abb2;
      box-shadow: none;
      font-weight: 400;
    }
  }

  .action-btn {
    height: 72rpx;
    line-height: 72rpx;
    padding: 0 28rpx;
    border-radius: 36rpx;
    font-size: 26rpx;
    margin-left: 16rpx;

    &.action-btn-plain {
      border: 1rpx solid #dcdee0;
      color: #303133;
      background: #fff;
    }

    &.action-btn-danger {
      background: #fff1f0;
      color: var(--wot-color-theme-primary, #ff2237);
    }

    &.is-disabled {
      opacity: 0.5;
    }
  }

  .clear-entry {
    font-size: 26rpx;
    color: #969799;
    padding: 0 12rpx;
  }
}

.like-title {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24rpx 0 8rpx;
}

// 与固定结算条等高的滚动占位，保证最后一行推荐商品可完整滚入可视区
.cart-footer-holder {
  height: calc(112rpx + env(safe-area-inset-bottom));
}

/* 空态：两条出路并排，主次分明；补一句分组说明让用户知道加购后会发生什么 */
.cart-empty-img {
  width: 280rpx;
  height: 280rpx;
}

.cart-empty-title {
  color: #1f2329;
  font-size: 28rpx;
  font-weight: 600;
}

.cart-empty {
  padding: 120rpx 60rpx 0;
  display: flex;
  flex-direction: column;
  align-items: center;

  .cart-empty-actions {
    display: flex;
    align-items: center;
    margin-top: 40rpx;
  }

  .empty-btn {
    min-width: 200rpx;
    height: 72rpx;
    line-height: 72rpx;
    text-align: center;
    border-radius: 36rpx;
    font-size: 28rpx;
    color: #4e5969;
    background: #fff;
    border: 1rpx solid #e5e6eb;

    + .empty-btn {
      margin-left: 24rpx;
    }

    &.empty-btn-primary {
      color: #fff;
      border: none;
      background: linear-gradient(135deg, var(--wot-color-theme-secondary, #ff8a00), var(--wot-color-theme-primary, #ff4d2e));
    }
  }

  .cart-empty-tip {
    margin-top: 32rpx;
    font-size: 22rpx;
    color: #a8abb2;
    text-align: center;
    line-height: 34rpx;
  }
}
</style>

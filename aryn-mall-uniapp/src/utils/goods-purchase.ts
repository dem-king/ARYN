/**
 * 商品详情页购买决策（纯函数，便于单测）。
 *
 * 详情页与小象超市/盒马的结构差异在于：本商城 98% 的在架商品是单规格
 * （`enableSpecs === '0'`），但详情页原先无条件把「选择规格」当成加购的前置闸门，
 * 单规格用户也要进弹层点一次「默认」才能购买，且规格行永远显示「选择规格」。
 *
 * 这里把「该不该让用户选规格」「规格行/发货行显示什么」收敛为纯函数，
 * 详情页与底部详情面板（goods-detail-sheet）共用，避免两处口径漂移。
 * 数量规则（MOQ/步长）不在本文件重复实现，统一走 `utils/quick-cart` 的
 * `resolveQuickAddQuantity`——列表页快捷加购与详情页弹层共用同一份口径。
 */

/** 单规格（后端 goods_spu.enable_specs 取值，与 QuickCartServiceImpl 的 SINGLE_SPEC 对齐） */
export const ENABLE_SPECS_SINGLE = '0'

/** 单规格商品在弹层里的占位规格名，与 vk-data-goods-sku-popup 的 defaultSingleSkuName 对齐 */
export const SINGLE_SPEC_NAME = '默认'

export interface SpecsRowSource {
  enableSpecs?: string
  goodsSkus?: Array<{ specsArr?: Array<{ specsValueName?: string }> }>
}

export interface PurchaseDecision {
  /** 是否需要在加购前让用户选规格（非单规格一律为 true） */
  needChoose: boolean
  /** 单规格时唯一可售 SKU 的规格值（用于规格行只读展示；须在注入「默认」占位前调用） */
  specText: string
}

function firstSkuSpecText(goods?: SpecsRowSource | null): string {
  const specsArr = goods?.goodsSkus?.[0]?.specsArr
  if (!Array.isArray(specsArr))
    return ''
  return specsArr
    .map(spec => spec?.specsValueName)
    .filter((name): name is string => typeof name === 'string' && name.length > 0)
    .join('；')
}

/**
 * 判断详情页点击购买/加购时是否需要用户先选规格。
 *
 * 判据是「明确为单规格（`'0'`）才免选」而非「明确为多规格才要选」：后端
 * `QuickCartServiceImpl` 与前端 `initGoodsSpecs` 都用 `'0'` 表示单规格、
 * 其余值走多规格分支，这里保持同一口径。字段缺失或取值异常时同样要求选规格，
 * 与快捷加购 `resolveQuickAddMode` 同为 fail-safe 取向——宁可多点一次规格，
 * 也不能在规格未定的情况下把错误的商品加进购物车。
 */
export function resolvePurchaseDecision(goods?: SpecsRowSource | null): PurchaseDecision {
  const isSingle = goods?.enableSpecs === ENABLE_SPECS_SINGLE
  return {
    needChoose: !isSingle,
    specText: isSingle ? firstSkuSpecText(goods) : '',
  }
}

export interface SpecRowView {
  /** 是否渲染规格行 */
  visible: boolean
  /** 行文案 */
  text: string
  /** 是否可点（多规格=选规格；单规格=调数量，弹层对单规格只显示数量步进器） */
  tappable: boolean
}

/**
 * 规格行展示视图。
 *
 * 多规格：未选时提示「选择规格」，选过显示已选组合——规格是必须做的决策。
 * 单规格：规格是商品固有属性而非用户决策，只在数据里真有规格值时才展示
 * （本库单规格 SKU 的 specs_arr 基本为空，规格信息都在商品名/船供箱规里，
 * 此时整行隐藏，与「不需要手动选规格」的商超结构一致）；点了弹层只调数量。
 */
export function resolveSpecRow(
  decision: PurchaseDecision,
  selectedText: string,
): SpecRowView {
  if (decision.needChoose) {
    const selected = (selectedText || '').trim()
    return { visible: true, text: selected || '选择规格', tappable: true }
  }
  return { visible: decision.specText.length > 0, text: decision.specText, tappable: true }
}

export interface DeliveryRowInput {
  /** 是否已绑定船舶+靠港上下文（与结算页 deliveryWay='4' 的判据一致） */
  hasVesselContext: boolean
  vesselName?: string
  portName?: string
  berth?: string
  deliveryWindowStart?: string
  deliveryWindowEnd?: string
  freightType?: string
  fixedFreightPrice?: number | string | null
  /** 已格式化的收货地址文本（省市区+详细地址），空串表示未设置 */
  addressText?: string
}

export interface DeliveryRowView {
  /** 主文案（运费/配送方式） */
  text: string
  /** 副文案（地址/靠港信息），空串表示不渲染 */
  detail: string
  /** 是否可点（仅普通快递场景可去改地址；内部配送改船舶/靠港在购物车与工作台） */
  tappable: boolean
}

/**
 * 发货行展示视图。
 *
 * 已绑定船舶上下文时，订单将按内部配送（delivery_way=4）履约——此时展示
 * 「配送至 船舶」与靠港信息，而不是收货地址；措辞与结算页内部配送卡片一致。
 * 普通场景维持 运费/包邮 + 配送至收货地址。
 */
export function resolveDeliveryRow(input: DeliveryRowInput): DeliveryRowView {
  if (input.hasVesselContext) {
    const vesselName = (input.vesselName ?? '').trim() || '所在船舶'
    const location = [input.portName, input.berth]
      .map(part => (part ?? '').trim())
      .filter(Boolean)
      .join(' ')
    const window = input.deliveryWindowStart
      ? `（${input.deliveryWindowStart}${input.deliveryWindowEnd ? ` ~ ${input.deliveryWindowEnd}` : ''}）`
      : ''
    return {
      text: `配送至 ${vesselName}`,
      // 全角括号紧跟地名，中间不留空格
      detail: `${location}${window}`,
      tappable: false,
    }
  }

  const text = input.freightType === '0'
    ? '包邮'
    : `运费：${input.fixedFreightPrice ?? 0}元`
  const addressText = (input.addressText ?? '').trim()
  return {
    text,
    detail: addressText ? `配送至：${addressText}` : '',
    tappable: true,
  }
}

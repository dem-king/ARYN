/**
 * 场景选货页的行视图口径（纯函数，便于单测）。
 *
 * 页面拿到的是 `GoodsCatalogSummaryVO` 行（SPU + SKU 联表结果），
 * 卡片该显示哪些信息、数量下限/上限怎么算，都收敛在这里，
 * 避免模板里散落默认值而与其他入口漂移。
 *
 * 船供包装资料（采购单位/MOQ/步长）已随船供化下线（2026-09-29）：
 * 数量规则恒为 MOQ=1/步长=1。保留规则骨架而不是删掉，是为了步进器
 * 与校验口径不动 —— 将来若在商品本体上恢复数量规则可无缝接回。
 *
 * 数量规则刻意**不复用** `utils/quick-cart` 的 `resolveQuantityRule`：
 * 那边的 `toPositiveInt` 把 stock<=0 归一成 null（「后端没下发」），
 * 用于快捷加购时由服务端兜底校验。选货页要当场告诉用户能不能加，
 * 因此这里保留 0 的语义 —— 0 就是缺货，不能当成「不限量」。
 */

/** 库存低于该值时提示「仅剩 N」，提醒用户早点下单 */
export const LOW_STOCK_THRESHOLD = 10

/** 卡片行（GoodsCatalogSummaryVO 的字段子集；字段缺失按未维护处理） */
export interface ShipSupplyRow {
  spuId?: string
  skuId?: string
  name?: string
  /** 列表缩略图：SKU 图优先、回退 SPU 首图（后端 COALESCE 取好，端上不再拼接） */
  picUrl?: string
  stock?: number | null
  salesPrice?: number | string | null
}

/** 数量规则（船供包装资料下线后恒为 1/1，保留结构兼容步进器） */
export interface ShipQuantityRule {
  /** 最小起订量 */
  moq: number
  /** 数量步长 */
  stepQty: number
  /** 库存；null 表示后端未下发，不做上限约束 */
  stock: number | null
}

/** 库存保留 0：0 与负数都是「无货」，不能与「未下发」混为一谈 */
function toStock(value: unknown): number | null {
  if (value === null || value === undefined || value === '')
    return null
  const parsed = Number(value)
  if (!Number.isFinite(parsed))
    return null
  return Math.floor(parsed)
}

/** 列表行唯一键：同一 SPU 可能因为多规格展开成多行，必须带 SKU 维度 */
export function shipSupplyRowKey(row: ShipSupplyRow): string {
  return `${row.spuId ?? ''}-${row.skuId ?? ''}`
}

export function quantityRuleOf(row: ShipSupplyRow): ShipQuantityRule {
  return {
    moq: 1,
    stepQty: 1,
    stock: toStock(row.stock),
  }
}

/**
 * 加购数量上限；无库存信息或库存充足时不设上限（返回 null）。
 */
export function quantityMaxOf(row: ShipSupplyRow): number | null {
  const { stock } = quantityRuleOf(row)
  return stock !== null && stock > 0 ? stock : null
}

/**
 * 步进器默认值 = 最小起订量；缺货时返回 0，表示不可加购。
 */
export function defaultQuantityOf(row: ShipSupplyRow): number {
  const { moq, stock } = quantityRuleOf(row)
  if (stock !== null && stock <= 0)
    return 0
  return moq
}

/**
 * 是否可加入清单（无可用 SKU / 缺货时为 false，按钮置灰而不是点完才报错）。
 *
 * 没有 skuId 就是没有可售规格：加购请求缺了它必然失败，因此和缺货同属「点之前就该知道」
 * 的一类。
 */
export function isRowAddable(row: ShipSupplyRow): boolean {
  if (!row.skuId)
    return false
  return defaultQuantityOf(row) > 0
}

/**
 * 不可加购的原因，用于图片角标。
 */
export function unavailableReasonOf(row: ShipSupplyRow): string {
  if (isRowAddable(row))
    return ''
  if (!row.skuId)
    return '暂无可售规格'
  const { stock } = quantityRuleOf(row)
  if (stock !== null && stock <= 0)
    return '缺货'
  return ''
}

/**
 * 库存文案：未下发库存时不写「库存 0」这种会误导的假数据，返回空串。
 */
export function stockHintOf(row: ShipSupplyRow): string {
  const { stock } = quantityRuleOf(row)
  if (stock === null)
    return ''
  if (stock <= 0)
    return '缺货'
  if (stock <= LOW_STOCK_THRESHOLD)
    return `仅剩 ${stock}`
  return `库存 ${stock}`
}

/**
 * 库存文案的强调级别：`out`（无货）/ `low`（低于阈值，催单）/ `normal`。
 *
 * 返回语义化档位而不是颜色值，配色留在样式层，
 * 也避免「用颜色单独表达信息」。
 */
export function stockToneOf(row: ShipSupplyRow): 'low' | 'normal' | 'out' {
  const { stock } = quantityRuleOf(row)
  if (stock === null)
    return 'normal'
  if (stock <= 0)
    return 'out'
  return stock <= LOW_STOCK_THRESHOLD ? 'low' : 'normal'
}

export function salesPriceText(row: ShipSupplyRow): string {
  return row.salesPrice === null || row.salesPrice === undefined || row.salesPrice === ''
    ? '-'
    : String(row.salesPrice)
}

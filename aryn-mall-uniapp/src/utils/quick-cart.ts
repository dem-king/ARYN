/**
 * 快捷加购规则（纯函数，便于单测）。
 *
 * 列表/推荐位上的商品卡片拿不到 SKU 明细，点击「快捷加购」时按需向后端
 * 取一次加购信息，再由这里决定：直接加购、唤起规格选择，还是给出不可加购提示。
 *
 * 数量规则与船供包装资料对齐（`ship_sku_profile.moq` / `step_qty`）：
 * 后端在共享购物车提交时会重新校验，前端这里先给出一个「必定合法」的默认数量，
 * 避免用户一键加购后卡在结算环节。
 */

/** 可直接加购，无需选择规格 */
export const QUICK_CART_MODE_DIRECT = 'direct'
/** 需用户选择规格（多规格商品） */
export const QUICK_CART_MODE_CHOOSE = 'choose'
/** 当前不可加购（售罄/无有效 SKU） */
export const QUICK_CART_MODE_UNAVAILABLE = 'unavailable'

export interface QuickCartInfo {
  enableSpecs?: string
  mode?: string
  moq?: number | string | null
  name?: string
  purchaseUnit?: string
  reason?: string
  salesPrice?: number | string
  skuId?: string
  specsInfo?: string
  spuId?: string
  stepQty?: number | string | null
  stock?: number | string | null
}

function toPositiveInt(value: unknown): number | null {
  const parsed = Number(value)
  if (!Number.isFinite(parsed) || parsed <= 0)
    return null
  return Math.floor(parsed)
}

/**
 * 归一化后端返回的加购方式。
 *
 * 未知值一律按「需选择规格」处理（fail-safe）：宁可让用户多点一次规格，
 * 也不能在缺少 skuId 的情况下把错误的商品塞进购物车。
 */
export function resolveQuickAddMode(info: QuickCartInfo | null | undefined): string {
  if (!info)
    return QUICK_CART_MODE_CHOOSE
  if (info.mode === QUICK_CART_MODE_UNAVAILABLE)
    return QUICK_CART_MODE_UNAVAILABLE
  if (info.mode === QUICK_CART_MODE_DIRECT && info.skuId)
    return QUICK_CART_MODE_DIRECT
  return QUICK_CART_MODE_CHOOSE
}

/**
 * 计算快捷加购的默认数量。
 *
 * 取「满足最小起订量且为步长整数倍」的最小值，库存不足该值时返回 0，
 * 由调用方转为不可加购提示；无船供资料的商品按 MOQ=1、步长=1。
 */
export function resolveQuickAddQuantity(info: QuickCartInfo | null | undefined): number {
  const moq = toPositiveInt(info?.moq) ?? 1
  const stepQty = toPositiveInt(info?.stepQty) ?? 1
  const quantity = Math.ceil(moq / stepQty) * stepQty
  const stock = toPositiveInt(info?.stock)
  if (stock !== null && stock < quantity)
    return 0
  return quantity
}

/** 不可加购时的提示文案：后端给出原因时优先展示，否则回落为通用提示 */
export function quickAddBlockedText(info: QuickCartInfo | null | undefined): string {
  const reason = typeof info?.reason === 'string' ? info.reason.trim() : ''
  return reason || '该商品暂时无法加购'
}

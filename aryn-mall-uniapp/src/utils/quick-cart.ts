/**
 * 快捷加购规则（纯函数，便于单测）。
 *
 * 列表/推荐位上的商品卡片拿不到 SKU 明细，点击「快捷加购」时按需向后端
 * 取一次加购信息，再由这里决定：直接加购、唤起规格选择，还是给出不可加购提示。
 *
 * 数量规则（moq/stepQty/purchaseUnit）随船供包装资料下线（2026-09-29）后
 * 后端不再下发，字段保留可选并按 1/空兜底；若将来在商品本体上恢复数量规则，
 * 无需改这里的调用方即可接回。
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
  picUrl?: string
  purchaseUnit?: string
  reason?: string
  salesPrice?: number | string
  skuId?: string
  specsInfo?: string
  spuId?: string
  stepQty?: number | string | null
  stock?: number | string | null
  /** 多规格商品的可选 SKU（mode=choose 时后端下发），常购清单按历史 SKU 回加时用 */
  goodsSkus?: QuickCartSkuInfo[]
}

/** 多规格商品的可选 SKU（字段口径见后端 QuickCartSkuVO） */
export interface QuickCartSkuInfo {
  skuId?: string
  salesPrice?: number | string
  stock?: number | string | null
  picUrl?: string
  specsInfo?: string
  moq?: number | string | null
  stepQty?: number | string | null
  purchaseUnit?: string
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

/** 数量规则（MOQ/步长/库存），采购量输入的约束口径 */
export interface QuantityRule {
  /** 最小起订量，缺省 1 */
  moq: number
  /** 数量步长，缺省 1 */
  stepQty: number
  /** 库存上限；null 表示后端未下发，不做上限约束 */
  stock: number | null
  /** 采购单位（如「箱」「斤」），用于输入框后缀与提示文案 */
  purchaseUnit: string
}

/**
 * 从加购信息里提取数量规则。
 *
 * 与 `resolveQuickAddQuantity` 同一份默认值口径：船供资料未维护的商品
 * 按 MOQ=1、步长=1 处理，避免采购大数量商品时被前端凭空拦住。
 */
export function resolveQuantityRule(info: QuickCartInfo | null | undefined): QuantityRule {
  const unit = typeof info?.purchaseUnit === 'string' ? info.purchaseUnit.trim() : ''
  return {
    moq: toPositiveInt(info?.moq) ?? 1,
    stepQty: toPositiveInt(info?.stepQty) ?? 1,
    stock: toPositiveInt(info?.stock),
    purchaseUnit: unit,
  }
}

/**
 * 按具体 SKU 取数量规则（常购清单回加用）。
 *
 * 常购清单只存 SKU 快照，而数量规则挂在 SKU 上：单规格商品的规则在
 * `QuickCartInfo` 顶层，多规格商品的则在 `goodsSkus[]` 里逐条下发。
 * 因此必须按 skuId 精确匹配——多规格 SPU 下不同 SKU 的起订量/步长可以不同，
 * 用 SPU 顶层字段会张冠李戴。
 *
 * @returns 该 SKU 的数量规则；后端未下发（无船供资料/该 SKU 不在售）时返回 null，
 *          调用方据此退回「只确认去向、数量按历史值」的行为，不因缺资料拦住加购
 */
export function resolveQuantityRuleForSku(
  info: QuickCartInfo | null | undefined,
  skuId: unknown,
): QuantityRule | null {
  const target = typeof skuId === 'string' || typeof skuId === 'number' ? String(skuId) : ''
  if (!info || !target)
    return null
  const matched = (info.goodsSkus ?? []).find(sku => String(sku?.skuId ?? '') === target)
  if (matched)
    return resolveQuantityRule(matched)
  if (String(info.skuId ?? '') === target && (info.moq != null || info.stepQty != null || info.stock != null))
    return resolveQuantityRule(info)
  return null
}

/**
 * 把任意输入数量归一化为「合法且不超库存」的值。
 *
 * 采购量动辄几十上百（小青菜一次买几十斤），用户手输的值通常不是步长整数倍，
 * 直接拒绝会让人反复试错；这里统一向上取到最近的合法值，与
 * `SharedCartServiceImpl.validateQuantityRules`（数量 ≥ MOQ 且为步长整数倍）
 * 同一口径，保证前端放行的数量在结算时必定通过。
 *
 * 向上取整后再做库存封顶：封顶可能把值带回整数倍以下（库存本身不是步长
 * 整数倍时），此时不足 MOQ 返回 0，由调用方转为「库存不足」提示。
 *
 * @returns 合法数量；0 表示库存不足以满足最小起订量
 */
export function normalizeQuantity(value: unknown, rule: QuantityRule): number {
  const moq = toPositiveInt(rule.moq) ?? 1
  const stepQty = toPositiveInt(rule.stepQty) ?? 1
  const raw = Number(value)
  // 非法/非正输入回落到 MOQ，让输入框清空或输入 0 时给出确定性结果
  const base = Number.isFinite(raw) && raw > 0 ? Math.floor(raw) : moq
  let quantity = Math.ceil(base / stepQty) * stepQty
  if (quantity < moq)
    quantity = Math.ceil(moq / stepQty) * stepQty

  const stock = toPositiveInt(rule.stock)
  if (stock !== null && quantity > stock) {
    quantity = Math.floor(stock / stepQty) * stepQty
    if (quantity < moq)
      return 0
  }
  return quantity
}

/** 采购量是否满足数量规则（MOQ 与步长），不满足时给出可展示的中文原因 */
export function validateQuantity(value: unknown, rule: QuantityRule): { ok: boolean, reason: string } {
  const moq = toPositiveInt(rule.moq) ?? 1
  const stepQty = toPositiveInt(rule.stepQty) ?? 1
  const quantity = Number(value)
  if (!Number.isFinite(quantity) || quantity <= 0)
    return { ok: false, reason: '请输入采购数量' }
  if (quantity < moq)
    return { ok: false, reason: `最少起订 ${moq}${rule.purchaseUnit}` }
  if (quantity % stepQty !== 0)
    return { ok: false, reason: `数量需为 ${stepQty} 的整数倍` }
  const stock = toPositiveInt(rule.stock)
  if (stock !== null && quantity > stock)
    return { ok: false, reason: `库存仅剩 ${stock}${rule.purchaseUnit}` }
  return { ok: true, reason: '' }
}

/**
 * 数量规则提示文案：把「起订量 / 步长 / 库存」讲在输入框旁，
 * 用户填大数量（几十上百斤）时一眼能看到约束，不必提交后才被拦。
 *
 * 没有约束（无船供资料、库存未下发）时返回空串，由调用方整行隐藏。
 */
export function quantityRuleHint(rule: QuantityRule): string {
  const unit = rule.purchaseUnit
  const parts: string[] = []
  if (rule.moq > 1)
    parts.push(`最少起订 ${rule.moq}${unit}`)
  if (rule.stepQty > 1)
    parts.push(`按 ${rule.stepQty}${unit}递增`)
  if (rule.stock !== null)
    parts.push(`库存 ${rule.stock}${unit}`)
  return parts.join(' · ')
}

/** 不可加购时的提示文案：后端给出原因时优先展示，否则回落为通用提示 */
export function quickAddBlockedText(info: QuickCartInfo | null | undefined): string {
  const reason = typeof info?.reason === 'string' ? info.reason.trim() : ''
  return reason || '该商品暂时无法加购'
}

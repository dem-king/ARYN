/**
 * 商品卡片的「划线原价」显示判定。
 *
 * 各零售楼层都只渲染现价，原价（`goods_spu.original_price`）是可选信息：
 * 存量商品大量未填原价（值为 0），直接渲染会划出一个「￥0」。
 * 因此口径与商详页（GoodsInfo.vue）保持一致：**原价严格大于现价时才显示**。
 *
 * 相等也不显示 —— 无折扣的划线价没有信息量，只会让卡片更挤。
 */
export function shouldShowOriginalPrice(
  price: unknown,
  originalPrice: unknown,
): boolean {
  const current = Number(price)
  const original = Number(originalPrice)
  if (!Number.isFinite(current) || !Number.isFinite(original))
    return false
  return original > current
}

export interface PriceParts {
  integer: string
  decimal: string
}

/**
 * 金额拆成整数/两位小数（整数部分带千分位），供「¥ 小、整数大、小数小」
 * 的电商价格排版使用。负数与非法值返回 null，页面直接不渲染价格。
 */
export function splitPrice(value: unknown): PriceParts | null {
  const amount = Number(value)
  if (!Number.isFinite(amount) || amount < 0)
    return null
  const [integer, decimal] = amount.toFixed(2).split('.')
  return {
    integer: integer.replace(/\B(?=(\d{3})+(?!\d))/g, ','),
    decimal,
  }
}

/**
 * 把已格式化的金额文本拆成整数/小数两段（小数位数原样保留，不补零）。
 *
 * 与 `splitPrice` 的区别：本函数不改写数字格式，只做「整数大、小数小」的排版拆分，
 * 供仍沿用 `formatAmount`（去掉末尾 0）的卡片保持原有金额文案。
 */
export function splitAmountText(text: string): PriceParts {
  const dot = text.indexOf('.')
  return dot === -1
    ? { integer: text, decimal: '' }
    : { integer: text.slice(0, dot), decimal: text.slice(dot + 1) }
}

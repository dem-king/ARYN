import { SINGLE_SPEC_NAME } from './goods-purchase'

interface ParamSku {
  id?: string
  status?: string
  specsArr?: Array<{ specsValueName?: string }>
  salesUnit?: string
  baseDimension?: string
  baseUnit?: string
  baseQtyPerSalesUnit?: number | string | null
  quantityPrecision?: string
  unitSpecText?: string
  minOrderQty?: number | string | null
  stepQty?: number | string | null
}

interface ParamGoods {
  brandName?: string
  categoryName?: string
  goodsSkus?: ParamSku[]
  params?: Array<{ label?: string, value?: string }>
}

export interface GoodsParamRow {
  key: string
  label?: string
  value?: string
  valueKey?: string
  args?: Record<string, string | number>
  selectable?: boolean
}

/** 参数卡独立成卡的最小行数：只剩一两行时并回上方信息区，避免出现「半张表」 */
export const MIN_PARAM_ROWS = 3

function cleanText(value: unknown): string {
  return typeof value === 'string' ? value.trim() : ''
}

/**
 * 类目路径按段去重。
 *
 * 后端用 `CONCAT_WS(' / ', 一级, 二级)` 拼类目名，一级与二级同名时会出现
 * 「时令鲜果 / 时令鲜果」；端上收敛成单段，不改类目数据本身。
 * 展示用 `›` 表示层级，比斜杠更像面包屑，也不会和规格里的「/」混淆。
 */
export function normalizeCategoryPath(value: string): string {
  const segments = value.split('/').map(segment => segment.trim()).filter(Boolean)
  const unique: string[] = []
  for (const segment of segments) {
    if (!unique.includes(segment))
      unique.push(segment)
  }
  return unique.join(' › ')
}

function positiveInteger(value: unknown): number | null {
  if (typeof value !== 'number' && typeof value !== 'string')
    return null
  const number = Number(value)
  return Number.isSafeInteger(number) && number > 0 ? number : null
}

export function skuSpecValues(sku?: ParamSku): string[] {
  return Array.isArray(sku?.specsArr) ? sku.specsArr.map(spec => cleanText(spec?.specsValueName)) : []
}

/**
 * 拆分 `unitSpecText` 里的“单份规格”和“箱规”。
 *
 * 船供商品的箱规经常只写在 `unitSpecText` 里（如“约 500g / 盒 · 10 盒 / 箱”），
 * 而 `base_qty_per_sales_unit` 可能是 1。只靠 base 字段会丢掉箱规，
 * 所以参数卡和首屏采购标签都从这里取同一份口径。
 */
export function splitUnitSpecText(value: unknown): { spec: string, box: string } {
  const raw = cleanText(value)
  if (!raw)
    return { spec: '', box: '' }
  const parts = raw.split('·').map(part => part
    .replace(/\s*\/\s*/g, '/')
    // 中文量词前的空格收掉（10 盒 → 10盒），保留 450 mL 这类拉丁单位写法
    .replace(/(\d)\s+(?=[\u4E00-\u9FFF])/g, '$1')
    .replace(/([\u4E00-\u9FFF])\s+(?=\d)/g, '$1')
    .trim()).filter(Boolean)
  const box = parts.length > 1 ? (parts.find(part => part.includes('箱')) ?? '') : ''
  return { spec: parts[0] ?? '', box }
}

export function resolveGoodsParams(
  goods: ParamGoods | null | undefined,
  selection: { needChoose: boolean, singleSpecText: string, skuId: string },
): GoodsParamRow[] {
  const rows: GoodsParamRow[] = []
  const brand = cleanText(goods?.brandName)
  const category = normalizeCategoryPath(cleanText(goods?.categoryName))
  if (brand)
    rows.push({ key: 'brand', value: brand })
  if (category)
    rows.push({ key: 'category', value: category })

  const skus = Array.isArray(goods?.goodsSkus) ? goods.goodsSkus : []
  const activeSkus = skus.filter(sku => sku?.status === '0')
  const sku = activeSkus.find(item => String(item.id) === selection.skuId)
  const unitSpec = splitUnitSpecText(sku?.unitSpecText)
  if (selection.needChoose) {
    const combinations = new Set(activeSkus
      .map(skuSpecValues)
      .filter(values => values.length && values.every(Boolean))
      .map(values => JSON.stringify(values)))
    const specText = skuSpecValues(sku).filter(Boolean).join('；')
    if (specText)
      rows.push({ key: 'spec', value: specText, selectable: true })
    else if (combinations.size)
      rows.push({ key: 'spec', valueKey: 'product.params.specCount', args: { count: combinations.size }, selectable: true })
  }
  else {
    const specText = cleanText(selection.singleSpecText)
    const value = specText && specText !== SINGLE_SPEC_NAME ? specText : unitSpec.spec
    if (value && value !== SINGLE_SPEC_NAME)
      rows.push({ key: 'spec', value })
  }
  if (unitSpec.box)
    rows.push({ key: 'boxSpec', value: unitSpec.box })

  const salesUnit = cleanText(sku?.salesUnit)
  const baseUnit = cleanText(sku?.baseUnit)
  const count = positiveInteger(sku?.baseQtyPerSalesUnit)
  if (!unitSpec.box && salesUnit && baseUnit && salesUnit !== baseUnit && count && sku?.baseDimension === 'COUNT' && sku.quantityPrecision === 'EXACT') {
    rows.push({ key: 'pack', valueKey: 'product.params.packValue', args: { salesUnit, baseUnit, count } })
  }
  const min = positiveInteger(sku?.minOrderQty)
  const step = positiveInteger(sku?.stepQty)
  if (salesUnit && min && step && (min > 1 || step > 1 || salesUnit === '箱')) {
    rows.push({ key: 'order', valueKey: 'product.params.orderValue', args: { salesUnit, min, step } })
  }
  const labels = new Set<string>()
  for (const param of Array.isArray(goods?.params) ? goods.params.slice(0, 12) : []) {
    const label = cleanText(param?.label)
    const value = cleanText(param?.value)
    const normalized = label.toLowerCase()
    if (label && value && !labels.has(normalized)) {
      labels.add(normalized)
      rows.push({ key: `custom:${normalized}`, label, value })
    }
  }
  return rows
}

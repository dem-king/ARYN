import type { GoodsAttrTagIconKey, GoodsAttrTagTone } from '@/api/product/attr-tags'
import { SINGLE_SPEC_NAME } from './goods-purchase'
import { splitUnitSpecText } from './goods-params'

interface PurchaseSku {
  id?: string
  status?: string
  salesUnit?: string
  baseUnit?: string
  baseQtyPerSalesUnit?: number | string | null
  unitSpecText?: string
  minOrderQty?: number | string | null
  stepQty?: number | string | null
}

interface PurchaseGoods {
  params?: Array<{ label?: string, value?: string }>
  goodsSkus?: PurchaseSku[]
}

export interface PurchaseTagSelection {
  skuId: string
  needChoose: boolean
  singleSpecText: string
}

export type PurchaseTagKey = 'storage' | 'spec' | 'box' | 'order' | 'origin' | 'shelf'

export interface PurchaseTagFact {
  key: PurchaseTagKey
  value: string
  icon: GoodsAttrTagIconKey
  tone: GoodsAttrTagTone
}

function cleanText(value: unknown): string {
  return typeof value === 'string' ? value.trim() : ''
}

function positiveInteger(value: unknown): number | null {
  if (typeof value !== 'number' && typeof value !== 'string')
    return null
  const number = Number(value)
  return Number.isSafeInteger(number) && number > 0 ? number : null
}

function formatCount(value: number): string {
  return Number.isInteger(value) ? String(value) : String(value).replace(/0+$/, '').replace(/\.$/, '')
}

/** 在商品级参数里按标签别名取值；同一标签只取第一条，避免重复配置互相打架 */
function paramValue(goods: PurchaseGoods | null | undefined, aliases: string[]): string {
  const params = Array.isArray(goods?.params) ? goods.params : []
  for (const alias of aliases) {
    const hit = params.find((param) => {
      const label = cleanText(param?.label).toLowerCase()
      return label === alias.toLowerCase()
    })
    const value = cleanText(hit?.value)
    if (value)
      return value
  }
  return ''
}

function shortenOrigin(value: string): string {
  return value.split(/[·,，]/)[0]?.trim() ?? ''
}

function shortenStorage(value: string): string {
  return value.replace(/\s*(冷藏|冷冻|常温)$/u, '').trim()
}

function shortenShelfLife(value: string): string {
  return value.replace(/^(冷藏|冷冻|常温|避光)\s*/u, '').trim()
}

/**
 * 首屏采购标签：从现有 SKU 与 params 计算，不引入新接口。
 *
 * 标签顺序按 B2B 采购判断的优先级：储存 → 规格 → 箱规 → 起订 → 产地 → 保质期。
 * 最多 4 个；起订量和箱规只在真实存在时才出现，不用“1 件起订”这类噪声占位。
 */
export function resolvePurchaseTags(
  goods: PurchaseGoods | null | undefined,
  selection: PurchaseTagSelection,
): PurchaseTagFact[] {
  const skus = Array.isArray(goods?.goodsSkus) ? goods.goodsSkus : []
  const sku = skus.find(item => item?.status === '0' && String(item?.id) === selection.skuId)
  const unitSpec = splitUnitSpecText(sku?.unitSpecText)

  const storage = shortenStorage(paramValue(goods, ['储存', '储存条件', '储藏', '储藏方式']))
  const shelfLife = shortenShelfLife(paramValue(goods, ['保质期', '保质期限']))
  const origin = shortenOrigin(paramValue(goods, ['产地', '原产地']))

  const selectedSpec = cleanText(selection.singleSpecText)
  const spec = (!selection.needChoose || sku
    ? (selectedSpec && selectedSpec !== SINGLE_SPEC_NAME ? selectedSpec : unitSpec.spec)
    : '').replace(/^约\s*/u, '')

  const salesUnit = cleanText(sku?.salesUnit)
  const baseUnit = cleanText(sku?.baseUnit)
  const count = positiveInteger(sku?.baseQtyPerSalesUnit)
  const box = unitSpec.box
    || (count && count > 1 && salesUnit && baseUnit ? `${formatCount(count)}${baseUnit}/${salesUnit}` : '')

  const min = positiveInteger(sku?.minOrderQty)
  const step = positiveInteger(sku?.stepQty)
  const order = salesUnit && min && step && (min > 1 || step > 1)
    ? `${min}${salesUnit}起`
    : ''

  const facts: PurchaseTagFact[] = []
  if (storage)
    facts.push({ key: 'storage', value: storage, icon: 'snowflake', tone: 'blue' })
  if (spec)
    facts.push({ key: 'spec', value: spec, icon: 'package', tone: 'green' })
  if (box)
    facts.push({ key: 'box', value: box, icon: 'package', tone: 'gray' })
  if (order)
    facts.push({ key: 'order', value: order, icon: 'shopping-bag', tone: 'orange' })
  if (origin)
    facts.push({ key: 'origin', value: origin, icon: 'location', tone: 'orange' })
  if (shelfLife)
    facts.push({ key: 'shelf', value: shelfLife, icon: 'time', tone: 'green' })

  return facts.slice(0, 4)
}

/**
 * 商详首屏服务承诺（2026-11-21）：
 *
 * 领券中心下线、船供资料卡退场后，价格与评价之间少了一层「敢不敢下单」的回答。
 * 商品级承诺走 `goods_spu.service_tags`（code 数组），端上按 code 出中英文案；
 * 未配置/全部非法时回退全局三项，保证老商品也有承诺行，且不会出现空白。
 */

/** 白名单：与后端 `GoodsSpuServiceImpl.SERVICE_TAG_CODES` 一一对应 */
export const SERVICE_TAG_CODES = [
  'cold_chain',
  'damage_refund',
  'invoice',
  'authentic',
] as const

export type ServiceTagCode = (typeof SERVICE_TAG_CODES)[number]

/** 未配置时的全局兜底：正品保障 / 破损包赔 / 可开发票（平台自营与售后能力可兑现） */
export const DEFAULT_SERVICE_TAGS: ServiceTagCode[] = [
  'authentic',
  'damage_refund',
  'invoice',
]

/** 一行最多四项：超出会换行挤压卖点，与后端上限一致 */
export const MAX_SERVICE_TAGS = 4

/** code → i18n key（`product.promise.*`） */
export const SERVICE_TAG_LABEL_KEYS: Record<ServiceTagCode, string> = {
  cold_chain: 'coldChain',
  damage_refund: 'damageRefund',
  invoice: 'invoice',
  authentic: 'authentic',
}

/**
 * code → 图标：承诺行不再统一用对号。
 * 正品用盾牌勾、破损用退款箭头、开票用票据；冷链单独用雪花 SVG，
 * 因为 wd-icon 字体里没有雪花，用 refresh 会被误读成「刷新」。
 */
export const SERVICE_TAG_ICON_NAMES: Record<ServiceTagCode, string> = {
  cold_chain: 'refresh',
  damage_refund: 'rollback',
  invoice: 'spool',
  authentic: 'secured',
}

/** 冷链雪花图标：静态 SVG，避免字体图标缺雪花时用近义图标误导 */
export const COLD_CHAIN_ICON_SRC = '/static/product/promise-cold-chain.svg'

function isServiceTagCode(value: string): value is ServiceTagCode {
  return (SERVICE_TAG_CODES as readonly string[]).includes(value)
}

/**
 * 收敛接口下发的承诺标签：去空、小写、白名单过滤、去重、截断；全空时回退全局三项。
 *
 * 不在函数里抛错：后台已校验，端上遇到脏数据只降级，不能让商详白屏。
 */
export function resolveServiceTags(tags?: Array<null | string | undefined> | null): ServiceTagCode[] {
  const valid: ServiceTagCode[] = []
  for (const tag of tags ?? []) {
    if (typeof tag !== 'string')
      continue
    const code = tag.trim().toLowerCase()
    if (!code || !isServiceTagCode(code) || valid.includes(code))
      continue
    valid.push(code)
  }
  return valid.length ? valid.slice(0, MAX_SERVICE_TAGS) : [...DEFAULT_SERVICE_TAGS]
}

/**
 * 商品卡片/商详的「专区价」展示契约：协议价 / 特供价 / 会员价 / 捡漏价统一收敛。
 *
 * 背景：会员专区、低价捡漏专区展示了会员价/捡漏价，同一商品出现在其它页面
 * （首页其它 Tab、分类、搜索、收藏、足迹、推荐流……）时若只显示原售价，
 * 用户会看到同一个商品两种价格。各页面各写一遍判定必然漂移，因此把
 * 「协议价 vs 身份价家族 vs 原价」的展示口径收敛到这里。
 *
 * **价格优先级（与后端 `OrderPriceComputeService` / 促销引擎一致，2026-11-14 B2B 重构）**：
 * 协议价（命中且件数 ≥ minQty 即终态）> 秒杀（存量遗留，终态）
 * > 身份价家族取低 min(档期特供, 会员价, 出清价)（同价归属：特供 > 会员 > 出清）
 * > 新人价（仅严格低于身份价时覆盖）> 售价。
 * 协议价（price_policy，按主体/船舶/客群圈定的合同价）由营销引擎锁定成交基价，
 * 公开活动价不可覆盖，命中即终态；**minQty 参与成交校验**，件数不足时调用方不得传入
 * policyPrice（商详改为「协议价 N 件起」提示）。
 * 秒杀是平台级硬互斥活动（结算不可叠任何活动价），命中即终态。拼团不承载在列表卡（有独立会场）。
 *
 * **阶梯价与数量相关**：卡片不算阶梯价（数量未定，不传 ladderPrice）；商详按当前数量
 * 取档后传入 ladderPrice，与身份价家族取低——同价保留身份价角标（只有严格更低才接管，
 * 与新人价「严格更低才覆盖」同口径）；协议价锁定后阶梯/身份/特供一律不覆盖。
 *
 * 1. 协议价命中：主价=协议价，划线=当前售价，角标「协议价」（合同价独占公开优惠）；
 * 2. 秒杀价命中：主价=秒杀价，划线=当前售价，角标「秒杀价」；
 * 3. 新人价严格低于身份价家族成交价：主价=新人价，划线=当前售价，角标「新人价」；
 * 4. 身份价家族胜出：主价=家族最低价，角标按归属（特供/会员/捡漏/阶梯），
 *    会员价划线取会员展示的划线基准（缺省为当前售价），其余划当前售价；
 * 5. 都未命中：主价=当前售价；未达标/未登录时保留「会员专享 ¥x」引导文案，
 *    普通划线仍按「原价 > 售价」口径（与既有楼层一致）。
 *
 * 身份价家族取低而不是叠加，同价归属特供 > 会员 > 出清——与后端
 * 「同 SKU 多活动命中取低者、同价按归属优先级」同一口径；展示与结算不漂移。
 */

import type { ListMemberPriceDisplay } from '../components/diy/retail-types'
import type { ClearancePriceDisplay } from './clearance-price'

import { translate } from '@/locales'

/** 角标类型：空串=无角标，由调用方映射成 i18n 文案 */
export type ZonePriceBadgeKind = '' | 'clearance' | 'member' | 'policy' | 'seckill' | 'newbie' | 'special' | 'ladder'

/** 社证类型白名单（与后端 SocialProofFormatter.KIND_* 一一对应）；未知值一律视为未知 */
export type SocialProofKind = 'repurchase' | 'repeat' | 'buyer'

const SOCIAL_PROOF_KINDS = new Set<SocialProofKind>(['repurchase', 'repeat', 'buyer'])

/** 社证类型图标：回购率=循环复购 / 回头客=喜爱 / 月售=人群；未知类型兜底人群 */
const SOCIAL_PROOF_ICONS: Record<SocialProofKind, string> = {
  buyer: 'usergroup',
  repurchase: 'refresh',
  repeat: 'heart',
}

/** 社证行的 wd-icon 图标名；null/未知类型兜底人群图标 */
export function socialProofIcon(kind: unknown): string {
  return SOCIAL_PROOF_KINDS.has(kind as SocialProofKind)
    ? SOCIAL_PROOF_ICONS[kind as SocialProofKind]
    : SOCIAL_PROOF_ICONS.buyer
}

/**
 * 社证数字强调拆分：把服务端成文拆成「前缀 + 数字 + 后缀」，数字段加粗渲染。
 *
 * 纯展示装饰，不做门槛/取整（口径仍在服务端）：匹配不到数字时整句落 suffix，
 * 渲染侧等价于原文直出。覆盖 SocialProofFormatter 全部成文形态：
 * 「月1000+人已下单」「月6万+人已下单」「月回头客1000+人」「30%人回购」。
 */
export function splitSocialProofText(text: unknown): { after: string, before: string, number: string } {
  const raw = typeof text === 'string' ? text : ''
  // 只匹配到数字段为止，后缀用 slice 截取：避免尾部通配与 \d+ 交换字符产生超线性回溯
  const match = /^(\D*)(\d+(?:\.\d+)?万?[+%]?)/.exec(raw)
  if (!match)
    return { after: raw, before: '', number: '' }
  return { after: raw.slice(match[0].length), before: match[1], number: match[2] }
}

/** 列表卡片专区价展示结果（调用方直接按字段渲染） */
export interface ListZonePrice {
  /** 卡片主价（元） */
  mainPrice: number
  /** 划线价（元）；null=不划线 */
  strikePrice: number | null
  /** 角标类型 */
  badgeKind: ZonePriceBadgeKind
  /** 未达标/未登录的引导文案「会员专享 ¥x」；null=不展示 */
  guideText: string | null
  /** 社证文案（月 N+ 人已下单 / 月 N+ 位回头客 / N% 的人回购，服务端下发成文）；null=不展示 */
  socialProofText: string | null
  /** 社证类型（与 socialProofText 同生共死）；null/未知=前端兜底图标 */
  socialProofKind: string | null
  /** 价格声明文案（近N天最低价，服务端判定成文，主价旁描边小标）；null=不展示 */
  priceTagText: string | null
}

export interface ListZonePriceInput {
  /** 卡片当前展示价（SPU salesPrice / 已归一化列表项的 price） */
  salesPrice: unknown
  /**
   * 协议价（合同价，主体/船舶/客群 scoped；补价层按登录态+上下文批量拉取）。
   * 后端引擎锁定成交基价，展示优先级最高（倒挂/非正数视为未命中）。
   * **minQty 参与成交校验**：件数不足时调用方必须不传（或传 null），改出「N 件起」提示。
   */
  policyPrice?: unknown
  /**
   * 档期特供价（type=12，服务端已按场景/船舶圈定并剔除限量耗尽活动）。
   * 与会员价/出清价同属身份价家族取低，同价归属特供（倒挂/非正数视为未命中）。
   */
  specialPrice?: unknown
  /**
   * 阶梯价按当前数量命中的单价（`utils/ladder-price#resolveLadderUnitPrice`）。
   * 卡片不传（数量未定）；商详传入后与身份价家族取低，严格更低才接管角标。
   */
  ladderPrice?: unknown
  /** 商品原价（普通划线用；仅在原价 > 售价时渲染） */
  originalPrice?: unknown
  /**
   * 是否允许渲染划线价：装修「显示原价」开关关闭时传 false。
   *
   * 只影响划线，不影响主价与角标——专区价本身是成交价，必须展示。
   */
  showOriginal?: boolean
  /** 会员价展示（列表口径：`resolveListMemberPrice` 的返回值） */
  memberDisplay?: ListMemberPriceDisplay | null
  /** 出清价展示（SPU 聚合口径，`resolveListClearanceDisplay` 的返回值） */
  clearanceDisplay?: ClearancePriceDisplay | null
  /** 进行中的秒杀价（card-tags 下发；倒挂/非正数视为未命中） */
  seckillPrice?: unknown
  /** 新人专享价（card-tags 下发；未登录或无资格时后端不下发） */
  newbiePrice?: unknown
  /** 社证文案（card-tags 下发成文，服务端已做门槛/取整；透传渲染） */
  socialProofText?: unknown
  /** 社证类型（card-tags 下发，与 socialProofText 同生共死；透传渲染） */
  socialProofKind?: unknown
  /** 价格声明文案（card-tags 下发成文，服务端已做窗口/打标判定；透传渲染） */
  priceTagText?: unknown
}

function toFiniteNumber(value: unknown): number | null {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

function roundToCent(value: number): number {
  return Math.round(value * 100) / 100
}

/** 有效成交价：正数且严格低于当前售价（倒挂/等价一律不展示） */
function validLowerPrice(value: unknown, salesPrice: number): number | null {
  const price = toFiniteNumber(value)
  if (price === null || price <= 0 || price >= salesPrice)
    return null
  return roundToCent(price)
}

/** 社证文案防御：非字符串/空白一律视为不展示 */
function sanitizeProofText(value: unknown): string | null {
  const text = typeof value === 'string' ? value.trim() : ''
  return text || null
}

/** 社证类型防御：不在白名单（含脏数据/旧响应缺字段）一律视为未知，前端兜底图标 */
function sanitizeProofKind(value: unknown): string | null {
  return typeof value === 'string' && SOCIAL_PROOF_KINDS.has(value as SocialProofKind)
    ? value
    : null
}

/** 未命中时的空展示（主价回落到调用方传入的售价） */
export function emptyListZonePrice(sales: unknown = 0): ListZonePrice {
  const fallbackSales = toFiniteNumber(sales) ?? 0
  return {
    badgeKind: '',
    guideText: null,
    mainPrice: fallbackSales,
    priceTagText: null,
    socialProofKind: null,
    socialProofText: null,
    strikePrice: null,
  }
}

/** 角标文案（协议价/特供价/会员价/捡漏价/秒杀价/新人价/阶梯价）；无角标返回空串 */
export function zonePriceBadgeText(kind: ZonePriceBadgeKind): string {
  if (kind === 'policy')
    return translate('labels.policyPriceBadge') || '协议价'
  if (kind === 'special')
    return translate('labels.specialPriceBadge') || '档期特供'
  if (kind === 'member')
    return translate('labels.memberPriceBadge') || '会员价'
  if (kind === 'clearance')
    return translate('product.clearancePrice') || '捡漏价'
  if (kind === 'seckill')
    return translate('labels.seckillPriceBadge') || '秒杀价'
  if (kind === 'newbie')
    return translate('labels.newbiePriceBadge') || '新人首单价'
  if (kind === 'ladder')
    return translate('labels.ladderPriceBadge') || '阶梯价'
  return ''
}

/**
 * 解析列表卡片的专区价展示。
 *
 * 入参都做防御校验：接口脏数据（非数值/0/负数/倒挂）一律视为未命中，
 * 绝不把原价标成会员价/捡漏价。
 */
export function resolveListZonePrice(input: ListZonePriceInput): ListZonePrice {
  const sales = toFiniteNumber(input.salesPrice)
  const showStrike = input.showOriginal !== false
  if (sales === null || sales <= 0)
    return emptyListZonePrice(sales ?? 0)

  // 社证文案与价格声明都与价格判定无关（服务端已成文），透传到所有展示组合
  const socialProofText = sanitizeProofText(input.socialProofText)
  const socialProofKind = sanitizeProofKind(input.socialProofKind)
  const priceTagText = sanitizeProofText(input.priceTagText)

  const member = input.memberDisplay
  const memberPrice = member?.showMemberPrice
    ? validLowerPrice(member.memberPrice, sales)
    : null

  const clearance = input.clearanceDisplay
  const clearancePrice = clearance?.showClearancePrice
    ? validLowerPrice(clearance.clearancePrice, sales)
    : null

  // 协议价（合同价）：引擎锁定成交基价，公开活动价不可覆盖——最高优先级，命中即终态
  const policyPrice = validLowerPrice(input.policyPrice, sales)
  if (policyPrice !== null) {
    return {
      badgeKind: 'policy',
      guideText: null,
      mainPrice: policyPrice,
      priceTagText,
      socialProofKind,
      socialProofText,
      strikePrice: showStrike ? roundToCent(sales) : null,
    }
  }

  // 秒杀是平台级硬互斥活动：结算价即秒杀价，命中即终态，不再比会员/出清/新人价
  const seckillPrice = validLowerPrice(input.seckillPrice, sales)
  if (seckillPrice !== null) {
    return {
      badgeKind: 'seckill',
      guideText: null,
      mainPrice: seckillPrice,
      priceTagText,
      socialProofKind,
      socialProofText,
      strikePrice: showStrike ? roundToCent(sales) : null,
    }
  }

  // 身份价家族取低：特供 / 会员 / 出清（同价归属按入队顺序：特供 > 会员 > 出清，与后端引擎一致）
  const specialPrice = validLowerPrice(input.specialPrice, sales)
  const family: Array<{ kind: ZonePriceBadgeKind, price: number }> = []
  if (specialPrice !== null)
    family.push({ kind: 'special', price: specialPrice })
  if (memberPrice !== null)
    family.push({ kind: 'member', price: memberPrice })
  if (clearancePrice !== null)
    family.push({ kind: 'clearance', price: clearancePrice })
  let identity: { kind: ZonePriceBadgeKind, price: number } | null = null
  for (const candidate of family) {
    if (!identity || candidate.price < identity.price)
      identity = candidate
  }

  // 阶梯价（商详按当前数量取档）：严格低于身份价才接管，同价保留身份角标
  const ladderPrice = validLowerPrice(input.ladderPrice, sales)
  if (ladderPrice !== null && (!identity || ladderPrice < identity.price))
    identity = { kind: 'ladder', price: ladderPrice }

  // 新人价严格低于身份价家族成交价才覆盖，同价身份价保留——
  // ¥1 新人钩子不再被全店会员价吞掉，卡价仍等于结算到手价
  const newbiePrice = validLowerPrice(input.newbiePrice, sales)
  const identityPrice = identity?.price ?? null
  if (newbiePrice !== null && (identityPrice === null || newbiePrice < identityPrice)) {
    return {
      badgeKind: 'newbie',
      guideText: null,
      mainPrice: newbiePrice,
      priceTagText,
      socialProofKind,
      socialProofText,
      strikePrice: showStrike ? roundToCent(sales) : null,
    }
  }

  if (identity) {
    // 会员价划线取会员展示的划线基准（后端回填的当前售价）；其余身份价划当前售价
    const memberStrike = identity.kind === 'member' ? toFiniteNumber(member?.strikePrice) : null
    const strike = memberStrike !== null && memberStrike > identity.price
      ? roundToCent(memberStrike)
      : roundToCent(sales)
    return {
      badgeKind: identity.kind,
      guideText: null,
      mainPrice: identity.price,
      priceTagText,
      socialProofKind,
      socialProofText,
      strikePrice: showStrike ? strike : null,
    }
  }

  // 未命中身份价：保持售价；普通划线遵循「原价 > 售价」的既有口径
  const original = toFiniteNumber(input.originalPrice)
  const strikePrice = showStrike && original !== null && original > sales
    ? roundToCent(original)
    : null
  const guide = member?.guideText
  return {
    badgeKind: '',
    guideText: guide && guide.trim() ? guide.trim() : null,
    mainPrice: sales,
    priceTagText,
    socialProofKind,
    socialProofText,
    strikePrice,
  }
}

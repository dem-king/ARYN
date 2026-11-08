/**
 * 商品列表卡片的「专区价」展示契约：会员价 / 捡漏价统一收敛。
 *
 * 背景：会员专区、低价捡漏专区展示了会员价/捡漏价，同一商品出现在其它页面
 * （首页其它 Tab、分类、搜索、收藏、足迹、推荐流……）时若只显示原售价，
 * 用户会看到同一个商品两种价格。各页面各写一遍判定必然漂移，因此把
 * 「会员价 vs 出清价 vs 原价」的卡片展示口径收敛到这里。
 *
 * **价格优先级（与后端 `OrderPriceComputeService` / 促销引擎一致，2026-11-08 起身份价取低）**：
 * 拼团 > 秒杀 > {会员价 / 出清价 / 新人价 三者取低（同价归属先到者，新人须严格更低才覆盖）} > 限时折扣 > 原价。
 * 秒杀是平台级硬互斥活动（结算不可叠任何活动价），命中即终态。拼团不承载在列表卡（有独立会场）：
 *
 * 1. 秒杀价命中：主价=秒杀价，划线=当前售价，角标「秒杀价」；
 * 2. 新人价严格低于会员/出清成交价：主价=新人价，划线=当前售价，角标「新人价」（进价格行 badge，与会员/秒杀/捡漏同位）；
 * 3. 出清价胜出：主价=出清价，划线=当前售价，角标「捡漏价」；
 * 4. 会员价胜出：主价=会员价，划线=会员价展示的划线基准（缺省为当前售价），角标「会员价」；
 * 5. 都未命中：主价=当前售价；未达标/未登录时保留「会员专享 ¥x」引导文案，
 *    普通划线仍按「原价 > 售价」口径（与既有楼层一致）。
 *
 * 出清价与会员价取低而不是叠加，且同价时会员优先——与后端
 * 「同 SKU 两者都命中取低者、同价优先会员价」同一口径；展示与结算不漂移。
 */

import type { ListMemberPriceDisplay } from '../components/diy/retail-types'
import type { ClearancePriceDisplay } from './clearance-price'

import { translate } from '@/locales'

/** 角标类型：空串=无角标，由调用方映射成 i18n 文案 */
export type ZonePriceBadgeKind = '' | 'clearance' | 'member' | 'seckill' | 'newbie'

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

/** 角标文案（会员价/捡漏价/秒杀价/新人价）；无角标返回空串 */
export function zonePriceBadgeText(kind: ZonePriceBadgeKind): string {
  if (kind === 'member')
    return translate('labels.memberPriceBadge') || '会员价'
  if (kind === 'clearance')
    return translate('product.clearancePrice') || '捡漏价'
  if (kind === 'seckill')
    return translate('labels.seckillPriceBadge') || '秒杀价'
  if (kind === 'newbie')
    return translate('labels.newbiePriceBadge') || '新人首单价'
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

  // 出清价与会员价取低；同价归属会员价（与后端引擎一致）
  const clearanceWins = clearancePrice !== null
    && (memberPrice === null || clearancePrice < memberPrice)

  // 身份价取低（与结算引擎 2026-11-08 同口径）：新人价严格低于会员/出清成交价才覆盖，
  // 同价身份价保留——¥1 新人钩子不再被全店会员价吞掉，卡价仍等于结算到手价
  const newbiePrice = validLowerPrice(input.newbiePrice, sales)
  const identityPrice = clearanceWins ? clearancePrice : memberPrice
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

  if (clearanceWins) {
    return {
      badgeKind: 'clearance',
      guideText: null,
      mainPrice: clearancePrice,
      priceTagText,
      socialProofKind,
      socialProofText,
      strikePrice: showStrike ? roundToCent(sales) : null,
    }
  }

  if (memberPrice !== null) {
    const memberStrike = toFiniteNumber(member?.strikePrice)
    const strike = memberStrike !== null && memberStrike > memberPrice
      ? roundToCent(memberStrike)
      : roundToCent(sales)
    return {
      badgeKind: 'member',
      guideText: null,
      mainPrice: memberPrice,
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

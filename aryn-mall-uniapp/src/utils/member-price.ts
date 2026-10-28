/**
 * 会员专享价的展示口径（纯函数，便于单测）。
 *
 * 背景与新人价/限时折扣同源：商详、购物车拿到的是 SKU 当前售价，而会员价
 * 由 `/promotion/app/member/prices`（可选登录）下发。若各页面各写一遍判定，
 * 很快会出现「商详标会员价、购物车还是原价」的漂移，因此统一收敛到这里。
 *
 * **聚合优先级（与后端 `OrderPriceComputeService` 一致）**：
 * 拼团 > 秒杀 > 会员价 > 新人价 > 限时折扣 > 原价。
 * 会员价是身份权益，优先于一次性拉新的新人价与常态限时折扣；拼团/秒杀属
 * 稀缺场景仍压会员价。拼团价由后端按拼团记录重算、商详/购物车不参与展示，
 * 故前端只覆盖「秒杀 > 会员价 > 新人价 > 限时折扣」四层，由
 * `resolvePromotionPriority` 一次性收敛，页面只读聚合结果。
 *
 * **资格三态**（与后端 `AppMemberPriceVO.eligible` 一一对应）：
 * - `true`  ：等级达标 → 主价切会员价 + 「会员价」角标；
 * - `false` ：已登录但等级不达标 → 保持原价，可给「会员专享 ¥x」引导；
 * - `null`  ：未登录 → 保持原价，同样可给引导文案（后端对未登录也下发活动价）。
 *
 * **引导文案不带等级名**：会员价活动的等级门槛最低档就是「入会即得」的档位
 * （门槛 0），对「登录未入会 / 未登录」用户把等级名拼进引导句，既像把入门权益
 * 说成新客专属，也把租户可配的等级名当成固定文案；高等级门槛对未达标用户
 * 只是噪声（会员中心「等级权益」页有完整等级表）。统一「会员专享 ¥x」，
 * 与权益条「本区享会员专享价」同一「会员」口径；`minLevelName` 仍保留在
 * `MemberPriceDisplay` 里供角标/埋点使用。
 *
 * **达标展示价 = min(会员价, round2(当前售价 × 会员折扣率))**：
 * 会员价（活动价）与会员折扣率（全场身份权益）是两套定价体系，结算按两者取优
 * 而不是叠加（不是「会员价再打折」）；展示必须同步取优，否则会出现
 * 「页面会员价、结算折扣价」的漂移。未达标/未登录的引导价不取优（营销展示口径）。
 *
 * 划线价口径不变：只有主价是真的更低（`originalPrice > memberPrice`）才切换与划线，
 * 与 `diy/price-display.ts` 的 `shouldShowOriginalPrice` 同族。
 */

import type { AppMemberPriceVO, AppMemberSkuPriceVO } from '@/api/promotion'
// 列表卡片的会员价聚合结果类型是「卡片展示契约」，与商品项类型同放 retail-types；
// 这里只做 type-only 引用（不引入组件运行时依赖）
import type { ListMemberPriceDisplay } from '../components/diy/retail-types'

import { translate } from '@/locales'

import type { DiscountPriceDisplay } from './discount-price'
import type { NewbiePriceDisplay } from './newbie-price'
import { emptyDiscountDisplay, formatStripAmount } from './discount-price'
import { emptyNewbieDisplay } from './newbie-price'

/** 会员价角标文案（商详/购物车同款口径） */
export const MEMBER_PRICE_BADGE_TEXT = '会员价'

/** 引导文案统一前缀：不拼等级名（等级名是租户可配数据，且最低档=入会即得） */
export const MEMBER_GUIDE_PREFIX = '会员专享'

/** 某个 SKU 的会员价展示结果 */
export interface MemberPriceDisplay {
  /** 是否命中会员价活动（该 SKU 在活动配置里） */
  matched: boolean
  /** 主价是否应切为会员价（仅资格达标且会员价真的更低） */
  showMemberPrice: boolean
  /** 展示用会员价（达标时为取优后价格：min(会员价, 售价×折扣率)）；未命中/不展示时为 null */
  memberPrice: number | null
  /** 划线基准价（当前售价）；不展示会员价/无意义划线时为 null */
  originalPrice: number | null
  /** 角标文案：达标时「会员价」，否则空串 */
  badgeText: string
  /** 未达标/未登录的引导文案「会员专享 ¥x」；无可引导信息时为 null */
  guideText: string | null
  /** 资格三态：true 达标 / false 已登录但不达标 / null 未登录 */
  eligible: boolean | null
  /** 活动配置的等级门槛名称（角标/埋点用；引导文案已统一「会员专享」，不再拼它） */
  minLevelName: string
  /** 当前用户等级名称；未登录为空串 */
  userLevelName: string
}

/** 命中的会员价商品项（已归一化为数字） */
export interface MemberSkuPriceItem {
  skuId: string
  spuId: string
  memberPrice: number
  originalPrice: number
  minLevelId: string
  minLevelName: string
}

function toFiniteNumber(value: unknown): number | null {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

function toText(value: unknown): string {
  return value === null || value === undefined ? '' : String(value).trim()
}

/** 未命中时的空展示结果（调用方直接沿用商品原价，无需判空） */
export function emptyMemberPriceDisplay(): MemberPriceDisplay {
  return {
    badgeText: '',
    eligible: null,
    guideText: null,
    matched: false,
    memberPrice: null,
    minLevelName: '',
    originalPrice: null,
    showMemberPrice: false,
    userLevelName: '',
  }
}

/**
 * 命中的会员价商品表：skuId → 价格项。
 *
 * 页面一次请求会带上多个 SKU（商详多规格、购物车整车），逐条 find 是 O(n²)；
 * 同一 SKU 在响应里重复时后者覆盖前者，行为确定。
 */
export function memberSkuPriceMap(
  vo: AppMemberPriceVO | null | undefined,
): Map<string, MemberSkuPriceItem> {
  const map = new Map<string, MemberSkuPriceItem>()
  if (!vo?.items?.length)
    return map
  for (const item of vo.items) {
    const skuId = toText(item?.skuId)
    if (!skuId)
      continue
    const memberPrice = toFiniteNumber(item.memberPrice)
    if (memberPrice === null || memberPrice <= 0)
      continue
    const originalPrice = toFiniteNumber(item.originalPrice)
    map.set(skuId, {
      memberPrice,
      minLevelId: toText(item.minLevelId),
      minLevelName: toText(item.minLevelName),
      originalPrice: originalPrice === null ? 0 : originalPrice,
      skuId,
      spuId: toText(item.spuId),
    })
  }
  return map
}

/**
 * 引导文案：`会员专享 ¥x`（金额去无意义尾零）。
 *
 * 不再拼接 `minLevelName`：见文件头「引导文案不带等级名」。价格非法时返回空串
 * （不造 ¥0），调用方据此隐藏整行。
 */
export function memberPriceGuideText(memberPrice: unknown): string {
  const price = toFiniteNumber(memberPrice)
  if (price === null || price <= 0)
    return ''
  const prefix = translate('labels.memberGuidePrefix') || MEMBER_GUIDE_PREFIX
  return `${prefix} ¥${formatStripAmount(price)}`
}

/** 资格三态归一化：只有明确的 true/false 才落两端，其余（含 undefined）视为未登录/未知 */
function normalizeEligible(value: unknown): boolean | null {
  if (value === true)
    return true
  if (value === false)
    return false
  return null
}

/**
 * 会员折扣率归一化：null/非数值、≤0、>1 一律视为 1（无折扣）。
 * 只有 (0,1) 区间内才参与「展示取优」。
 */
export function normalizeMemberDiscountRate(rate: unknown): number {
  const value = toFiniteNumber(rate)
  if (value === null || value <= 0 || value > 1)
    return 1
  return value
}

/** 金额按分四舍五入：折扣率折后价与后端 BigDecimal 的展示口径对齐 */
export function roundMemberPriceToCent(value: number): number {
  return Math.round(value * 100) / 100
}

/**
 * 达标会员的展示价：会员价与「当前售价 × 会员折扣率」取较低者。
 *
 * **是取优不是叠加**：会员价（活动价）与会员折扣率（全场身份权益）是两套定价体系，
 * 用户实际只按其中更低的那个成交；这里不做「会员价再打折」的乘法。
 * 后端下单/结算同口径，展示取优避免「页面标一个、结算按另一个」的漂移。
 *
 * @param memberPrice   活动配置的会员价
 * @param originalPrice 当前售价（划线基准）
 * @param discountRate  会员折扣率（null/≤0/>1 视为 1=无折扣）
 * @returns 取优后的展示价；会员价非法时返回 null
 */
export function resolveBestMemberPrice(
  memberPrice: unknown,
  originalPrice: unknown,
  discountRate: unknown,
): number | null {
  const member = toFiniteNumber(memberPrice)
  if (member === null || member <= 0)
    return null
  const rate = normalizeMemberDiscountRate(discountRate)
  if (rate >= 1)
    return member
  const original = toFiniteNumber(originalPrice)
  if (original === null || original <= 0)
    return member
  return Math.min(member, roundMemberPriceToCent(original * rate))
}

/**
 * 组装某个 SKU 的会员价展示结果。
 *
 * 达标分支的展示价取优：`min(会员价, round2(当前售价 × vo.userDiscountRate))`，
 * 折扣率来自会员折扣率权益（与会员价活动两套体系，取优不叠加）；划线仍取当前售价，
 * 角标统一「会员价」（不新增第二种角标）。未达标/未登录的引导价仍用活动会员价。
 *
 * @param vo 后端返回的会员价（可为 null：无活动 / 接口失败；userDiscountRate 无效视为无折扣）
 * @param skuId 当前页面聚焦的 SKU；购物车/商详都必须按 SKU 精确匹配
 *              （同一 SPU 下不同规格可能只有一个配了会员价）
 */
export function resolveMemberPriceDisplay(
  vo: AppMemberPriceVO | null | undefined,
  skuId: unknown,
): MemberPriceDisplay {
  const empty = emptyMemberPriceDisplay()
  const target = toText(skuId)
  if (!vo || !target)
    return empty
  const hit = memberSkuPriceMap(vo).get(target)
  if (!hit)
    return empty

  const eligible = normalizeEligible(vo.eligible)
  const minLevelName = hit.minLevelName
  const userLevelName = toText(vo.userLevelName)
  // 会员价必须真的低于当前售价才切换（倒挂回退）：
  // 活动配置异常/接口兜底时「会员价 ≥ 售价」，此时保持原价，角标与引导都不出，
  // 否则会划出一个反向「优惠」。
  const hasRealDiscount = hit.originalPrice > 0 && hit.memberPrice < hit.originalPrice
  if (!hasRealDiscount) {
    return {
      ...empty,
      eligible,
      matched: true,
      memberPrice: hit.memberPrice,
      minLevelName,
      userLevelName,
    }
  }

  // 达标：主价切取优后价格（会员价 vs 会员折扣率，取较低者），划当前售价，出「会员价」角标
  if (eligible === true) {
    const effectivePrice = resolveBestMemberPrice(
      hit.memberPrice,
      hit.originalPrice,
      vo.userDiscountRate,
    ) ?? hit.memberPrice
    return {
      badgeText: translate('labels.memberPriceBadge'),
      eligible,
      guideText: null,
      matched: true,
      memberPrice: effectivePrice,
      minLevelName,
      originalPrice: hit.originalPrice,
      showMemberPrice: true,
      userLevelName,
    }
  }

  // 未达标/未登录：保持原价，只透出「会员专享 ¥x」引导（不做购买拦截）
  return {
    badgeText: '',
    eligible,
    guideText: memberPriceGuideText(hit.memberPrice),
    matched: true,
    memberPrice: hit.memberPrice,
    minLevelName,
    originalPrice: null,
    showMemberPrice: false,
    userLevelName,
  }
}

/**
 * 该 SKU 在「无秒杀、无新人价时」的实际成交单价：可享会员价时取会员价，否则回落售价。
 *
 * 行价/合计/主价都走这里，保证「标价 × 数量 = 合计」与页面标价一致。
 */
export function memberPriceUnitPrice(
  fallbackPrice: unknown,
  display: MemberPriceDisplay,
): number {
  if (display.showMemberPrice && display.memberPrice !== null)
    return display.memberPrice
  const fallback = toFiniteNumber(fallbackPrice)
  return fallback === null || fallback < 0 ? 0 : fallback
}

export interface PromotionPriorityInput {
  /** 秒杀是否进行中（商详掌握该状态；购物车未接入秒杀，传 false） */
  seckill?: boolean
  member: MemberPriceDisplay
  newbie: NewbiePriceDisplay
  discount: DiscountPriceDisplay
}

export interface PromotionPriorityResult {
  member: MemberPriceDisplay
  newbie: NewbiePriceDisplay
  discount: DiscountPriceDisplay
}

/**
 * 按「拼团 > 秒杀 > 会员价 > 新人价 > 限时折扣 > 原价」收敛三层展示：
 * 命中更高优先级时把低优先级展示清空（返回共享空展示），页面据此同一时刻
 * 只解释一个成交价，避免「页面标一个、结算按另一个」的价差观感。
 *
 * 拼团价不在前端展示层参与（后端按拼团记录重算），故入参从秒杀开始。
 */
export function resolvePromotionPriority(input: PromotionPriorityInput): PromotionPriorityResult {
  if (input.seckill) {
    return {
      discount: emptyDiscountDisplay(),
      member: emptyMemberPriceDisplay(),
      newbie: emptyNewbieDisplay(),
    }
  }
  if (input.member.showMemberPrice) {
    return {
      discount: emptyDiscountDisplay(),
      member: input.member,
      newbie: emptyNewbieDisplay(),
    }
  }
  if (input.newbie.showNewbiePrice) {
    return {
      discount: emptyDiscountDisplay(),
      member: input.member,
      newbie: input.newbie,
    }
  }
  return { discount: input.discount, member: input.member, newbie: input.newbie }
}

// ============ 列表卡片（会员专区 Tab 按 SPU 补充） ============

/** 卡片展示价 ↔ 会员价规格价 的匹配容差（价格两位小数，留半分钱浮点误差） */
export const MEMBER_LIST_PRICE_TOLERANCE = 0.005

/** 未命中时的空展示（调用方据 matched=false 判断「完全不动」） */
export function emptyListMemberPriceDisplay(): ListMemberPriceDisplay {
  return {
    badgeText: '',
    guideText: null,
    matched: false,
    memberPrice: null,
    minLevelName: '',
    showMemberPrice: false,
    strikePrice: null,
  }
}

/**
 * 按 spuId 分组会员价商品项（按 spuIds 列表取价后使用）。
 *
 * 后端对同一 SPU 的不同规格都配了会员价时会返回多条 item，不做挑选，
 * 由 `resolveListMemberPrice` 按规格价挑选。
 */
export function memberPriceItemsBySpuId(
  vo: AppMemberPriceVO | null | undefined,
): Map<string, AppMemberSkuPriceVO[]> {
  const map = new Map<string, AppMemberSkuPriceVO[]>()
  if (!vo?.items?.length)
    return map
  for (const item of vo.items) {
    const spuId = toText(item?.spuId)
    const skuId = toText(item?.skuId)
    const memberPrice = toFiniteNumber(item?.memberPrice)
    if (!spuId || !skuId || memberPrice === null || memberPrice <= 0)
      continue
    const list = map.get(spuId) || []
    list.push(item)
    map.set(spuId, list)
  }
  return map
}

/**
 * 列表卡片会员价展示判定（会员专区 Tab 专用，SPU 维度）。
 *
 * 列表接口只给 SPU 价格而会员价按 SKU 配置，必须先确认「会员价属于卡片展示价对应的
 * 那个规格」：在 items 中找 `originalPrice` 与卡片价一致（容差 0.005）的 SKU。
 * - 命中 + `eligible===true` + 会员价 < 卡片价 → 主价切会员价，划线取卡片价（与商详
 *   同口径，不再叠加 SPU originalPrice 划线）；
 * - 命中 + 未达标/未登录 → 主价不动，只给「会员专享 ¥X」引导；
 * - 未命中（会员价配在更贵规格）/ 倒挂 / 无活动 → 全部不展示，绝不让「会员价 >
 *   卡片价」误导用户。
 *
 * 同一 SPU 同价规格可能多条（颜色/包装不同），取最低会员价，避免同价商品标高价。
 *
 * 达标展示价同样**取优不叠加**：`min(会员价, round2(卡片价 × 会员折扣率))`，
 * 划线仍取卡片价；未达标/未登录的引导价不取优（营销展示口径）。
 *
 * @param items     该 SPU 在 `/app/member/prices` 的 item 列表（后端已按 spuIds 过滤）
 * @param spuId     列表项 SPU ID
 * @param cardPrice 列表卡片当前展示价（SPU salesPrice）
 * @param eligible  资格三态（true 达标 / false 已登录不达标 / null 未登录）
 * @param userDiscountRate 当前用户会员折扣率（`vo.userDiscountRate`；null/无效视为无折扣）
 */
export function resolveListMemberPrice(
  items: AppMemberSkuPriceVO[] | null | undefined,
  spuId: unknown,
  cardPrice: unknown,
  eligible: boolean | null | undefined,
  userDiscountRate: unknown = null,
): ListMemberPriceDisplay {
  const empty = emptyListMemberPriceDisplay()
  const target = toText(spuId)
  const card = toFiniteNumber(cardPrice)
  if (!target || card === null || card <= 0 || !items?.length)
    return empty

  let hit: { memberPrice: number, minLevelName: string } | null = null
  for (const item of items) {
    // 只认请求到的 SPU：后端按 spuIds 过滤，这里再兜一层防串价
    if (toText(item?.spuId) !== target)
      continue
    const specPrice = toFiniteNumber(item?.originalPrice)
    const memberPrice = toFiniteNumber(item?.memberPrice)
    if (specPrice === null || memberPrice === null || memberPrice <= 0)
      continue
    if (Math.abs(specPrice - card) > MEMBER_LIST_PRICE_TOLERANCE)
      continue
    if (!hit || memberPrice < hit.memberPrice)
      hit = { memberPrice, minLevelName: toText(item?.minLevelName) }
  }
  if (!hit)
    return empty

  const matched: ListMemberPriceDisplay = {
    ...empty,
    matched: true,
    memberPrice: hit.memberPrice,
    minLevelName: hit.minLevelName,
  }
  // 倒挂 / 无优惠：会员价不低于卡片价时一律不展示（防「会员价 > 卡片价」误导）
  if (hit.memberPrice >= card)
    return matched
  if (eligible === true) {
    // 取优不叠加：会员价与「卡片价 × 会员折扣率」取较低者；划线仍取卡片价
    const effectivePrice = resolveBestMemberPrice(hit.memberPrice, card, userDiscountRate)
      ?? hit.memberPrice
    return {
      ...matched,
      badgeText: translate('labels.memberPriceBadge'),
      memberPrice: effectivePrice,
      showMemberPrice: true,
      strikePrice: card,
    }
  }
  // 未达标/未登录：主价不动，只透出与商详同源的引导文案
  return {
    ...matched,
    guideText: memberPriceGuideText(hit.memberPrice),
  }
}

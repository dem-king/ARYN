/**
 * 多 Tab 商品流「会员权益条」的展示口径（纯函数，便于单测）。
 *
 * 需求：goods-tabs 每个 Tab 可配一条会员权益横幅（默认关），开启后按用户态三态展示：
 * - 会员（有等级）：「您已是 {levelName}，本区享会员专享价」→ 无动作；
 * - 登录但无等级：游客文案 → 跳会员中心；
 * - 未登录：游客文案 → 跳登录。
 *
 * **会员态文案只用确定性标签，不写「您享 X 折」**（竞品调研结论：PLUS 价 / X 会员价 /
 * 88VIP 95 折这类确定性利益点；折扣数与本区会员价活动是两套体系，写进横幅会导致
 * 「横幅 8.5 折、专区实价 9 折」的错觉）。
 *
 * 文案占位符：`{levelName}` 等级名；`{discount}` 是**会员折扣率权益**（全场身份折扣，
 * 0.9 → 9、0.95 → 9.5），与本区「会员价活动」是两套体系、**默认文案不再引用**，
 * 仅为运营自定义文案保留（可能已配置），替换值按 benefits.discountRate 计算；
 * 兼容中文写法 `{等级}` / `{折扣}`（管理端占位示例里出现的中文占位）。
 * 会员态自定义文案若含折扣占位但当前等级没有折扣率权益，无法填值，回落内置文案；
 * 游客态没有等级，占位符一律按空值替换后收敛空白。
 *
 * 只做权益**透出**，不做购买拦截；会员信息接口失败 / 未配置 / 未启用时
 * 组件侧一律不渲染横幅（本模块只负责「能渲染时渲染成什么样」）。
 */

import type { GoodsTabMemberBanner } from './retail-types'

import { translate } from '@/locales'

/*
 * 内置文案统一放在 `diy.memberBanner.*` 词条里，渲染时经 translate 求值。
 *
 * 会员态文案是确定性标签（两态合一）：有/无折扣率权益都用同一句，承诺的是
 * 「会员专享价」（会员价活动 + 会员折扣率展示取优后的确定性成交价），不依赖
 * 折扣率数字；等级名自带「会员」后缀也无妨（渲染时只做相邻「会员」去重）。
 */

/** 未登录点击横幅的去向 */
export const MEMBER_BANNER_LOGIN_URL = '/pages/login/index'

/**
 * 会员中心路由。
 *
 * 会员中心是分包页面（pages.json 的 `sub-pages` 下 `user/member/index`，
 * 页面 name 为 `member-center`），跳转必须用完整分包路径。
 */
export const MEMBER_BANNER_MEMBER_CENTER_URL = '/sub-pages/user/member/index'

export type MemberBannerState = 'guest' | 'member'
export type MemberBannerAction = 'login' | 'member-center' | 'none'

/** 用户会员概况（来自 `/mall-user/app/member/current-info` + `/benefits`） */
export interface MemberBannerProfile {
  isLoggedIn: boolean
  levelId: string
  levelName: string
  /**
   * 会员折扣率权益（benefits.discountRate，全场身份折扣）；0 或缺失表示该等级无折扣权益。
   * 仅自定义文案的 `{discount}` 占位使用，内置默认文案不再依赖它。
   */
  discountRate: number | null
}

export interface MemberBannerView {
  /** 是否渲染横幅（enabled 关时为 false） */
  visible: boolean
  state: MemberBannerState
  /** 最终展示文案（占位符已替换） */
  text: string
  /** 点击动作：会员态无动作 */
  action: MemberBannerAction
}

/**
 * 折扣率 → 「x 折」里的 x（0.9 → `9`、0.95 → `9.5`）。
 *
 * **只服务于运营自定义文案里的 `{discount}` 占位**（会员折扣率权益，全场身份折扣）；
 * 与本区会员价活动是两套体系，取优逻辑在 `utils/member-price.ts`。
 * 与会员中心页同口径（`(rate * 10).toFixed(1)` 去尾零）；≥1 或 ≤0 视为无折扣，
 * 返回空串，由调用方省略该分句而不是展示「10 折」。
 */
export function formatMemberDiscountZhe(rate: unknown): string {
  const value = Number(rate)
  if (!Number.isFinite(value) || value <= 0 || value >= 1)
    return ''
  const zhe = Math.round(value * 100) / 10
  return Number.isInteger(zhe) ? String(zhe) : zhe.toFixed(1)
}

/**
 * 占位符替换：支持的变量按值替换，其余（如游客态的 `{levelName}`）替换为空串。
 *
 * 官方 token 为 `{levelName}` / `{discount}`；同时兼容管理端文案示例里可能出现的中文
 * 占位 `{等级}` / `{折扣}`（运营直接拷示例文案时不会把占位符原样透出给用户）。
 */
export function formatMemberBannerCopy(
  template: unknown,
  vars: { levelName?: string, discount?: string } = {},
): string {
  return String(template ?? '')
    .replace(/\{levelName\}|\{等级\}/g, vars.levelName ?? '')
    .replace(/\{discount\}|\{折扣\}/g, vars.discount ?? '')
    .replace(/\s+/g, ' ')
    .trim()
}

/** 自定义会员文案是否引用了「折扣」占位（两种写法都算） */
function needsDiscountValue(copy: string): boolean {
  return copy.includes('{discount}') || copy.includes('{折扣}')
}

function normalizeCopy(value: unknown): string {
  return String(value ?? '').trim()
}

/**
 * 收敛「会员」重复：本系统等级名普遍自带「会员」后缀（银卡会员/金卡会员/钻石会员），
 * 运营自定义文案若又拼了「{levelName} 会员」，直接拼会得到「金卡会员 会员」。
 * 这里只去重相邻的两次「会员」，不改变其它措辞。
 */
function tidyMemberText(text: string): string {
  return text.replace(/会员\s*会员/g, '会员')
}

/**
 * 解析某个 Tab 的会员权益条展示结果。
 *
 * @param config   Tab 级横幅配置（enabled/memberCopy/guestCopy）
 * @param profile  当前用户会员概况；isLoggedIn 且 levelId 非空才算「会员」
 */
export function resolveMemberBannerView(
  config: GoodsTabMemberBanner | null | undefined,
  profile: MemberBannerProfile,
): MemberBannerView {
  if (!config?.enabled) {
    return { action: 'none', state: 'guest', text: '', visible: false }
  }

  const isMember = Boolean(profile.isLoggedIn && normalizeCopy(profile.levelId))
  if (isMember) {
    const levelName = normalizeCopy(profile.levelName) || translate('diy.memberBanner.memberFallbackName')
    // 折扣率只在运营自定义了 {discount}/{折扣} 时参与替换；内置文案不引用它
    const discount = formatMemberDiscountZhe(profile.discountRate)
    const custom = normalizeCopy(config.memberCopy)
    // 自定义文案引用了 {discount} 但该等级无折扣权益：填不出值，回落内置文案，
    // 避免出现「本区享  折」这种半截话。
    const customUsable = Boolean(custom) && !(needsDiscountValue(custom) && !discount)
    // 内置文案按当前语言取词条（调用时求值），等级名经词条插值填入。
    const text = customUsable
      ? formatMemberBannerCopy(custom, { discount, levelName })
      : translate('diy.memberBanner.memberCopy', { levelName })
    return {
      action: 'none',
      state: 'member',
      text: tidyMemberText(text),
      visible: true,
    }
  }

  const builtinGuestCopy = translate('diy.memberBanner.guestCopy')
  const customGuest = normalizeCopy(config.guestCopy)
  const text = customGuest
    ? formatMemberBannerCopy(customGuest)
    : builtinGuestCopy
  return {
    action: profile.isLoggedIn ? 'member-center' : 'login',
    state: 'guest',
    text: text || builtinGuestCopy,
    visible: true,
  }
}

/**
 * 会员介绍弹层的内容口径（纯数据 + 纯函数，便于单测）。
 *
 * 背景：首页多 Tab 商品流「会员专区」顶部的权益条文案是「入会享会员价与专属券」，
 * 右侧动作「了解会员」原先直接整页跳会员中心——用户只是想先知道「会员是什么、
 * 我能得到什么、怎么升级」，一次整页跳转既打断浏览也解释不了卡片上的
 * 「会员专享 ¥x」。改为先底部弹层介绍，想深入（看等级权益、积分、储值）
 * 再进会员中心。
 *
 * 文案约束（都是踩过的坑）：
 * - **不写死等级名 / 等级档数 / 门槛金额**：`member_level` 是租户可配置数据，
 *   写死「新客→银卡→金卡」会在另一个租户串味，门槛也会随运营调整过期；
 * - **不承诺「付费开通」**：本系统会员等级按累计消费自动评定，没有付费开通动作，
 *   文案只说「累计消费后自动评定、无需申请」；
 * - **会员价只做确定性标签描述、不写折数**：会员价活动与会员折扣率是两套体系，
 *   写「享 X 折」会与商品实价对不上（见 `components/diy/member-banner.ts` 同款结论）；
 * - 卡片引导价已统一为「会员专享 ¥x」（见 `utils/member-price.ts`），弹层用同款措辞
 *   解释这个标签从哪来、怎么生效，形成「列表标签 → 弹层说明 → 会员中心」的闭环。
 *
 * 中英双语：本文件只导出 i18n key 与在调用时求值的纯函数（`translate`），
 * 组件用 `t()` 渲染 key，切语言实时刷新；不在模块顶层固化任何文案。
 */

import { translate } from '@/locales'

/** 弹层标题：品牌名 + 会员身份，和会员中心页头一致 */
export const MEMBER_INTRO_TITLE_KEY = 'promotion.memberIntro.title'

/**
 * 弹层副标题：与首页权益条游客态内置文案同一句，同一入口前后措辞不漂移。
 *
 * 权益条文案由装修批次维护，这里按同一文案落一份词条，
 * 避免英文环境弹层副标题回落到中文。
 */
export const MEMBER_INTRO_SUBTITLE_KEY = 'promotion.memberIntro.subtitle'

/** 会员能得到的四类权益（与后端 member_benefit 的五类权益同源，按用户可感知口径归并） */
export interface MemberIntroBenefit {
  /** 稳定 key：渲染列表与埋点用，不随文案变化 */
  key: string
  /** 权益名 key（≤6 字，宫格两行内放得下） */
  titleKey: string
  /** 一句话说明 key，不写具体折数/金额（租户可配） */
  descKey: string
}

export const MEMBER_INTRO_BENEFITS: readonly MemberIntroBenefit[] = [
  {
    descKey: 'promotion.memberIntro.benefits.price.desc',
    key: 'price',
    titleKey: 'promotion.memberIntro.benefits.price.title',
  },
  {
    descKey: 'promotion.memberIntro.benefits.coupon.desc',
    key: 'coupon',
    titleKey: 'promotion.memberIntro.benefits.coupon.title',
  },
  {
    descKey: 'promotion.memberIntro.benefits.points.desc',
    key: 'points',
    titleKey: 'promotion.memberIntro.benefits.points.title',
  },
  {
    descKey: 'promotion.memberIntro.benefits.privilege.desc',
    key: 'privilege',
    titleKey: 'promotion.memberIntro.benefits.privilege.title',
  },
]

/** 会员成长三步（勿写具体门槛值：门槛是租户配置） */
export interface MemberIntroStep {
  key: string
  /** 步骤名 key（≤5 字） */
  titleKey: string
  /** 一句话说明 key */
  descKey: string
}

export const MEMBER_INTRO_STEPS: readonly MemberIntroStep[] = [
  {
    descKey: 'promotion.memberIntro.steps.login.desc',
    key: 'login',
    titleKey: 'promotion.memberIntro.steps.login.title',
  },
  {
    descKey: 'promotion.memberIntro.steps.consume.desc',
    key: 'consume',
    titleKey: 'promotion.memberIntro.steps.consume.title',
  },
  {
    descKey: 'promotion.memberIntro.steps.upgrade.desc',
    key: 'upgrade',
    titleKey: 'promotion.memberIntro.steps.upgrade.title',
  },
]

/** 会员专享价的解释条：把商品卡上的引导文案与弹层内容接上 */
export const MEMBER_INTRO_PRICE_NOTE_KEY = 'promotion.memberIntro.priceNote'

/** 主按钮文案（会员中心承载等级权益、积分、储值等完整信息） */
export const MEMBER_INTRO_CTA_TEXT_KEY = 'promotion.memberIntro.cta'

/** 次要关闭文案（比「取消」更贴合「先看看、不打断浏览」的语境） */
export const MEMBER_INTRO_DISMISS_TEXT_KEY = 'promotion.memberIntro.dismiss'

/** 当前用户会员态（只影响状态行文案，不影响权益介绍本身） */
export type MemberIntroState = 'guest' | 'member'

/** 弹层状态行展示结果 */
export interface MemberIntroView {
  state: MemberIntroState
  /** 状态主文案：游客「当前状态：未入会」/ 会员「当前等级：金卡会员」 */
  statusText: string
  /** 状态辅助文案：解释下一步怎么走 */
  statusHint: string
  /** 主按钮文案 */
  ctaText: string
}

export interface MemberIntroProfile {
  /** 是否是已有等级的会员（`current-info` 的 levelId 非空） */
  isMember?: boolean
  /** 当前等级名；等级名是租户数据，缺失时不拼接 */
  levelName?: string
}

function normalizeText(value: unknown): string {
  return value === null || value === undefined ? '' : String(value).trim()
}

/**
 * 解析弹层状态行。
 *
 * 会员态读真实等级名（缺省回落「会员」，不编造等级）；
 * 游客态统一「未入会 + 累计消费后自动评定，无需申请」，
 * 不写「立即开通」——系统没有付费开通动作，等级由累计消费自动评定。
 * 文案在调用时翻译，组件 computed 依赖当前语言，切换后实时刷新。
 */
export function resolveMemberIntroView(profile?: MemberIntroProfile | null): MemberIntroView {
  const levelName = normalizeText(profile?.levelName)
  if (profile?.isMember === true) {
    return {
      ctaText: translate(MEMBER_INTRO_CTA_TEXT_KEY),
      state: 'member',
      statusHint: translate('promotion.memberIntro.statusHintMember'),
      statusText: translate('promotion.memberIntro.statusMember', {
        levelName: levelName || translate('promotion.memberIntro.memberFallback'),
      }),
    }
  }
  return {
    ctaText: translate(MEMBER_INTRO_CTA_TEXT_KEY),
    state: 'guest',
    statusHint: translate('promotion.memberIntro.statusHintGuest'),
    statusText: translate('promotion.memberIntro.statusGuest'),
  }
}

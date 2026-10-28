import { describe, expect, it } from 'vitest'

import { translate } from '@/locales'

import {
  MEMBER_INTRO_BENEFITS,
  MEMBER_INTRO_CTA_TEXT_KEY,
  MEMBER_INTRO_DISMISS_TEXT_KEY,
  MEMBER_INTRO_PRICE_NOTE_KEY,
  MEMBER_INTRO_STEPS,
  MEMBER_INTRO_SUBTITLE_KEY,
  MEMBER_INTRO_TITLE_KEY,
  resolveMemberIntroView,
} from './member-intro'

/** 弹层全部文案（当前语言）：口径约束是「文案本身」，所以按词条求值后统一检查 */
const introCopy = [
  translate(MEMBER_INTRO_TITLE_KEY),
  translate(MEMBER_INTRO_SUBTITLE_KEY),
  translate(MEMBER_INTRO_PRICE_NOTE_KEY),
  translate(MEMBER_INTRO_CTA_TEXT_KEY),
  translate(MEMBER_INTRO_DISMISS_TEXT_KEY),
  ...MEMBER_INTRO_BENEFITS.flatMap(item => [translate(item.titleKey), translate(item.descKey)]),
  ...MEMBER_INTRO_STEPS.flatMap(item => [translate(item.titleKey), translate(item.descKey)]),
].join(' ')

describe('resolveMemberIntroView 会员态', () => {
  it('游客态（登录但无等级 / 未登录）：未入会 + 自动评级说明，不承诺付费开通', () => {
    const view = resolveMemberIntroView({ isMember: false })

    expect(view.state).toBe('guest')
    expect(view.statusText).toBe('当前状态：未入会')
    expect(view.statusHint).toContain('累计消费后自动评定')
    expect(view.statusHint).toContain('无需申请')
    expect(view.ctaText).toBe(translate(MEMBER_INTRO_CTA_TEXT_KEY))
    // 系统没有付费开通动作：不得出现「开通会员 / 付费」这类承诺
    expect(view.statusHint).not.toContain('开通')
    expect(view.statusHint).not.toContain('付费')
  })

  it('入参缺省 / null 按游客态渲染，不抛错', () => {
    for (const input of [undefined, null, {}]) {
      expect(resolveMemberIntroView(input).state).toBe('guest')
    }
  })

  it('会员态：展示租户配置的真实等级名', () => {
    const view = resolveMemberIntroView({ isMember: true, levelName: '金卡会员' })

    expect(view.state).toBe('member')
    expect(view.statusText).toBe('当前等级：金卡会员')
    expect(view.statusHint).toContain('会员专享价')
    expect(view.ctaText).toBe(translate(MEMBER_INTRO_CTA_TEXT_KEY))
  })

  it('会员态等级名缺失时回落「会员」，不编造等级', () => {
    for (const levelName of ['', '   ', null, undefined]) {
      const view = resolveMemberIntroView({ isMember: true, levelName: levelName as any })
      expect(view.statusText).toBe('当前等级：会员')
    }
  })

  it('等级名两端空白会被清理', () => {
    expect(resolveMemberIntroView({ isMember: true, levelName: '  银卡会员 ' }).statusText)
      .toBe('当前等级：银卡会员')
  })
})

describe('弹层内容口径', () => {
  it('四类权益与成长三步齐全，key 稳定且不重复', () => {
    expect(MEMBER_INTRO_BENEFITS).toHaveLength(4)
    expect(MEMBER_INTRO_STEPS).toHaveLength(3)

    const benefitKeys = MEMBER_INTRO_BENEFITS.map(item => item.key)
    expect(new Set(benefitKeys).size).toBe(benefitKeys.length)
    // 会员价、券、积分、等级特权是会员中心「我的权益」同源的四类用户可感知权益
    expect(benefitKeys).toEqual(['price', 'coupon', 'points', 'privilege'])

    const stepKeys = MEMBER_INTRO_STEPS.map(item => item.key)
    expect(new Set(stepKeys).size).toBe(stepKeys.length)
    expect(stepKeys).toEqual(['login', 'consume', 'upgrade'])
  })

  it('每条权益/步骤都有标题与一句话说明（含英文词条）', () => {
    for (const item of [...MEMBER_INTRO_BENEFITS, ...MEMBER_INTRO_STEPS]) {
      expect(translate(item.titleKey).trim().length).toBeGreaterThan(0)
      expect(translate(item.descKey).trim().length).toBeGreaterThan(0)
    }
  })

  it('不写死等级名与门槛金额：member_level 是租户可配置数据', () => {
    // 等级名/档数是租户数据，写死会在另一个租户串味、也会随运营调整过期
    for (const levelName of ['新客会员', '银卡会员', '金卡会员', '铂金会员', '钻石会员']) {
      expect(introCopy).not.toContain(levelName)
    }
    // 门槛金额（如「满 100」「累计消费 500 元」）同理不写死
    expect(introCopy).not.toMatch(/满\s*\d/)
    expect(introCopy).not.toMatch(/\d+\s*元/)
  })

  it('会员价只做确定性标签描述，不写折数（会员价活动与会员折扣率是两套体系）', () => {
    const priceCopy = [
      translate(MEMBER_INTRO_PRICE_NOTE_KEY),
      ...MEMBER_INTRO_BENEFITS.flatMap(item => [translate(item.titleKey), translate(item.descKey)]),
    ].join(' ')

    // 权益说明里不能出现「X 折 / X.X 折」这类折数承诺
    expect(priceCopy).not.toMatch(/\d+(\.\d+)?\s*折/)
    // 但必须明确「会员专享价」这一确定性标签，与卡片引导价同口径
    expect(priceCopy).toContain('会员专享')
  })

  it('会员专享价说明与卡片引导文案对得上（会员专享 ¥xx）', () => {
    const priceNote = translate(MEMBER_INTRO_PRICE_NOTE_KEY)
    expect(priceNote).toContain('会员专享 ¥xx')
    expect(priceNote).toContain('自动生效')
  })

  it('副标题与权益条游客态文案同源，入口前后措辞不漂移', () => {
    // 首页权益条游客态内置文案与这里的词条是同一句（中文逐字一致）
    expect(translate(MEMBER_INTRO_SUBTITLE_KEY)).toBe('入会享会员价与专属券')
  })
})

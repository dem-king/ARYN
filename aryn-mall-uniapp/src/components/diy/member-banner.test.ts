import type { GoodsTabMemberBanner } from './retail-types'

import { describe, expect, it } from 'vitest'
import diyZhCN from '@/locales/modules/diy.zh-CN'
import {
  formatMemberBannerCopy,
  formatMemberDiscountZhe,
  MEMBER_BANNER_LOGIN_URL,
  MEMBER_BANNER_MEMBER_CENTER_URL,
  resolveMemberBannerView,
} from './member-banner'

/** 内置文案的词条原文（含占位符），断言中文口径时直接从词条取，避免测试再复制一份文案 */
const MEMBER_COPY = diyZhCN.diy.memberBanner.memberCopy
const GUEST_COPY = diyZhCN.diy.memberBanner.guestCopy

function config(overrides: Partial<GoodsTabMemberBanner> = {}): GoodsTabMemberBanner {
  return { enabled: true, ...overrides }
}

function profile(overrides: Partial<Parameters<typeof resolveMemberBannerView>[1]> = {}) {
  return {
    discountRate: 0.9 as number | null,
    isLoggedIn: true,
    levelId: 'level-gold',
    levelName: '黄金会员',
    ...overrides,
  }
}

describe('formatMemberDiscountZhe 折扣口径', () => {
  it('0.9 → 9、0.95 → 9.5，与会员中心页同口径', () => {
    expect(formatMemberDiscountZhe(0.9)).toBe('9')
    expect(formatMemberDiscountZhe(0.95)).toBe('9.5')
    expect(formatMemberDiscountZhe(0.88)).toBe('8.8')
    // 浮点误差收敛
    expect(formatMemberDiscountZhe(0.7)).toBe('7')
  })

  it('无折扣权益（1 / 0 / 越界 / 非数值）返回空串，永不出现「10 折」', () => {
    for (const value of [1, 1.2, 0, -0.5, Number.NaN, undefined, null])
      expect(formatMemberDiscountZhe(value)).toBe('')
  })
})

describe('formatMemberBannerCopy 占位符替换', () => {
  it('默认内置文案只替换 {levelName}（不再引用折数）并收敛多余空白', () => {
    // 默认文案是确定性标签：即便传入折扣值也不会被拼进文案
    expect(formatMemberBannerCopy(MEMBER_COPY, { discount: '9', levelName: '黄金会员' }))
      .toBe('您已是 黄金会员，本区享会员专享价')
    expect(formatMemberBannerCopy('  本区享 {discount} 折  ', { discount: '9.5' }))
      .toBe('本区享 9.5 折')
  })

  it('未提供的变量按空值替换，不残留占位符', () => {
    const text = formatMemberBannerCopy('入会享{levelName}会员价', {})
    expect(text).toBe('入会享会员价')
    expect(text).not.toContain('{')
  })

  it('兼容管理端占位示例里的中文占位 {等级}/{折扣}', () => {
    expect(formatMemberBannerCopy('您已是 {等级} 会员', { levelName: '银卡会员' }))
      .toBe('您已是 银卡会员 会员')
    expect(formatMemberBannerCopy('本区享 {折扣} 折', { discount: '9.5' }))
      .toBe('本区享 9.5 折')
    // 中文占位在会员态解析后同样生效
    const view = resolveMemberBannerView(
      config({ memberCopy: '您已是{等级}，本区享{折扣}折' }),
      profile(),
    )
    expect(view.text).toBe('您已是黄金会员，本区享9折')
  })

  it('空模板返回空串（调用方回落到内置文案）', () => {
    expect(formatMemberBannerCopy('')).toBe('')
    expect(formatMemberBannerCopy(null)).toBe('')
  })
})

describe('resolveMemberBannerView 三态渲染', () => {
  it('开关关闭：不渲染且无动作', () => {
    expect(resolveMemberBannerView({ enabled: false }, profile())).toEqual({
      action: 'none',
      state: 'guest',
      text: '',
      visible: false,
    })
    // 未配置（undefined/null）同样不渲染
    expect(resolveMemberBannerView(undefined, profile()).visible).toBe(false)
    expect(resolveMemberBannerView(null, profile()).visible).toBe(false)
  })

  it('会员态默认文案是确定性标签：含等级名、不含折数、无点击动作', () => {
    const view = resolveMemberBannerView(config(), profile())

    expect(view).toEqual({
      action: 'none',
      state: 'member',
      // 等级名原样拼入；「会员」「折率」不再参与内置文案
      text: '您已是 黄金会员，本区享会员专享价',
      visible: true,
    })
    expect(view.text).toContain('黄金会员')
    // 竞品调研结论：不写「您享 X 折」（折率与本区会员价是两套体系）
    expect(MEMBER_COPY).not.toContain('折')
    expect(view.text).not.toContain('折')
  })

  it('有/无折扣率权益两态文案一致（不再切换到第二句）', () => {
    const withDiscount = resolveMemberBannerView(config(), profile({ discountRate: 0.9 }))
    const withoutDiscount = resolveMemberBannerView(config(), profile({ discountRate: 1 }))
    const noBenefit = resolveMemberBannerView(config(), profile({ discountRate: null }))

    expect(withDiscount.text).toBe('您已是 黄金会员，本区享会员专享价')
    expect(withoutDiscount.text).toBe(withDiscount.text)
    expect(noBenefit.text).toBe(withDiscount.text)
    expect(isDeterministicMemberCopy(withDiscount.text)).toBe(true)
  })

  it('会员等级名缺失时回落「会员」，不出现空等级名', () => {
    const view = resolveMemberBannerView(config(), profile({ levelName: '' }))

    expect(view.visible).toBe(true)
    expect(view.text).toBe('您已是 会员，本区享会员专享价')
  })

  it('等级名原样拼入，自带「会员」后缀时也不重复', () => {
    const plain = resolveMemberBannerView(config(), profile({ levelName: '黄金' }))
    const suffixed = resolveMemberBannerView(config(), profile({ levelName: '黄金会员' }))

    expect(plain.text).toBe('您已是 黄金，本区享会员专享价')
    expect(suffixed.text).toBe('您已是 黄金会员，本区享会员专享价')
  })

  it('配置了 memberCopy 时用配置文案并替换占位符', () => {
    const view = resolveMemberBannerView(
      config({ memberCopy: '{levelName}专享本区 {discount} 折' }),
      profile(),
    )

    expect(view.text).toBe('黄金会员专享本区 9 折')
    expect(view.action).toBe('none')
  })

  it('memberCopy 引用 {discount} 但无折扣权益时回落内置文案（不出现半截话）', () => {
    const view = resolveMemberBannerView(
      config({ memberCopy: '本区享 {discount} 折' }),
      profile({ discountRate: null }),
    )

    // 回顾默认确定性文案（无折数）
    expect(view.text).toBe('您已是 黄金会员，本区享会员专享价')
    // 不含 {discount} 的自定义文案在无折扣时仍按配置展示
    const another = resolveMemberBannerView(
      config({ memberCopy: '{levelName}会员日' }),
      profile({ discountRate: null }),
    )
    // 等级名自带「会员」后缀 + 自配文案也带「会员」时同样去重
    expect(another.text).toBe('黄金会员日')
  })

  it('登录但无等级：游客文案 + 跳会员中心', () => {
    const view = resolveMemberBannerView(
      config(),
      profile({ discountRate: null, levelId: '', levelName: '' }),
    )

    expect(view).toEqual({
      action: 'member-center',
      state: 'guest',
      text: GUEST_COPY,
      visible: true,
    })
  })

  it('未登录：游客文案 + 跳登录（不请求会员接口由组件侧保证）', () => {
    const view = resolveMemberBannerView(
      config(),
      profile({ discountRate: null, isLoggedIn: false, levelId: '', levelName: '' }),
    )

    expect(view).toEqual({
      action: 'login',
      state: 'guest',
      text: GUEST_COPY,
      visible: true,
    })
  })

  it('配置了 guestCopy 时用配置文案；空白配置回落内置文案', () => {
    expect(resolveMemberBannerView(config({ guestCopy: '开通会员立省' }), profile({ isLoggedIn: false })).text)
      .toBe('开通会员立省')
    expect(resolveMemberBannerView(config({ guestCopy: '   ' }), profile({ isLoggedIn: false })).text)
      .toBe(GUEST_COPY)
    expect(resolveMemberBannerView(config({ guestCopy: '' }), profile({ levelId: '' })).text)
      .toBe(GUEST_COPY)
  })

  it('跳转常量指向既有登录页与会员中心分包路由', () => {
    // 会员中心是 sub-pages 分包页面（name=member-center），不能用 pages/user/... 拼
    expect(MEMBER_BANNER_LOGIN_URL).toBe('/pages/login/index')
    expect(MEMBER_BANNER_MEMBER_CENTER_URL).toBe('/sub-pages/user/member/index')
  })
})

/** 会员态内置文案必须是确定性标签（不含折数） */
function isDeterministicMemberCopy(text: string): boolean {
  return text.includes('会员专享价') && !text.includes('折')
}

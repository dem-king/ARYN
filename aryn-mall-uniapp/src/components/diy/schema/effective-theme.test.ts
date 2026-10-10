import type { PageSettings } from './types'
import { describe, expect, it } from 'vitest'
import { resolveEffectivePageTheme } from './effective-theme'

function makePage(overrides?: Partial<PageSettings>): PageSettings {
  return {
    backgroundColor: '#1543e8',
    backgroundImage: '',
    enablePullDownRefresh: true,
    navigation: {
      backgroundColor: '#1543e8',
      textColor: '#ffffff',
      title: '',
      visible: true,
    },
    share: { description: '', imageUrl: '', title: '' },
    ...overrides,
  }
}

const mallTheme = {
  navigationColor: '#FF5500',
  navigationTextColor: '#ffffff',
  pageBackgroundColor: '#FFF3EC',
}

describe('resolveEffectivePageTheme', () => {
  it('未引用主题的页面跟随商城默认主题：页面底色与导航配色被覆盖', () => {
    const effective = resolveEffectivePageTheme(makePage(), mallTheme)
    expect(effective.backgroundColor).toBe('#FFF3EC')
    expect(effective.navigationBackgroundColor).toBe('#FF5500')
    expect(effective.navigationTextColor).toBe('#ffffff')
  })

  it('页面引用过主题（theme=undefined）时保持页面自身颜色：快照已由 migrate 合并', () => {
    const effective = resolveEffectivePageTheme(makePage(), undefined)
    expect(effective.backgroundColor).toBe('#1543e8')
    expect(effective.navigationBackgroundColor).toBe('#1543e8')
    expect(effective.navigationTextColor).toBe('#ffffff')
  })

  it('页面关闭「跟随商城主题」（followMallTheme=false）时无视主题，页面自存配色直接生效', () => {
    const effective = resolveEffectivePageTheme(
      makePage({ followMallTheme: false }),
      mallTheme,
    )
    expect(effective.backgroundColor).toBe('#1543e8')
    expect(effective.navigationBackgroundColor).toBe('#1543e8')
    expect(effective.navigationTextColor).toBe('#ffffff')
  })

  it('followMallTheme 缺省或 true 均视为跟随商城主题', () => {
    expect(
      resolveEffectivePageTheme(makePage(), mallTheme).backgroundColor,
    ).toBe('#FFF3EC')
    expect(
      resolveEffectivePageTheme(makePage({ followMallTheme: true }), mallTheme)
        .backgroundColor,
    ).toBe('#FFF3EC')
  })

  it('主题字段为空串时逐字段回落页面自存值，不做整体回退', () => {
    const effective = resolveEffectivePageTheme(makePage(), {
      navigationColor: '',
      navigationTextColor: '#222222',
      pageBackgroundColor: '',
    })
    expect(effective.backgroundColor).toBe('#1543e8')
    expect(effective.navigationBackgroundColor).toBe('#1543e8')
    expect(effective.navigationTextColor).toBe('#222222')
  })

  it('只解析主题颜色字段：背景图等页面配置不参与（由调用方另行读取）', () => {
    const effective = resolveEffectivePageTheme(
      makePage({ backgroundImage: 'https://cdn.example.com/bg.png' }),
      mallTheme,
    )
    expect(effective).toEqual({
      backgroundColor: '#FFF3EC',
      navigationBackgroundColor: '#FF5500',
      navigationTextColor: '#ffffff',
    })
  })
})

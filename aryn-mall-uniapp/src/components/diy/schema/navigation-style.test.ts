import type { DecorationSection, PageSettings, SectionStyle } from './types'

import { describe, expect, it } from 'vitest'
import {
  pickReadableNavigationTextColor,
  resolveNavigationAppearance,
} from './navigation-style'

function makePage(overrides?: Partial<PageSettings>): PageSettings {
  return {
    backgroundColor: '#f5f5f5',
    backgroundImage: '',
    enablePullDownRefresh: true,
    navigation: {
      backgroundColor: '#ffffff',
      backgroundMode: 'custom',
      textColor: '#000000',
      textColorAuto: true,
      title: '',
      visible: true,
    },
    share: { description: '', imageUrl: '', title: '' },
    ...overrides,
  }
}

const theme = {
  backgroundColor: '#FFF3EC',
  navigationBackgroundColor: '#FF5500',
  navigationTextColor: '#ffffff',
}

function makeSection(style: Partial<SectionStyle>): DecorationSection {
  return {
    components: [{ id: 'component-1', props: {}, type: 'notice', version: 1 }],
    id: 'section-1',
    style: {
      backgroundColor: '',
      backgroundImage: '',
      condition: 'always',
      horizontalScroll: false,
      marginX: 0,
      marginY: 0,
      paddingX: 0,
      paddingY: 0,
      radius: 0,
      sticky: false,
      ...style,
    },
    type: 'default',
  }
}

describe('resolveNavigationAppearance', () => {
  it('自定义模式沿用有效主题导航色，不跟随区块', () => {
    const appearance = resolveNavigationAppearance(makePage(), theme, [
      makeSection({ backgroundColor: '#123456' }),
    ])
    expect(appearance).toEqual({
      backgroundColor: '#FF5500',
      textColor: '#ffffff',
    })
  })

  it('跟随首个区块：上下渐变取顶边起始色，导航栏与区块接缝同色', () => {
    const page = makePage({
      navigation: {
        ...makePage().navigation,
        backgroundMode: 'followFirstSection',
      },
    })
    const appearance = resolveNavigationAppearance(page, theme, [
      makeSection({
        backgroundGradient: {
          direction: 'to bottom',
          endColor: '#f5f7fa',
          startColor: '#1543e8',
        },
      }),
    ])
    expect(appearance.backgroundColor).toBe('#1543e8')
    expect(appearance.backgroundImage).toBeUndefined()
    // 深蓝底自动配白字
    expect(appearance.textColor).toBe('#FFFFFF')
  })

  it('跟随首个区块：左右渐变整段复用到导航栏（接缝逐列同色）', () => {
    const page = makePage({
      navigation: {
        ...makePage().navigation,
        backgroundMode: 'followFirstSection',
      },
    })
    const appearance = resolveNavigationAppearance(page, theme, [
      makeSection({
        backgroundGradient: {
          direction: 'to right',
          endColor: '#ffffff',
          startColor: '#ff5500',
        },
      }),
    ])
    expect(appearance.backgroundColor).toBe('#ff5500')
    expect(appearance.backgroundImage).toBe(
      'linear-gradient(to right, #ff5500, #ffffff)',
    )
  })

  it('跟随首个区块：纯色区块取该纯色，并按亮度自动选文字色', () => {
    const page = makePage({
      navigation: {
        ...makePage().navigation,
        backgroundMode: 'followFirstSection',
      },
    })
    const dark = resolveNavigationAppearance(page, theme, [
      makeSection({ backgroundColor: '#123456' }),
    ])
    expect(dark.backgroundColor).toBe('#123456')
    expect(dark.textColor).toBe('#FFFFFF')

    const light = resolveNavigationAppearance(page, theme, [
      makeSection({ backgroundColor: '#f5f5f5' }),
    ])
    expect(light.backgroundColor).toBe('#f5f5f5')
    expect(light.textColor).toBe('#222222')
  })

  it('跳过头一个空区块：取首个有内容的区块（与 C 端过滤空区块同口径）', () => {
    const page = makePage({
      navigation: {
        ...makePage().navigation,
        backgroundMode: 'followFirstSection',
      },
    })
    const emptySection: DecorationSection = {
      components: [],
      id: 'section-empty',
      style: makeSection({}).style,
      type: 'default',
    }
    const appearance = resolveNavigationAppearance(page, theme, [
      emptySection,
      makeSection({ backgroundColor: '#07c160' }),
    ])
    expect(appearance.backgroundColor).toBe('#07c160')
  })

  it('跟随态关闭文字自动对比时用页面自存文字色', () => {
    const page = makePage({
      navigation: {
        ...makePage().navigation,
        backgroundMode: 'followFirstSection',
        textColor: '#111111',
        textColorAuto: false,
      },
    })
    const appearance = resolveNavigationAppearance(page, theme, [
      makeSection({ backgroundColor: '#123456' }),
    ])
    expect(appearance.textColor).toBe('#111111')
  })

  it('首区块只有背景图时无法连续呈现，回落主题导航色', () => {
    const page = makePage({
      navigation: {
        ...makePage().navigation,
        backgroundMode: 'followFirstSection',
      },
    })
    const appearance = resolveNavigationAppearance(page, theme, [
      makeSection({ backgroundImage: 'https://cdn.example.com/bg.png' }),
    ])
    expect(appearance).toEqual({
      backgroundColor: '#FF5500',
      textColor: '#ffffff',
    })
  })

  it('没有区块或首区块无背景时回落主题导航色', () => {
    const page = makePage({
      navigation: {
        ...makePage().navigation,
        backgroundMode: 'followFirstSection',
      },
    })
    expect(resolveNavigationAppearance(page, theme, [])).toEqual({
      backgroundColor: '#FF5500',
      textColor: '#ffffff',
    })
    expect(
      resolveNavigationAppearance(page, theme, [makeSection({})]),
    ).toEqual({ backgroundColor: '#FF5500', textColor: '#ffffff' })
  })

  it('非法渐变色回落区块纯色；均无有效色时回落主题导航色', () => {
    const page = makePage({
      navigation: {
        ...makePage().navigation,
        backgroundMode: 'followFirstSection',
      },
    })
    const withSolid = resolveNavigationAppearance(page, theme, [
      makeSection({
        backgroundColor: '#07c160',
        backgroundGradient: {
          direction: 'to bottom',
          endColor: 'red;background:url(x)',
          startColor: 'url(javascript:alert(1))',
        },
      }),
    ])
    expect(withSolid.backgroundColor).toBe('#07c160')

    const noAnchor = resolveNavigationAppearance(page, theme, [
      makeSection({
        backgroundGradient: {
          direction: 'to bottom',
          endColor: '',
          startColor: '',
        },
      }),
    ])
    expect(noAnchor).toEqual({
      backgroundColor: '#FF5500',
      textColor: '#ffffff',
    })
  })
})

describe('pickReadableNavigationTextColor', () => {
  it('深色底给白字，浅色底给深字', () => {
    expect(pickReadableNavigationTextColor('#1543e8', '#000000')).toBe(
      '#FFFFFF',
    )
    expect(pickReadableNavigationTextColor('#f5f5f5', '#ffffff')).toBe(
      '#222222',
    )
  })

  it('支持三位 hex 与 rgb/rgba 写法', () => {
    expect(pickReadableNavigationTextColor('#fff', '#000000')).toBe('#222222')
    expect(pickReadableNavigationTextColor('rgb(21, 67, 232)', '#000000')).toBe(
      '#FFFFFF',
    )
    expect(
      pickReadableNavigationTextColor('rgba(255, 255, 255, 0.8)', '#000000'),
    ).toBe('#222222')
  })

  it('无法解析的颜色回落调用方给的兜底色', () => {
    expect(pickReadableNavigationTextColor('transparent', '#abcdef')).toBe(
      '#abcdef',
    )
  })
})

import type { NavigationAppearance } from './navigation-style'
import type { NavigationScrollEffect, PageSettings } from './types'

import { describe, expect, it } from 'vitest'

import {
  DEFAULT_NAVIGATION_SCROLL_DISTANCE,
  FROSTED_SCROLL_EFFECT,
  FROSTED_SCROLL_SURFACE,
  normalizeNavigationScrollEffect,
  resolveNavigationScrollAppearance,
  resolveNavigationScrollSurface,
} from './navigation-scroll'

function makePage(scrollEffect?: NavigationScrollEffect): PageSettings {
  return {
    backgroundColor: '#f5f5f5',
    backgroundImage: '',
    enablePullDownRefresh: true,
    navigation: {
      backgroundColor: '#ffffff',
      backgroundMode: 'custom',
      scrollEffect,
      textColor: '#000000',
      title: '',
      visible: true,
    },
    share: { description: '', imageUrl: '', title: '' },
  }
}

const theme = {
  backgroundColor: '#FFF3EC',
  navigationBackgroundColor: '#048EEA',
  navigationTextColor: '#ffffff',
}

/** 线上首页真实起始态：首区块渐变顶边色 rgb(54,120,250)，文字自动取白 */
const base: NavigationAppearance = {
  backgroundColor: 'rgb(54, 120, 250)',
  textColor: '#FFFFFF',
}

function fade(overrides: Partial<NavigationScrollEffect> = {}): NavigationScrollEffect {
  return {
    mode: 'fade',
    surface: 'frosted',
    surfaceColor: '',
    distance: DEFAULT_NAVIGATION_SCROLL_DISTANCE,
    blur: true,
    ...overrides,
  }
}

describe('normalizeNavigationScrollEffect', () => {
  it('缺省/非对象回落 mode=none（存量行为不变）', () => {
    for (const value of [undefined, null, '', 1, [], {}]) {
      expect(normalizeNavigationScrollEffect(value)).toEqual({
        mode: 'none',
        surface: 'frosted',
        surfaceColor: '',
        distance: DEFAULT_NAVIGATION_SCROLL_DISTANCE,
        blur: true,
      })
    }
  })

  it('遗留距离字段仍 clamp 到 40–400 并取整（旧文档兼容）', () => {
    expect(normalizeNavigationScrollEffect({ distance: 10 }).distance).toBe(40)
    expect(normalizeNavigationScrollEffect({ distance: 999 }).distance).toBe(400)
    expect(normalizeNavigationScrollEffect({ distance: 139.6 }).distance).toBe(140)
    expect(normalizeNavigationScrollEffect({ distance: Number.NaN }).distance)
      .toBe(DEFAULT_NAVIGATION_SCROLL_DISTANCE)
  })

  it('枚举与颜色白名单：非法值回落缺省', () => {
    const effect = normalizeNavigationScrollEffect({
      mode: 'weird',
      surface: 'rainbow',
      surfaceColor: 'not-a-color',
      blur: 'no',
    })
    expect(effect.mode).toBe('none')
    expect(effect.surface).toBe('frosted')
    expect(effect.surfaceColor).toBe('')
    expect(effect.blur).toBe(true)
  })

  it('合法自定义色与 blur=false 保留', () => {
    const effect = normalizeNavigationScrollEffect({
      mode: 'fade',
      surface: 'custom',
      surfaceColor: '#F5F5F5',
      blur: false,
    })
    expect(effect).toMatchObject({
      mode: 'fade',
      surface: 'custom',
      surfaceColor: '#F5F5F5',
      blur: false,
    })
  })
})

describe('resolveNavigationScrollSurface', () => {
  it('mode=none 原样返回起始外观（与旧渲染一致）', () => {
    const surface = resolveNavigationScrollSurface(makePage(), theme, base)
    expect(surface).toEqual({
      color: base.backgroundColor,
      alpha: 1,
      blur: false,
      textColor: base.textColor,
    })
  })

  it('磨砂白：有毛玻璃 .86，关闭毛玻璃 .97', () => {
    expect(resolveNavigationScrollSurface(makePage(fade()), theme, base)).toMatchObject({
      color: '#FFFFFF',
      alpha: 0.86,
      blur: true,
      textColor: '#222222',
    })
    expect(
      resolveNavigationScrollSurface(makePage(fade({ blur: false })), theme, base),
    ).toMatchObject({ alpha: 0.97, blur: false })
  })

  it('商城主题导航色取主题文字色', () => {
    expect(
      resolveNavigationScrollSurface(makePage(fade({ surface: 'theme' })), theme, base),
    ).toEqual({
      color: '#048EEA',
      alpha: 1,
      blur: false,
      textColor: '#ffffff',
    })
  })

  it('自定义色按对比度取字色；非法色回落磨砂白', () => {
    expect(
      resolveNavigationScrollSurface(
        makePage(fade({ surface: 'custom', surfaceColor: '#F5F5F5' })),
        theme,
        base,
      ),
    ).toMatchObject({ color: '#F5F5F5', alpha: 1, blur: false, textColor: '#222222' })
    expect(
      resolveNavigationScrollSurface(
        makePage(fade({ surface: 'custom', surfaceColor: '' })),
        theme,
        base,
      ),
    ).toMatchObject({ color: '#FFFFFF', alpha: 0.86, blur: true })
  })
})

describe('resolveNavigationScrollAppearance', () => {
  const surface = resolveNavigationScrollSurface(makePage(fade()), theme, base)

  it('mode=none 时 progress=0 且不接管（旧行为）', () => {
    const appearance = resolveNavigationScrollAppearance(
      base,
      resolveNavigationScrollSurface(makePage(), theme, base),
      undefined,
      200,
    )
    expect(appearance).toMatchObject({
      progress: 0,
      baseOpacity: 1,
      surfaceOpacity: 0,
      textColor: base.textColor,
      hairlineOpacity: 0,
    })
  })

  it('一步切换：顶部保持起始外观，任意正滚动距离即完整目标态', () => {
    const at = (scrollTop: number) =>
      resolveNavigationScrollAppearance(base, surface, fade(), scrollTop)
    expect(at(0)).toMatchObject({
      progress: 0,
      baseOpacity: 1,
      surfaceOpacity: 0,
    })
    // 没有「透明 → 模糊」的过渡：1px 与 140px 的解析结果完全一致
    for (const scrollTop of [1, 8, 70, 140, 999]) {
      expect(at(scrollTop)).toMatchObject({
        progress: 1,
        baseOpacity: 0,
        surfaceOpacity: 1,
      })
    }
    // iOS 回弹的负值按顶部处理
    expect(at(-20).progress).toBe(0)
  })

  it('标题与状态栏自动反色：顶部白字，完成态黑字', () => {
    const top = resolveNavigationScrollAppearance(base, surface, fade(), 0)
    expect(top.textColor).toBe('#FFFFFF')
    expect(top.statusBarFrontColor).toBe('#ffffff')
    const done = resolveNavigationScrollAppearance(base, surface, fade(), 140)
    expect(done.textColor).toBe('#222222')
    expect(done.statusBarFrontColor).toBe('#000000')
  })

  it('起始底色是 CSS 变量时，顶部沿用调用方给的文字色（业务页品牌色导航）', () => {
    // 个人中心/会员页的底色是 var(--theme-color-primary)：解析器读不出通道，
    // 若仍按白底推断会把品牌色导航上的白字推成深色
    const brandBase: NavigationAppearance = {
      backgroundColor: 'var(--theme-color-primary, var(--wot-color-theme-primary, #FF2237))',
      textColor: '#FFFFFF',
    }
    const top = resolveNavigationScrollAppearance(
      brandBase,
      FROSTED_SCROLL_SURFACE,
      FROSTED_SCROLL_EFFECT,
      0,
    )
    expect(top.textColor).toBe('#FFFFFF')
    expect(top.statusBarFrontColor).toBe('#ffffff')
    const done = resolveNavigationScrollAppearance(
      brandBase,
      FROSTED_SCROLL_SURFACE,
      FROSTED_SCROLL_EFFECT,
      1,
    )
    expect(done.textColor).toBe('#222222')
  })

  it('业务页磨砂常量与装修页磨砂白目标态同源', () => {
    expect(FROSTED_SCROLL_EFFECT).toMatchObject({
      mode: 'fade',
      surface: 'frosted',
      blur: true,
    })
    expect(FROSTED_SCROLL_SURFACE).toMatchObject({
      color: '#FFFFFF',
      alpha: 0.86,
      blur: true,
    })
  })

  it('浅色目标底色才出现底部细线', () => {
    expect(resolveNavigationScrollAppearance(base, surface, fade(), 0).hairlineOpacity).toBe(0)
    expect(resolveNavigationScrollAppearance(base, surface, fade(), 140).hairlineOpacity).toBe(1)
    const darkSurface = resolveNavigationScrollSurface(
      makePage(fade({ surface: 'custom', surfaceColor: '#111111' })),
      theme,
      base,
    )
    expect(
      resolveNavigationScrollAppearance(base, darkSurface, fade(), 140).hairlineOpacity,
    ).toBe(0)
  })

  it('distance 为遗留字段：不再影响切换（旧文档也按一步切换渲染）', () => {
    const appearance = resolveNavigationScrollAppearance(
      base,
      surface,
      fade({ distance: 40 }),
      1,
    )
    expect(appearance.progress).toBe(1)
    expect(appearance.surfaceOpacity).toBe(1)
  })

  it('毛玻璃开关透传到动态外观', () => {
    const noBlurSurface = resolveNavigationScrollSurface(
      makePage(fade({ blur: false })),
      theme,
      base,
    )
    expect(
      resolveNavigationScrollAppearance(base, noBlurSurface, fade({ blur: false }), 140).blur,
    ).toBe(false)
  })
})

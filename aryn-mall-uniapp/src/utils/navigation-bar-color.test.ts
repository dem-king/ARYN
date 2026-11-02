import { describe, expect, it } from 'vitest'

import { toNativeNavigationBarHexColor } from './navigation-bar-color'

/**
 * 回归测试：微信 setNavigationBarColor 的 backgroundColor 必填。
 *
 * 背景（真实缺陷）：沉浸式导航（装修页滚动切换 / 业务页磨砂顶栏）滚动时只更新
 * 状态栏前景色，调用写成 `uni.setNavigationBarColor({ frontColor })`，漏了微信
 * 必填的 backgroundColor。微信端每次进入个人中心、首页等自定义导航页都会在控制台
 * 抛：
 *   `setNavigationBarColor:fail parameter error: parameter.backgroundColor
 *    should be String instead of Undefined`
 * 这些页面的原生导航条被 `navigationStyle: custom` 隐藏，字段不可见但要合法，
 * 因此统一用它归一化；CSS 变量（个人中心的品牌色 `var(--theme-color-primary…)`）
 * 无法转成 hex 时回退白色。
 */
describe('原生导航条背景色归一化', () => {
  it('十六进制色值原样转为小写 6 位', () => {
    expect(toNativeNavigationBarHexColor('#FF2237')).toBe('#ff2237')
    expect(toNativeNavigationBarHexColor('#ffffff')).toBe('#ffffff')
  })

  it('短写补全、带 alpha 的色值丢弃 alpha 段', () => {
    expect(toNativeNavigationBarHexColor('#fff')).toBe('#ffffff')
    expect(toNativeNavigationBarHexColor('#f23')).toBe('#ff2233')
    expect(toNativeNavigationBarHexColor('#F238')).toBe('#ff2233')
    expect(toNativeNavigationBarHexColor('#FF223780')).toBe('#ff2237')
  })

  it('rgb()/rgba() 转 hex', () => {
    expect(toNativeNavigationBarHexColor('rgb(54, 120, 250)')).toBe('#3678fa')
    expect(toNativeNavigationBarHexColor('rgba(255, 34, 55, 0.5)')).toBe('#ff2237')
    expect(toNativeNavigationBarHexColor('rgb(0, 0, 0)')).toBe('#000000')
  })

  it('遇到 CSS 变量 / 空值 / 脏数据时回退兜底白，绝不再把 undefined 传进 API', () => {
    const brand = 'var(--theme-color-primary, var(--wot-color-theme-primary, #FF2237))'
    expect(toNativeNavigationBarHexColor(brand)).toBe('#ffffff')
    expect(toNativeNavigationBarHexColor('')).toBe('#ffffff')
    expect(toNativeNavigationBarHexColor('   ')).toBe('#ffffff')
    expect(toNativeNavigationBarHexColor(undefined)).toBe('#ffffff')
    expect(toNativeNavigationBarHexColor(null)).toBe('#ffffff')
    expect(toNativeNavigationBarHexColor('#12345')).toBe('#ffffff')
    expect(toNativeNavigationBarHexColor('rgb(300, 0, 0)')).toBe('#ffffff')
    expect(toNativeNavigationBarHexColor('not-a-color')).toBe('#ffffff')
  })

  it('兜底色可指定，仍然只输出合法 hex', () => {
    expect(toNativeNavigationBarHexColor('', '#000000')).toBe('#000000')
  })
})

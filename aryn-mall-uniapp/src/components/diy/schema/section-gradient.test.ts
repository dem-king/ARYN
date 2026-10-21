import { describe, expect, it } from 'vitest'
import {
  buildSectionGradient,
  getSectionGradientAnchorColor,
  isHorizontalSectionGradient,
} from './section-gradient'

describe('buildSectionGradient', () => {
  it('把起止色与方向拼成 CSS linear-gradient', () => {
    expect(
      buildSectionGradient({
        direction: '135deg',
        endColor: 'rgba(0, 0, 0, 0.5)',
        startColor: '#ff5500',
      }),
    ).toBe('linear-gradient(135deg, #ff5500, rgba(0, 0, 0, 0.5))')
  })

  it('只配置一端颜色时两端同色，等价于纯色填充', () => {
    expect(
      buildSectionGradient({
        direction: 'to right',
        endColor: '',
        startColor: '#123456',
      }),
    ).toBe('linear-gradient(to right, #123456, #123456)')
  })

  it('起止色都为空时返回 undefined，由纯色兜底', () => {
    expect(
      buildSectionGradient({
        direction: 'to right',
        endColor: '',
        startColor: '',
      }),
    ).toBeUndefined()
    expect(buildSectionGradient(undefined)).toBeUndefined()
  })

  it('方向不在白名单时回落 to right，非法色值被忽略', () => {
    expect(
      buildSectionGradient({
        direction: 'url(javascript:alert(1))',
        endColor: 'red;background:url(x)',
        startColor: '#ffffff',
      }),
    ).toBe('linear-gradient(to right, #ffffff, #ffffff)')
  })
})

describe('区块背景渐变辅助解析', () => {
  it('getSectionGradientAnchorColor 取顶边起色：优先起始色，只填一端时取那一端', () => {
    expect(
      getSectionGradientAnchorColor({
        direction: 'to bottom',
        endColor: '#f5f7fa',
        startColor: '#1543e8',
      }),
    ).toBe('#1543e8')
    expect(
      getSectionGradientAnchorColor({
        direction: 'to bottom',
        endColor: '#f5f7fa',
        startColor: '',
      }),
    ).toBe('#f5f7fa')
    expect(
      getSectionGradientAnchorColor({
        direction: 'to bottom',
        endColor: '',
        startColor: '',
      }),
    ).toBe('')
    expect(getSectionGradientAnchorColor(undefined)).toBe('')
  })

  it('isHorizontalSectionGradient 只认 to right，非法方向按 to right 口径处理', () => {
    expect(
      isHorizontalSectionGradient({
        direction: 'to right',
        endColor: '#ffffff',
        startColor: '#ff5500',
      }),
    ).toBe(true)
    expect(
      isHorizontalSectionGradient({
        direction: 'to bottom',
        endColor: '#ffffff',
        startColor: '#ff5500',
      }),
    ).toBe(false)
    expect(
      isHorizontalSectionGradient({
        direction: 'url(javascript:alert(1))',
        endColor: '#ffffff',
        startColor: '#ff5500',
      }),
    ).toBe(true)
    expect(isHorizontalSectionGradient(undefined)).toBe(false)
  })
})

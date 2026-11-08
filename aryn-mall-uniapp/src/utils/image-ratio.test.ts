import { describe, expect, it } from 'vitest'
import { imageAspectPadding } from './image-ratio'

describe('imageAspectPadding', () => {
  it('按原图宽高换算 padding-top 百分比', () => {
    expect(imageAspectPadding(800, 800)).toBe('100%')
    expect(imageAspectPadding(750, 1000)).toBe('133.33%')
    expect(imageAspectPadding(1000, 750)).toBe('75%')
  })

  it('宽高缺失或非法时回落 1:1', () => {
    expect(imageAspectPadding()).toBe('100%')
    expect(imageAspectPadding(null, 100)).toBe('100%')
    expect(imageAspectPadding(0, 100)).toBe('100%')
    expect(imageAspectPadding(-10, 100)).toBe('100%')
    expect(imageAspectPadding('abc', 100)).toBe('100%')
  })

  it('极端长图限制在 60%~180%，避免单卡过高或过扁', () => {
    expect(imageAspectPadding(100, 1000)).toBe('180%')
    expect(imageAspectPadding(1000, 100)).toBe('60%')
  })
})

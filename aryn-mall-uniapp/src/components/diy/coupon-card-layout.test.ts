import { describe, expect, it } from 'vitest'
import { couponCardLayout } from './coupon-card-layout'

describe('首页领券组件卡片宽度', () => {
  it('只剩 1 张券时不拉满整行，且整行居中而不是右侧留白', () => {
    const layout = couponCardLayout(1, 3)

    expect(layout.style.width).toBe('50%')
    expect(layout.fillsRow).toBe(false)
  })

  it('2 张券各占半行，正好铺满（不再留大片空白）', () => {
    const layout = couponCardLayout(2, 3)

    expect(layout.style.width).toBe('calc(50% - 8rpx)')
    expect(layout.fillsRow).toBe(true)
  })

  it('券数达到配置数量时按配置均分，与后台「显示数量」一致', () => {
    const layout = couponCardLayout(3, 3)

    expect(layout.style.width).toBe('calc(33.3333% - 10.6667rpx)')
    expect(layout.fillsRow).toBe(true)
  })

  it('配置数量很大时保留最小宽度，改为横向滚动而不是挤成细条', () => {
    const layout = couponCardLayout(10, 10)

    expect(layout.style.width).toBe('calc(10% - 14.4rpx)')
    expect(layout.style.minWidth).toBe('220rpx')
  })

  it('运营只配置 1 张时尊重配置，占满整行', () => {
    const layout = couponCardLayout(1, 1)

    expect(layout.style.width).toBe('100%')
    expect(layout.fillsRow).toBe(true)
  })

  it('非法入参按 1 张兜底，不产生 NaN 宽度', () => {
    const layout = couponCardLayout(0, 0)

    expect(layout.style.width).toBe('100%')
    expect(layout.style.width).not.toContain('NaN')
  })
})

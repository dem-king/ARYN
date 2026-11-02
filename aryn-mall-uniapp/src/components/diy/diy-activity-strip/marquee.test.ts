import { describe, expect, it } from 'vitest'

import {
  isMarqueeNeeded,
  MARQUEE_GAP_PX,
  MARQUEE_MIN_COPIES,
  MARQUEE_MIN_DURATION_SEC,
  marqueeCopies,
  marqueeDuration,
  marqueeTrackWidth,
} from './marquee'

/**
 * 活动入口条长文案跑马灯的纯逻辑回归。
 *
 * 这些数字直接决定卡片里「滚不滚、滚多快、轨道多宽」：
 * 阈值偏小会让短文案来回抖，轨道宽度算错会在循环接缝处跳字。
 */
describe('isMarqueeNeeded 溢出判定', () => {
  it('文本严格超出容器时才滚动', () => {
    expect(isMarqueeNeeded(101.5, 100)).toBe(true)
    expect(isMarqueeNeeded(120, 100)).toBe(true)
  })

  it('等宽或只多出亚像素时不滚动（吸收测量误差）', () => {
    expect(isMarqueeNeeded(100, 100)).toBe(false)
    expect(isMarqueeNeeded(100.6, 100)).toBe(false)
    expect(isMarqueeNeeded(50, 100)).toBe(false)
  })

  it('任何宽度为 0 / 非法时都不滚动', () => {
    expect(isMarqueeNeeded(0, 100)).toBe(false)
    expect(isMarqueeNeeded(100, 0)).toBe(false)
    expect(isMarqueeNeeded(Number.NaN, 100)).toBe(false)
    expect(isMarqueeNeeded(100, Number.POSITIVE_INFINITY)).toBe(false)
  })
})

describe('marqueeTrackWidth 轨道宽度', () => {
  it('默认双份：(文本宽度 + 间距) × 2，位移 -50% 正好一份', () => {
    expect(marqueeTrackWidth(120, 12)).toBe(264)
    expect(marqueeTrackWidth(120, MARQUEE_GAP_PX)).toBe(
      (120 + MARQUEE_GAP_PX) * 2,
    )
  })

  it('ticker 按份数展开轨道', () => {
    expect(marqueeTrackWidth(120, 12, 6)).toBe((120 + 12) * 6)
  })

  it('奇数/非法份数收敛为偶数双份，避免接缝跳字', () => {
    expect(marqueeTrackWidth(120, 12, 3)).toBe((120 + 12) * 4)
    expect(marqueeTrackWidth(120, 12, 0)).toBe((120 + 12) * MARQUEE_MIN_COPIES)
    expect(marqueeTrackWidth(120, 12, 2.5)).toBe((120 + 12) * MARQUEE_MIN_COPIES)
  })

  it('非法/负数按 0 处理，不产生负宽度', () => {
    expect(marqueeTrackWidth(-20, 12)).toBe(24)
    expect(marqueeTrackWidth(Number.NaN, 12)).toBe(24)
  })
})

describe('marqueeCopies 恒定跑马灯重复份数', () => {
  it('份数为偶数，且半条轨道至少铺满容器', () => {
    // 单元宽 132：2 × 300 / 132 ≈ 4.55 → 5 → 收敛为 6 份
    expect(marqueeCopies(120, 300, 12)).toBe(6)
    expect(marqueeCopies(120, 300, 12) % 2).toBe(0)
  })

  it('极短文案重复到覆盖容器，滚动时不露空白', () => {
    // 单元宽 32：2 × 300 / 32 ≈ 18.75 → 19 → 收敛为 20 份
    expect(marqueeCopies(20, 300, 12)).toBe(20)
  })

  it('长文案只需双份', () => {
    expect(marqueeCopies(400, 300, 12)).toBe(MARQUEE_MIN_COPIES)
  })

  it('宽度缺失时回落到双份', () => {
    expect(marqueeCopies(0, 300)).toBe(MARQUEE_MIN_COPIES)
    expect(marqueeCopies(120, 0)).toBe(MARQUEE_MIN_COPIES)
  })
})

describe('marqueeDuration 滚动时长', () => {
  it('按 px/s 折算：132px / 28px/s ≈ 4.71s', () => {
    expect(marqueeDuration(120, 12, 28)).toBe(4.71)
  })

  it('ticker 时长按半条轨道折算，速度口径不变', () => {
    // 6 份 → 位移 3 × 132 = 396px；396 / 28 ≈ 14.14s
    expect(marqueeDuration(120, 12, 28, 6)).toBe(14.14)
  })

  it('极短文本也保留最短时长，避免一闪而过', () => {
    expect(marqueeDuration(10, 12, 28)).toBe(MARQUEE_MIN_DURATION_SEC)
  })

  it('速度非法时回落到默认速度，不返回 Infinity', () => {
    const duration = marqueeDuration(120, 12, 0)
    expect(Number.isFinite(duration)).toBe(true)
    expect(duration).toBeGreaterThan(0)
  })
})

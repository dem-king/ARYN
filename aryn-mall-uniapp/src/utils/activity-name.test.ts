import { describe, expect, it } from 'vitest'
import { stripGroupBadgePrefix } from './activity-name'

describe('stripGroupBadgePrefix', () => {
  it('剥离与人数徽标重复的前缀（间隔符可为 · / 空格 / 短横）', () => {
    expect(stripGroupBadgePrefix('2人成团·农夫山泉天然水整箱', 2)).toBe('农夫山泉天然水整箱')
    expect(stripGroupBadgePrefix('3人成团 百草味夏威夷果', 3)).toBe('百草味夏威夷果')
    expect(stripGroupBadgePrefix('2人成团-青岛啤酒经典', 2)).toBe('青岛啤酒经典')
  })

  it('groupNum 缺失/为 0 时不剥离（徽标不显示人数，前缀是唯一信息）', () => {
    expect(stripGroupBadgePrefix('2人成团·农夫山泉', 0)).toBe('2人成团·农夫山泉')
  })

  it('不误伤规格词：「3人份」「2人套餐」没有「成团」二字', () => {
    expect(stripGroupBadgePrefix('3人份家庭装火锅套餐', 2)).toBe('3人份家庭装火锅套餐')
    expect(stripGroupBadgePrefix('2人套餐 双人份', 2)).toBe('2人套餐 双人份')
  })

  it('剥离后为空时回退原名（活动名只有「2人成团」）', () => {
    expect(stripGroupBadgePrefix('2人成团', 2)).toBe('2人成团')
    expect(stripGroupBadgePrefix('2人成团·', 2)).toBe('2人成团·')
  })

  it('空值安全', () => {
    expect(stripGroupBadgePrefix('', 2)).toBe('')
    expect(stripGroupBadgePrefix(undefined as unknown as string, 2)).toBe('')
  })
})
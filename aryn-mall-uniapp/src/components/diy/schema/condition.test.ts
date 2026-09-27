import type { ConditionContext } from './condition'

import { describe, expect, it } from 'vitest'
import { evaluateCondition } from './condition'

const LOGGED_IN: ConditionContext = {
  isLoggedIn: true,
  memberLevelId: 'lv1',
  userTags: ['tag-a'],
}

const GUEST: ConditionContext = {
  isLoggedIn: false,
  userTags: [],
}

describe('diy schema evaluateCondition', () => {
  it('string shorthand keeps legacy behavior', () => {
    expect(evaluateCondition('always', GUEST)).toBe(true)
    expect(evaluateCondition('login', GUEST)).toBe(false)
    expect(evaluateCondition('login', LOGGED_IN)).toBe(true)
    expect(evaluateCondition('guest', GUEST)).toBe(true)
    expect(evaluateCondition('guest', LOGGED_IN)).toBe(false)
  })

  it('treats an empty memberLevelIds list as unrestricted (homepage white-screen regression)', () => {
    // 线上首页真实数据：唯一区块挂着 memberLevelIds: []，导致整页只剩导航条
    const condition = {
      logic: 'and' as const,
      rules: [{ type: 'memberLevel' as const, memberLevelIds: [] }],
    }
    expect(evaluateCondition(condition, GUEST)).toBe(true)
    expect(evaluateCondition(condition, LOGGED_IN)).toBe(true)
  })

  it('treats an empty userTagIds list as unrestricted', () => {
    const condition = {
      logic: 'and' as const,
      rules: [{ type: 'userTag' as const, userTagIds: [] }],
    }
    expect(evaluateCondition(condition, GUEST)).toBe(true)
    expect(evaluateCondition(condition, LOGGED_IN)).toBe(true)
  })

  it('matches member levels only when configured and logged in', () => {
    const restricted = {
      logic: 'and' as const,
      rules: [{ type: 'memberLevel' as const, memberLevelIds: ['lv2'] }],
    }
    expect(evaluateCondition(restricted, LOGGED_IN)).toBe(false)
    expect(
      evaluateCondition({
        logic: 'and',
        rules: [{ type: 'memberLevel', memberLevelIds: ['lv1'] }],
      }, LOGGED_IN),
    ).toBe(true)
    // 未登录时会员等级条件不满足
    expect(evaluateCondition({
      logic: 'and',
      rules: [{ type: 'memberLevel', memberLevelIds: ['lv1'] }],
    }, GUEST)).toBe(false)
  })

  it('matches user tags only when configured and logged in', () => {
    const condition = {
      logic: 'and' as const,
      rules: [{ type: 'userTag' as const, userTagIds: ['tag-a', 'tag-b'] }],
    }
    expect(evaluateCondition(condition, LOGGED_IN)).toBe(true)
    expect(evaluateCondition(condition, GUEST)).toBe(false)
  })

  it('aggregates rules by group logic', () => {
    const rules = [
      { type: 'memberLevel' as const, memberLevelIds: ['lv1'] },
      { type: 'userTag' as const, userTagIds: ['tag-b'] },
    ]
    expect(evaluateCondition({ logic: 'and', rules }, LOGGED_IN)).toBe(false)
    expect(evaluateCondition({ logic: 'or', rules }, LOGGED_IN)).toBe(true)
  })

  it('treats a rule group without rules as unconfigured', () => {
    expect(evaluateCondition({ logic: 'and', rules: [] }, GUEST)).toBe(true)
    expect(evaluateCondition({ logic: 'or', rules: [] }, GUEST)).toBe(true)
  })

  it('fails closed on unknown rule types', () => {
    const unknown = { type: 'future-rule', foo: 1 } as unknown as { type: 'login' }
    expect(evaluateCondition({ logic: 'and', rules: [unknown] }, LOGGED_IN)).toBe(false)
    expect(evaluateCondition({ logic: 'or', rules: [unknown, { type: 'login' }] }, LOGGED_IN)).toBe(true)
  })

  it('evaluates time ranges including cross-midnight and malformed values', () => {
    const at = (hours: number, minutes: number): ConditionContext => ({
      ...LOGGED_IN,
      now: new Date(2026, 8, 25, hours, minutes),
    })
    const daytime = { type: 'timeRange' as const, startTime: '09:00', endTime: '21:00' }
    const overnight = { type: 'timeRange' as const, startTime: '22:00', endTime: '06:00' }

    expect(evaluateCondition({ logic: 'and', rules: [daytime] }, at(10, 0))).toBe(true)
    expect(evaluateCondition({ logic: 'and', rules: [daytime] }, at(22, 0))).toBe(false)
    expect(evaluateCondition({ logic: 'and', rules: [overnight] }, at(23, 30))).toBe(true)
    expect(evaluateCondition({ logic: 'and', rules: [overnight] }, at(5, 0))).toBe(true)
    expect(evaluateCondition({ logic: 'and', rules: [overnight] }, at(12, 0))).toBe(false)
    // 格式异常时不阻断展示
    expect(evaluateCondition({
      logic: 'and',
      rules: [{ type: 'timeRange', startTime: '', endTime: '21:00' }],
    }, at(10, 0))).toBe(true)
  })
})

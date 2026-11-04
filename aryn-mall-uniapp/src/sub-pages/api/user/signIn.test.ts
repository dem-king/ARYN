import { beforeEach, describe, expect, it, vi } from 'vitest'

import { getTodaySignInRecord } from './signIn'

/**
 * `getTodaySignInRecord` 的参数契约。
 *
 * 背景：会员中心今日签到态依赖「记录接口收窄到当天单条」。若 beginDate/endDate
 * 或分页参数写错/丢失，接口会退化为「取最近一条记录」——昨天签过的用户会被判成
 * 今天已签到，而命中快照后当天又不再请求校正，属于最难发现的错误状态。
 * 因此这里锁死请求参数与空响应两个边界，不只依赖页面级 mock。
 */

const { getMock } = vi.hoisted(() => ({
  getMock: vi.fn(),
}))

vi.mock('@/api/core/instance', () => ({
  alovaInstance: {
    Get: (...args: unknown[]) => getMock(...args),
    Post: vi.fn(),
  },
}))

const RECORD = {
  id: 'r-1',
  signDate: '2026-11-03',
  consecutiveDay: 2,
  rewardPoint: 10,
  createTime: '2026-11-03 09:00:00',
}

describe('getTodaySignInRecord（今日签到记录收窄）', () => {
  beforeEach(() => {
    getMock.mockReset()
  })

  it('把记录接口收窄到「当天 + 第一条」', async () => {
    getMock.mockResolvedValue({ records: [RECORD], total: 1 })

    const record = await getTodaySignInRecord('2026-11-03')

    expect(getMock).toHaveBeenCalledWith('/mall-user/app/signin/records', {
      params: {
        current: 1,
        size: 1,
        beginDate: '2026-11-03',
        endDate: '2026-11-03',
      },
    })
    expect(record).toEqual(RECORD)
  })

  it('当天无记录 / 空响应体一律返回 null（=今天还没签）', async () => {
    getMock.mockResolvedValue({ records: [], total: 0 })
    expect(await getTodaySignInRecord('2026-11-03')).toBeNull()

    getMock.mockResolvedValue({ records: undefined })
    expect(await getTodaySignInRecord('2026-11-03')).toBeNull()

    getMock.mockResolvedValue(undefined)
    expect(await getTodaySignInRecord('2026-11-03')).toBeNull()
  })
})

import { beforeEach, describe, expect, it, vi } from 'vitest'

import {
  buildSignInSnapshot,
  formatSignInDate,
  resolveSignInSnapshot,
  SIGN_IN_SNAPSHOT_KEY,
  useSignInStatus,
} from './useSignInStatus'

/**
 * 今日签到态（useSignInStatus）的回归测试。
 *
 * 背景（真实缺陷）：会员中心「每日签到」横幅写死「去签到」，已签到用户回到
 * 会员中心仍被引导去签到，点进签到页才看到「今日已签到」。
 *
 * 这里固定四件事：
 *  · 本地快照只对「当前用户 + 今天」生效，跨天 / 换账号必须作废；
 *  · 命中「今天已签到」快照就不再发请求（签到当天不可逆）；
 *  · 未签 / 无快照时以接口为准，接口失败保留原状态且不抛错打断页面；
 *  · 签到页签到成功后写快照，会员中心返回时能同步读到、无需等接口。
 */

const { authState, userState, apiState } = vi.hoisted(() => ({
  authState: { isLoggedIn: true },
  userState: { getUserId: 'u-1' },
  apiState: {
    getTodaySignInRecord: vi.fn(),
  },
}))

vi.mock('@/store/authStore', () => ({
  useAuthStore: () => authState,
}))

vi.mock('@/store/userStore', () => ({
  useUserStore: () => userState,
}))

vi.mock('@/sub-pages/api/user/signIn', () => ({
  getTodaySignInRecord: apiState.getTodaySignInRecord,
}))

/** 固定「现在」：2026-11-03 09:30 本地时间（快照日期只做自然日比较，不涉及时区换算） */
const NOW = new Date(2026, 10, 3, 9, 30)
const TODAY = '2026-11-03'

const storage: Record<string, unknown> = {}

function signedSnapshot(overrides: Partial<{ userId: string, date: string, consecutiveDay: number }> = {}) {
  return {
    userId: overrides.userId ?? 'u-1',
    date: overrides.date ?? TODAY,
    signed: true,
    consecutiveDay: overrides.consecutiveDay ?? 3,
  }
}

beforeEach(() => {
  for (const key of Object.keys(storage))
    delete storage[key]
  authState.isLoggedIn = true
  userState.getUserId = 'u-1'
  apiState.getTodaySignInRecord.mockReset()
  vi.stubGlobal('uni', {
    setStorageSync: (key: string, value: unknown) => {
      storage[key] = value
    },
    getStorageSync: (key: string) => storage[key],
    removeStorageSync: (key: string) => {
      delete storage[key]
    },
  })
})

describe('快照可用性（纯函数）', () => {
  it('本地日期格式化为 yyyy-MM-dd', () => {
    expect(formatSignInDate(new Date(2026, 10, 3, 23, 59, 59))).toBe('2026-11-03')
    expect(formatSignInDate(new Date(2026, 0, 9, 0, 0, 1))).toBe('2026-01-09')
  })

  it('只认「当前用户 + 今天」，其余一律作废', () => {
    expect(resolveSignInSnapshot(signedSnapshot(), 'u-1', TODAY)).toEqual(signedSnapshot())
    // 换账号：不能把上一个人的「已签到」显示给新用户
    expect(resolveSignInSnapshot(signedSnapshot(), 'u-2', TODAY)).toBeNull()
    // 跨天：昨天的已签到不代表今天
    expect(resolveSignInSnapshot(signedSnapshot(), 'u-1', '2026-11-04')).toBeNull()
    // 脏数据 / 缺字段
    expect(resolveSignInSnapshot(null, 'u-1', TODAY)).toBeNull()
    expect(resolveSignInSnapshot('signed', 'u-1', TODAY)).toBeNull()
    expect(resolveSignInSnapshot({ userId: 'u-1', date: TODAY, signed: 'yes' }, 'u-1', TODAY)).toBeNull()
  })

  it('连签天数的脏值归零，避免渲染「-1 天」', () => {
    expect(resolveSignInSnapshot(signedSnapshot({ consecutiveDay: -2 }), 'u-1', TODAY)?.consecutiveDay).toBe(0)
    expect(resolveSignInSnapshot({ ...signedSnapshot(), consecutiveDay: '2' }, 'u-1', TODAY)?.consecutiveDay).toBe(2)
  })

  it('接口结果转快照：无记录 = 今天未签到', () => {
    expect(buildSignInSnapshot('u-1', TODAY, null)).toEqual({
      userId: 'u-1',
      date: TODAY,
      signed: false,
      consecutiveDay: 0,
    })
    expect(buildSignInSnapshot('u-1', TODAY, {
      id: 'r-1',
      signDate: TODAY,
      consecutiveDay: 3,
      rewardPoint: 10,
      createTime: '2026-11-03 09:00:00',
    })).toEqual({
      userId: 'u-1',
      date: TODAY,
      signed: true,
      consecutiveDay: 3,
    })
  })
})

describe('useSignInStatus（页面状态）', () => {
  it('首帧读本地快照：已签到用户不必等接口', () => {
    storage[SIGN_IN_SNAPSHOT_KEY] = signedSnapshot()
    const status = useSignInStatus()

    expect(status.syncFromCache(NOW)).toBe(true)
    expect(status.signedToday.value).toBe(true)
    expect(status.consecutiveDay.value).toBe(3)
  })

  it('命中「今天已签到」快照就不再请求（签到当天不可逆）', async () => {
    storage[SIGN_IN_SNAPSHOT_KEY] = signedSnapshot()
    const status = useSignInStatus()

    await status.refresh(NOW)

    expect(apiState.getTodaySignInRecord).not.toHaveBeenCalled()
    expect(status.signedToday.value).toBe(true)
    expect(status.consecutiveDay.value).toBe(3)
  })

  it('无快照时按接口校正并落快照', async () => {
    apiState.getTodaySignInRecord.mockResolvedValue({
      id: 'r-1',
      signDate: TODAY,
      consecutiveDay: 4,
      rewardPoint: 10,
      createTime: '2026-11-03 09:00:00',
    })
    const status = useSignInStatus()

    await status.refresh(NOW)

    expect(apiState.getTodaySignInRecord).toHaveBeenCalledWith(TODAY)
    expect(status.signedToday.value).toBe(true)
    expect(status.consecutiveDay.value).toBe(4)
    expect(storage[SIGN_IN_SNAPSHOT_KEY]).toEqual({
      userId: 'u-1',
      date: TODAY,
      signed: true,
      consecutiveDay: 4,
    })
  })

  it('接口返回空记录：今天未签到，CTA 回落「去签到」', async () => {
    apiState.getTodaySignInRecord.mockResolvedValue(null)
    const status = useSignInStatus()

    await status.refresh(NOW)

    expect(status.signedToday.value).toBe(false)
    expect(status.consecutiveDay.value).toBe(0)
    expect(storage[SIGN_IN_SNAPSHOT_KEY]).toEqual({
      userId: 'u-1',
      date: TODAY,
      signed: false,
      consecutiveDay: 0,
    })
  })

  it('接口失败保留快照态、不覆盖快照且不抛错打断页面', async () => {
    const cached = { ...signedSnapshot(), signed: false, consecutiveDay: 0 }
    storage[SIGN_IN_SNAPSHOT_KEY] = cached
    apiState.getTodaySignInRecord.mockRejectedValue(new Error('network'))
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
    const status = useSignInStatus()

    await expect(status.refresh(NOW)).resolves.toBeUndefined()

    expect(status.signedToday.value).toBe(false)
    // 失败不能把快照写成「已签到/未签到」的接口结果，更不能清掉原快照
    expect(storage[SIGN_IN_SNAPSHOT_KEY]).toEqual(cached)
    expect(errorSpy).toHaveBeenCalled()
    errorSpy.mockRestore()
  })

  it('请求期间退出登录/换账号：迟到响应不覆盖当前页面态', async () => {
    let resolveFetch: (value: unknown) => void = () => {}
    apiState.getTodaySignInRecord.mockImplementation(() => new Promise((resolve) => {
      resolveFetch = resolve
    }))
    const status = useSignInStatus()
    const pending = status.refresh(NOW)

    // 请求在途时账号切换（真实场景：退登后快速换号登录）
    userState.getUserId = 'u-2'
    resolveFetch({
      id: 'r-1',
      signDate: TODAY,
      consecutiveDay: 5,
      rewardPoint: 10,
      createTime: '2026-11-03 09:00:00',
    })
    await pending

    expect(status.signedToday.value).toBe(false)
    expect(status.consecutiveDay.value).toBe(0)
    expect(storage[SIGN_IN_SNAPSHOT_KEY]).toBeUndefined()
  })

  it('未登录时残留快照一律不采用（syncFromCache 内部门控）', () => {
    storage[SIGN_IN_SNAPSHOT_KEY] = signedSnapshot()
    authState.isLoggedIn = false
    const status = useSignInStatus()

    expect(status.syncFromCache(NOW)).toBe(false)
    expect(status.signedToday.value).toBe(false)
  })

  it('换账号后旧快照作废，必须重新请求', async () => {
    storage[SIGN_IN_SNAPSHOT_KEY] = signedSnapshot()
    userState.getUserId = 'u-2'
    apiState.getTodaySignInRecord.mockResolvedValue(null)
    const status = useSignInStatus()

    await status.refresh(NOW)

    expect(apiState.getTodaySignInRecord).toHaveBeenCalledWith(TODAY)
    expect(status.signedToday.value).toBe(false)
  })

  it('签到页签到成功后写快照，会员中心下次进入可同步读到', () => {
    const signInPage = useSignInStatus()
    signInPage.markSigned(3, NOW)

    expect(storage[SIGN_IN_SNAPSHOT_KEY]).toEqual({
      userId: 'u-1',
      date: TODAY,
      signed: true,
      consecutiveDay: 3,
    })

    const memberCenter = useSignInStatus()
    expect(memberCenter.syncFromCache(NOW)).toBe(true)
    expect(memberCenter.consecutiveDay.value).toBe(3)
  })

  it('未登录绝不发 /app/** 请求，状态回落未签到', async () => {
    authState.isLoggedIn = false
    userState.getUserId = ''
    const status = useSignInStatus()
    status.signedToday.value = true
    status.consecutiveDay.value = 2

    await status.refresh(NOW)

    expect(apiState.getTodaySignInRecord).not.toHaveBeenCalled()
    expect(status.signedToday.value).toBe(false)
    expect(status.consecutiveDay.value).toBe(0)
  })

  it('reset 清空页面态（退出登录分支）', () => {
    storage[SIGN_IN_SNAPSHOT_KEY] = signedSnapshot()
    const status = useSignInStatus()
    status.syncFromCache(NOW)
    expect(status.signedToday.value).toBe(true)

    status.reset()

    expect(status.signedToday.value).toBe(false)
    expect(status.consecutiveDay.value).toBe(0)
  })
})

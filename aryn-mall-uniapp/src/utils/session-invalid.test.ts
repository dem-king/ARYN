import type { SessionInvalidSubReason } from './session-invalid'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { setStoredLocale } from '@/locales'
import {
  buildSessionInvalidDeviceLine,
  extractSessionInvalidPayload,
  formatSessionInvalidTime,
  normalizeSessionInvalidDevice,
  normalizeSessionInvalidReason,
  normalizeSessionInvalidSubReason,
  resolveSessionInvalidActions,
  resolveSessionInvalidCopy,
  resolveSessionInvalidTitle,
  SESSION_INVALID_REASONS,
  SESSION_INVALID_SUB_REASONS,
} from './session-invalid'

/**
 * 会话失效契约解析与文案（一期契约 §4 + 二期契约 §1/§2 逐字）。
 *
 * 这里用 `translate` 的真实 i18n 实例跑中英文，不 mock 文案 —— 这样 key 缺失、
 * 中英 key 不对齐、插值语法不支持都会在这里直接暴露。
 */

/** 固定「今天」为 2026-10-28 22:00（本地时区 naive 字符串，测试不依赖运行机器时区） */
const NOW = new Date('2026-10-28T22:00:00').getTime()

describe('extractSessionInvalidPayload（兼容对象/JSON 字符串/信封三层）', () => {
  it('reads the payload object directly', () => {
    expect(extractSessionInvalidPayload({
      reason: 'BE_REPLACED',
      invalidAt: '2026-10-28T21:32:07',
      device: { os: 'iOS', browser: '微信', location: '上海市', loginTime: '2026-10-28T21:32:05' },
    })).toEqual({
      reason: 'BE_REPLACED',
      subReason: '',
      invalidAt: '2026-10-28T21:32:07',
      device: { os: 'iOS', browser: '微信', location: '上海市', loginTime: '2026-10-28T21:32:05' },
    })
  })

  it('unwraps the full business envelope ({code,msg,data})', () => {
    expect(extractSessionInvalidPayload({
      code: 401,
      msg: '账号已在其他设备登录',
      data: { reason: 'BE_REPLACED', invalidAt: '2026-10-28T21:32:07' },
    })).toMatchObject({ reason: 'BE_REPLACED', invalidAt: '2026-10-28T21:32:07' })
  })

  it('parses a JSON string body and a JSON string payload', () => {
    expect(extractSessionInvalidPayload(JSON.stringify({
      code: 401,
      msg: 'x',
      data: { reason: 'TOKEN_EXPIRED' },
    }))).toMatchObject({ reason: 'TOKEN_EXPIRED' })
    expect(extractSessionInvalidPayload({ data: '{"reason":"KICK_OUT"}' })).toMatchObject({ reason: 'KICK_OUT' })
  })

  it('degrades unknown reason to empty string and filters empty device fields', () => {
    const payload = extractSessionInvalidPayload({
      data: {
        reason: 'SOME_FUTURE_REASON',
        device: { os: '  iOS  ', browser: '', location: null, loginTime: '   ', extra: 'ignored' },
      },
    })
    expect(payload.reason).toBe('')
    expect(payload.device).toEqual({ os: 'iOS' })
  })

  it('returns an empty payload for null/garbage/non-contract objects', () => {
    const empty = { reason: '', subReason: '', invalidAt: '', device: null }
    expect(extractSessionInvalidPayload(null)).toEqual(empty)
    expect(extractSessionInvalidPayload(undefined)).toEqual(empty)
    expect(extractSessionInvalidPayload('<html>Bad Gateway</html>')).toEqual(empty)
    expect(extractSessionInvalidPayload({ code: 401, msg: 'x', data: null })).toEqual(empty)
  })

  it('accepts exactly the frozen wire reasons', () => {
    expect([...SESSION_INVALID_REASONS]).toEqual([
      'NOT_TOKEN',
      'TOKEN_INVALID',
      'TOKEN_EXPIRED',
      'BE_REPLACED',
      'KICK_OUT',
      'TOKEN_FREEZE',
      'NO_PREFIX',
    ])
    expect(normalizeSessionInvalidReason('BE_REPLACED')).toBe('BE_REPLACED')
    expect(normalizeSessionInvalidReason('be_replaced')).toBe('')
    expect(normalizeSessionInvalidReason(undefined)).toBe('')
    expect(normalizeSessionInvalidDevice('not-an-object')).toBeNull()
  })
})

describe('subReason 解析（二期契约 §1：仅 KICK_OUT、未知值丢弃）', () => {
  it('accepts exactly the frozen wire subReasons', () => {
    expect([...SESSION_INVALID_SUB_REASONS]).toEqual([
      'ADMIN_KICK',
      'PASSWORD_CHANGED',
      'ACCOUNT_DISABLED',
      'ACCOUNT_DELETED',
      'TENANT_DISABLED',
      'MEMBER_DISABLED',
      'DELIVERY_REVOKED',
    ])
    expect(normalizeSessionInvalidSubReason('ACCOUNT_DISABLED')).toBe('ACCOUNT_DISABLED')
    expect(normalizeSessionInvalidSubReason('account_disabled')).toBe('')
    expect(normalizeSessionInvalidSubReason('SOME_FUTURE_SUB_REASON')).toBe('')
    expect(normalizeSessionInvalidSubReason(undefined)).toBe('')
  })

  it('keeps subReason only when reason is KICK_OUT', () => {
    expect(extractSessionInvalidPayload({
      reason: 'KICK_OUT',
      subReason: 'PASSWORD_CHANGED',
      invalidAt: '2026-10-28T23:50:12',
    })).toMatchObject({ reason: 'KICK_OUT', subReason: 'PASSWORD_CHANGED' })

    // 契约外组合（非 KICK_OUT 带 subReason）丢弃，避免出现契约未定义的文案分支
    expect(extractSessionInvalidPayload({ reason: 'BE_REPLACED', subReason: 'ADMIN_KICK' }).subReason).toBe('')
    expect(extractSessionInvalidPayload({ reason: '', subReason: 'ADMIN_KICK' }).subReason).toBe('')
  })

  it('drops unknown subReason without throwing (old backend / future value)', () => {
    expect(extractSessionInvalidPayload({
      reason: 'KICK_OUT',
      subReason: 'BRAND_NEW_REASON_FROM_FUTURE',
    })).toEqual({ reason: 'KICK_OUT', subReason: '', invalidAt: '', device: null })
  })
})

describe('time / device formatting（一期契约 §4）', () => {
  it('shows HH:mm for today and MM-dd HH:mm across days', () => {
    expect(formatSessionInvalidTime('2026-10-28T21:32:07', NOW)).toBe('21:32')
    expect(formatSessionInvalidTime('2026-10-27T21:32:07', NOW)).toBe('10-27 21:32')
    expect(formatSessionInvalidTime('not-a-date', NOW)).toBe('')
    expect(formatSessionInvalidTime('', NOW)).toBe('')
    expect(formatSessionInvalidTime(undefined, NOW)).toBe('')
  })

  it('builds the mobile device fragment: os · browser｜location｜HH:mm', () => {
    expect(buildSessionInvalidDeviceLine(
      { os: 'iOS', browser: '微信', loginTime: '2026-10-28T21:32:05' },
      NOW,
    )).toBe('iOS · 微信｜21:32')

    // 2026-11-01 契约增量：地点段（IP 仅管理端展示，移动端不下发渲染）
    expect(buildSessionInvalidDeviceLine(
      { os: 'iOS', browser: '微信', location: '上海市', loginTime: '2026-10-28T21:32:05' },
      NOW,
    )).toBe('iOS · 微信｜上海市｜21:32')

    expect(buildSessionInvalidDeviceLine({ os: 'Android', loginTime: '2026-10-28T21:32:05' }, NOW))
      .toBe('Android｜21:32')
    // 只有地点也展示（设备解析失败时用户仍能看到新登录的位置线索）
    expect(buildSessionInvalidDeviceLine({ location: '上海市', loginTime: '2026-10-28T21:32:05' }, NOW))
      .toBe('上海市｜21:32')
    // 全部为空：整行隐藏（不显示「未知设备」）
    expect(buildSessionInvalidDeviceLine(null, NOW)).toBe('')
    expect(buildSessionInvalidDeviceLine({}, NOW)).toBe('')
  })

  it('treats the Unknown UA fallback as missing in device fields', () => {
    expect(normalizeSessionInvalidDevice({
      browser: 'Unknown',
      ip: '192.168.8.105',
      location: '内网IP',
      os: 'Unknown',
    })).toEqual({ location: '内网IP' })
  })
})

describe('resolveSessionInvalidCopy（一期 reason 文案，中文逐字）', () => {
  beforeEach(() => {
    setStoredLocale('zh-CN')
  })

  afterEach(() => {
    setStoredLocale('zh-CN')
  })

  it('renders the BE_REPLACED title/lead/device line/tail and buttons', () => {
    const copy = resolveSessionInvalidCopy({
      reason: 'BE_REPLACED',
      invalidAt: '2026-10-28T21:32:07',
      device: { os: 'iOS', browser: '微信', location: '上海市', loginTime: '2026-10-28T21:32:05' },
      target: 'mall',
      now: NOW,
    })

    expect(copy.title).toBe('账号已在其他设备登录')
    expect(copy.lead).toBe('当前账号于 21:32 在另一台设备登录，你已被迫下线。')
    expect(copy.deviceLine).toBe('设备：iOS · 微信｜上海市｜21:32')
    expect(copy.tail).toBe('重新登录会使那台设备下线。')
    expect(copy.relogin).toBe('重新登录')
    expect(copy.exit).toBe('退出')
    expect(copy.canRelogin).toBe(true)
  })

  it('uses the delivery-specific title but the same body for the delivery target', () => {
    const copy = resolveSessionInvalidCopy({
      reason: 'BE_REPLACED',
      invalidAt: '2026-10-28T21:32:07',
      target: 'delivery',
      now: NOW,
    })

    expect(copy.title).toBe('配送账号已在其他设备登录')
    expect(copy.lead).toBe('当前账号于 21:32 在另一台设备登录，你已被迫下线。')
    expect(copy.deviceLine).toBe('')
  })

  it('falls back to device.loginTime when invalidAt is missing, then to the no-time copy', () => {
    expect(resolveSessionInvalidCopy({
      reason: 'BE_REPLACED',
      device: { os: 'iOS', browser: '微信', loginTime: '2026-10-28T21:32:05' },
      now: NOW,
    }).lead).toBe('当前账号于 21:32 在另一台设备登录，你已被迫下线。')

    const degraded = resolveSessionInvalidCopy({ reason: 'BE_REPLACED', now: NOW })
    expect(degraded.lead).toBe('当前账号已在其他设备登录，请重新登录后继续使用。')
    expect(degraded.deviceLine).toBe('')
  })

  it('uses the expired copy for TOKEN_EXPIRED', () => {
    const copy = resolveSessionInvalidCopy({ reason: 'TOKEN_EXPIRED', now: NOW })
    expect(copy.title).toBe('登录已过期')
    expect(copy.lead).toBe('登录状态已过期，请重新登录。')
    expect(copy.tail).toBe('')
    expect(copy.canRelogin).toBe(true)
  })

  it.each([
    'KICK_OUT',
    'TOKEN_INVALID',
    'NOT_TOKEN',
    'TOKEN_FREEZE',
    'NO_PREFIX',
    '',
  ] as const)('uses the generic copy for %s', (reason) => {
    const copy = resolveSessionInvalidCopy({ reason, now: NOW })
    expect(copy.title).toBe('登录状态已失效')
    expect(copy.lead).toBe('登录状态已失效，请重新登录。')
    expect(copy.deviceLine).toBe('')
    expect(copy.tail).toBe('')
    expect(copy.canRelogin).toBe(true)
    expect(resolveSessionInvalidTitle({ reason, target: 'mall' })).toBe('登录状态已失效')
  })
})

describe('resolveSessionInvalidCopy（二期 KICK_OUT + subReason 文案，中文逐字）', () => {
  beforeEach(() => {
    setStoredLocale('zh-CN')
  })

  afterEach(() => {
    setStoredLocale('zh-CN')
  })

  interface SubReasonCase {
    subReason: SessionInvalidSubReason
    title: string
    lead: string
    canRelogin: boolean
    singleActionText: string
  }

  /**
   * 二期契约 §2 表（移动端正文列）逐字。`singleActionText` 仅在 canRelogin=false 时生效：
   * ACCOUNT_DISABLED / ACCOUNT_DELETED / MEMBER_DISABLED → 「返回登录」，
   * TENANT_DISABLED → 「退出」。
   */
  const CASES: SubReasonCase[] = [
    {
      subReason: 'ADMIN_KICK',
      title: '已被管理员下线',
      lead: '管理员已将你下线。',
      canRelogin: true,
      singleActionText: '',
    },
    {
      subReason: 'PASSWORD_CHANGED',
      title: '密码已修改',
      lead: '密码已修改，请使用新密码重新登录。',
      canRelogin: true,
      singleActionText: '',
    },
    {
      subReason: 'ACCOUNT_DISABLED',
      title: '账号已被停用',
      lead: '该账号已被停用，请联系管理员。',
      canRelogin: false,
      singleActionText: '返回登录',
    },
    {
      subReason: 'ACCOUNT_DELETED',
      title: '账号已被删除',
      lead: '该账号已被删除。',
      canRelogin: false,
      singleActionText: '返回登录',
    },
    {
      subReason: 'TENANT_DISABLED',
      title: '所属租户已停用',
      lead: '账号所属店铺已停用。',
      canRelogin: false,
      singleActionText: '退出',
    },
    {
      subReason: 'MEMBER_DISABLED',
      title: '账号已被禁用',
      lead: '该账号已被禁用，请联系客服。',
      canRelogin: false,
      singleActionText: '返回登录',
    },
    {
      subReason: 'DELIVERY_REVOKED',
      title: '配送资格已变更',
      lead: '你的配送资格已变更，请重新登录配送端。',
      canRelogin: true,
      singleActionText: '',
    },
  ]

  it.each(CASES)('$subReason → $title / canRelogin=$canRelogin', (testCase) => {
    const copy = resolveSessionInvalidCopy({
      reason: 'KICK_OUT',
      subReason: testCase.subReason,
      now: NOW,
    })

    expect(copy.title).toBe(testCase.title)
    expect(copy.lead).toBe(testCase.lead)
    expect(copy.canRelogin).toBe(testCase.canRelogin)
    expect(copy.relogin).toBe('重新登录')
    expect(copy.exit).toBe('退出')
    if (!testCase.canRelogin)
      expect(copy.singleActionText).toBe(testCase.singleActionText)

    // 请求层抛错用标题与弹窗标题同源
    expect(resolveSessionInvalidTitle({ reason: 'KICK_OUT', subReason: testCase.subReason })).toBe(testCase.title)
  })

  it('falls back to the generic KICK_OUT copy for unknown subReason', () => {
    const copy = resolveSessionInvalidCopy({
      reason: 'KICK_OUT',
      // 运行时来自网络，可能比前端类型更新
      subReason: 'BRAND_NEW_REASON_FROM_FUTURE' as SessionInvalidSubReason,
      now: NOW,
    })
    expect(copy.title).toBe('登录状态已失效')
    expect(copy.lead).toBe('登录状态已失效，请重新登录。')
    expect(copy.canRelogin).toBe(true)
  })
})

describe('resolveSessionInvalidActions（按钮规则：二期契约 §2 硬要求）', () => {
  beforeEach(() => {
    setStoredLocale('zh-CN')
  })

  afterEach(() => {
    setStoredLocale('zh-CN')
  })

  it('canRelogin=true renders「重新登录」(primary/relogin) +「退出」(exit)', () => {
    const copy = resolveSessionInvalidCopy({ reason: 'BE_REPLACED', now: NOW })
    const actions = resolveSessionInvalidActions(copy)

    expect(actions).toEqual([
      { key: 'relogin', text: '重新登录', action: 'relogin', primary: true },
      { key: 'exit', text: '退出', action: 'exit', primary: false },
    ])
  })

  it.each([
    ['ACCOUNT_DISABLED', '返回登录'],
    ['ACCOUNT_DELETED', '返回登录'],
    ['TENANT_DISABLED', '退出'],
    ['MEMBER_DISABLED', '返回登录'],
  ] as const)('canRelogin=false for %s renders a single exitToLogin action「%s」', (subReason, text) => {
    const copy = resolveSessionInvalidCopy({ reason: 'KICK_OUT', subReason, now: NOW })
    const actions = resolveSessionInvalidActions(copy)

    // 不渲染「重新登录」（重登必然失败，避免白试）；落点是登录页而不是首页
    expect(actions).toHaveLength(1)
    expect(actions[0]).toEqual({ key: 'single', text, action: 'exitToLogin', primary: true })
    expect(actions.some(action => action.action === 'relogin')).toBe(false)
    expect(actions.some(action => action.action === 'exit')).toBe(false)
  })
})

describe('resolveSessionInvalidCopy（en-US）', () => {
  beforeEach(() => {
    setStoredLocale('en-US')
  })

  afterEach(() => {
    setStoredLocale('zh-CN')
  })

  it('keeps the same semantics in English', () => {
    const replaced = resolveSessionInvalidCopy({
      reason: 'BE_REPLACED',
      invalidAt: '2026-10-28T21:32:07',
      device: { os: 'iOS', browser: 'WeChat', loginTime: '2026-10-28T21:32:05' },
      now: NOW,
    })
    expect(replaced.title).toBe('Signed in on another device')
    expect(replaced.lead).toBe('Your account signed in on another device at 21:32 and you were signed out.')
    expect(replaced.deviceLine).toBe('Device: iOS · WeChat｜21:32')
    expect(replaced.tail).toBe('Signing in again will sign that device out.')
    expect(replaced.relogin).toBe('Sign in again')
    expect(replaced.exit).toBe('Exit')

    expect(resolveSessionInvalidCopy({ reason: 'TOKEN_EXPIRED', now: NOW }).title).toBe('Session expired')
    expect(resolveSessionInvalidCopy({ reason: 'KICK_OUT', now: NOW }).title).toBe('Session no longer valid')
    expect(resolveSessionInvalidTitle({ reason: 'BE_REPLACED', target: 'delivery' }))
      .toBe('Delivery account signed in on another device')
  })

  it.each([
    ['ADMIN_KICK', 'Signed out by an administrator', 'An administrator has signed you out.', true, ''],
    ['PASSWORD_CHANGED', 'Password changed', 'Your password was changed. Please sign in again with the new password.', true, ''],
    ['ACCOUNT_DISABLED', 'Account disabled', 'This account has been disabled. Please contact an administrator.', false, 'Back to sign in'],
    ['ACCOUNT_DELETED', 'Account deleted', 'This account has been deleted.', false, 'Back to sign in'],
    ['TENANT_DISABLED', 'Tenant disabled', 'The store this account belongs to has been disabled.', false, 'Exit'],
    ['MEMBER_DISABLED', 'Account banned', 'This account has been banned. Please contact customer service.', false, 'Back to sign in'],
    ['DELIVERY_REVOKED', 'Delivery access changed', 'Your delivery access has changed. Please sign in to the delivery app again.', true, ''],
  ] as const)('renders the English copy for %s', (subReason, title, lead, canRelogin, singleActionText) => {
    const copy = resolveSessionInvalidCopy({ reason: 'KICK_OUT', subReason, now: NOW })
    expect(copy.title).toBe(title)
    expect(copy.lead).toBe(lead)
    expect(copy.canRelogin).toBe(canRelogin)
    if (!canRelogin)
      expect(copy.singleActionText).toBe(singleActionText)
  })
})

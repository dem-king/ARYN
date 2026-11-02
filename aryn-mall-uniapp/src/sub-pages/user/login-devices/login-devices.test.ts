import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { setStoredLocale, translate } from '@/locales'
import enUS from '@/locales/en-US'
import zhCN from '@/locales/zh-CN'
import type { LoginSessionDevice } from '@/api/session-devices'
import { getMyLoginDevices, removeLoginDevice } from '@/api/session-devices'
import {
  canRemoveLoginDevice,
  loginTimeTimestamp,
  resolveDeviceLoginTimeText,
  resolveDeviceSystemText,
  resolveLoginDevicesView,
} from './presentation'
import { useLoginDevices } from './useLoginDevices'

/**
 * 登录设备页（三期契约 §2.1/§2.2/§3.2）测试。
 *
 * node 环境没有小程序渲染器，所以把「列表/空态/下线/当前设备不可下线」的决策
 * 全部收敛在 `presentation.ts` + `useLoginDevices.ts` 里直接断言；页面本身用
 * 源码契约测试钉住挂载点/二次确认/入口/状态分支，避免重构时退化。
 * API 走 alova 实例：路径首段 `auth`，boot 改写交给请求层（页面不拼 /boot）。
 */

vi.mock('@/api/session-devices', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/session-devices')>()
  return {
    ...actual,
    getMyLoginDevices: vi.fn(),
    removeLoginDevice: vi.fn(),
  }
})

const projectRoot = fileURLToPath(new URL('../../../../', import.meta.url))
function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

/** 注释里会解释「boot 改写为 /boot/...」，断言「不拼 /boot」前先剥注释 */
function code(relativePath: string) {
  return source(relativePath)
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/^\s*\/\/.*$/gm, '')
}

function device(overrides: Partial<LoginSessionDevice>): LoginSessionDevice {
  return {
    id: 'hash',
    browser: 'Chrome 120',
    os: 'Windows 10',
    location: '上海市',
    loginTime: '2026-10-29T09:10:00+08:00',
    current: false,
    ...overrides,
  }
}

const CURRENT = device({ id: 'current-hash', current: true, loginTime: '2026-10-29T09:00:00+08:00' })
const OLDER = device({ id: 'older-hash', loginTime: '2026-10-28T21:00:00+08:00' })
const NEWER = device({ id: 'newer-hash', loginTime: '2026-10-29T08:00:00+08:00' })

function mockListResponse(value: unknown) {
  const send = vi.fn().mockResolvedValue(value)
  vi.mocked(getMyLoginDevices).mockReturnValue({ send } as any)
  return send
}

function mockRemoveResponse() {
  const send = vi.fn().mockResolvedValue(undefined)
  vi.mocked(removeLoginDevice).mockReturnValue({ send } as any)
  return send
}

describe('登录设备：列表视图（当前设备 / 其他会话 / 空态）', () => {
  it('splits current from others and sorts other sessions by login time desc', () => {
    const view = resolveLoginDevicesView([NEWER, CURRENT, OLDER])

    expect(view.current?.id).toBe('current-hash')
    expect(view.others.map(item => item.id)).toEqual(['newer-hash', 'older-hash'])
    expect(view.isEmpty).toBe(false)
  })

  it('returns an empty view for null/empty/invalid payloads', () => {
    expect(resolveLoginDevicesView(null)).toEqual({ current: null, others: [], isEmpty: true })
    expect(resolveLoginDevicesView([])).toEqual({ current: null, others: [], isEmpty: true })
    expect(resolveLoginDevicesView(undefined).isEmpty).toBe(true)
  })

  it('treats every row as "other" when the backend does not mark current', () => {
    const view = resolveLoginDevicesView([OLDER, NEWER])
    expect(view.current).toBeNull()
    expect(view.others).toHaveLength(2)
  })

  it('supports both ISO and space-separated login times when sorting', () => {
    expect(loginTimeTimestamp('2026-10-29T09:10:00+08:00')).toBeGreaterThan(0)
    expect(loginTimeTimestamp('2026-10-28 21:00:00')).toBeGreaterThan(0)
    expect(loginTimeTimestamp(undefined)).toBe(0)
    expect(loginTimeTimestamp('not-a-date')).toBe(0)
  })
})

describe('登录设备：当前设备不可下线', () => {
  it('only non-current sessions with an id can be removed', () => {
    expect(canRemoveLoginDevice(CURRENT)).toBe(false)
    expect(canRemoveLoginDevice(OLDER)).toBe(true)
    expect(canRemoveLoginDevice(device({ id: '' }))).toBe(false)
    expect(canRemoveLoginDevice(null)).toBe(false)
    expect(canRemoveLoginDevice(undefined)).toBe(false)
  })
})

describe('登录设备：设备文案', () => {
  it('renders os · browser and omits empty fragments', () => {
    expect(resolveDeviceSystemText(device({}))).toBe('Windows 10 · Chrome 120')
    expect(resolveDeviceSystemText(device({ browser: '' }))).toBe('Windows 10')
    expect(resolveDeviceSystemText(device({ os: '  ', browser: '' }))).toBe('')
    expect(resolveDeviceSystemText(null)).toBe('')
  })

  it('formats login time as full date and hides invalid values', () => {
    expect(resolveDeviceLoginTimeText(device({ loginTime: '2026-10-29T09:10:00' }))).toBe('2026-10-29 09:10')
    expect(resolveDeviceLoginTimeText(device({ loginTime: '2026-10-28T21:00:00' }))).toBe('2026-10-28 21:00')
    expect(resolveDeviceLoginTimeText(device({ loginTime: '' }))).toBe('')
    expect(resolveDeviceLoginTimeText(null)).toBe('')
  })
})

describe('登录设备：useLoginDevices 加载/下线逻辑', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('loads and normalizes the session list', async () => {
    mockListResponse([NEWER, CURRENT, { id: '' }, null])

    const devices = useLoginDevices()
    await expect(devices.load()).resolves.toBe(true)

    expect(devices.loading.value).toBe(false)
    expect(devices.loaded.value).toBe(true)
    expect(devices.loadError.value).toBe('')
    // 缺 id 的脏数据被丢弃
    expect(devices.devices.value.map(item => item.id)).toEqual(['newer-hash', 'current-hash'])
    expect(devices.view.value.current?.id).toBe('current-hash')
  })

  it('keeps the page functional when loading fails (returns false + message)', async () => {
    vi.mocked(getMyLoginDevices).mockReturnValue({
      send: vi.fn().mockRejectedValue(new Error('network down')),
    } as any)

    const devices = useLoginDevices()
    await expect(devices.load()).resolves.toBe(false)

    expect(devices.loading.value).toBe(false)
    expect(devices.loaded.value).toBe(false)
    expect(devices.loadError.value).toBe('network down')
  })

  it('refuses to remove the current device without calling the API', async () => {
    mockListResponse([CURRENT, NEWER])
    const devices = useLoginDevices()
    await devices.load()
    const removeSend = mockRemoveResponse()

    await expect(devices.remove('current-hash')).resolves.toBe('blocked-current')
    expect(removeSend).not.toHaveBeenCalled()
  })

  it('removes another session then reloads the list from the server', async () => {
    const listSend = mockListResponse([CURRENT, NEWER])
    const removeSend = mockRemoveResponse()
    const devices = useLoginDevices()
    await devices.load()

    const nextList = [CURRENT]
    listSend.mockResolvedValueOnce(nextList)

    await expect(devices.remove('newer-hash')).resolves.toBe('removed')
    expect(removeSend).toHaveBeenCalledTimes(1)
    // 下线后重新拉列表（以服务端结果为准，不本地删行）
    expect(listSend).toHaveBeenCalledTimes(2)
    expect(devices.devices.value.map(item => item.id)).toEqual(['current-hash'])
  })

  it('returns failed when the DELETE request fails and keeps the list', async () => {
    mockListResponse([CURRENT, NEWER])
    vi.mocked(removeLoginDevice).mockReturnValue({
      send: vi.fn().mockRejectedValue(new Error('boom')),
    } as any)
    const devices = useLoginDevices()
    await devices.load()

    await expect(devices.remove('newer-hash')).resolves.toBe('failed')
    expect(devices.devices.value).toHaveLength(2)
    expect(devices.removingId.value).toBe('')
  })

  it('rejects concurrent remove attempts (anti double-tap)', async () => {
    mockListResponse([CURRENT, NEWER, OLDER])
    let resolveRemove: () => void = () => {}
    vi.mocked(removeLoginDevice).mockReturnValue({
      send: vi.fn(() => new Promise<void>((resolve) => {
        resolveRemove = resolve
      })),
    } as any)
    const listSend = mockListResponse([CURRENT, NEWER, OLDER])
    const devices = useLoginDevices()
    await devices.load()
    listSend.mockResolvedValue([CURRENT, NEWER, OLDER])

    const first = devices.remove('newer-hash')
    await expect(devices.remove('older-hash')).resolves.toBe('failed')
    resolveRemove()
    await expect(first).resolves.toBe('removed')
  })
})

describe('登录设备：API 契约（三期契约 §2）', () => {
  it('uses /auth/session/devices paths and leaves boot rewriting to the request layer', () => {
    const api = code('src/api/session-devices.ts')

    expect(api).toContain('\'/auth/session/devices\'')
    expect(api).toContain('/auth/session/devices/${encodeURIComponent(id)}')
    expect(api).toContain('alovaInstance.Get')
    expect(api).toContain('alovaInstance.Delete')
    // 页面/接口都不许自己拼 /boot（双模式适配规则）
    expect(api).not.toContain('/boot/')
  })
})

describe('登录设备：页面与入口契约', () => {
  const page = source('src/sub-pages/user/login-devices/index.vue')

  it('registers the sub-page with the frozen name/title', () => {
    expect(page).toContain('name: \'user-login-devices\'')
    expect(page).toContain('navigationBarTitleText: \'登录设备\'')
    expect(page).toContain('useLoginDevices')
    expect(page).toContain('<hr-navbar')
  })

  it('renders current device / others / loading / error / empty states', () => {
    expect(page).toContain('view.current')
    expect(page).toContain('view.others')
    expect(page).toContain('loading && !loaded')
    expect(page).toContain('!loaded && loadError')
    expect(page).toContain('view.isEmpty')
    expect(page).toContain('loginDevices.policyTip')
  })

  it('confirms before kicking and never offers kick on the current device', () => {
    // 二次确认 → DELETE（通过 composable remove）
    expect(page).toContain('useGlobalMessage')
    expect(page).toContain('confirm({')
    expect(page).toContain('canRemoveLoginDevice(device)')
    expect(page).toContain('await remove(id)')
    // 当前设备卡片上没有「下线」按钮（只有 others 列表渲染 kick）
    expect(page).toContain('v-for="device in view.others"')
    expect(page).not.toContain('uni.showModal')
  })

  it('reuses the shared logout logic and surfaces errors via useGlobalToast', () => {
    expect(page).toContain('useUserLogout')
    expect(page).toContain('confirmLogout')
    expect(page).toContain('useGlobalToast')
    expect(page).toContain('loginDevices.kickFailed')
  })

  it('adds the entry on the personal settings page', () => {
    const settings = source('src/sub-pages/user/user-setting/index.vue')
    expect(settings).toContain('loginDevices.entry')
    expect(settings).toContain('user-login-devices')
    // 退出登录收敛到同一个 composable（不再各自复制一份确认框逻辑）
    expect(settings).toContain('useUserLogout')
  })
})

describe('登录设备：中英双语文案完整性', () => {
  function collectKeys(value: Record<string, unknown>, prefix = ''): string[] {
    return Object.entries(value).flatMap(([key, child]) => {
      const path = prefix ? `${prefix}.${key}` : key
      return child && typeof child === 'object' && !Array.isArray(child)
        ? collectKeys(child as Record<string, unknown>, path)
        : [path]
    })
  }

  function collectEntries(value: Record<string, unknown>): Array<[string, string]> {
    return collectKeys(value).map((key) => {
      const segments = key.split('.')
      let current: any = value
      for (const segment of segments)
        current = current?.[segment]
      return [key, String(current)] as [string, string]
    })
  }

  const zhKeys = collectKeys((zhCN as any).loginDevices ?? {})
  const enKeys = collectKeys((enUS as any).loginDevices ?? {})

  it('keeps zh/en keys in sync and every value non-empty', () => {
    expect(zhKeys.length).toBeGreaterThan(0)
    expect(enKeys.sort()).toEqual(zhKeys.sort())
    for (const [key, text] of [...collectEntries((zhCN as any).loginDevices), ...collectEntries((enUS as any).loginDevices)])
      expect(text.trim(), key).not.toBe('')
  })

  it('has no Chinese left in the English copy', () => {
    const offenders = collectEntries((enUS as any).loginDevices)
      .filter(([, text]) => /[\u4E00-\u9FFF]/.test(text))
      .map(([key]) => key)
    expect(offenders).toEqual([])
  })

  it('resolves runtime copy in both locales', () => {
    setStoredLocale('zh-CN')
    expect(translate('loginDevices.kick')).toBe('下线')
    expect(translate('loginDevices.location', { location: '上海市' })).toBe('地点：上海市')
    setStoredLocale('en-US')
    expect(translate('loginDevices.kick')).toBe('Sign out')
    setStoredLocale('zh-CN')
  })
})

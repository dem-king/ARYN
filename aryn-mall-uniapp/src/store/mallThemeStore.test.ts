import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { getMallTheme } from '@/api/promotion/pageDesign'
import { DEFAULT_MALL_THEME, useMallThemeStore } from './mallThemeStore'

/**
 * uni storage 与商城主题接口的测试替身：
 * store 读写缓存 / 拉取默认主题都经这两个出口，其余 uni 能力不触达。
 */
const storage = new Map<string, unknown>()

vi.mock('@/api/promotion/pageDesign', () => ({
  getMallTheme: vi.fn(),
}))

const themeServer = {
  navigationColor: '#FF5500',
  navigationTextColor: '#ffffff',
  pageBackgroundColor: '#FFF3EC',
  primaryColor: '#FF5500',
  radius: 8,
  secondaryColor: '#FF8A66',
  themeId: 'theme-1',
  themeName: '测试',
}

describe('mallThemeStore', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.stubEnv('VITE_TENANT_ID', 'tenant-test')
    vi.stubGlobal('uni', {
      getStorageSync: (key: string) => storage.get(key) ?? '',
      removeStorageSync: (key: string) => storage.delete(key),
      setStorageSync: (key: string, value: unknown) => storage.set(key, value),
    })
    storage.clear()
    vi.mocked(getMallTheme).mockReset()
    setActivePinia(createPinia())
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllEnvs()
    vi.unstubAllGlobals()
  })

  it('ensureLoaded 拉取默认主题并写入带租户后缀的缓存', async () => {
    vi.mocked(getMallTheme).mockResolvedValue(themeServer)
    const store = useMallThemeStore()

    await store.ensureLoaded()

    expect(store.primaryColor).toBe('#FF5500')
    expect(store.secondaryColor).toBe('#FF8A66')
    expect(store.pageBackgroundColor).toBe('#FFF3EC')
    expect(store.navigationColor).toBe('#FF5500')
    expect(store.themeId).toBe('theme-1')
    expect(storage.get('mall-theme-cache:tenant-test')).toMatchObject({
      primaryColor: '#FF5500',
    })
  })

  it('缓存脏值被逐字段校验拦下：非法色值不渗进状态', async () => {
    storage.set('mall-theme-cache:tenant-test', {
      primaryColor: 'red',
      secondaryColor: '#ZZZZZZ',
    })
    const store = useMallThemeStore()
    expect(store.primaryColor).toBe(DEFAULT_MALL_THEME.primaryColor)

    vi.mocked(getMallTheme).mockResolvedValue({
      ...themeServer,
      primaryColor: 'not-a-color',
    })
    await store.ensureLoaded()
    expect(store.primaryColor).toBe(DEFAULT_MALL_THEME.primaryColor)
  })

  it('resolved 后 ensureLoaded 不再请求，refresh 强制重拉', async () => {
    vi.setSystemTime(1_000)
    vi.mocked(getMallTheme).mockResolvedValue(themeServer)
    const store = useMallThemeStore()
    await store.ensureLoaded()

    store.primaryColor = '#123456'
    await store.ensureLoaded()
    expect(store.primaryColor).toBe('#123456')

    vi.mocked(getMallTheme).mockResolvedValue({
      ...themeServer,
      primaryColor: '#00B8A9',
    })
    await store.refresh()
    expect(store.primaryColor).toBe('#00B8A9')
    expect(getMallTheme).toHaveBeenCalledTimes(2)
  })

  it('并发请求去重：同时多次调用只打一次接口', async () => {
    vi.mocked(getMallTheme).mockResolvedValue(themeServer)
    const store = useMallThemeStore()

    await Promise.all([
      store.ensureLoaded(),
      store.ensureLoaded(),
      store.refresh({ maxAgeMs: 0 }),
    ])

    expect(getMallTheme).toHaveBeenCalledTimes(1)
  })

  it('refresh 带节流窗口：窗口内跳过、窗口外或未 resolved 时重拉', async () => {
    vi.setSystemTime(1_000)
    vi.mocked(getMallTheme).mockResolvedValue(themeServer)
    const store = useMallThemeStore()
    await store.refresh({ maxAgeMs: 5 * 60 * 1000 })
    expect(getMallTheme).toHaveBeenCalledTimes(1)

    // 首拉后 resolved=true：窗口内不再请求
    vi.setSystemTime(1_000 + 4 * 60 * 1000)
    await store.refresh({ maxAgeMs: 5 * 60 * 1000 })
    expect(getMallTheme).toHaveBeenCalledTimes(1)

    // 超过窗口（App 回前台场景）重新拉取
    vi.setSystemTime(1_000 + 5 * 60 * 1000 + 1)
    await store.refresh({ maxAgeMs: 5 * 60 * 1000 })
    expect(getMallTheme).toHaveBeenCalledTimes(2)
  })

  it('服务端返回 null（默认主题被删/未设置）时回内置默认并清缓存', async () => {
    vi.mocked(getMallTheme).mockResolvedValue(null)
    const store = useMallThemeStore()

    await store.ensureLoaded()

    expect(store.primaryColor).toBe(DEFAULT_MALL_THEME.primaryColor)
    expect(store.themeId).toBe(DEFAULT_MALL_THEME.themeId)
    expect(storage.has('mall-theme-cache:tenant-test')).toBe(false)
    // 已完成一次拉取：后续不再重复请求
    await store.ensureLoaded()
    expect(getMallTheme).toHaveBeenCalledTimes(1)
  })

  it('请求失败保持当前配色，且标记已 resolved', async () => {
    vi.mocked(getMallTheme).mockRejectedValue(new Error('network'))
    const store = useMallThemeStore()

    await store.ensureLoaded()

    expect(store.primaryColor).toBe(DEFAULT_MALL_THEME.primaryColor)
    expect(getMallTheme).toHaveBeenCalledTimes(1)
    await store.ensureLoaded()
    expect(getMallTheme).toHaveBeenCalledTimes(1)
  })

  it('reset 清空状态与缓存，避免跨租户残留', async () => {
    vi.mocked(getMallTheme).mockResolvedValue(themeServer)
    const store = useMallThemeStore()
    await store.ensureLoaded()

    store.reset()

    expect(store.primaryColor).toBe(DEFAULT_MALL_THEME.primaryColor)
    expect(store.resolved).toBe(false)
    expect(storage.has('mall-theme-cache:tenant-test')).toBe(false)
  })
})

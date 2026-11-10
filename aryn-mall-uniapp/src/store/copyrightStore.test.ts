import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { getAppCopyrightConfig } from '@/api/upms/copyright'
import { DEFAULT_COPYRIGHT_CONFIG, useCopyrightStore } from './copyrightStore'

/**
 * 版权配置 store 的测试替身：只触及 uni storage 与免登接口两个出口。
 */
const storage = new Map<string, unknown>()

vi.mock('@/api/upms/copyright', () => ({
  getAppCopyrightConfig: vi.fn(),
}))

const serverConfig = {
  clientType: 'app',
  logoUrl: 'https://cdn.example.com/logo.png',
  name: '悦航购',
  copyrightText: '天启雨数版权所有',
  copyrightLink: 'https://www.iaryn.cn',
  icp: '沪ICP备00000000号',
  icpLink: 'https://beian.miit.gov.cn/',
}

describe('copyrightStore', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.stubEnv('VITE_TENANT_ID', 'tenant-test')
    vi.stubGlobal('uni', {
      getStorageSync: (key: string) => storage.get(key) ?? '',
      removeStorageSync: (key: string) => storage.delete(key),
      setStorageSync: (key: string, value: unknown) => storage.set(key, value),
    })
    storage.clear()
    vi.mocked(getAppCopyrightConfig).mockReset()
    setActivePinia(createPinia())
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllEnvs()
    vi.unstubAllGlobals()
  })

  it('ensureLoaded 拉取配置并写入带租户后缀的缓存', async () => {
    vi.mocked(getAppCopyrightConfig).mockResolvedValue(serverConfig)
    const store = useCopyrightStore()

    await store.ensureLoaded()

    expect(store.name).toBe('悦航购')
    expect(store.logoUrl).toBe('https://cdn.example.com/logo.png')
    expect(store.copyrightLine).toBe('天启雨数版权所有')
    expect(store.icp).toBe('沪ICP备00000000号')
    expect(storage.get('copyright-config-cache:tenant-test')).toMatchObject({
      name: '悦航购',
      copyrightText: '天启雨数版权所有',
    })
  })

  it('冷启动首帧读缓存：无需等待接口即有品牌', () => {
    storage.set('copyright-config-cache:tenant-test', {
      name: '缓存品牌',
      logoUrl: '/cached.png',
    })
    const store = useCopyrightStore()
    expect(store.name).toBe('缓存品牌')
    expect(store.logoUrl).toBe('/cached.png')
  })

  it('缓存脏值被逐字段过滤：非字符串不渗进状态', () => {
    storage.set('copyright-config-cache:tenant-test', {
      name: 123,
      logoUrl: null,
      copyrightText: '合法文案',
    })
    const store = useCopyrightStore()
    expect(store.name).toBe(DEFAULT_COPYRIGHT_CONFIG.name)
    expect(store.logoUrl).toBe(DEFAULT_COPYRIGHT_CONFIG.logoUrl)
    expect(store.copyrightLine).toBe('合法文案')
  })

  it('链接只放行 http/https：javascript: 与站内相对路径不渲染为外链', () => {
    const store = useCopyrightStore()
    store.copyrightLink = 'javascript:alert(1)'
    store.icpLink = '/about'
    expect(store.safeCopyrightLink).toBe('')
    expect(store.safeIcpLink).toBe('')

    store.copyrightLink = 'https://www.iaryn.cn'
    expect(store.safeCopyrightLink).toBe('https://www.iaryn.cn')
  })

  it('服务端全空（未配置）时回内置默认并清缓存', async () => {
    storage.set('copyright-config-cache:tenant-test', { name: '旧品牌' })
    vi.mocked(getAppCopyrightConfig).mockResolvedValue({
      clientType: 'app',
      logoUrl: '',
      name: '',
      copyrightText: '',
      copyrightLink: '',
      icp: '',
      icpLink: '',
    })
    const store = useCopyrightStore()

    await store.ensureLoaded()

    expect(store.name).toBe('')
    expect(store.logoUrl).toBe(DEFAULT_COPYRIGHT_CONFIG.logoUrl)
    expect(storage.has('copyright-config-cache:tenant-test')).toBe(false)
  })

  it('请求失败保持缓存值并标记 resolved', async () => {
    storage.set('copyright-config-cache:tenant-test', { name: '缓存品牌' })
    vi.mocked(getAppCopyrightConfig).mockRejectedValue(new Error('network'))
    const store = useCopyrightStore()

    await store.ensureLoaded()

    expect(store.name).toBe('缓存品牌')
    await store.ensureLoaded()
    expect(getAppCopyrightConfig).toHaveBeenCalledTimes(1)
  })

  it('并发请求去重 + refresh 节流窗口', async () => {
    vi.setSystemTime(1_000)
    vi.mocked(getAppCopyrightConfig).mockResolvedValue(serverConfig)
    const store = useCopyrightStore()

    await Promise.all([store.ensureLoaded(), store.ensureLoaded()])
    expect(getAppCopyrightConfig).toHaveBeenCalledTimes(1)

    vi.setSystemTime(1_000 + 60 * 1000)
    await store.refresh({ maxAgeMs: 5 * 60 * 1000 })
    expect(getAppCopyrightConfig).toHaveBeenCalledTimes(1)

    vi.setSystemTime(1_000 + 5 * 60 * 1000 + 1)
    await store.refresh({ maxAgeMs: 5 * 60 * 1000 })
    expect(getAppCopyrightConfig).toHaveBeenCalledTimes(2)
  })

  it('reset 清状态与缓存，避免跨租户/跨账号残留', async () => {
    vi.mocked(getAppCopyrightConfig).mockResolvedValue(serverConfig)
    const store = useCopyrightStore()
    await store.ensureLoaded()

    store.reset()

    expect(store.name).toBe(DEFAULT_COPYRIGHT_CONFIG.name)
    expect(store.resolved).toBe(false)
    expect(storage.has('copyright-config-cache:tenant-test')).toBe(false)
  })
})

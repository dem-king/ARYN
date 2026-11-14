import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { getAppStatusTipConfig } from '@/api/upms/status-tip'
import { STATUS_TIP_REFRESH_THROTTLE_MS, useStatusTipStore } from './statusTipStore'

const storage = new Map<string, unknown>()

vi.mock('@/api/upms/status-tip', () => ({
  getAppStatusTipConfig: vi.fn(),
}))

const serverConfig = {
  searchUrl: 'https://cdn.example.com/search.png',
  networkUrl: '/upms/file/local/tenant-test/network.png',
  contentUrl: '',
  collectUrl: '',
  commentUrl: '',
  haloUrl: '',
  messageUrl: 'https://cdn.example.com/message.png',
  emptyCartUrl: 'https://cdn.example.com/empty-cart.png',
  emptyOrderUrl: '',
  emptySeckillUrl: '',
}

describe('statusTipStore', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.stubEnv('VITE_TENANT_ID', 'tenant-test')
    vi.stubEnv('VITE_OPEN_BOOT', 'false')
    vi.stubGlobal('uni', {
      getStorageSync: (key: string) => storage.get(key) ?? '',
      removeStorageSync: (key: string) => storage.delete(key),
      setStorageSync: (key: string, value: unknown) => storage.set(key, value),
    })
    storage.clear()
    vi.mocked(getAppStatusTipConfig).mockReset()
    setActivePinia(createPinia())
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllEnvs()
    vi.unstubAllGlobals()
  })

  it('ensureLoaded 把扁平字段映射到预设位并写入租户后缀缓存', async () => {
    vi.mocked(getAppStatusTipConfig).mockResolvedValue(serverConfig)
    const store = useStatusTipStore()

    await store.ensureLoaded()

    expect(store.imageFor('search')).toBe('https://cdn.example.com/search.png')
    expect(store.imageFor('message')).toBe('https://cdn.example.com/message.png')
    expect(store.imageFor('empty-cart')).toBe('https://cdn.example.com/empty-cart.png')
    expect(storage.get('status-tip-cache:tenant-test')).toMatchObject({
      'search': 'https://cdn.example.com/search.png',
      'message': 'https://cdn.example.com/message.png',
      'empty-cart': 'https://cdn.example.com/empty-cart.png',
    })
  })

  it('未配置的预设位回落内置静态图', async () => {
    vi.mocked(getAppStatusTipConfig).mockResolvedValue(serverConfig)
    const store = useStatusTipStore()

    await store.ensureLoaded()

    expect(store.imageFor('content')).toMatch(/\/status-tip\/content\.png$/)
    expect(store.imageFor('collect')).toMatch(/\/status-tip\/collect\.png$/)
    expect(store.imageFor('empty-order')).toBe('/static/promotion/empty/empty-order.png')
    expect(store.imageFor('empty-seckill')).toBe('/static/promotion/empty/empty-seckill.png')
  })

  it('站内相对路径补成可渲染地址（未配置 API 域名时保持相对路径）', async () => {
    vi.mocked(getAppStatusTipConfig).mockResolvedValue(serverConfig)
    const store = useStatusTipStore()

    await store.ensureLoaded()

    expect(store.imageFor('network')).toBe('/upms/file/local/tenant-test/network.png')
  })

  it('冷启动首帧读缓存；缓存里的非法协议被过滤后回落内置图', () => {
    storage.set('status-tip-cache:tenant-test', {
      search: 'https://cdn.example.com/cached.png',
      halo: 'javascript:alert(1)',
    })
    const store = useStatusTipStore()

    expect(store.imageFor('search')).toBe('https://cdn.example.com/cached.png')
    expect(store.imageFor('halo')).toMatch(/\/status-tip\/halo\.png$/)
  })

  it('拉取失败时保留已有缓存', async () => {
    storage.set('status-tip-cache:tenant-test', { search: 'https://cdn.example.com/cached.png' })
    vi.mocked(getAppStatusTipConfig).mockRejectedValue(new Error('network down'))
    const store = useStatusTipStore()

    await store.ensureLoaded()

    expect(store.imageFor('search')).toBe('https://cdn.example.com/cached.png')
  })

  it('服务端明确返回空配置时清缓存并回落内置图', async () => {
    storage.set('status-tip-cache:tenant-test', { search: 'https://cdn.example.com/cached.png' })
    vi.mocked(getAppStatusTipConfig).mockResolvedValue({
      searchUrl: '',
      networkUrl: '',
      contentUrl: '',
      collectUrl: '',
      commentUrl: '',
      haloUrl: '',
      messageUrl: '',
      emptyCartUrl: '',
      emptyOrderUrl: '',
      emptySeckillUrl: '',
    })
    const store = useStatusTipStore()

    await store.ensureLoaded()

    expect(storage.has('status-tip-cache:tenant-test')).toBe(false)
    expect(store.imageFor('search')).toMatch(/\/status-tip\/search\.png$/)
  })

  it('回前台刷新按 5 分钟节流', async () => {
    vi.mocked(getAppStatusTipConfig).mockResolvedValue(serverConfig)
    const store = useStatusTipStore()
    await store.ensureLoaded()
    expect(getAppStatusTipConfig).toHaveBeenCalledTimes(1)

    vi.advanceTimersByTime(60 * 1000)
    await store.refresh({ maxAgeMs: STATUS_TIP_REFRESH_THROTTLE_MS })
    expect(getAppStatusTipConfig).toHaveBeenCalledTimes(1)

    vi.advanceTimersByTime(STATUS_TIP_REFRESH_THROTTLE_MS)
    await store.refresh({ maxAgeMs: STATUS_TIP_REFRESH_THROTTLE_MS })
    expect(getAppStatusTipConfig).toHaveBeenCalledTimes(2)
  })
})

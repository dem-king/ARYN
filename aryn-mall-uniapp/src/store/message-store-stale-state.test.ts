import type { PiniaPluginContext } from 'pinia'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createApp, nextTick } from 'vue'
import { useAuthStore } from '@/store/authStore'
import { useDeliveryMessageStore } from './deliveryMessageStore'
import { useMessageStore } from './messageStore'
import { persistPlugin } from './persist'

/**
 * 2026-11-05 线上问题回归：小程序重新编译 / 冷启动后，通用 Pinia 持久化会把上一次
 * 进程的 `message.socketState='open'` 恢复出来，`connect()` 命中
 * `socketState === 'open' → return` 提前返回，真实 socket 从未创建 —— 界面显示
 * 「消息实时同步」，客服回复和会话状态推送却全部收不到（C 端排队状态滞留）。
 *
 * 断言的是「用户可见后果」：
 *  · 持久化水合后 socketState 必须是本次进程的默认值（closed），未读数仍保留；
 *  · 即使 state 里残留伪 open/connecting，connect() 也会校正并真实建连；
 *  · 配送端 deliveryMessage store 同样自愈。
 */

interface FakeSocketTask {
  onOpen: (cb: () => void) => void
  onMessage: (cb: (event: { data: unknown }) => void) => void
  onClose: (cb: () => void) => void
  onError: (cb: () => void) => void
  close: ReturnType<typeof vi.fn>
  send: ReturnType<typeof vi.fn>
  emitOpen: () => void
}

const storage = new Map<string, unknown>()
let sockets: FakeSocketTask[] = []

function installUniMock() {
  sockets = []
  vi.stubGlobal('uni', {
    getStorageSync: (key: string) => storage.get(key) ?? '',
    setStorageSync: (key: string, value: unknown) => storage.set(key, value),
    removeStorageSync: (key: string) => storage.delete(key),
    $emit: vi.fn(),
    connectSocket: vi.fn(() => {
      const callbacks: {
        open?: () => void
        message?: (event: { data: unknown }) => void
        close?: () => void
        error?: () => void
      } = {}
      const task = {
        onOpen: (cb: () => void) => {
          callbacks.open = cb
        },
        onMessage: (cb: (event: { data: unknown }) => void) => {
          callbacks.message = cb
        },
        onClose: (cb: () => void) => {
          callbacks.close = cb
        },
        onError: (cb: () => void) => {
          callbacks.error = cb
        },
        close: vi.fn(),
        send: vi.fn(),
        emitOpen: () => callbacks.open?.(),
      } as FakeSocketTask
      sockets.push(task)
      return task
    }),
  })
  // 源码里的 CommonUtil 由 unplugin-auto-import 注入；测试环境没有该插件，用全局替身
  vi.stubGlobal('CommonUtil', {
    deepClone: (value: unknown) => JSON.parse(JSON.stringify(value)),
  })
  vi.stubGlobal('window', { location: { origin: 'http://localhost:5173' } })
}

function createPiniaWithPersist() {
  const pinia = createPinia()
  pinia.use((context: PiniaPluginContext) => persistPlugin(context))
  // Pinia 2.x 的插件只有在 pinia 被安装到 app 后才会从 toBeInstalled 移入 _p；
  // 测试里不 mount，只用空 app 安装以复现运行时插件链路。
  createApp({ render: () => null }).use(pinia)
  setActivePinia(pinia)
  return pinia
}

describe('message store stale persisted socketState recovery', () => {
  beforeEach(() => {
    storage.clear()
    installUniMock()
  })

  afterEach(async () => {
    try {
      useMessageStore().disconnect()
      useDeliveryMessageStore().disconnect()
    }
    catch {
      // 测试可能已重置 pinia，忽略清理失败
    }
    // $subscribe 是异步 flush：先等挂起的落盘回调跑完再撤掉 uni 替身
    await nextTick()
    vi.unstubAllGlobals()
    vi.clearAllMocks()
  })

  it('hydrates persisted unread counters but resets socketState to closed', () => {
    storage.set('message', {
      conversationUnread: 3,
      noticeUnread: 2,
      socketState: 'open',
    })
    createPiniaWithPersist()

    const store = useMessageStore()

    expect(store.socketState).toBe('closed')
    expect(store.conversationUnread).toBe(3)
    expect(store.noticeUnread).toBe(2)
  })

  it('writes state back without socketState', async () => {
    createPiniaWithPersist()
    const store = useMessageStore()
    store.noticeUnread = 7
    await nextTick()

    const persisted = storage.get('message') as Record<string, unknown>
    expect(persisted.noticeUnread).toBe(7)
    expect(persisted).not.toHaveProperty('socketState')
  })

  it.each(['open', 'connecting'] as const)(
    'reconnects when state claims %s but no in-memory socket exists',
    async (staleState) => {
      createPiniaWithPersist()
      useAuthStore().setLoginState('token-mall')
      const store = useMessageStore()
      vi.spyOn(store, 'refreshUnread').mockResolvedValue(undefined)
      store.socketState = staleState

      await store.connect()

      expect(sockets).toHaveLength(1)
      sockets[0].emitOpen()
      expect(store.socketState).toBe('open')
    },
  )

  it('reports sync only after the socket is really open', async () => {
    createPiniaWithPersist()
    useAuthStore().setLoginState('token-mall')
    const store = useMessageStore()
    vi.spyOn(store, 'refreshUnread').mockResolvedValue(undefined)
    store.socketState = 'open'

    await store.connect()

    // 校正后的真实状态是 connecting，不能沿用持久化的假 open
    expect(store.socketState).toBe('connecting')
    sockets[0].emitOpen()
    expect(store.socketState).toBe('open')
  })

  it('reconnects the delivery message store from a stale persisted state', async () => {
    storage.set('deliveryToken', 'token-delivery')
    createPiniaWithPersist()
    const store = useDeliveryMessageStore()
    vi.spyOn(store, 'refreshUnread').mockResolvedValue(undefined)
    store.socketState = 'open'

    await store.connect()

    expect(sockets).toHaveLength(1)
    sockets[0].emitOpen()
    expect(store.socketState).toBe('open')
  })
})

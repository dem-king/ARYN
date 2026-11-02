import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '@/store/authStore'
import { useSessionStore } from '@/store/sessionStore'
import { useDeliveryMessageStore } from './deliveryMessageStore'
import { useMessageStore } from './messageStore'

/**
 * WS 主动推送消费（三期契约 §1/§3.2）。
 *
 * 断言的是「用户可见后果」而不是实现细节：
 *  · 收到 `SESSION_INVALID` → 进入会话失效态（reason/subReason/device 全链路落地）、
 *    断开连接、**不再**刷新未读/发 message-push（那时接口已被 beforeRequest 短路）；
 *  · 幂等：并发/重复推送保留首次原因，不重复动作；
 *  · PONG / 非 JSON / 未知 eventType 保持一期原逻辑（刷新未读 + 推送事件）。
 *
 * 测试替身：uni.connectSocket 返回可手动触发 onMessage 的假 SocketTask；
 * getPageOrigin 在 vitest 下走 `#ifdef H5` 分支（源码未预编译），因此需要 stub window。
 */

interface FakeSocketTask {
  onOpen: (cb: () => void) => void
  onMessage: (cb: (event: { data: unknown }) => void) => void
  onClose: (cb: () => void) => void
  onError: (cb: () => void) => void
  close: ReturnType<typeof vi.fn>
  send: ReturnType<typeof vi.fn>
  emitMessage: (data: unknown) => void
}

const storage = new Map<string, unknown>()
const emit = vi.fn()
let sockets: FakeSocketTask[] = []

function installUniMock() {
  sockets = []
  vi.stubGlobal('uni', {
    getStorageSync: (key: string) => storage.get(key) ?? '',
    setStorageSync: (key: string, value: unknown) => storage.set(key, value),
    removeStorageSync: (key: string) => storage.delete(key),
    $emit: emit,
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
        emitMessage: (data: unknown) => callbacks.message?.({ data }),
      } as FakeSocketTask
      sockets.push(task)
      return task
    }),
  })
  vi.stubGlobal('window', { location: { origin: 'http://localhost:5173' } })
}

const SESSION_INVALID_PUSH = JSON.stringify({
  eventType: 'SESSION_INVALID',
  tenantId: '1590229800633634816',
  recipientType: 'MALL_USER',
  recipientId: '1001',
  reason: 'KICK_OUT',
  subReason: 'ACCOUNT_DISABLED',
  invalidAt: '2026-10-29T09:12:33+08:00',
})

async function connectMall() {
  const authStore = useAuthStore()
  authStore.setLoginState('token-mall')
  const store = useMessageStore()
  const refresh = vi.spyOn(store, 'refreshUnread').mockResolvedValue(undefined)
  await store.connect()
  const socket = sockets.at(-1)
  if (!socket)
    throw new Error('mall socket was not created')
  return { store, refresh, socket }
}

async function connectDelivery() {
  storage.set('deliveryToken', 'token-delivery')
  const store = useDeliveryMessageStore()
  const refresh = vi.spyOn(store, 'refreshUnread').mockResolvedValue(undefined)
  await store.connect()
  const socket = sockets.at(-1)
  if (!socket)
    throw new Error('delivery socket was not created')
  return { store, refresh, socket }
}

function emittedEvents() {
  return emit.mock.calls.map(call => call[0])
}

describe('WS SESSION_INVALID push (mall)', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    setActivePinia(createPinia())
    storage.clear()
    emit.mockReset()
    installUniMock()
  })

  afterEach(() => {
    try {
      useMessageStore().disconnect()
      useDeliveryMessageStore().disconnect()
    }
    catch {
      // 测试可能已卸载 pinia，忽略清理失败
    }
    vi.useRealTimers()
    vi.unstubAllGlobals()
    vi.clearAllMocks()
  })

  it('enters the invalid state, disconnects, and does not refresh unread', async () => {
    const { store, refresh, socket } = await connectMall()

    socket.emitMessage(SESSION_INVALID_PUSH)

    const session = useSessionStore()
    expect(session.invalid).toBe(true)
    expect(session.reason).toBe('KICK_OUT')
    expect(session.subReason).toBe('ACCOUNT_DISABLED')
    expect(session.invalidAt).toBe('2026-10-29T09:12:33+08:00')
    expect(session.target).toBe('mall')
    // 主动推送命中后：断开连接 + 不再当普通消息处理
    expect(socket.close).toHaveBeenCalled()
    expect(store.socketState).toBe('closed')
    expect(refresh).not.toHaveBeenCalled()
    expect(emittedEvents()).not.toContain('message-push')
    // 断开后不排重连定时器（否则死 token 会继续空转）
    expect(vi.getTimerCount()).toBe(0)
  })

  it('parses BE_REPLACED device info from the push payload', async () => {
    const { socket } = await connectMall()

    socket.emitMessage(JSON.stringify({
      eventType: 'SESSION_INVALID',
      reason: 'BE_REPLACED',
      invalidAt: '2026-10-29T09:12:33+08:00',
      device: { os: 'Windows 10', browser: 'Chrome 120', location: '上海市', loginTime: '2026-10-29T09:10:00+08:00' },
    }))

    const session = useSessionStore()
    expect(session.reason).toBe('BE_REPLACED')
    expect(session.device).toEqual({
      os: 'Windows 10',
      browser: 'Chrome 120',
      location: '上海市',
      loginTime: '2026-10-29T09:10:00+08:00',
    })
  })

  it('is idempotent: repeated pushes keep the first reason and stay silent', async () => {
    const { refresh, socket } = await connectMall()
    socket.emitMessage(SESSION_INVALID_PUSH)
    const firstDetectedAt = useSessionStore().detectedAt

    vi.advanceTimersByTime(1000)
    socket.emitMessage(JSON.stringify({ eventType: 'SESSION_INVALID', reason: 'KICK_OUT', subReason: 'ADMIN_KICK' }))

    const session = useSessionStore()
    expect(session.subReason).toBe('ACCOUNT_DISABLED')
    expect(session.detectedAt).toBe(firstDetectedAt)
    expect(refresh).not.toHaveBeenCalled()
    expect(emittedEvents()).not.toContain('message-push')
  })

  it('keeps the original behavior for PONG / unknown events / non-JSON frames', async () => {
    const { refresh, socket } = await connectMall()

    socket.emitMessage('PONG')
    expect(refresh).not.toHaveBeenCalled()
    expect(emittedEvents()).not.toContain('message-push')

    socket.emitMessage(JSON.stringify({ eventType: 'NOTICE_RECEIVED', noticeId: 'n1' }))
    expect(refresh).toHaveBeenCalledTimes(1)
    expect(emittedEvents()).toContain('message-push')
    expect(useSessionStore().invalid).toBe(false)

    // 非 JSON 帧（老服务端/保活帧）按普通消息处理，不能静默吞掉
    socket.emitMessage('HEARTBEAT')
    expect(refresh).toHaveBeenCalledTimes(2)
  })

  it('accepts an already-parsed object payload (some hosts deliver objects)', async () => {
    const { socket } = await connectMall()

    socket.emitMessage({
      eventType: 'SESSION_INVALID',
      reason: 'KICK_OUT',
      subReason: 'PASSWORD_CHANGED',
    })

    expect(useSessionStore().invalid).toBe(true)
    expect(useSessionStore().subReason).toBe('PASSWORD_CHANGED')
  })
})

describe('WS SESSION_INVALID push (delivery)', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    setActivePinia(createPinia())
    storage.clear()
    emit.mockReset()
    installUniMock()
  })

  afterEach(() => {
    try {
      useMessageStore().disconnect()
      useDeliveryMessageStore().disconnect()
    }
    catch {
      // 测试可能已卸载 pinia，忽略清理失败
    }
    vi.useRealTimers()
    vi.unstubAllGlobals()
    vi.clearAllMocks()
  })

  it('invalidates with the delivery target and stops unread refresh', async () => {
    const { store, refresh, socket } = await connectDelivery()

    socket.emitMessage(SESSION_INVALID_PUSH)

    const session = useSessionStore()
    expect(session.invalid).toBe(true)
    expect(session.target).toBe('delivery')
    expect(session.reason).toBe('KICK_OUT')
    expect(session.subReason).toBe('ACCOUNT_DISABLED')
    expect(socket.close).toHaveBeenCalled()
    expect(store.socketState).toBe('closed')
    expect(refresh).not.toHaveBeenCalled()
    expect(emittedEvents()).not.toContain('delivery-message-push')
    expect(vi.getTimerCount()).toBe(0)
  })

  it('keeps the original behavior for ordinary delivery pushes', async () => {
    const { refresh, socket } = await connectDelivery()

    socket.emitMessage(JSON.stringify({ eventType: 'CONVERSATION_MESSAGE', conversationId: 'c1' }))

    expect(refresh).toHaveBeenCalledTimes(1)
    expect(emittedEvents()).toContain('delivery-message-push')
    expect(useSessionStore().invalid).toBe(false)
  })
})

import { describe, expect, it } from 'vitest'
import { shouldPersistStore, stripTransientState } from './persist'

describe('store persistence exclusions', () => {
  it.each([
    'temp',
    'shoppingCart',
    'global-loading',
    'global-toast',
    'global-message',
    // 会话失效标记只属于当前进程：持久化会让冷启动弹出过期旧窗
    'session',
  ])('does not persist transient store %s', (storeId: string) => {
    expect(shouldPersistStore(storeId)).toBe(false)
  })

  it('continues to persist authentication state', () => {
    expect(shouldPersistStore('auth')).toBe(true)
  })
})

describe('transient runtime state stripping', () => {
  it('drops persisted socketState but keeps unread counters', () => {
    expect(
      stripTransientState('message', {
        conversationUnread: 3,
        noticeUnread: 1,
        socketState: 'open',
      }),
    ).toEqual({ conversationUnread: 3, noticeUnread: 1 })
    expect(
      stripTransientState('deliveryMessage', {
        noticeUnread: 2,
        socketState: 'connecting',
      }),
    ).toEqual({ noticeUnread: 2 })
  })

  it('does not mutate the input state object', () => {
    const persisted = { noticeUnread: 2, socketState: 'open' }
    stripTransientState('message', persisted)
    expect(persisted.socketState).toBe('open')
  })

  it('leaves other stores untouched', () => {
    expect(
      stripTransientState('auth', { token: 't', socketState: 'open' }),
    ).toEqual({ token: 't', socketState: 'open' })
  })
})

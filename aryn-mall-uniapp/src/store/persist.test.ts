import { describe, expect, it } from 'vitest'
import { shouldPersistStore } from './persist'

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

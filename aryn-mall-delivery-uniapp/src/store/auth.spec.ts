import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { DELIVERY_AUTH_STORAGE_KEY, useAuthStore } from './auth'
import { buildDeliveryHeaders } from '@/api/core/instance'

const storage = new Map<string, unknown>()

beforeEach(() => {
  storage.clear()
  vi.stubGlobal('uni', {
    getStorageSync: (key: string) => storage.get(key),
    setStorageSync: (key: string, value: unknown) => storage.set(key, value),
    removeStorageSync: (key: string) => storage.delete(key),
  })
  setActivePinia(createPinia())
})

describe('配送员认证边界', () => {
  it('使用独立存储键且请求头携带配送端身份上下文', () => {
    expect(DELIVERY_AUTH_STORAGE_KEY).toBe('aryn-delivery-auth')
    expect(DELIVERY_AUTH_STORAGE_KEY).not.toBe('auth')
    expect(buildDeliveryHeaders('token-1', 'tenant-1', 'delivery-app')).toEqual({
      'app-id': 'delivery-app',
      'satoken': 'token-1',
      'tenant-id': 'tenant-1',
    })
  })

  it('无配送权限时清除登录状态', () => {
    const store = useAuthStore()
    store.saveSession({ token: 'token-1', tenantId: 'tenant-1', user: { id: 'staff-1', nickname: '张三' } })
    expect(store.validateDeliveryPermission(['order:delivery:page'])).toBe(false)
    expect(store.token).toBe('')
    expect(storage.has(DELIVERY_AUTH_STORAGE_KEY)).toBe(false)
  })
})

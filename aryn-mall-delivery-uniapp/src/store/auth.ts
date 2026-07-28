import { defineStore } from 'pinia'
import {
  bindWechat,
  getStaffMenus,
  getStaffProfile,
  login,
  logout,
  type DeliveryMenuNode,
  type DeliveryStaffProfile,
} from '@/api/auth'

export const DELIVERY_AUTH_STORAGE_KEY = 'aryn-delivery-auth'
export const DELIVERY_PERMISSION = 'order:delivery:execute'

export interface DeliverySession {
  token: string
  tenantId: string
  user: {
    id: string
    nickname: string
    username?: string
  }
  permissions?: string[]
}

function collectPermissions(nodes: DeliveryMenuNode[] | undefined, target = new Set<string>()) {
  for (const node of nodes ?? []) {
    const permission = node.extra?.permission || node.permission
    if (permission)
      target.add(permission)
    collectPermissions(node.children, target)
  }
  return [...target]
}

function normalizeUser(profile: DeliveryStaffProfile) {
  const id = profile.userId || profile.id
  if (!id)
    throw new Error('员工资料缺少用户标识')
  return {
    id,
    nickname: profile.nickname || profile.username || '配送员',
    username: profile.username,
  }
}

export const useAuthStore = defineStore('delivery-auth', {
  state: () => ({
    token: '',
    tenantId: '',
    user: null as DeliverySession['user'] | null,
    permissions: [] as string[],
  }),
  getters: {
    hasDeliveryPermission: state => state.permissions.includes(DELIVERY_PERMISSION),
    isAuthenticated: state => Boolean(state.token && state.user),
  },
  actions: {
    saveSession(session: DeliverySession) {
      this.token = session.token
      this.tenantId = session.tenantId
      this.user = session.user
      this.permissions = session.permissions ?? []
      uni.setStorageSync(DELIVERY_AUTH_STORAGE_KEY, session)
    },
    clear() {
      this.token = ''
      this.tenantId = ''
      this.user = null
      this.permissions = []
      uni.removeStorageSync(DELIVERY_AUTH_STORAGE_KEY)
    },
    validateDeliveryPermission(permissions: string[]) {
      if (!permissions.includes(DELIVERY_PERMISSION)) {
        this.clear()
        return false
      }
      this.permissions = permissions
      const stored = uni.getStorageSync(DELIVERY_AUTH_STORAGE_KEY) as DeliverySession | undefined
      if (stored)
        this.saveSession({ ...stored, permissions })
      return true
    },
    restore() {
      const stored = uni.getStorageSync(DELIVERY_AUTH_STORAGE_KEY) as DeliverySession | undefined
      if (!stored?.token || !stored.tenantId || !stored.user)
        return false
      this.saveSession(stored)
      return this.validateDeliveryPermission(stored.permissions ?? [])
    },
    async loginAndVerify(username: string, password: string) {
      const result = await login(username, password)
      const tenantId = import.meta.env.VITE_TENANT_ID
      this.saveSession({
        token: result.tokenValue,
        tenantId,
        user: { id: 'pending', nickname: username, username },
      })
      try {
        const [profile, menus] = await Promise.all([getStaffProfile(), getStaffMenus()])
        const permissions = collectPermissions(menus)
        if (!permissions.includes(DELIVERY_PERMISSION))
          throw new Error('当前账号没有商城配送权限')
        const session = {
          token: result.tokenValue,
          tenantId: profile.tenantId || tenantId,
          user: normalizeUser(profile),
          permissions,
        }
        this.saveSession(session)
        return session
      }
      catch (error) {
        this.clear()
        throw error
      }
    },
    async bindCurrentWechat() {
      const loginResult = await uni.login({ provider: 'weixin' })
      if (!loginResult.code)
        throw new Error('未获取到微信登录凭证')
      return bindWechat(loginResult.code)
    },
    async signOut() {
      try {
        await logout()
      }
      finally {
        this.clear()
      }
    },
  },
})

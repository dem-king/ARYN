import alovaInstance from './core/instance'

export interface DeliveryLoginResult {
  tokenValue: string
}

export interface DeliveryStaffProfile {
  id?: string
  userId?: string
  username?: string
  nickname?: string
  tenantId?: string
}

export interface DeliveryMenuNode {
  children?: DeliveryMenuNode[]
  extra?: { permission?: string }
  permission?: string
}

export function login(username: string, password: string) {
  return alovaInstance.Post<DeliveryLoginResult>('/auth/token/login', { username, password }, {
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
      skipToken: true,
    },
  })
}

export function getStaffProfile() {
  return alovaInstance.Get<DeliveryStaffProfile>('/upms/user/info')
}

export function getStaffMenus() {
  return alovaInstance.Get<DeliveryMenuNode[]>('/upms/menu')
}

export function logout() {
  return alovaInstance.Delete('/auth/token/logout')
}

export function bindWechat(jsCode: string) {
  return alovaInstance.Post('/upms/staff/wechat-binding', {
    appId: import.meta.env.VITE_DELIVERY_MINI_APP_ID,
    jsCode,
  })
}

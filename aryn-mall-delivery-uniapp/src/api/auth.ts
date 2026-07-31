import alovaInstance from './core/instance'
import { encodeDeliveryForm } from './core/instance'

export const DELIVERY_CAPTCHA_TYPE = 'blockPuzzle'

export interface DeliveryLoginResult {
  tokenValue: string
}

export interface DeliveryStaffProfile {
  id?: string
  userId?: string
  username?: string
  nickname?: string
  tenantId?: string
  permissions?: string[]
}

export interface DeliveryCaptchaData {
  jigsawImageBase64: string
  originalImageBase64: string
  secretKey: string
  token: string
}

export interface DeliveryCaptchaResult {
  repCode: string
  repData: DeliveryCaptchaData
  repMsg?: string
  success: boolean
}

function formHeaders() {
  return {
    'Content-Type': 'application/x-www-form-urlencoded',
    skipToken: true,
  }
}

export function getDeliveryCaptcha() {
  return alovaInstance.Get<DeliveryCaptchaResult>('/auth/code/get', {
    params: { captchaType: DELIVERY_CAPTCHA_TYPE },
    headers: { skipToken: true },
  })
}

export function checkDeliveryCaptcha(pointJson: string, token: string) {
  return alovaInstance.Post<DeliveryCaptchaResult>('/auth/code/check', encodeDeliveryForm({
    captchaType: DELIVERY_CAPTCHA_TYPE,
    pointJson,
    token,
  }), {
    headers: formHeaders(),
  })
}

export function login(username: string, password: string, code: string) {
  return alovaInstance.Post<DeliveryLoginResult>('/auth/token/login', encodeDeliveryForm({
    code,
    password,
    randomStr: DELIVERY_CAPTCHA_TYPE,
    username,
  }), {
    headers: {
      ...formHeaders(),
    },
  })
}

export function getStaffProfile() {
  return alovaInstance.Get<DeliveryStaffProfile>('/upms/user/info')
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

/**
 * 用户认证相关API配置
 * 定义登录、注册等认证相关的API接口
 */

import { alovaInstance } from './core/instance'
// 用户信息类型定义
export interface UserInfo {
  id?: string
  username?: string
  nickname?: string
  avatarUrl?: string
  phone?: string
  sex?: string
  [key: string]: any
}
// 登录相关类型定义
export interface LoginApiResponse {
  code: number
  msg?: string
  data: {
    tokenValue: string
    expiresIn?: number
    user?: any
  }
  success?: boolean
}

export interface PhoneLoginParams {
  phone: string
  code: string
}

export interface PasswordLoginParams {
  phone: string
  password: string
}

export interface QuickLoginParams {
  encryptedData: string
  iv: string
  code: string
}

export interface UserInfoResponse {
  code: number
  msg?: string
  data: any
  success?: boolean
}

export interface MPLoginParams {
  code: string
}
/**
 * 微信小程序登录（静默登录获取openid）
 * @param data 包含 jsCode 的登录参数
 */
export function wxLogin(data: MPLoginParams) {
  return alovaInstance.Post('/auth/toc-token/ma/login', data, {
    headers: {
      skipToken: true,
    },
  })
}
/**
 * 手机验证码登录
 * 注意：手机号和验证码仅通过 request body 传递，不放入 URL query 防止泄露
 */
export function phoneLogin(data: PhoneLoginParams) {
  return alovaInstance.Post('/auth/toc-token/sms/login', data, {
    headers: {
      skipToken: true,
    },
  })
}

/**
 * 手机号密码登录
 */
export function passwordLogin(data: PasswordLoginParams) {
  return alovaInstance.Post('/auth/toc-token/password/login', data, {
    headers: {
      skipToken: true,
    },
  })
}

/**
 * 小程序手机号快速登录
 */
export function quickLogin(data: QuickLoginParams) {
  return alovaInstance.Post('/auth/toc-token/ma/phone/login', data, {
    headers: {
      skipToken: true,
    },
  })
}

/**
 * 获取用户信息
 */
export function getUserInfo(params: any = {}) {
  return alovaInstance.Get<UserInfo>('/mall-user/app/userinfo', {
    params,
  })
}

/**
 * 用户登出
 */
export function logout() {
  return alovaInstance.Delete('/auth/toc-token/logout')
}

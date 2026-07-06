/**
 * 多平台适配工具函数
 * 提供各小程序平台差异的统一封装
 */

/**
 * 获取当前运行平台标识
 * @returns 平台标识字符串
 */
export function getPlatformType(): string {
  // #ifdef MP-WEIXIN
  return 'weixin'
  // #endif
  // #ifdef MP-ALIPAY
  return 'alipay'
  // #endif
  // #ifdef MP-TOUTIAO
  return 'toutiao'
  // #endif
  // #ifdef MP-BAIDU
  return 'baidu'
  // #endif
  // #ifdef MP-QQ
  return 'qq'
  // #endif
  // #ifdef MP-KUAISHOU
  return 'kuaishou'
  // #endif
  // #ifdef MP-LARK
  return 'lark'
  // #endif
  // #ifdef H5
  return 'h5'
  // #endif
  // #ifdef APP-PLUS
  return 'app'
  // #endif
  return 'unknown'
}

/**
 * 判断是否为小程序环境
 */
export function isMiniProgram(): boolean {
  // #ifdef MP
  return true
  // #endif
  return false
}

/**
 * 判断当前平台是否支持分享功能（open-type="share"）
 */
export function supportsOpenTypeShare(): boolean {
  // #ifdef MP-WEIXIN || MP-TOUTIAO
  return true
  // #endif
  return false
}

/**
 * 判断当前平台是否支持获取手机号
 */
export function supportsGetPhoneNumber(): boolean {
  // #ifdef MP-WEIXIN || MP-ALIPAY
  return true
  // #endif
  return false
}

/**
 * 判断当前平台是否支持隐私协议弹窗
 */
export function supportsPrivacyPopup(): boolean {
  // #ifdef MP-WEIXIN || MP-TOUTIAO
  return true
  // #endif
  return false
}

/**
 * 判断当前平台是否支持 web-view 组件
 * 抖音小程序不支持 web-view
 */
export function supportsWebView(): boolean {
  // #ifdef MP-TOUTIAO
  return false
  // #endif
  return true
}
import { createI18n } from 'vue-i18n'
import { compileMessages } from './compile-messages'
import enUS from './en-US'
import zhCN from './zh-CN'

export const SUPPORTED_LOCALES = ['zh-CN', 'en-US'] as const
export type SupportedLocale = (typeof SUPPORTED_LOCALES)[number]

/** 语言偏好与 store 持久化分开存，避免 i18n 初始化时依赖 pinia */
const STORAGE_KEY = 'ARYN_LOCALE'

export function normalizeLocale(value?: string | null): SupportedLocale {
  return value && value.toLowerCase().startsWith('en') ? 'en-US' : 'zh-CN'
}

export function getStoredLocale(): SupportedLocale | null {
  try {
    const saved = uni.getStorageSync(STORAGE_KEY)
    return typeof saved === 'string' && saved ? normalizeLocale(saved) : null
  }
  catch {
    return null
  }
}

function getSystemLocale(): SupportedLocale {
  try {
    return normalizeLocale(typeof uni.getLocale === 'function' ? uni.getLocale() : '')
  }
  catch {
    return 'zh-CN'
  }
}

/** 租户构建可用 VITE_DEFAULT_LOCALE 指定默认语言；未配置时跟随系统 */
function getBuildDefaultLocale(): SupportedLocale | null {
  const configured = (import.meta.env.VITE_DEFAULT_LOCALE as string | undefined)?.trim()
  return configured ? normalizeLocale(configured) : null
}

export function resolveInitialLocale(): SupportedLocale {
  return getStoredLocale() ?? getBuildDefaultLocale() ?? getSystemLocale()
}

/**
 * 小程序端 `vue-i18n` 是 runtime-only 构建（微信禁用 new Function，无法编译词条），
 * 字符串词条会被原样返回、占位符裸露在界面上；这里统一预编译成 message 函数，
 * 三端行为一致。详见 `./compile-messages.ts` 顶部说明。
 */
const messages = {
  'zh-CN': compileMessages(zhCN),
  'en-US': compileMessages(enUS),
}

export const i18n = createI18n({
  legacy: false,
  globalInjection: true,
  locale: resolveInitialLocale(),
  fallbackLocale: 'zh-CN',
  messages,
})

export function getLocale(): SupportedLocale {
  return i18n.global.locale.value as SupportedLocale
}

export function setStoredLocale(locale: SupportedLocale) {
  i18n.global.locale.value = locale
  try {
    uni.setStorageSync(STORAGE_KEY, locale)
  }
  catch {
    // 存储失败不阻断本次切换，当前会话仍生效
  }
}

/** 非组件环境（请求层、全局提示）用这个取文案 */
export function translate(key: string, named?: Record<string, unknown>): string {
  return named ? i18n.global.t(key, named) : i18n.global.t(key)
}

export default i18n

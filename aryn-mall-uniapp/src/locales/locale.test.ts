import { describe, expect, it } from 'vitest'
import { getLocale, setStoredLocale, translate } from './index'
import enUS from './en-US'
import zhCN from './zh-CN'

function collectKeys(value: Record<string, unknown>, prefix = ''): string[] {
  return Object.entries(value).flatMap(([key, child]) => {
    const path = prefix ? `${prefix}.${key}` : key
    return child && typeof child === 'object' && !Array.isArray(child)
      ? collectKeys(child as Record<string, unknown>, path)
      : [path]
  })
}

function collectEntries(
  value: Record<string, unknown>,
  prefix = '',
): Array<[string, string]> {
  return Object.entries(value).flatMap(([key, child]) => {
    const path = prefix ? `${prefix}.${key}` : key
    if (child && typeof child === 'object' && !Array.isArray(child))
      return collectEntries(child as Record<string, unknown>, path)
    return [[path, String(child)] as [string, string]]
  })
}

/** 英文包里允许出现的非英文词条：语言名按自身语言展示，不随界面语言翻译 */
const EN_ALLOWED_CJK_KEYS = new Set(['common.chinese'])

describe('locale messages', () => {
  it('keeps en-US keys in sync with zh-CN', () => {
    expect(collectKeys(enUS).sort()).toEqual(collectKeys(zhCN).sort())
  })

  it('has no Chinese left in en-US messages', () => {
    const han = /[\u4E00-\u9FFF]/
    const offenders = collectEntries(enUS)
      .filter(([key, text]) => !EN_ALLOWED_CJK_KEYS.has(key) && han.test(text))
      .map(([key, text]) => `${key}: ${text}`)
    expect(offenders).toEqual([])
  })

  it('has non-empty zh-CN messages', () => {
    const empties = collectEntries(zhCN)
      .filter(([, text]) => !text.trim())
      .map(([key]) => key)
    expect(empties).toEqual([])
  })

  it('switches runtime locale through the public helpers', () => {
    const original = getLocale()
    try {
      setStoredLocale('en-US')
      expect(getLocale()).toBe('en-US')
      expect(translate('common.cancel')).toBe('Cancel')
      expect(translate('common.cancel')).not.toBe(zhCN.common.cancel)

      setStoredLocale('zh-CN')
      expect(getLocale()).toBe('zh-CN')
      expect(translate('common.cancel')).toBe(zhCN.common.cancel)
    }
    finally {
      setStoredLocale(original)
    }
  })

  it('compiles every message without vue-i18n warnings', () => {
    const warnings: string[] = []
    const originalWarn = console.warn
    const originalError = console.error
    console.warn = (...args: unknown[]) => warnings.push(args.join(' '))
    console.error = (...args: unknown[]) => warnings.push(args.join(' '))
    const original = getLocale()
    try {
      for (const locale of ['zh-CN', 'en-US'] as const) {
        setStoredLocale(locale)
        // 把两种语言里出现过的占位符都喂进去，避免因缺参触发 message 编译警告
        const params: Record<string, string | number> = {}
        for (const [key, text] of [...collectEntries(zhCN), ...collectEntries(enUS)]) {
          void key
          for (const match of text.matchAll(/\{([a-zA-Z0-9_]+)\}/g))
            params[match[1]] = 1
        }
        for (const key of collectKeys(zhCN))
          translate(key, params)
      }
    }
    finally {
      setStoredLocale(original)
      console.warn = originalWarn
      console.error = originalError
    }
    expect(warnings).toEqual([])
  })

  it('renders literal pipe as text, not as a plural separator', () => {
    const original = getLocale()
    try {
      setStoredLocale('zh-CN')
      expect(translate('userSetting.pointsBalance', { points: 1, balance: 2 })).toBe('积分1 | 余额2')
      setStoredLocale('en-US')
      expect(translate('userSetting.pointsBalance', { points: 1, balance: 2 })).toBe('Points 1 | Balance 2')
    }
    finally {
      setStoredLocale(original)
    }
  })
})


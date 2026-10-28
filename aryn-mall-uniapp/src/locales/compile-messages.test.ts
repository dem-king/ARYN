import { describe, expect, it } from 'vitest'
import { compileMessage, compileMessages } from './compile-messages'
import enUS from './en-US'
import zhCN from './zh-CN'

/**
 * 与 `@intlify/runtime` 的默认 MessageContext 对齐：
 * `normalize(values) => values.join('')`、`interpolate(value) => toDisplayString(value)`。
 */
function context(named: Record<string, unknown>) {
  return {
    named: (key: string) => named[key],
    interpolate: (value: unknown) => (value == null ? '' : String(value)),
    normalize: (values: unknown[]) => values.join(''),
  }
}

describe('compileMessage', () => {
  it('插值命名占位符', () => {
    expect(compileMessage('共 {count} 件商品')(context({ count: 5 }))).toBe('共 5 件商品')
    expect(compileMessage('{groupNum}人拼团')(context({ groupNum: 3 }))).toBe('3人拼团')
    expect(compileMessage('去结算({count})')(context({ count: 2 }))).toBe('去结算(2)')
    expect(compileMessage('{count} 项')(context({ count: 0 }))).toBe('0 项')
  })

  it('多个占位符与字面量转义', () => {
    expect(compileMessage('积分{points} {\'|\'} 余额{balance}')(context({ points: 1, balance: 2 })))
      .toBe('积分1 | 余额2')
    expect(compileMessage('靠港：{port}{berth}{window}')(context({ port: 'A', berth: 'B', window: 'C' })))
      .toBe('靠港：ABC')
  })

  it('缺参渲染为空串（与官方编译器行为一致）', () => {
    expect(compileMessage('共 {count} 件商品')(context({}))).toBe('共  件商品')
  })

  it('无占位符词条原样返回', () => {
    expect(compileMessage('购物车')(context({}))).toBe('购物车')
    expect(compileMessage('')(context({}))).toBe('')
  })

  it('不误伤非占位符花括号', () => {
    expect(compileMessage('JSON { } 示例')(context({}))).toBe('JSON { } 示例')
    expect(compileMessage('{ not_a_key }')(context({}))).toBe('{ not_a_key }')
  })
})

describe('compileMessages', () => {
  it('递归编译词条且不修改入参', () => {
    const raw = { cart: { itemCount: '共 {count} 件商品', title: '购物车' } }
    const compiled = compileMessages(raw)

    expect(typeof compiled.cart.itemCount).toBe('function')
    expect(typeof compiled.cart.title).toBe('function')
    expect(raw.cart.itemCount).toBe('共 {count} 件商品')
  })
})

/**
 * 词条语法守卫：小程序端由本文件的自研编译器负责插值，
 * 官方编译器的复数 / 链接消息 / 位置参数等语法在这里没有实现。
 * 词条一旦引入未支持的语法，三端表现就会不一致——用测试钉住当前支持面。
 */
const SUPPORTED_BRACE_TOKEN = /^\{(?:'[^']*'|[a-z_]\w*)\}$/i

function collectMessages(value: Record<string, unknown>, prefix = ''): Array<[string, string]> {
  return Object.entries(value).flatMap(([key, child]) => {
    const path = prefix ? `${prefix}.${key}` : key
    if (child && typeof child === 'object' && !Array.isArray(child))
      return collectMessages(child as Record<string, unknown>, path)
    return [[path, String(child)] as [string, string]]
  })
}

describe('locale message syntax guard', () => {
  const entries = [...collectMessages(zhCN), ...collectMessages(enUS)]

  it('只使用 {name} 命名插值与 {\'literal\'} 字面量转义', () => {
    const offenders = entries
      .flatMap(([key, text]) => (text.match(/\{[^}]*\}/g) ?? [])
        .filter(token => !SUPPORTED_BRACE_TOKEN.test(token))
        .map(token => `${key}: ${token}`))
    expect(offenders).toEqual([])
  })

  it('字面竖线必须写成 {\'|\'}，避免被当成复数分隔符', () => {
    const offenders = entries
      .filter(([, text]) => text.replace(/\{'[^']*'\}/g, '').includes('|'))
      .map(([key, text]) => `${key}: ${text}`)
    expect(offenders).toEqual([])
  })

  it('不使用链接消息与位置参数', () => {
    const offenders = entries
      .filter(([, text]) => text.includes('@:') || /\{\d+\}/.test(text))
      .map(([key, text]) => `${key}: ${text}`)
    expect(offenders).toEqual([])
  })
})

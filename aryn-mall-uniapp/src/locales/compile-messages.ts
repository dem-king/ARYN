/**
 * 词条预编译：把字符串词条编译成 vue-i18n 的 message 函数。
 *
 * 为什么需要预编译：uni-app 在小程序 / App 端把 `vue-i18n` 别名到 runtime-only 构建
 * （`@dcloudio/uni-cli-shared/lib/vue-i18n/dist/vue-i18n.runtime.esm-bundler.js`），
 * 该构建里的 messageCompiler 是直通桩：
 *
 *     messageCompiler: source => ctx => ctx.normalize([source])
 *
 * 微信小程序禁止 `new Function` / `eval`，官方 message 编译器无法在端上运行，
 * 于是字符串词条完全不做插值——`共 {count} 件商品` 会把 `{count}` 原样渲染到界面上
 * （2026-10-26 真机反馈的首页/购物车占位符问题）。vue-i18n 对 message 函数不再走
 * 编译器（`isMessageFunction` 分支），因此启动时把词条预编译成函数，即可让
 * H5 / 小程序 / App 三端行为一致，也便于单测覆盖。
 *
 * 支持的语法（当前词条只用到这两种；新增语法需同步扩展并补测试）：
 * - `{name}`：命名插值，值来自 `t(key, { name: value })`；
 * - `{'literal'}`：字面量转义（如字面竖线 `{'|'}`，否则会被当成复数分隔符）。
 */
export interface MessageContext {
  named: (key: string) => unknown
  interpolate: (value: unknown) => string
  normalize: (values: unknown[]) => string
}

export type CompiledMessage = (ctx: MessageContext) => string

/** `{name}` 命名插值或 `{'literal'}` 字面量转义 */
const TOKEN_RE = /\{('([^']*)'|([a-z_]\w*))\}/gi

/**
 * 把单条词条编译成 message 函数。
 *
 * 编译结果与官方编译器一致：字面片段与 `interpolate(named(key))` 交给
 * `ctx.normalize` 拼接（缺参时官方编译器同样产出空串）。
 */
export function compileMessage(source: string): CompiledMessage {
  const parts: Array<string | { name: string }> = []
  let cursor = 0
  for (const match of source.matchAll(TOKEN_RE)) {
    const index = match.index ?? 0
    if (index > cursor)
      parts.push(source.slice(cursor, index))
    if (match[2] !== undefined)
      parts.push(match[2])
    else
      parts.push({ name: match[3] as string })
    cursor = index + match[0].length
  }
  if (cursor < source.length)
    parts.push(source.slice(cursor))

  // 无占位符的词条直接返回常量，避免每次渲染都拼数组
  if (!parts.some(part => typeof part !== 'string')) {
    const text = parts.join('')
    return () => text
  }

  return ctx => ctx.normalize(parts.map(part => (typeof part === 'string'
    ? part
    : ctx.interpolate(ctx.named(part.name)))))
}

type CompiledMessages<T> = {
  [K in keyof T]: T[K] extends string
    ? CompiledMessage
    : T[K] extends object
      ? CompiledMessages<T[K]>
      : T[K]
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

/**
 * 递归编译词条对象（不修改入参，原始字符串词条仍可被测试与工具直接使用）。
 */
export function compileMessages<T extends Record<string, unknown>>(messages: T): CompiledMessages<T> {
  const compiled: Record<string, unknown> = {}
  for (const [key, value] of Object.entries(messages)) {
    compiled[key] = typeof value === 'string'
      ? compileMessage(value)
      : isPlainObject(value)
        ? compileMessages(value)
        : value
  }
  return compiled as CompiledMessages<T>
}

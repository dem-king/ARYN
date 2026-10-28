import { createRequire } from 'node:module'
import { dirname, resolve } from 'node:path'
import { pathToFileURL } from 'node:url'
import { describe, expect, it } from 'vitest'
import { compileMessages } from './compile-messages'
import enUS from './en-US'
import zhCN from './zh-CN'

/**
 * 小程序端 i18n 运行时契约。
 *
 * uni-app 在 mp 平台把 `vue-i18n` 别名到 runtime-only 构建
 * （`@dcloudio/uni-cli-shared/lib/vue-i18n/dist/vue-i18n.runtime.esm-bundler.js`）：
 * 微信小程序禁用 `new Function`，该构建没有 message 编译器，字符串词条原样返回。
 * 2026-10-26 真机因此出现「共 {count} 件商品」这类占位符裸露。
 *
 * 这里直接加载同一份 runtime-only 构建，钉住两件事：
 * 1. 字符串词条在该构建下不插值（问题的根因，防止有人误以为官方编译器会兜底）；
 * 2. `compileMessages` 预编译后的词条能正常插值（修复本身）。
 *
 * 若 uni-app 升级后移动了该文件路径，测试会直接报错——那是重新核对小程序
 * i18n 行为的信号，不要简单改路径了事。
 */
const require = createRequire(import.meta.url)

function resolveMpI18nRuntimePath() {
  const pkgPath = require.resolve('@dcloudio/uni-cli-shared/package.json')
  return resolve(dirname(pkgPath), 'lib/vue-i18n/dist/vue-i18n.runtime.esm-bundler.js')
}

async function createMpI18n(messages: Record<string, unknown>) {
  const runtimePath = resolveMpI18nRuntimePath()
  const { createI18n } = await import(pathToFileURL(runtimePath).href)
  return createI18n({
    legacy: false,
    locale: 'zh-CN',
    fallbackLocale: 'zh-CN',
    messages,
  })
}

describe('小程序 runtime-only 构建下的插值契约', () => {
  it('字符串词条不插值（根因：runtime-only 构建没有 message 编译器）', async () => {
    const i18n = await createMpI18n({
      'zh-CN': { raw: '共 {count} 件商品' },
    })
    expect(i18n.global.t('raw', { count: 5 })).toBe('共 {count} 件商品')
  })

  it('预编译后的词条在 runtime-only 构建里正常插值', async () => {
    const i18n = await createMpI18n({
      'zh-CN': compileMessages(zhCN),
      'en-US': compileMessages(enUS),
    })

    // 真机截图里裸露的几处：购物车件数 / 结算按钮 / 首页拼团条 / 新人专享
    expect(i18n.global.t('cart.itemCount', { count: 5 })).toBe('共 5 件商品')
    expect(i18n.global.t('cart.checkoutWithCount', { count: 5 })).toBe('去结算(5)')
    expect(i18n.global.t('diy.activityStrip.groupBuyTitle', { groupNum: 3 })).toBe('3人拼团')
    expect(i18n.global.t('diy.newbieFloor.perks', { registerDays: 7, limit: 2 }))
      .toBe('注册7天内新人 · 每人每款限2件')
    expect(i18n.global.t('userSetting.pointsBalance', { points: 1, balance: 2 })).toBe('积分1 | 余额2')

    i18n.global.locale.value = 'en-US'
    expect(i18n.global.t('cart.itemCount', { count: 5 })).toBe('5 item(s)')
  })
})

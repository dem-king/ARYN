import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * 会话失效弹窗挂载与交互契约（一期 + 二期）。
 *
 * 契约（README §7.1/§7.2、二期实现契约 §2/§4）：
 *  · 弹窗必须挂在根组件 `src/App.ku.vue`，且全局唯一状态在 sessionStore；
 *  · 用 `wd-popup` 自研弹窗，**不得**用 `uni.showModal`（纯文本、按钮 4 字限制、
 *    reLaunch 会把未关闭的 modal 一起干掉）；
 *  · 不可关闭：无关闭按钮、点遮罩不关；唯一出口是「重新登录」「退出」；
 *  · 文案走 i18n `sessionInvalid.*`，中英双语；设备行缺失整行隐藏。
 *
 * 这些是「实现形态」约束（模板属性/挂载点），单测环境没有小程序渲染器，
 * 用源码契约钉住，避免后续重构把出口/层级/挂载点改坏。
 */
const projectRoot = fileURLToPath(new URL('../../', import.meta.url))

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

/** 注释里会解释「为什么不用 uni.showModal / 为什么 store 不 import clearAuthData」，断言前先剥注释 */
function code(relativePath: string) {
  return source(relativePath)
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/^\s*\/\/.*$/gm, '')
}

const ROOT_VIEW = 'src/App.ku.vue'
const DIALOG = 'src/components/SessionInvalidDialog.vue'
const SESSION_STORE = 'src/store/sessionStore.ts'

describe('会话失效弹窗', () => {
  it('mounts the dialog on the root component (every page gets exactly one instance)', () => {
    const root = source(ROOT_VIEW)
    expect(root).toContain('<session-invalid-dialog />')
    expect(root).toContain('SessionInvalidDialog')
    // 失效时主动断开两条 WS，避免死 token 每 3 秒重连
    expect(root).toContain('useMessageStore().disconnect()')
    expect(root).toContain('useDeliveryMessageStore().disconnect()')
  })

  it('uses a wd-popup based, non-closable dialog instead of uni.showModal', () => {
    const dialog = code(DIALOG)
    expect(dialog).toContain('<wd-popup')
    expect(dialog).toContain(':closable="false"')
    expect(dialog).toContain(':close-on-click-modal="false"')
    expect(dialog).not.toContain('uni.showModal(')

    // 唯一出口（按按钮计划分派：relogin → confirmRelogin，exit → exit，单按钮 → exitToLogin）
    expect(dialog).toContain('sessionStore.confirmRelogin()')
    expect(dialog).toContain('sessionStore.exit()')
    expect(dialog).toContain('sessionStore.exitToLogin()')
    expect(dialog).toContain('resolveSessionInvalidActions')
    expect(dialog).toContain('onAction(action)')

    // 设备行缺失时整行隐藏（不显示「未知设备」）
    expect(dialog).toContain('v-if="copy.deviceLine"')
  })

  it('renders the single-button variant for canRelogin=false without a relogin button (二期)', () => {
    const dialog = code(DIALOG)
    // 按钮统一 v-for 渲染：canRelogin=false 时计划里只有一条 exit 动作，
    // 因此「重新登录」不会被渲染出来（而不是靠模板里再判一次）
    expect(dialog).toContain('v-for="action in actions"')
    expect(dialog).toContain('action.text')
    expect(dialog).toContain('action.primary')
    // 模板里不得直接写死「重新登录」按钮：单按钮规则靠 actions 计划保证
    expect(dialog).not.toContain('{{ copy.relogin }}')
    // canRelogin=false 的单按钮落到登录页（清态→登录页），不是一期 exit() 的首页
    expect(dialog).toContain('sessionStore.exitToLogin()')
  })

  it('drives copy/target/subReason from the session store and i18n keys', () => {
    const dialog = source(DIALOG)
    expect(dialog).toContain('useSessionStore()')
    expect(dialog).toContain('resolveSessionInvalidCopy')
    expect(dialog).toContain('subReason: subReason.value')
    expect(dialog).toContain('OVERLAY_Z_INDEX.sessionInvalid')
  })

  it('keeps the invalidated state in memory only (no persisted dialog on cold start)', () => {
    expect(source('src/store/persist.ts')).toContain('\'session\'')
    const store = code(SESSION_STORE)
    expect(store).toContain('invalidate(')
    expect(store).toContain('subReason')
    expect(store).toContain('confirmRelogin(')
    expect(store).toContain('exit(')
    expect(store).toContain('reset(')
    // 检测瞬间不清凭证：store 自己不清态（清态实现由 main.ts 注册，只在用户动作时调用）
    expect(store).not.toContain('clearAuthData(')
  })
})

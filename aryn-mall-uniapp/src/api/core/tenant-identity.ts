import { tenantBuildIdentity } from '@/generated/tenant-build'
import type { TenantBuildIdentity } from './tenant-build-types'
import { parseOpenBoot } from './boot-url'
import { TenantPreflightError, preflightTenantBinding, preflightTenantSession } from './tenant-preflight'
import type { TenantPreflightScope } from './tenant-preflight'

/**
 * 租户身份守卫状态机（P0-B 前端侧）。
 *
 * - 仅租户构建生效（generated identity 非 null）；默认开发链路 generated=null，全部守卫直接放行。
 * - 本地校验同步执行：generated identity 与 import.meta.env 逐字段一致；微信小程序下真实
 *   AppID 必须等于包内期望。
 * - binding preflight 通过前不放行任何业务请求；安全边界在 request/send/上传/WebSocket/支付
 *   入口（instance/file/message/pay），不依赖 onLaunch 的时序。
 * - shared Promise 去重；失败进入 blocked，后续调用直接抛缓存错误（不自动重试打洪），
 *   用户重试（retryTenantBinding）创建新 Promise。没有任何持久化的永久 ready。
 * - 本模块不 import alova/Pinia/业务 API，避免 instance→guard→API→instance 循环。
 */

export type TenantGuardErrorType
  = | 'identity-mismatch'
    | 'device-mismatch'
    | 'binding-blocked'

export class TenantGuardError extends Error {
  type: TenantGuardErrorType

  buildId?: string

  constructor(type: TenantGuardErrorType, message: string, buildId?: string) {
    super(message)
    this.name = 'TenantGuardError'
    this.type = type
    this.buildId = buildId
  }
}

export type TenantGuardState = 'idle' | 'local-check' | 'binding-check' | 'ready' | 'blocked'

type TenantStateListener = (state: TenantGuardState) => void

const stateListeners = new Set<TenantStateListener>()

function setState(next: TenantGuardState): void {
  state = next
  stateListeners.forEach(listener => listener(next))
}

/** 订阅守卫状态变化（阻断 UI 用）；返回取消订阅函数。 */
export function onTenantGuardStateChange(listener: TenantStateListener): () => void {
  stateListeners.add(listener)
  return () => stateListeners.delete(listener)
}

const BINDING_REFRESH_THROTTLE_MS = 5 * 60 * 1000

let state: TenantGuardState = 'idle'
let bindingPromise: Promise<void> | null = null
let bindingError: TenantGuardError | TenantPreflightError | null = null
let lastBindingAt = 0

/** scope → 当前 token 的会话校验缓存；token 变化自动重查，不同 scope 完全隔离。 */
const sessionChecks = new Map<TenantPreflightScope, { token: string, promise: Promise<void> }>()

export function isTenantBuild(): boolean {
  return tenantBuildIdentity !== null
}

export function getTenantGuardState(): TenantGuardState {
  return state
}

export function getTenantGuardError(): TenantGuardError | TenantPreflightError | null {
  return bindingError
}

function identityOrThrow(): Readonly<TenantBuildIdentity> {
  if (tenantBuildIdentity === null) {
    throw new TenantGuardError('identity-mismatch', '租户身份模块缺失')
  }
  return tenantBuildIdentity
}

/** 本地一致性断言：generated identity ↔ import.meta.env，任何漂移立即拒绝（不发请求）。 */
function assertLocalIdentity(): void {
  const identity = identityOrThrow()
  const envTenantId = import.meta.env.VITE_TENANT_ID
  const envAppId = import.meta.env.VITE_TENANT_WX_APPID
  const envOpenBoot = parseOpenBoot(import.meta.env.VITE_OPEN_BOOT)
  const envApiBaseUrl = import.meta.env.VITE_API_BASE_URL
  if (envTenantId !== identity.tenantId) {
    throw new TenantGuardError('identity-mismatch', '包内租户标识与环境注入不一致', identity.buildId)
  }
  if (envAppId !== identity.wxAppId) {
    throw new TenantGuardError('identity-mismatch', '包内微信 AppID 与环境注入不一致', identity.buildId)
  }
  if (envOpenBoot !== identity.openBoot) {
    throw new TenantGuardError('identity-mismatch', '包内部署模式与环境注入不一致', identity.buildId)
  }
  if (envApiBaseUrl !== identity.apiBaseUrl) {
    throw new TenantGuardError('identity-mismatch', '包内 API 地址与环境注入不一致', identity.buildId)
  }
  // #ifdef MP-WEIXIN
  // 真机/开发者工具读取运行容器 AppID；vitest（node 环境）里 #ifdef 不剥离，
  // 用 typeof 防御避免直接引用未定义的 uni 全局
  if (typeof uni !== 'undefined' && typeof uni.getAccountInfoSync === 'function') {
    let realAppId = ''
    try {
      realAppId = uni.getAccountInfoSync().miniProgram.appId
    }
    catch {
      realAppId = ''
    }
    if (realAppId !== identity.wxAppId) {
      throw new TenantGuardError('device-mismatch', `运行容器 AppID 与包内期望不一致（real=${realAppId || 'unknown'}）`, identity.buildId)
    }
  }
  // #endif
}

function bindingHeaders(): Record<string, string> {
  const identity = identityOrThrow()
  const headers: Record<string, string> = {
    'tenant-id': identity.tenantId,
  }
  // #ifdef MP-WEIXIN
  headers['app-id'] = identity.wxAppId
  headers['platform-type'] = 'WX_MA'
  // #endif
  // #ifdef APP-PLUS
  headers['platform-type'] = 'APP'
  // #endif
  // #ifdef H5
  headers['platform-type'] = 'H5'
  // #endif
  return headers
}

async function runBindingCheck(): Promise<void> {
  setState('local-check')
  assertLocalIdentity()
  setState('binding-check')
  const identity = identityOrThrow()
  try {
    const binding = await preflightTenantBinding(bindingHeaders())
    if (!binding.ready || binding.appId !== identity.wxAppId || binding.tenantId !== identity.tenantId) {
      throw new TenantGuardError('binding-blocked', '服务端租户映射与包内身份不一致', identity.buildId)
    }
    setState('ready')
    lastBindingAt = Date.now()
    bindingError = null
  }
  catch (error) {
    setState('blocked')
    bindingError = error instanceof TenantPreflightError || error instanceof TenantGuardError
      ? error
      : new TenantGuardError('binding-blocked', (error as Error | null)?.message || '租户绑定校验失败', identity.buildId)
    throw bindingError
  }
}

/**
 * 业务请求放行前的绑定校验。默认开发（generated=null）直接放行。
 */
export async function ensureTenantReady(): Promise<void> {
  if (!isTenantBuild()) {
    return
  }
  if (state === 'ready') {
    return
  }
  if (state === 'blocked') {
    // 不自动重试：blocked 的恢复只能走用户重试或前台刷新
    throw bindingError ?? new TenantGuardError('binding-blocked', '应用配置暂不可用')
  }
  if (!bindingPromise) {
    bindingPromise = runBindingCheck().finally(() => {
      bindingPromise = null
    })
  }
  await bindingPromise
}

/** 用户主动重试（启动界面按钮）：清空 blocked 状态并新建 Promise。 */
export async function retryTenantBinding(): Promise<void> {
  if (!isTenantBuild()) {
    return
  }
  setState('idle')
  bindingError = null
  bindingPromise = null
  await ensureTenantReady()
}

/**
 * 回前台共享节流刷新（建议 5 分钟）：binding 通过后静默重查；
 * 刷新发现错配时才翻转 blocked。期间已有 ready 不撤（最终一致）。
 */
export async function refreshBindingOnShow(): Promise<void> {
  if (!isTenantBuild() || state !== 'ready') {
    return
  }
  if (Date.now() - lastBindingAt < BINDING_REFRESH_THROTTLE_MS) {
    return
  }
  lastBindingAt = Date.now()
  const identity = identityOrThrow()
  try {
    const binding = await preflightTenantBinding(bindingHeaders())
    if (!binding.ready || binding.appId !== identity.wxAppId || binding.tenantId !== identity.tenantId) {
      setState('blocked')
      bindingError = new TenantGuardError('binding-blocked', '服务端租户映射已变更，请重新打开应用', identity.buildId)
    }
  }
  catch {
    // 网络抖动不阻断已就绪会话；下次前台再试
  }
}

/**
 * 会话租户校验（每个当前 token 首次使用前）：mall=TOC / delivery=TOB 由服务端判定。
 * token 变化自动重查；401 仅代表本次校验的旧 token 失效（checkedToken 供调用方比对）。
 */
export async function ensureSessionTenant(scope: TenantPreflightScope, token: string): Promise<void> {
  if (!isTenantBuild() || !token) {
    return
  }
  const cached = sessionChecks.get(scope)
  if (cached && cached.token === token) {
    await cached.promise
    return
  }
  const identity = identityOrThrow()
  const promise = preflightTenantSession({ headers: bindingHeaders(), token, scope })
    .then((payload) => {
      if (payload.tenantId !== identity.tenantId) {
        throw new TenantPreflightError({
          type: 'forbidden',
          message: '会话租户与包内身份不一致',
          endpoint: '/auth/toc-token/tenant-session',
          statusCode: 403,
          scope,
          checkedToken: token,
        })
      }
    })
    .catch((error) => {
      if (error instanceof TenantPreflightError) {
        error.checkedToken = token
      }
      // 失败不缓存：同 token 下次使用时重新校验
      sessionChecks.delete(scope)
      throw error
    })
  sessionChecks.set(scope, { token, promise })
  await promise
}

/** 登出/换 token 时清除会话校验缓存；scope 独立清理，不能一次清两边。 */
export function invalidateSessionCheck(scope: TenantPreflightScope): void {
  sessionChecks.delete(scope)
}

/** 判断错误是否属于租户身份校验链（preflight/守卫）：调用方据此不得吞错或降级。 */
export function isTenantIdentityError(error: unknown): boolean {
  const name = (error as { name?: string } | null)?.name
  return name === 'TenantPreflightError' || name === 'TenantGuardError'
}

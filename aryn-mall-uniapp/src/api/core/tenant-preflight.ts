import { buildApiUrl } from './api-base-url'
import { parseOpenBoot, rewriteBootUrl } from './boot-url'

/**
 * 租户 preflight 传输层：仅支持两个固定端点的原始 uni.request。
 *
 * 不 import alova/store，避免 instance→guard→API→instance 循环；
 * 错误只返回类型化结果给守卫/启动界面，不触碰会清 token 的全局响应处理器。
 * binding 不携带任何 token；session 显式传入本次待验证 token。
 */

export type TenantPreflightEndpoint = '/auth/toc-token/tenant-binding' | '/auth/toc-token/tenant-session'

export type TenantPreflightScope = 'mall' | 'delivery'

export interface TenantBindingPayload {
  appId: string
  tenantId: string
  ready: boolean
}

export interface TenantSessionPayload {
  tenantId: string
}

export type TenantPreflightErrorType
  = | 'network'
    | 'server'
    | 'unauthorized'
    | 'forbidden'
    | 'bad-payload'

export class TenantPreflightError extends Error {
  type: TenantPreflightErrorType

  endpoint: TenantPreflightEndpoint

  scope?: TenantPreflightScope

  statusCode: number

  /** 仅 session 检查携带：便于调用方比对"迟到 401 是否属于本次校验的 token"。 */
  checkedToken?: string

  constructor(options: {
    type: TenantPreflightErrorType
    message: string
    endpoint: TenantPreflightEndpoint
    statusCode?: number
    scope?: TenantPreflightScope
    checkedToken?: string
  }) {
    super(options.message)
    this.name = 'TenantPreflightError'
    this.type = options.type
    this.endpoint = options.endpoint
    this.statusCode = options.statusCode ?? 0
    this.scope = options.scope
    this.checkedToken = options.checkedToken
  }
}

interface PreflightOptions {
  endpoint: TenantPreflightEndpoint
  headers: Record<string, string>
  scope?: TenantPreflightScope
}

function resolvePreflightUrl(endpoint: TenantPreflightEndpoint): string {
  const openBoot = parseOpenBoot(import.meta.env.VITE_OPEN_BOOT)
  const rewritten = rewriteBootUrl(endpoint, openBoot) ?? endpoint
  return buildApiUrl(rewritten)
}

function statusCodeToType(statusCode: number): TenantPreflightErrorType {
  if (statusCode === 401) {
    return 'unauthorized'
  }
  if (statusCode === 403) {
    return 'forbidden'
  }
  return 'server'
}

function request<T>(options: PreflightOptions): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    uni.request({
      url: resolvePreflightUrl(options.endpoint),
      method: 'GET',
      header: options.headers,
      timeout: 10000,
      success: (result) => {
        const statusCode = result.statusCode ?? 0
        if (statusCode < 200 || statusCode >= 300) {
          reject(new TenantPreflightError({
            type: statusCodeToType(statusCode),
            message: `租户预检请求失败（HTTP ${statusCode}）`,
            endpoint: options.endpoint,
            statusCode,
            scope: options.scope,
          }))
          return
        }
        const body = result.data as { code?: number, data?: T, msg?: string } | undefined
        if (!body || typeof body.code !== 'number' || body.code !== 0) {
          const code = body?.code ?? 0
          reject(new TenantPreflightError({
            type: code === 401 ? 'unauthorized' : code === 403 ? 'forbidden' : 'server',
            message: body?.msg || `租户预检返回异常 code=${code}`,
            endpoint: options.endpoint,
            statusCode,
            scope: options.scope,
          }))
          return
        }
        if (body.data === undefined || body.data === null) {
          reject(new TenantPreflightError({
            type: 'bad-payload',
            message: '租户预检响应缺少数据',
            endpoint: options.endpoint,
            statusCode,
            scope: options.scope,
          }))
          return
        }
        resolve(body.data)
      },
      fail: (error) => {
        reject(new TenantPreflightError({
          type: 'network',
          message: error?.errMsg || '租户预检网络失败',
          endpoint: options.endpoint,
          scope: options.scope,
        }))
      },
    })
  })
}

/** 匿名绑定预检：不携带 token。 */
export function preflightTenantBinding(headers: Record<string, string>): Promise<TenantBindingPayload> {
  return request<TenantBindingPayload>({ endpoint: '/auth/toc-token/tenant-binding', headers })
}

/** 会话租户预检：显式传本次待验证 token 与 scope，不从共享 header 猜 token。 */
export function preflightTenantSession(options: {
  headers: Record<string, string>
  token: string
  scope: TenantPreflightScope
}): Promise<TenantSessionPayload> {
  return request<TenantSessionPayload>({
    endpoint: '/auth/toc-token/tenant-session',
    headers: { ...options.headers, satoken: options.token },
    scope: options.scope,
  }).then((payload) => {
    // 让错误对象也能拿到本次校验的 token（成功路径用不到）
    return payload
  })
}

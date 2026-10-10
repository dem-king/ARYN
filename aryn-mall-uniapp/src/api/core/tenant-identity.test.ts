import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * 租户身份守卫状态机单测。
 *
 * 模块级状态（state/sessionChecks）通过 vi.resetModules + 动态 import 隔离；
 * generated identity 与 preflight 传输用 vi.mock 控制，不真发请求。
 */

const preflightBindingMock = vi.fn()
const preflightSessionMock = vi.fn()

vi.mock('@/generated/tenant-build', () => ({
  get tenantBuildIdentity() {
    return (globalThis as any).__TEST_TENANT_IDENTITY__ ?? null
  },
}))

vi.mock('./tenant-preflight', () => ({
  TenantPreflightError: class TenantPreflightError extends Error {
    type: string
    endpoint: string
    statusCode: number
    scope?: string
    checkedToken?: string
    constructor(options: any) {
      super(options.message)
      this.name = 'TenantPreflightError'
      this.type = options.type
      this.endpoint = options.endpoint
      this.statusCode = options.statusCode ?? 0
      this.scope = options.scope
      this.checkedToken = options.checkedToken
    }
  },
  preflightTenantBinding: (...args: unknown[]) => preflightBindingMock(...(args as [])),
  preflightTenantSession: (...args: unknown[]) => preflightSessionMock(...(args as [])),
}))

const TENANT_IDENTITY = Object.freeze({
  schemaVersion: 1,
  key: 'aetheryn',
  tenantId: '1590229800633634816',
  wxAppId: 'wx0a8242ea59f3e6b4',
  name: '悦航购',
  profile: 'production',
  deployment: 'boot',
  openBoot: true,
  apiBaseUrl: 'https://api.example.com',
  buildId: 'b-test',
})

async function loadModule() {
  return await import('./tenant-identity')
}

function setEnvForTenant() {
  ;(import.meta.env as any).VITE_TENANT_ID = TENANT_IDENTITY.tenantId
  ;(import.meta.env as any).VITE_TENANT_WX_APPID = TENANT_IDENTITY.wxAppId
  ;(import.meta.env as any).VITE_OPEN_BOOT = 'true'
  ;(import.meta.env as any).VITE_API_BASE_URL = TENANT_IDENTITY.apiBaseUrl
}

beforeEach(() => {
  vi.resetModules()
  preflightBindingMock.mockReset()
  preflightSessionMock.mockReset()
  ;(globalThis as any).__TEST_TENANT_IDENTITY__ = null
  setEnvForTenant()
})

afterEach(() => {
  delete (globalThis as any).__TEST_TENANT_IDENTITY__
})

describe('默认开发链路（generated=null）', () => {
  it('守卫全部放行且不发起任何 preflight', async () => {
    const mod = await loadModule()
    expect(mod.isTenantBuild()).toBe(false)
    await expect(mod.ensureTenantReady()).resolves.toBeUndefined()
    await expect(mod.ensureSessionTenant('mall', 'token-x')).resolves.toBeUndefined()
    expect(preflightBindingMock).not.toHaveBeenCalled()
    expect(preflightSessionMock).not.toHaveBeenCalled()
  })
})

describe('租户构建（generated=identity）', () => {
  beforeEach(() => {
    ;(globalThis as any).__TEST_TENANT_IDENTITY__ = TENANT_IDENTITY
  })

  it('env 与 identity 漂移时本地立即拒绝，不发业务请求也不打 preflight', async () => {
    ;(import.meta.env as any).VITE_TENANT_ID = 'WRONG'
    const mod = await loadModule()
    await expect(mod.ensureTenantReady()).rejects.toMatchObject({ type: 'identity-mismatch' })
    expect(preflightBindingMock).not.toHaveBeenCalled()
  })

  it('binding 通过后进入 ready，并发调用共享同一次请求', async () => {
    preflightBindingMock.mockResolvedValue({
      appId: TENANT_IDENTITY.wxAppId,
      tenantId: TENANT_IDENTITY.tenantId,
      ready: true,
    })
    const mod = await loadModule()
    await Promise.all([mod.ensureTenantReady(), mod.ensureTenantReady()])
    await mod.ensureTenantReady()
    expect(preflightBindingMock).toHaveBeenCalledTimes(1)
    expect(mod.getTenantGuardState()).toBe('ready')
  })

  it('binding 403 后 blocked，后续调用直接抛缓存错误不重发请求', async () => {
    preflightBindingMock.mockRejectedValue(
      new (await import('./tenant-preflight')).TenantPreflightError({
        type: 'forbidden',
        message: '小程序与租户配置不一致',
        endpoint: '/auth/toc-token/tenant-binding',
        statusCode: 403,
      }),
    )
    const mod = await loadModule()
    await expect(mod.ensureTenantReady()).rejects.toMatchObject({ type: 'forbidden' })
    await expect(mod.ensureTenantReady()).rejects.toMatchObject({ type: 'forbidden' })
    expect(preflightBindingMock).toHaveBeenCalledTimes(1)
    expect(mod.getTenantGuardState()).toBe('blocked')
  })

  it('用户重试创建新 Promise 并可恢复 ready', async () => {
    preflightBindingMock
      .mockRejectedValueOnce(new (await import('./tenant-preflight')).TenantPreflightError({
        type: 'network',
        message: '超时',
        endpoint: '/auth/toc-token/tenant-binding',
      }))
      .mockResolvedValue({
        appId: TENANT_IDENTITY.wxAppId,
        tenantId: TENANT_IDENTITY.tenantId,
        ready: true,
      })
    const mod = await loadModule()
    await expect(mod.ensureTenantReady()).rejects.toMatchObject({ type: 'network' })
    await mod.retryTenantBinding()
    expect(preflightBindingMock).toHaveBeenCalledTimes(2)
    expect(mod.getTenantGuardState()).toBe('ready')
  })

  describe('会话租户校验（mall/delivery 隔离）', () => {
    const sessionOk = { tenantId: TENANT_IDENTITY.tenantId }

    it('同 token 复用校验结果，token 变化重新校验', async () => {
      preflightBindingMock.mockResolvedValue({
        appId: TENANT_IDENTITY.wxAppId,
        tenantId: TENANT_IDENTITY.tenantId,
        ready: true,
      })
      preflightSessionMock.mockResolvedValue(sessionOk)
      const mod = await loadModule()
      await mod.ensureTenantReady()
      await mod.ensureSessionTenant('mall', 'token-a')
      await mod.ensureSessionTenant('mall', 'token-a')
      expect(preflightSessionMock).toHaveBeenCalledTimes(1)
      await mod.ensureSessionTenant('mall', 'token-b')
      expect(preflightSessionMock).toHaveBeenCalledTimes(2)
    })

    it('会话租户错配按 forbidden 阻断且保留 token 语义（不清登录态由调用方决定）', async () => {
      preflightBindingMock.mockResolvedValue({
        appId: TENANT_IDENTITY.wxAppId,
        tenantId: TENANT_IDENTITY.tenantId,
        ready: true,
      })
      preflightSessionMock.mockResolvedValue({ tenantId: 'tenant-other' })
      const mod = await loadModule()
      await mod.ensureTenantReady()
      await expect(mod.ensureSessionTenant('mall', 'token-a')).rejects.toMatchObject({ type: 'forbidden' })
    })

    it('401 传播 checkedToken 供调用方比对迟到失效', async () => {
      preflightBindingMock.mockResolvedValue({
        appId: TENANT_IDENTITY.wxAppId,
        tenantId: TENANT_IDENTITY.tenantId,
        ready: true,
      })
      const { TenantPreflightError } = await import('./tenant-preflight')
      preflightSessionMock.mockRejectedValue(new TenantPreflightError({
        type: 'unauthorized',
        message: '会话不存在',
        endpoint: '/auth/toc-token/tenant-session',
        statusCode: 401,
        scope: 'mall',
      }))
      const mod = await loadModule()
      await mod.ensureTenantReady()
      const error = await mod.ensureSessionTenant('mall', 'token-a').catch((e: unknown) => e)
      expect(error).toBeInstanceOf(TenantPreflightError)
      expect((error as any).type).toBe('unauthorized')
      expect((error as any).checkedToken).toBe('token-a')
    })

    it('失败不缓存：同 token 下次使用重新校验', async () => {
      preflightBindingMock.mockResolvedValue({
        appId: TENANT_IDENTITY.wxAppId,
        tenantId: TENANT_IDENTITY.tenantId,
        ready: true,
      })
      const { TenantPreflightError } = await import('./tenant-preflight')
      preflightSessionMock
        .mockRejectedValueOnce(new TenantPreflightError({
          type: 'network',
          message: '超时',
          endpoint: '/auth/toc-token/tenant-session',
          scope: 'delivery',
        }))
        .mockResolvedValue(sessionOk)
      const mod = await loadModule()
      await mod.ensureTenantReady()
      await expect(mod.ensureSessionTenant('delivery', 'tok-d')).rejects.toMatchObject({ type: 'network' })
      await expect(mod.ensureSessionTenant('delivery', 'tok-d')).resolves.toBeUndefined()
      expect(preflightSessionMock).toHaveBeenCalledTimes(2)
    })
  })
})

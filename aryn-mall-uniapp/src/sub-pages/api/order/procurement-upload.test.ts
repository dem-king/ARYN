import { beforeEach, describe, expect, it, vi } from 'vitest'
const mocked = vi.hoisted(() => ({ ready: vi.fn(), session: vi.fn(), upload: vi.fn() }))
vi.mock('@/api/core/tenant-identity', () => ({ ensureTenantReady: mocked.ready, ensureSessionTenant: mocked.session }))
vi.mock('@/store/authStore', () => ({ useAuthStore: () => ({ getToken: 'session-token' }) }))
vi.mock('@/api/order/procurement-draft', () => ({ PROCUREMENT_BASE: '/mall-order/app/procurement-drafts' }))
vi.mock('@/api/core/api-base-url', () => ({ buildApiUrl: (path: string) => `https://example.test${path}` }))
vi.mock('@/locales', () => ({ translate: (key: string) => key }))
import { uploadProcurementExcel } from './procurement-upload'
beforeEach(() => {
  vi.clearAllMocks(); mocked.ready.mockResolvedValue(undefined); mocked.session.mockResolvedValue(undefined)
  vi.stubEnv('VITE_TENANT_ID', 'tenant'); vi.stubGlobal('uni', { uploadFile: mocked.upload })
})
describe('采购上传双模式与守卫', () => {
  it.each([['true', '/boot/app/'], ['false', '/mall-order/app/']])('模式 %s 的实际上传保持租户和会话', async (mode, prefix) => {
    vi.stubEnv('VITE_OPEN_BOOT', mode)
    mocked.upload.mockImplementation(options => options.success({ statusCode: 200, data: JSON.stringify({ code: 0, data: { decision: 'CREATED', batchId: 'batch', draftRevision: 1 } }) }))
    expect((await uploadProcurementExcel('draft', 'file.xlsx', { clientRequestId: 'request-id', expectedRevision: 0, explicitNew: false })).batchId).toBe('batch')
    expect(mocked.upload.mock.calls[0][0].url).toBe(`https://example.test${prefix}procurement-drafts/draft/excel`)
    expect(mocked.upload.mock.calls[0][0].header).toEqual({ 'tenant-id': 'tenant', satoken: 'session-token' })
    expect(mocked.session).toHaveBeenCalledWith('mall', 'session-token')
  })
  it('租户或会话预检失败时不上传', async () => {
    mocked.session.mockRejectedValue(new Error('forbidden'))
    await expect(uploadProcurementExcel('draft', 'file.xlsx', { clientRequestId: 'request-id', expectedRevision: 0, explicitNew: false })).rejects.toThrow('forbidden')
    expect(mocked.upload).not.toHaveBeenCalled()
  })
  it('HTTP成功但业务失败时保留失败，不把data当成功', async () => {
    mocked.upload.mockImplementation(options => options.success({ statusCode: 200, data: JSON.stringify({ code: 409, msg: 'revision conflict', data: {} }) }))
    await expect(uploadProcurementExcel('draft', 'file.xlsx', { clientRequestId: 'request-id', expectedRevision: 0, explicitNew: false })).rejects.toThrow('revision conflict')
  })
})

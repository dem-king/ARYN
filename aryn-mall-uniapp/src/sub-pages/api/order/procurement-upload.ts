import type { BatchSubmission } from '@/api/order/procurement-draft'
import { PROCUREMENT_BASE } from '@/api/order/procurement-draft'
import { buildApiUrl } from '@/api/core/api-base-url'
import { parseOpenBoot, rewriteBootUrl } from '@/api/core/boot-url'
import { ensureSessionTenant, ensureTenantReady } from '@/api/core/tenant-identity'
import { useAuthStore } from '@/store/authStore'
import { translate } from '@/locales'

export async function uploadProcurementExcel(draft: string, filePath: string, input: {
  clientRequestId: string, expectedRevision: number, explicitNew: boolean
}): Promise<BatchSubmission> {
  await ensureTenantReady()
  const token = useAuthStore().getToken
  await ensureSessionTenant('mall', token)
  const path = `${PROCUREMENT_BASE}/${encodeURIComponent(draft)}/excel`
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: buildApiUrl(rewriteBootUrl(path, parseOpenBoot(import.meta.env.VITE_OPEN_BOOT)) ?? path),
      filePath,
      name: 'file',
      header: { 'tenant-id': import.meta.env.VITE_TENANT_ID, satoken: token },
      formData: { ...input, expectedRevision: String(input.expectedRevision), explicitNew: String(input.explicitNew) },
      success(response) {
        try {
          const payload: { code: number, msg?: string, data?: BatchSubmission } = JSON.parse(response.data)
          if (response.statusCode < 200 || response.statusCode >= 300 || payload.code !== 0 || !payload.data)
            throw new Error(payload.msg || translate('common.requestFailed'))
          resolve(payload.data)
        }
        catch (error) { reject(error) }
      },
      fail: reject,
    })
  })
}

import {
  buildDeliveryApiUrl,
  buildDeliveryHeaders,
  DELIVERY_AUTH_STORAGE_KEY,
  type DeliveryStoredSession,
} from './core/instance'

interface UploadResponse {
  code?: number
  data?: string
  msg?: string
}

export function uploadDeliveryEvidence(filePath: string) {
  const stored = uni.getStorageSync(DELIVERY_AUTH_STORAGE_KEY) as DeliveryStoredSession | undefined
  return new Promise<string>((resolve, reject) => {
    uni.uploadFile({
      url: buildDeliveryApiUrl('/upms/file/staff/delivery-evidence/upload'),
      filePath,
      name: 'file',
      header: buildDeliveryHeaders(
        stored?.token,
        stored?.tenantId || import.meta.env.VITE_TENANT_ID,
        import.meta.env.VITE_DELIVERY_MINI_APP_ID,
      ),
      success: (response) => {
        try {
          const result = JSON.parse(response.data) as UploadResponse
          if (response.statusCode >= 400 || result.code !== 0 || !result.data) {
            reject(new Error(result.msg || '凭证上传失败'))
            return
          }
          resolve(result.data)
        }
        catch {
          reject(new Error('凭证上传响应格式错误'))
        }
      },
      fail: error => reject(new Error(error.errMsg || '凭证上传失败')),
    })
  })
}

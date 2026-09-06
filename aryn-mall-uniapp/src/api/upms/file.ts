import { buildApiUrl } from '@/api/core/api-base-url'
import { parseOpenBoot, rewriteBootUrl } from '@/api/core/boot-url'
import { alovaInstance } from '@/api/core/instance'
import { Local } from '@/utils/storage'

interface UploadResponse {
  data: string
}

export function uploadImg(filePath: string) {
  return alovaInstance.Post<any>(
    '/upms/file/app/upload',
    {
      filePath,
      name: 'file',
    },
    {
      requestType: 'upload',
      fileType: 'image',
    },
  )
}

export function uploadFile(filePath: string) {
  const authStore = useAuthStore()
  const uploadPath = rewriteBootUrl(
    '/upms/file/app/upload',
    parseOpenBoot(import.meta.env.VITE_OPEN_BOOT),
  ) ?? '/upms/file/app/upload'
  return new Promise<UploadResponse>((resolve, reject) => {
    uni.uploadFile({
      url: buildApiUrl(uploadPath),
      filePath,
      name: 'file',
      header: {
        'tenant-id': import.meta.env.VITE_TENANT_ID,
        'satoken': authStore.getToken,
      },
      success(res) {
        const data = JSON.parse(res.data) as UploadResponse
        resolve(data)
      },
      fail: reject,
    })
  })
}

/** 上传配送凭证并返回受控素材 ID。 */
export function uploadDeliveryEvidence(filePath: string): Promise<string> {
  const uploadPath = rewriteBootUrl(
    '/upms/file/staff/delivery-evidence/upload',
    parseOpenBoot(import.meta.env.VITE_OPEN_BOOT),
  ) ?? '/upms/file/staff/delivery-evidence/upload'
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: buildApiUrl(uploadPath),
      filePath,
      name: 'file',
      header: {
        'tenant-id': import.meta.env.VITE_TENANT_ID,
        'satoken': Local.get('deliveryToken'),
      },
      success(res) {
        try {
          const payload = JSON.parse(res.data) as { data?: string, msg?: string }
          if (res.statusCode === 401 || res.statusCode === 403) {
            Local.remove('deliveryToken')
            uni.reLaunch({ url: '/pages/delivery/login' })
            reject(new Error('配送登录已过期，请重新登录！'))
            return
          }
          if (res.statusCode < 200 || res.statusCode >= 300 || !payload.data) {
            reject(new Error(payload.msg || '凭证上传失败'))
            return
          }
          resolve(payload.data)
        }
        catch {
          reject(new Error('凭证上传响应无效'))
        }
      },
      fail: reject,
    })
  })
}

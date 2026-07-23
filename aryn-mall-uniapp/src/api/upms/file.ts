import { buildApiUrl } from '@/api/core/api-base-url'
import { parseOpenBoot, rewriteBootUrl } from '@/api/core/boot-url'
import { alovaInstance } from '@/api/core/instance'

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

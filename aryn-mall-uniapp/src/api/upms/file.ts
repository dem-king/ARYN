import { buildApiUrl } from '@/api/core/api-base-url'
import { parseOpenBoot, rewriteBootUrl } from '@/api/core/boot-url'
import { assertSessionUsable, convergePreflightUnauthorized } from '@/api/core/handlers'
import { alovaInstance } from '@/api/core/instance'
import { ensureSessionTenant, ensureTenantReady } from '@/api/core/tenant-identity'

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

export async function uploadFile(filePath: string) {
  const authStore = useAuthStore()
  // 上传走 uni.uploadFile 旁路，不经 alova 的 beforeRequest；失效态必须在这里同样短路
  assertSessionUsable()
  const uploadPath = rewriteBootUrl(
    '/upms/file/app/upload',
    parseOpenBoot(import.meta.env.VITE_OPEN_BOOT),
  ) ?? '/upms/file/app/upload'
  // 旁路 uni.uploadFile 与 alova 请求同权：守卫失败零传输（不挂起 Promise，直接 reject）
  await ensureTenantReady()
  const token = authStore.getToken
  if (token) {
    try {
      await ensureSessionTenant('mall', token)
    }
    catch (error) {
      convergePreflightUnauthorized(error, 'mall', token)
      throw error
    }
  }
  return new Promise<UploadResponse>((resolve, reject) => {
    uni.uploadFile({
      url: buildApiUrl(uploadPath),
      filePath,
      name: 'file',
      header: {
        'tenant-id': import.meta.env.VITE_TENANT_ID,
        'satoken': token,
      },
      success(res) {
        const data = JSON.parse(res.data) as UploadResponse
        resolve(data)
      },
      fail: reject,
    })
  })
}

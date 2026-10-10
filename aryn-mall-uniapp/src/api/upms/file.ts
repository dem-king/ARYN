import { buildApiUrl } from '@/api/core/api-base-url'
import { parseOpenBoot, rewriteBootUrl } from '@/api/core/boot-url'
import { alovaInstance } from '@/api/core/instance'
import { ensureSessionTenant, ensureTenantReady } from '@/api/core/tenant-identity'
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

export async function uploadFile(filePath: string) {
  const authStore = useAuthStore()
  const uploadPath = rewriteBootUrl(
    '/upms/file/app/upload',
    parseOpenBoot(import.meta.env.VITE_OPEN_BOOT),
  ) ?? '/upms/file/app/upload'
  // 旁路 uni.uploadFile 与 alova 请求同权：守卫失败零传输（不挂起 Promise，直接 reject）
  await ensureTenantReady()
  const token = authStore.getToken
  if (token) {
    await ensureSessionTenant('mall', token)
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

/** 上传配送凭证并返回受控素材 ID。 */
export async function uploadDeliveryEvidence(filePath: string): Promise<string> {
  const uploadPath = rewriteBootUrl(
    '/upms/file/staff/delivery-evidence/upload',
    parseOpenBoot(import.meta.env.VITE_OPEN_BOOT),
  ) ?? '/upms/file/staff/delivery-evidence/upload'
  await ensureTenantReady()
  const deliveryToken = String(Local.get('deliveryToken') || '')
  if (deliveryToken) {
    try {
      await ensureSessionTenant('delivery', deliveryToken)
    }
    catch (error) {
      // 401 才清配送 scope（比对该 token 仍有效）；403/网络故障保留 token、拒绝上传、不跳登录
      if (isUnauthorizedPreflight(error)
        && String(Local.get('deliveryToken') || '') === (error as { checkedToken?: string }).checkedToken) {
        Local.remove('deliveryToken')
        Local.remove('deliveryStaffInfo')
        uni.reLaunch({ url: '/pages/delivery/login' })
      }
      throw error instanceof Error ? error : new Error('配送身份校验未通过')
    }
  }
  const header: Record<string, string> = {
    'tenant-id': import.meta.env.VITE_TENANT_ID,
    'satoken': deliveryToken,
    'authScope': 'delivery',
  }
  // #ifdef MP
  header['app-id'] = uni.getAccountInfoSync().miniProgram.appId
  // #endif
  // #ifdef MP-WEIXIN
  header['platform-type'] = 'WX_MA'
  // #endif
  // #ifdef APP-PLUS
  header['platform-type'] = 'APP'
  // #endif
  // #ifdef H5
  header['platform-type'] = 'H5'
  // #endif
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: buildApiUrl(uploadPath),
      filePath,
      name: 'file',
      header,
      success(res) {
        try {
          const payload = JSON.parse(res.data) as { data?: string, msg?: string, code?: number }
          // 仅 401 清配送登录态；403 保留 token 拒绝上传（身份配置类 403 与普通业务 403 都不清）
          if ([401].includes(res.statusCode) || [401].includes(payload.code ?? 0)) {
            Local.remove('deliveryToken')
            Local.remove('deliveryStaffInfo')
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

function isUnauthorizedPreflight(error: unknown): boolean {
  return (error as { type?: string })?.type === 'unauthorized'
}

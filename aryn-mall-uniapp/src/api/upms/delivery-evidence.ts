import { buildApiUrl } from '@/api/core/api-base-url'
import { parseOpenBoot, rewriteBootUrl } from '@/api/core/boot-url'
import { assertSessionUsable, convergePreflightUnauthorized } from '@/api/core/handlers'
import { ensureSessionTenant, ensureTenantReady } from '@/api/core/tenant-identity'
import { translate } from '@/locales'
import { useSessionStore } from '@/store/sessionStore'
import { extractSessionInvalidPayload } from '@/utils/session-invalid'
import { Local } from '@/utils/storage'

/** 上传配送凭证并返回受控素材 ID。 */
export async function uploadDeliveryEvidence(filePath: string): Promise<string> {
  // 同 uploadFile：旁路请求在失效态必须零传输，否则会拿死 token 再吃一个 401
  assertSessionUsable()
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
      // 401 才进入失效流程（比对该 token 仍有效）；403/网络故障保留 token、只拒绝本次上传。
      // 收敛到统一 sessionStore：不再裸 reLaunch（无提示、会清页面栈），
      // 由根组件的失效弹窗给配送文案与目标页 `/pages/delivery/login`。
      convergePreflightUnauthorized(error, 'delivery', deliveryToken)
      throw error instanceof Error ? error : new Error(translate('app.file.deliveryIdentityFailed'))
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
          // 仅 401 进入会话失效流程（配送目标）；403 保留 token 拒绝上传（身份配置类 403 与普通业务 403 都不清）。
          // 统一交给 sessionStore 弹窗处理，不再无提示地 reLaunch 登录页。
          if ([401].includes(res.statusCode) || [401].includes(payload.code ?? 0)) {
            useSessionStore().invalidate({
              ...extractSessionInvalidPayload(payload),
              target: 'delivery',
            })
            reject(new Error(translate('common.deliveryLoginExpired')))
            return
          }
          if (res.statusCode < 200 || res.statusCode >= 300 || !payload.data) {
            reject(new Error(payload.msg || translate('app.file.uploadFailed')))
            return
          }
          resolve(payload.data)
        }
        catch {
          reject(new Error(translate('app.file.uploadResponseInvalid')))
        }
      },
      fail: reject,
    })
  })
}

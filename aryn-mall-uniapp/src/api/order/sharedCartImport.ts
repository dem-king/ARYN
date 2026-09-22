/**
 * 补给单导入的文件流（上传解析 / 模板下载）。
 *
 * 与 `sharedCart.ts` 分开的原因：该文件只放 alova 声明式接口，
 * 文件流要走 `uni.uploadFile` / `uni.downloadFile` 并显式处理双模式路径与鉴权头，
 * 混在一起会让「纯接口契约」被传输细节污染。
 *
 * 双模式：路径首段固定为微服务域 `mall-order`，boot 模式下由
 * `rewriteBootUrl` 改写为 `/boot/{去掉首段}` —— 与全站口径一致，
 * 页面里禁止硬编码 `/boot`。
 */
import type { SharedCartImport } from '@/api/order/sharedCart'
import { buildApiUrl } from '@/api/core/api-base-url'
import { parseOpenBoot, rewriteBootUrl } from '@/api/core/boot-url'

const BASE = '/mall-order/app/shared-cart'

/** 解析后的上传路径（boot/cloud 双模式） */
function importPath(id: string, suffix: string) {
  const cloudPath = `${BASE}/${id}${suffix}`
  return buildApiUrl(
    rewriteBootUrl(cloudPath, parseOpenBoot(import.meta.env.VITE_OPEN_BOOT)) ?? cloudPath,
  )
}

/** 模板下载的真实地址（boot/cloud 双模式） */
export function getSharedCartImportTemplateUrl() {
  const cloudPath = `${BASE}/import/template`
  return buildApiUrl(
    rewriteBootUrl(cloudPath, parseOpenBoot(import.meta.env.VITE_OPEN_BOOT)) ?? cloudPath,
  )
}

/** 移动端统一鉴权头（租户 + 登录态） */
function authHeader(): Record<string, string> {
  const authStore = useAuthStore()
  return {
    'tenant-id': import.meta.env.VITE_TENANT_ID,
    'satoken': authStore.getToken,
  }
}

/**
 * 上传补给清单并解析（确认人/发起人）。
 *
 * 解析同步完成、服务端一次性返回完整报告，因此**没有真实进度可回传**：
 * 页面用不确定态 loading 表达「正在解析」，不画假的百分比进度条。
 */
export function previewSharedCartImport(id: string, filePath: string): Promise<SharedCartImport> {
  return new Promise<SharedCartImport>((resolve, reject) => {
    uni.uploadFile({
      url: importPath(id, '/import/preview'),
      filePath,
      name: 'file',
      header: authHeader(),
      success(res) {
        let payload: { code?: number, msg?: string, data?: SharedCartImport }
        try {
          payload = JSON.parse(res.data)
        }
        catch {
          reject(new Error('解析响应无效'))
          return
        }
        if (res.statusCode < 200 || res.statusCode >= 300 || (payload.code ?? 0) !== 200 || !payload.data) {
          reject(new Error(payload.msg || '上传解析失败'))
          return
        }
        resolve(payload.data)
      },
      fail: reject,
    })
  })
}

/**
 * 下载并打开标准模板。
 *
 * 模板由服务端生成（与解析共用同一份列定义），前端不内置 xlsx，
 * 避免"模板改了、解析没改"的静默漂移；下载同样带鉴权头，
 * 不把模板接口挂到网关白名单。
 */
export function downloadSharedCartImportTemplate() {
  return new Promise<void>((resolve, reject) => {
    uni.downloadFile({
      url: getSharedCartImportTemplateUrl(),
      header: authHeader(),
      success: (res) => {
        if (res.statusCode !== 200) {
          reject(new Error('模板下载失败'))
          return
        }
        uni.openDocument({
          filePath: res.tempFilePath,
          fileType: 'xlsx',
          showMenu: true,
          success: () => resolve(),
          fail: () => reject(new Error('无法打开模板文件')),
        })
      },
      fail: () => reject(new Error('模板下载失败')),
    })
  })
}

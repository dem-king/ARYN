import { alovaInstance } from '@/api/core/instance'

export interface CopyrightConfig {
  clientType: string
  logoUrl: string
  name: string
  copyrightText: string
  copyrightLink: string
  icp: string
  icpLink: string
}

/**
 * 版权配置（免登）。
 *
 * 用户端登录页在登录前就要展示品牌/版权，故 skipToken；租户由请求头
 * tenant-id 携带（见 api/core/instance.ts），服务端据此取当前租户配置，
 * 名称/Logo 为空时回落租户品牌。
 */
export function getAppCopyrightConfig() {
  return alovaInstance.Get<CopyrightConfig>('/upms/app/copyright-config', {
    headers: { skipToken: true },
    params: { clientType: 'app' },
  })
}

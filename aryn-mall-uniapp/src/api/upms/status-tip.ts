import { alovaInstance } from '@/api/core/instance'

/**
 * 空态插画配置（C 端免登下发）。
 *
 * 空态/失败态可能出现在登录前的页面（分类、搜索、活动页），故 skipToken；
 * 租户由请求头 tenant-id 携带（见 api/core/instance.ts）。
 * 某项为空串表示该预设位未配置，由前端回落到内置静态图。
 */
export interface StatusTipConfig {
  searchUrl: string
  networkUrl: string
  contentUrl: string
  collectUrl: string
  commentUrl: string
  haloUrl: string
  messageUrl: string
  emptyCartUrl: string
  emptyOrderUrl: string
  emptySeckillUrl: string
}

export function getAppStatusTipConfig() {
  return alovaInstance.Get<StatusTipConfig>('/upms/app/status-tip-config', {
    headers: { skipToken: true },
  })
}

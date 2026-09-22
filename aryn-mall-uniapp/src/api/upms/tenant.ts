import { alovaInstance } from '@/api/core/instance'

export interface TenantShopInfo {
  address?: string
  id: string
  logoUrl?: string
  name: string
  /**
   * 业务模式：1 综合（个人 + 船供并存）；2 纯零售。
   * 后端 SysTenantShopVO 透出，供 C 端决定是否渲染船供入口。
   */
  businessMode?: string
  phone?: string
  siteUrl?: string
}

export function getCurrentShop() {
  return alovaInstance.Get<TenantShopInfo>('/upms/app/tenant/shop-info', {
    headers: { skipToken: true },
  })
}

import { alovaInstance } from '@/api/core/instance'

export interface TenantShopInfo {
  address?: string
  id: string
  logoUrl?: string
  name: string
  phone?: string
  siteUrl?: string
}

export function getCurrentShop() {
  return alovaInstance.Get<TenantShopInfo>('/upms/app/tenant/shop-info', {
    headers: { skipToken: true },
  })
}

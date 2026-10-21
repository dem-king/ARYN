import { ARYN_PLATFORM_TENANT_ID } from '@vben/constants';

export interface TenantBrandInfo {
  logoUrl?: null | string;
  name?: null | string;
}

export interface AdminBrand {
  logo?: string;
  name?: string;
}

/**
 * 平台租户继续使用平台品牌；普通租户（含平台管理员切换进入的租户）
 * 优先展示租户自身的名称与 Logo，字段为空时由布局层回退平台品牌。
 */
export function resolveAdminBrand(
  currentTenantId: null | string | undefined,
  shopInfo: null | TenantBrandInfo | undefined,
): AdminBrand {
  if (
    !currentTenantId ||
    currentTenantId === ARYN_PLATFORM_TENANT_ID ||
    !shopInfo
  ) {
    return {};
  }
  return {
    logo: shopInfo.logoUrl?.trim() || undefined,
    name: shopInfo.name?.trim() || undefined,
  };
}

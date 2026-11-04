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
 *
 * <p>是否平台管理员由登录用户信息里的 `tenantType` 判定（见 basic.vue），
 * 本函数只按「当前生效租户」决定品牌：平台租户未切换时传入空值，走平台品牌。</p>
 */
export function resolveAdminBrand(
  currentTenantId: null | string | undefined,
  shopInfo: null | TenantBrandInfo | undefined,
): AdminBrand {
  if (!currentTenantId || !shopInfo) {
    return {};
  }
  return {
    logo: shopInfo.logoUrl?.trim() || undefined,
    name: shopInfo.name?.trim() || undefined,
  };
}

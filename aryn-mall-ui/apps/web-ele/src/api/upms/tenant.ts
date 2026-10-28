import { requestClient } from '#/api/request';

export interface TenantShopInfo {
  address?: string;
  id: string;
  logoUrl?: string;
  name: string;
  phone?: string;
  siteUrl?: string;
}

export interface TenantOverview {
  adminUserCount?: null | number;
  authBeginTime?: null | string;
  authEndTime?: null | string;
  businessMode?: null | string;
  goodsCount?: null | number;
  id: string;
  lastLoginTime?: null | string;
  mallUserCount?: null | number;
  materialCount?: null | number;
  name: string;
  onlineSessionCount?: null | number;
  orderCount?: null | number;
  packageId?: null | string;
  packageName?: null | string;
  remainingDays?: null | number;
  status?: null | string;
  storageBytes?: null | number;
}

/**
 * 获取当前租户门店公开信息
 */
export async function getShopInfo() {
  return requestClient.get<null | TenantShopInfo>('/upms/app/tenant/shop-info');
}

/**
 * 获取租户分页
 */
export async function getPage(query: any) {
  return requestClient.get('/upms/tenant/page', {
    params: query,
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 根据ID获取租户
 */
export async function getById(id: string) {
  return requestClient.get(`/upms/tenant/${id}`, {
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 添加租户
 */
export async function addObj(data: any) {
  return requestClient.post('/upms/tenant', data, {
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 编辑租户
 */
export async function editObj(data: any) {
  return requestClient.put('/upms/tenant', data, {
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 删除租户
 */
export async function delObj(id: string) {
  return requestClient.delete(`/upms/tenant/${id}`, {
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 租户概览
 */
export async function getOverview(id: string) {
  return requestClient.get<TenantOverview>(`/upms/tenant/${id}/overview`, {
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 启用/停用租户
 */
export async function changeStatus(id: string, status: string) {
  return requestClient.put(`/upms/tenant/${id}/status`, null, {
    params: { status },
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 租户续期
 */
export async function renew(id: string, days: number) {
  return requestClient.put(`/upms/tenant/${id}/renew`, null, {
    params: { days },
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 强制下线租户全部会话
 */
export async function kickoutSessions(id: string) {
  return requestClient.delete(`/upms/tenant/${id}/sessions`, {
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 记录平台切换到目标租户（审计）
 */
export async function auditTenantSwitch(id: string) {
  return requestClient.post(`/upms/tenant/${id}/switch-audit`, null);
}

/**
 * 获取租户列表
 */
export async function getList(query: any) {
  return requestClient.get('/upms/tenant/list', {
    params: query,
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 查询租户菜单
 */
export async function getTenantMenuList(tenantId: string) {
  return requestClient.get(`/upms/tenant-menu/list/${tenantId}`);
}

/**
 * 保存租户菜单
 */
export async function saveTenantMenu(data: any) {
  return requestClient.post('/upms/tenant-menu/save', data, {
    headers: {
      isSwitchTenant: false,
    },
  });
}

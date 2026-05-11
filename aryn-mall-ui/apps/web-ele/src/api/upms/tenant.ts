import { requestClient } from '#/api/request';

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

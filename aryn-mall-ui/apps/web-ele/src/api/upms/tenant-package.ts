import { requestClient } from '#/api/request';

/**
 * 获取租户套餐分页
 */
export async function getPage(query: any) {
  return requestClient.get('/upms/tenantpackage/page', {
    params: query,
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 获取租户套餐列表
 */
export async function getList(query: any) {
  return requestClient.get('/upms/tenantpackage/list', {
    params: query,
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 根据ID获取租户套餐
 */
export async function getById(id: any) {
  return requestClient.get(`/upms/tenantpackage/${id}`, {
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 添加租户套餐
 */
export async function addObj(data: any) {
  return requestClient.post('/upms/tenantpackage', data, {
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 编辑租户套餐
 */
export async function editObj(data: any) {
  return requestClient.put('/upms/tenantpackage', data, {
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 删除租户套餐
 */
export async function delObj(id: string) {
  return requestClient.delete(`/upms/tenantpackage/${id}`, {
    headers: {
      isSwitchTenant: false,
    },
  });
}

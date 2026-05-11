import { requestClient } from '#/api/request';

/**
 * 获取字典值分页
 */
export async function getPage(query: any) {
  return requestClient.get('/upms/dictvalue/page', {
    params: query,
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 根据ID获取字典值
 */
export async function getById(id: string) {
  return requestClient.get(`/upms/dictvalue/${id}`, {
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 添加字典值
 */
export async function addObj(data: any) {
  return requestClient.post('/upms/dictvalue', data, {
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 编辑字典值
 */
export async function editObj(data: any) {
  return requestClient.put('/upms/dictvalue', data, {
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 删除字典值
 */
export async function delObj(id: string) {
  return requestClient.delete(`/upms/dictvalue/${id}`, {
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 根据类型获取字典值列表
 */
export async function getList(type: string) {
  return requestClient.get(`/upms/dictvalue/type/${type}`, {
    headers: {
      isSwitchTenant: false,
    },
  });
}

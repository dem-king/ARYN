import { requestClient } from '#/api/request';

/**
 * 获取字典分页
 */
export async function getPage(query: any) {
  return requestClient.get('/upms/dict/page', {
    params: query,
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 根据ID获取字典
 */
export async function getById(id: string) {
  return requestClient.get(`/upms/dict/${id}`, {
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 添加字典
 */
export async function addObj(data: any) {
  return requestClient.post('/upms/dict', data, {
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 编辑字典
 */
export async function editObj(data: any) {
  return requestClient.put('/upms/dict', data, {
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 删除字典
 */
export async function delObj(id: string) {
  return requestClient.delete(`/upms/dict/${id}`, {
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 刷新字典缓存
 */
export async function refresh() {
  return requestClient.get('/upms/dict/refresh', {
    headers: {
      isSwitchTenant: false,
    },
  });
}

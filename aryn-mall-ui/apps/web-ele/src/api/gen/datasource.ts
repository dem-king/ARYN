import { requestClient } from '#/api/request';

/**
 * 获取数据源分页
 */
export async function getPage(query: any) {
  return requestClient.get('/gen/gen-datasource/page', {
    params: query,
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 获取全部数据源
 */
export async function getList(query: any) {
  return requestClient.get('/gen/gen-datasource/list', {
    params: query,
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 根据ID获取数据源
 */
export async function getById(id: string) {
  return requestClient.get(`/gen/gen-datasource/${id}`, {
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 添加数据源
 */
export async function addObj(data: any) {
  return requestClient.post('/gen/gen-datasource', data, {
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 编辑数据源
 */
export async function editObj(data: any) {
  return requestClient.put('/gen/gen-datasource', data, {
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 删除数据源
 */
export async function delObj(id: string) {
  return requestClient.delete(`/gen/gen-datasource/${id}`, {
    headers: {
      isSwitchTenant: false,
    },
  });
}

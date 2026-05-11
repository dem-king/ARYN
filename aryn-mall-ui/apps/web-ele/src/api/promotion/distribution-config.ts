import { requestClient } from '#/api/request';

/**
 * 获取分销配置分页
 */
export async function getPage(query: any) {
  return requestClient.get('/promotion/distribution/config/page', {
    params: query,
  });
}

/**
 * 根据ID获取分销配置
 */
export async function getById(id: string) {
  return requestClient.get(`/promotion/distribution/config/${id}`);
}

/**
 * 新增分销配置
 */
export async function addObj(data: any) {
  return requestClient.post('/promotion/distribution/config', data);
}

/**
 * 编辑分销配置
 */
export async function editObj(data: any) {
  return requestClient.put('/promotion/distribution/config', data);
}

/**
 * 删除分销配置
 */
export async function delObj(id: string) {
  return requestClient.delete(`/promotion/distribution/config/${id}`);
}

/**
 * 启用分销配置
 */
export async function enableConfig(id: string) {
  return requestClient.put(`/promotion/distribution/config/enable/${id}`);
}

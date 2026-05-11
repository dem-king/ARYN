import { requestClient } from '#/api/request';

/**
 * 获取积分配置分页
 */
export async function getPage(query: any) {
  return requestClient.get('/mall-user/pointsconfig/page', { params: query });
}

/**
 * 根据ID获取积分配置
 */
export async function getById(id: string) {
  return requestClient.get(`/mall-user/pointsconfig/${id}`);
}

/**
 * 添加积分配置
 */
export async function addObj(data: any) {
  return requestClient.post('/mall-user/pointsconfig', data);
}

/**
 * 编辑积分配置
 */
export async function editObj(data: any) {
  return requestClient.put('/mall-user/pointsconfig', data);
}

/**
 * 删除积分配置
 */
export async function delObj(id: string) {
  return requestClient.delete(`/mall-user/pointsconfig/${id}`);
}

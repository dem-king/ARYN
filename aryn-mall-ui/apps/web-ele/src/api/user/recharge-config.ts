import { requestClient } from '#/api/request';

/**
 * 获取储值配置分页
 */
export async function getPage(query: any) {
  return requestClient.get('/mall-user/rechargeconfig/page', { params: query });
}

/**
 * 根据ID获取储值配置
 */
export async function getById(id: string) {
  return requestClient.get(`/mall-user/rechargeconfig/${id}`);
}

/**
 * 添加储值配置
 */
export async function addObj(data: any) {
  return requestClient.post('/mall-user/rechargeconfig', data);
}

/**
 * 编辑储值配置
 */
export async function editObj(data: any) {
  return requestClient.put('/mall-user/rechargeconfig', data);
}

/**
 * 删除储值配置
 */
export async function delObj(id: string) {
  return requestClient.delete(`/mall-user/rechargeconfig/${id}`);
}

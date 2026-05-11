import { requestClient } from '#/api/request';

/**
 * 获取签到配置分页
 */
export async function getPage(query: any) {
  return requestClient.get('/mall-user/signinconfig/page', { params: query });
}

/**
 * 根据ID获取签到配置
 */
export async function getById(id: string) {
  return requestClient.get(`/mall-user/signinconfig/${id}`);
}

/**
 * 添加签到配置
 */
export async function addObj(data: any) {
  return requestClient.post('/mall-user/signinconfig', data);
}

/**
 * 编辑签到配置
 */
export async function editObj(data: any) {
  return requestClient.put('/mall-user/signinconfig', data);
}

/**
 * 删除签到配置
 */
export async function delObj(id: string) {
  return requestClient.delete(`/mall-user/signinconfig/${id}`);
}

import { requestClient } from '#/api/request';

/**
 * 获取分销用户分页
 */
export async function getPage(query: any) {
  return requestClient.get('/promotion/distribution/user/page', {
    params: query,
  });
}

/**
 * 根据ID获取分销用户
 */
export async function getById(id: string) {
  return requestClient.get(`/promotion/distribution/user/${id}`);
}

/**
 * 注册分销用户
 */
export async function register(data: any) {
  return requestClient.post('/promotion/distribution/user/register', data);
}

/**
 * 启用分销用户
 */
export async function enable(userId: string) {
  return requestClient.put(`/promotion/distribution/user/enable/${userId}`);
}

/**
 * 禁用分销用户
 */
export async function disable(userId: string) {
  return requestClient.put(`/promotion/distribution/user/disable/${userId}`);
}

/**
 * 删除分销用户
 */
export async function delObj(id: string) {
  return requestClient.delete(`/promotion/distribution/user/${id}`);
}

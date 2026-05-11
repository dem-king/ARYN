import { requestClient } from '#/api/request';

/**
 * 获取用户信息分页
 */
export async function getPage(query: any) {
  return requestClient.get('/mall-user/userinfo/page', { params: query });
}

/**
 * 根据ID获取用户信息
 */
export async function getById(id: string) {
  return requestClient.get(`/mall-user/userinfo/${id}`);
}

/**
 * 添加用户信息
 */
export async function addObj(data: any) {
  return requestClient.post('/mall-user/userinfo', data);
}

/**
 * 编辑用户信息
 */
export async function editObj(data: any) {
  return requestClient.put('/mall-user/userinfo', data);
}

/**
 * 绑定用户
 */
export async function bindObj(data: any) {
  return requestClient.post('/mall-user/userinfo/bind', data);
}

/**
 * 解绑用户
 */
export async function unbindObj(id: string) {
  return requestClient.delete(`/mall-user/userinfo/unbind/${id}`);
}

/**
 * 获取用户数量
 */
export async function getCount(query: any) {
  return requestClient.get('/mall-user/userinfo/count', { params: query });
}

/**
 * 删除用户信息
 */
export async function delObj(id: string) {
  return requestClient.delete(`/mall-user/userinfo/${id}`);
}

/**
 * 获取用户统计信息
 */
export async function getStatistics() {
  return requestClient.get('/mall-user/userinfo/statistics');
}

/**
 * 获取团队分页
 */
export async function getTeamPage(query: any) {
  return requestClient.get('/mall-user/userinfo/team-page', { params: query });
}

/**
 * 调整积分
 */
export async function adjustPoint(data: any) {
  return requestClient.post('/mall-user/userinfo/adjustPoint', data);
}

/**
 * 调整余额
 */
export async function adjustBalance(data: any) {
  return requestClient.post('/mall-user/userinfo/adjustBalance', data);
}

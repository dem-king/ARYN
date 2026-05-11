import { requestClient } from '#/api/request';

/**
 * 获取用户分页
 */
export async function getPage(query: any) {
  return requestClient.get('/upms/user/page', {
    params: query,
  });
}

/**
 * 根据ID获取用户
 */
export async function getById(id: string) {
  return requestClient.get(`/upms/user/${id}`);
}

/**
 * 添加用户
 */
export async function addObj(data: any) {
  return requestClient.post('/upms/user', data);
}

/**
 * 编辑用户
 */
export async function editObj(data: any) {
  return requestClient.put('/upms/user', data);
}

/**
 * 删除用户
 */
export async function delObj(id: string) {
  return requestClient.delete(`/upms/user/${id}`);
}

/**
 * 检查手机号数量
 */
export async function checkPhoneCount(query: any) {
  return requestClient.get('/upms/user/check/phone', {
    params: query,
    headers: {
      isToken: false,
    },
  });
}

/**
 * 修改密码
 */
export async function editPassword(data: any) {
  return requestClient.post('/upms/user/password', data);
}

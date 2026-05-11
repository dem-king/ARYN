import { requestClient } from '#/api/request';

/**
 * 获取三方平台账号分页
 */
export async function getPage(query: any) {
  return requestClient.get('/user/socialAccount/page', {
    params: query,
  });
}

/**
 * 根据ID获取三方平台账号
 */
export async function getById(id: string) {
  return requestClient.get(`/user/socialAccount/${id}`);
}

/**
 * 添加三方平台账号
 */
export async function addObj(data: any) {
  return requestClient.post('/user/socialAccount', data);
}

/**
 * 编辑三方平台账号
 */
export async function editObj(data: any) {
  return requestClient.put('/user/socialAccount', data);
}

/**
 * 删除三方平台账号
 */
export async function delObj(id: string) {
  return requestClient.delete(`/user/socialAccount/${id}`);
}

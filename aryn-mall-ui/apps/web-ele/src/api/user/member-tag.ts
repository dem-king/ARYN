import { requestClient } from '#/api/request';

/**
 * 获取会员标签分页
 */
export async function getPage(query: any) {
  return requestClient.get('/mall-user/membertag/page', { params: query });
}

/**
 * 获取会员标签全量列表
 */
export async function getList() {
  return requestClient.get('/mall-user/membertag/list');
}

/**
 * 根据ID获取会员标签
 */
export async function getById(id: string) {
  return requestClient.get(`/mall-user/membertag/${id}`);
}

/**
 * 添加会员标签
 */
export async function addObj(data: any) {
  return requestClient.post('/mall-user/membertag', data);
}

/**
 * 编辑会员标签
 */
export async function editObj(data: any) {
  return requestClient.put('/mall-user/membertag', data);
}

/**
 * 删除会员标签
 */
export async function delObj(id: string) {
  return requestClient.delete(`/mall-user/membertag/${id}`);
}

/**
 * 给用户打标签
 */
export async function tagUser(data: any) {
  return requestClient.post('/mall-user/membertag/tagUser', data);
}

/**
 * 取消用户标签
 */
export async function untagUser(data: any) {
  return requestClient.post('/mall-user/membertag/untagUser', data);
}

/**
 * 获取用户标签列表
 */
export async function getUserTags(userId: string) {
  return requestClient.get('/mall-user/membertag/userTags', {
    params: { userId },
  });
}

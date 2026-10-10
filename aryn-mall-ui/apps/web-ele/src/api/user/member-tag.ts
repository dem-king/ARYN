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
 *
 * 后端签名为 tagUser(@RequestParam userId, @RequestBody List<String> tagIds)：
 * body 必须是标签 ID 数组，userId 走查询参数。
 */
export async function tagUser(userId: string, tagIds: string[]) {
  return requestClient.post('/mall-user/membertag/tagUser', tagIds, {
    params: { userId },
  });
}

/**
 * 取消用户标签
 *
 * 后端签名为 untagUser(@RequestParam userId, @RequestBody List<String> tagIds)。
 */
export async function untagUser(userId: string, tagIds: string[]) {
  return requestClient.post('/mall-user/membertag/untagUser', tagIds, {
    params: { userId },
  });
}

/**
 * 获取用户标签列表
 */
export async function getUserTags(userId: string) {
  return requestClient.get('/mall-user/membertag/userTags', {
    params: { userId },
  });
}

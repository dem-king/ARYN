import { requestClient } from '#/api/request';

/**
 * 获取积分商品分页
 */
export async function getPage(query: any) {
  return requestClient.get('/pointsgoods/page', { params: query });
}

/**
 * 根据ID获取积分商品
 */
export async function getById(id: string) {
  return requestClient.get(`/pointsgoods/${id}`);
}

/**
 * 添加积分商品
 */
export async function addObj(data: any) {
  return requestClient.post('/pointsgoods', data);
}

/**
 * 编辑积分商品
 */
export async function editObj(data: any) {
  return requestClient.put('/pointsgoods', data);
}

/**
 * 删除积分商品
 */
export async function delObj(id: string) {
  return requestClient.delete(`/pointsgoods/${id}`);
}

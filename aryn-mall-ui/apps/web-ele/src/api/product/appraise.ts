import { requestClient } from '#/api/request';

/**
 * 获取商品评价分页
 */
export async function getPage(query: any) {
  return requestClient.get('/product/appraise/page', { params: query });
}

/**
 * 获取商品评价列表
 */
export async function getList(query: any) {
  return requestClient.get('/product/appraise/list', { params: query });
}

/**
 * 根据ID获取商品评价
 */
export async function getById(id: string) {
  return requestClient.get(`/product/appraise/${id}`);
}

/**
 * 添加商品评价
 */
export async function addObj(data: any) {
  return requestClient.post('/product/appraise', data);
}

/**
 * 编辑商品评价
 */
export async function editObj(data: any) {
  return requestClient.put('/product/appraise', data);
}

/**
 * 删除商品评价
 */
export async function delObj(id: string) {
  return requestClient.delete(`/product/appraise/${id}`);
}
/**
 * 回复评价
 */
export async function replyObj(data: any) {
  return requestClient.post('/product/appraise/reply', data);
}

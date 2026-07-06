import { requestClient } from '#/api/request';

/**
 * 获取品牌分页
 */
export async function getPage(query: any) {
  return requestClient.get('/product/brand/page', { params: query });
}

/**
 * 获取品牌列表
 */
export async function getList(query?: any) {
  return requestClient.get('/product/brand/list', { params: query });
}

/**
 * 根据ID获取品牌
 */
export async function getById(id: string) {
  return requestClient.get(`/product/brand/${id}`);
}

/**
 * 添加品牌
 */
export async function addObj(data: any) {
  return requestClient.post('/product/brand', data);
}

/**
 * 编辑品牌
 */
export async function editObj(data: any) {
  return requestClient.put('/product/brand', data);
}

/**
 * 删除品牌
 */
export async function delObj(id: string) {
  return requestClient.delete(`/product/brand/${id}`);
}

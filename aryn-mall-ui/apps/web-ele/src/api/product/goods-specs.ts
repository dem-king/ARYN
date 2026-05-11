import { requestClient } from '#/api/request';

/**
 * 获取商品规格分页
 */
export async function getPage(query: any) {
  return requestClient.get('/product/goodsspecs/page', { params: query });
}

/**
 * 根据ID获取商品规格
 */
export async function getById(id: string) {
  return requestClient.get(`/product/goodsspecs/${id}`);
}

/**
 * 添加商品规格
 */
export async function addObj(data: any) {
  return requestClient.post('/product/goodsspecs', data);
}

/**
 * 编辑商品规格
 */
export async function editObj(data: any) {
  return requestClient.put('/product/goodsspecs', data);
}

/**
 * 删除商品规格
 */
export async function delObj(id: string) {
  return requestClient.delete(`/product/goodsspecs/${id}`);
}

/**
 * 获取商品规格列表
 */
export async function getList(query: any) {
  return requestClient.get('/product/goodsspecs/list', { params: query });
}

import { requestClient } from '#/api/request';

/**
 * 获取商品规格值分页
 */
export async function getPage(query: any) {
  return requestClient.get('/product/goodsspecsvalue/page', { params: query });
}

/**
 * 根据ID获取商品规格值
 */
export async function getById(id: string) {
  return requestClient.get(`/product/goodsspecsvalue/${id}`);
}

/**
 * 添加商品规格值
 */
export async function addObj(data: any) {
  return requestClient.post('/product/goodsspecsvalue', data);
}

/**
 * 编辑商品规格值
 */
export async function editObj(data: any) {
  return requestClient.put('/product/goodsspecsvalue', data);
}

/**
 * 删除商品规格值
 */
export async function delObj(id: string) {
  return requestClient.delete(`/product/goodsspecsvalue/${id}`);
}

/**
 * 获取商品规格值列表
 */
export async function getList(query: any) {
  return requestClient.get('/product/goodsspecsvalue/list', { params: query });
}

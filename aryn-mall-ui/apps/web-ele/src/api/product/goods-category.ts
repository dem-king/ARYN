import { requestClient } from '#/api/request';

/**
 * 获取商品分类树
 */
export async function getPage() {
  return requestClient.get('/product/goodscategory/tree');
}

/**
 * 获取商品分类列表
 */
export async function getList(query: any) {
  return requestClient.get('/product/goodscategory/list', { params: query });
}

/**
 * 根据ID获取商品分类
 */
export async function getById(id: string) {
  return requestClient.get(`/product/goodscategory/${id}`);
}

/**
 * 添加商品分类
 */
export async function addObj(data: any) {
  return requestClient.post('/product/goodscategory', data);
}

/**
 * 编辑商品分类
 */
export async function editObj(data: any) {
  return requestClient.put('/product/goodscategory', data);
}

/**
 * 删除商品分类
 */
export async function delObj(id: string) {
  return requestClient.delete(`/product/goodscategory/${id}`);
}

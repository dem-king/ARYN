import { requestClient } from '#/api/request';

/**
 * 获取商品分页
 */
export async function getPage(query: any) {
  return requestClient.get('/product/goodsspu/page', { params: query });
}

/**
 * 获取仓库商品分页
 */
export async function getWarehousePage(query: any) {
  return requestClient.get('/product/goodsspu/warehouse/page', {
    params: query,
  });
}

/**
 * 根据ID获取商品
 */
export async function getById(id: string) {
  return requestClient.get(`/product/goodsspu/${id}`);
}

/**
 * 根据多个ID获取商品
 */
export async function getByIds(ids: any) {
  return requestClient.get(`/product/goodsspu/byIds/${ids}`);
}

/**
 * 添加商品
 */
export async function addObj(data: any) {
  return requestClient.post('/product/goodsspu', data);
}

/**
 * 编辑商品
 */
export async function editObj(data: any) {
  return requestClient.put('/product/goodsspu', data);
}

/**
 * 删除商品
 */
export async function delObj(id: string) {
  return requestClient.delete(`/product/goodsspu/${id}`);
}

/**
 * 商品上下架
 */
export async function goodsShelf(data: any) {
  return requestClient.post('/product/goodsspu/goods-shelf', data);
}

/**
 * 读取商品专区标签（SPU + SKU 覆盖）
 */
export async function getZoneTags(spuId: string) {
  return requestClient.get(`/product/goodsspu/tags/${spuId}`);
}

/**
 * 批量打/取消会员与出清标签
 *
 * @deprecated 专区接口契约统一收敛到 `#/api/product/goods-zone`（契约 §3.1），
 * 此处仅保留 re-export 兼容旧调用，新代码请直接从 `#/api/product/goods-zone` 引入。
 */
export { batchZoneTags } from './goods-zone';

/**
 * 单商品专区标签精调（含 SKU 覆盖）
 */
export async function saveZoneTags(spuId: string, data: any) {
  return requestClient.put(`/product/goodsspu/${spuId}/tags`, data);
}

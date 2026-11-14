import { requestClient } from '#/api/request';

/** 价格政策（协议价）列表 */
export async function getPricePolicies(params?: any) {
  return requestClient.get('/promotion/price-policy/list', { params });
}

/** 价格政策详情（含 SKU 明细） */
export async function getPricePolicyDetail(id: string) {
  return requestClient.get(`/promotion/price-policy/${id}`);
}

/** 新建价格政策 */
export async function addPricePolicy(data: any) {
  return requestClient.post('/promotion/price-policy', data);
}

/** 修改价格政策（生效中不可改） */
export async function updatePricePolicy(id: string, data: any) {
  return requestClient.put(`/promotion/price-policy/${id}`, data);
}

/** 发布/停用/转草稿（targetStatus：1生效 2停用 0草稿） */
export async function changePricePolicyStatus(
  id: string,
  targetStatus: string,
) {
  return requestClient.put(
    `/promotion/price-policy/${id}/status/${targetStatus}`,
  );
}

/** 删除价格政策 */
export async function deletePricePolicy(id: string) {
  return requestClient.delete(`/promotion/price-policy/${id}`);
}

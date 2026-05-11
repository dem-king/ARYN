import { requestClient } from '#/api/request';

/**
 * 获取优惠券信息分页
 */
export async function getPage(query: any) {
  return requestClient.get('/promotion/couponinfo/page', { params: query });
}

/**
 * 根据ID获取优惠券信息
 */
export async function getById(id: string) {
  return requestClient.get(`/promotion/couponinfo/${id}`);
}

/**
 * 添加优惠券信息
 */
export async function addObj(data: any) {
  return requestClient.post('/promotion/couponinfo', data);
}

/**
 * 删除优惠券信息
 */
export async function delObj(id: string) {
  return requestClient.delete(`/promotion/couponinfo/${id}`);
}

/**
 * 编辑优惠券信息
 */
export async function editObj(data: any) {
  return requestClient.put('/promotion/couponinfo', data);
}

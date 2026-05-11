import { requestClient } from '#/api/request';

/**
 * 获取用户优惠券分页
 */
export async function getPage(query: any) {
  return requestClient.get('/promotion/couponuser/page', { params: query });
}

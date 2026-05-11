import { requestClient } from '#/api/request';

/**
 * 获取余额记录分页
 */
export async function getPage(query: any) {
  return requestClient.get('/mall-user/balancerecord/page', { params: query });
}

/**
 * 获取用户余额记录分页
 */
export async function getUserPage(query: any) {
  return requestClient.get('/mall-user/balancerecord/user/page', {
    params: query,
  });
}

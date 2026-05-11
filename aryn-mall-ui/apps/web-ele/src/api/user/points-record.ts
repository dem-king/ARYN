import { requestClient } from '#/api/request';

/**
 * 获取积分记录分页
 */
export async function getPage(query: any) {
  return requestClient.get('/mall-user/pointsrecord/page', { params: query });
}

/**
 * 获取用户积分记录分页
 */
export async function getUserPage(query: any) {
  return requestClient.get('/mall-user/pointsrecord/user/page', {
    params: query,
  });
}

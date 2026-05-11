import { requestClient } from '#/api/request';

/**
 * 获取签到记录分页
 */
export async function getPage(query: any) {
  return requestClient.get('/mall-user/signinrecord/page', { params: query });
}

import { requestClient } from '#/api/request';

/**
 * 获取三方平台用户分页
 */
export async function getPage(query: any) {
  return requestClient.get('/user/socialUser/page', {
    params: query,
  });
}
